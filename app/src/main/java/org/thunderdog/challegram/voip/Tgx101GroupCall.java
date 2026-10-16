/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/plumagram-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.voip;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.service.Tgx101GroupCallService;
import org.thunderdog.challegram.telegram.GroupCallListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import me.vkryl.core.reference.ReferenceList;

/**
 * TGx101: one voice chat (group call) session, audio only. Native part: jni/tgvoip/tgx101_group.cpp.
 * Only one voice chat at a time; the tgcalls group module runs only while a voice chat is open.
 */
public final class Tgx101GroupCall implements GroupCallListener {
  public interface Listener {
    default void onGroupCallStateChanged (Tgx101GroupCall call) { }
    default void onGroupCallParticipantsChanged (Tgx101GroupCall call) { }
    default void onGroupCallSpeakingChanged (Tgx101GroupCall call) { }
    default void onGroupCallMessagesChanged (Tgx101GroupCall call) { }
  }

  public static final int STATE_CONNECTING = 0, STATE_CONNECTED = 1, STATE_ENDED = 2;

  /** TGx101 (user 2026-10-14, step 2): the whole app learns about the running voice chat — the bar under the header */
  public interface GlobalListener {
    void onCurrentGroupCallUpdated (@Nullable Tgx101GroupCall call);
  }

  private static final ReferenceList<GlobalListener> globalListeners = new ReferenceList<>(true);

  public static void addGlobalListener (GlobalListener listener) {
    globalListeners.add(listener);
  }

  public static void removeGlobalListener (GlobalListener listener) {
    globalListeners.remove(listener);
  }

  /** The running voice chat (not ended), or null */
  public static @Nullable Tgx101GroupCall active () {
    return current != null && current.state != STATE_ENDED ? current : null;
  }

  private static void notifyGlobal () {
    UI.post(() -> {
      Tgx101GroupCall call = active();
      for (GlobalListener listener : globalListeners) listener.onCurrentGroupCallUpdated(call);
    });
  }

  private static @Nullable Tgx101GroupCall current;

  public static @Nullable Tgx101GroupCall current () {
    return current;
  }

  public final Tdlib tdlib;
  public final long chatId;
  public volatile int groupCallId; // a conference gets its id once it is created
  /** A group call not bound to a chat (a 1:1 call grown into a group one): end-to-end encrypted */
  public final boolean isConference;
  private final long[] inviteUserIds;
  private @Nullable TdApi.InputGroupCall joinInput; // a conference joined by its invite message
  /** the 1:1 call this conference grew from: ended once the conference has its link, so the other side follows it */
  private int upgradeCallId;
  private long upgradePeerUserId;
  private final String inviteHash;

  private long ptr;
  private int state = STATE_CONNECTING;
  private boolean muted = true, speaker = true, joinRequested;
  private int mySsrc;
  private @Nullable TdApi.GroupCall groupCall;
  private final LinkedHashMap<String, TdApi.GroupCallParticipant> participants = new LinkedHashMap<>();
  private final Set<Integer> speakingSsrcs = new HashSet<>();
  private boolean selfSpeaking;
  private long lastSpeakingDispatch;
  private final ReferenceList<Listener> listeners = new ReferenceList<>(true);
  // TGx101 (approved mockup «Групповой звонок», 2026-10-14): the call's own messages and the protection emojis
  private final List<TdApi.GroupCallMessage> messages = new ArrayList<>();
  private int unreadMessages;
  private @Nullable String[] verificationEmojis;
  private final long joinedAt = android.os.SystemClock.elapsedRealtime();

  private Tgx101GroupCall (Tdlib tdlib, long chatId, int groupCallId, @Nullable String inviteHash) {
    this(tdlib, chatId, groupCallId, inviteHash, false, null);
  }

  private Tgx101GroupCall (Tdlib tdlib, long chatId, int groupCallId, @Nullable String inviteHash, boolean isConference, @Nullable long[] inviteUserIds) {
    this.tdlib = tdlib;
    this.chatId = chatId;
    this.groupCallId = groupCallId;
    this.isConference = isConference;
    this.inviteUserIds = inviteUserIds != null ? inviteUserIds : new long[0];
    this.inviteHash = inviteHash != null ? inviteHash : "";
  }

  // Start

  /** Joins the voice chat, or returns the one already running for this group call. Microphone permission must be granted. */
  @UiThread
  public static Tgx101GroupCall join (Tdlib tdlib, long chatId, int groupCallId, @Nullable String inviteHash) {
    if (current != null) {
      if (current.tdlib == tdlib && current.groupCallId == groupCallId && current.state != STATE_ENDED) {
        return current;
      }
      current.leave();
    }
    Tgx101GroupCall call = new Tgx101GroupCall(tdlib, chatId, groupCallId, inviteHash);
    current = call;
    call.start();
    notifyGlobal();
    return call;
  }

  /**
   * TGx101 (user 2026-10-14 «добавить участника из окна звонка»): a new group call not bound to a chat — created and
   * joined at once, then the given people are invited (as in Telegram: a 1:1 call grows into a group call)
   */
  @UiThread
  public static Tgx101GroupCall startConference (Tdlib tdlib, long[] inviteUserIds) {
    return startConference(tdlib, inviteUserIds, 0, 0);
  }

  /**
   * (group call logs 2026-10-16: the 1:1 call was hung up first and the conference created after — the other side saw
   * «call ended» and only an invite to accept; nobody joined) As in Telegram: the conference is created first, then the
   * 1:1 call ends with the conference's link, and the other side's app moves into it by itself
   */
  @UiThread
  public static Tgx101GroupCall startConference (Tdlib tdlib, long[] inviteUserIds, int upgradeCallId, long upgradePeerUserId) {
    if (current != null) {
      current.leave();
    }
    Tgx101GroupCall call = new Tgx101GroupCall(tdlib, 0, 0, null, true, inviteUserIds);
    call.upgradeCallId = upgradeCallId;
    call.upgradePeerUserId = upgradePeerUserId;
    current = call;
    call.start();
    notifyGlobal();
    // (review) the 1:1 call's service resets the audio mode to normal ~5 s after it ends — take the call mode back then
    UI.post(() -> {
      if (current == call && call.state != STATE_ENDED) {
        final boolean on = call.speaker;
        Tgx101Audio.run(() -> {
          AudioManager audio = audioManager();
          if (audio != null) {
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audio.setSpeakerphoneOn(on);
          }
        });
      }
    }, 6500);
    return call;
  }

  // (group call logs 2026-10-16 11:35: the official app turned the 1:1 call into a conference — here it ended as «hung up»
  // and an invite came a second later; the official app follows such an invite by itself) — so do we
  private static int lastEndedAccountId;
  private static long lastEndedCallUserId, lastEndedCallTime;

  /** a 1:1 call with this person has just ended */
  public static void noteEndedCall (Tdlib tdlib, long userId) {
    lastEndedAccountId = tdlib.id();
    lastEndedCallUserId = userId;
    lastEndedCallTime = android.os.SystemClock.elapsedRealtime();
  }

  /** an invite to a conference came: from the person of the call that has just ended — join it at once */
  public static void onIncomingInvite (Tdlib tdlib, TdApi.Message message) {
    if (message.isOutgoing || message.content.getConstructor() != TdApi.MessageGroupCall.CONSTRUCTOR) return;
    if (!((TdApi.MessageGroupCall) message.content).isActive) return;
    if (!(message.senderId instanceof TdApi.MessageSenderUser)) return;
    long userId = ((TdApi.MessageSenderUser) message.senderId).userId;
    long since = android.os.SystemClock.elapsedRealtime() - lastEndedCallTime;
    if (lastEndedAccountId != tdlib.id() || userId != lastEndedCallUserId || since > 8000) return;
    lastEndedCallUserId = 0;
    UI.post(() -> {
      if (current != null && current.state != STATE_ENDED) return;
      org.thunderdog.challegram.Tgx101Diag.mark("conference: our call with " + userId + " moved into a conference " + since + " ms ago — joining");
      joinConference(tdlib, message.chatId, message.id);
      org.thunderdog.challegram.BaseActivity activity = UI.getUiContext();
      if (activity != null) org.thunderdog.challegram.ui.Tgx101GroupCallController.open(activity);
    });
  }

  /** TGx101: the other side turned our 1:1 call into a conference — we follow by its link */
  @UiThread
  public static Tgx101GroupCall joinConferenceByLink (Tdlib tdlib, String link) {
    if (current != null) {
      current.leave();
    }
    Tgx101GroupCall call = new Tgx101GroupCall(tdlib, 0, 0, null, true, null);
    call.joinInput = new TdApi.InputGroupCallLink(link);
    current = call;
    call.start();
    notifyGlobal();
    return call;
  }

  /** the 1:1 call ends now: with the link when there is one (the other side follows), else plainly — then they get an invite */
  private void finishUpgrade (@Nullable String link) {
    if (upgradeCallId == 0) return;
    int callId = upgradeCallId;
    upgradeCallId = 0;
    org.thunderdog.challegram.Tgx101Diag.mark("conference: 1:1 call " + callId + " ends " + (link != null ? "with the conference link" : "without a link"));
    if (link != null) {
      tdlib.context().calls().tgx101UpgradeToGroupCall(tdlib, callId, link);
    } else {
      tdlib.context().calls().hangUp(tdlib, callId);
      if (upgradePeerUserId != 0) inviteUser(upgradePeerUserId);
    }
  }

  private void inviteUser (long userId) {
    tdlib.send(new TdApi.InviteGroupCallParticipant(groupCallId, userId, false), (result, inviteError) -> {
      org.thunderdog.challegram.Tgx101Diag.mark("conference: invite " + userId + (inviteError != null ? " failed — " + TD.toErrorString(inviteError) : " sent"));
    });
  }

  /** TGx101: joins a conference someone invited us to (the invite message in a private chat) */
  @UiThread
  public static Tgx101GroupCall joinConference (Tdlib tdlib, long chatId, long messageId) {
    if (current != null) {
      current.leave();
    }
    Tgx101GroupCall call = new Tgx101GroupCall(tdlib, 0, 0, null, true, null);
    call.joinInput = new TdApi.InputGroupCallMessage(chatId, messageId);
    current = call;
    call.start();
    notifyGlobal();
    return call;
  }

  private void start () {
    org.thunderdog.challegram.Tgx101Diag.mark("voice chat: join chat " + chatId + " call " + groupCallId + (inviteHash.isEmpty() ? "" : " by link"));
    tdlib.listeners().subscribeForGlobalUpdates(this);
    if (groupCallId != 0) {
      tdlib.send(new TdApi.GetGroupCall(groupCallId), (result, error) -> UI.post(() -> {
        if (result != null) {
          onGroupCallUpdated(result);
        }
      }));
    }
    Context context = org.thunderdog.challegram.unsorted.AppContext.get();
    // WebRTC needs ContextUtils set before the audio device is created; otherwise it's done only by TGCallService
    VoIP.initialize(context);
    File log = new File(context.getCacheDir(), "tgx101_voice_chat.log");
    try {
      ptr = nativeCreate(log.getPath(), muted, isConference);
    } catch (UnsatisfiedLinkError e) {
      ptr = 0; // (review) Android 4 builds have no group call module — a message instead of a crash
    }
    if (ptr == 0) {
      fail(Lang.getString(R.string.Tgx101VoiceChatFailed, "tgcalls"));
      return;
    }
    setupAudio(true);
    Tgx101GroupCallService.start(context);
    requestJoin();
  }

  private void requestJoin () {
    if (ptr != 0 && !joinRequested) {
      joinRequested = true;
      nativeEmitJoinPayload(ptr);
    }
  }

  // Native callbacks (tgcalls threads)

  @Keep
  private void onNativeJoinPayload (String json, int ssrc) {
    UI.post(() -> {
      if (state == STATE_ENDED || ptr == 0) return;
      mySsrc = ssrc;
      TdApi.GroupCallJoinParameters parameters = new TdApi.GroupCallJoinParameters(ssrc, json, muted, false);
      if (isConference) {
        joinConference(parameters);
        return;
      }
      tdlib.send(new TdApi.JoinVideoChat(groupCallId, null, parameters, inviteHash), (response, error) -> UI.post(() -> {
        joinRequested = false;
        if (state == STATE_ENDED || ptr == 0) return;
        if (error != null) {
          fail(Lang.getString(R.string.Tgx101VoiceChatFailed, TD.toErrorString(error)));
          return;
        }
        nativeSetJoinResponse(ptr, response.text);
        tdlib.send(new TdApi.LoadGroupCallParticipants(groupCallId, 100), (ok, loadError) -> { });
      }));
    });
  }

  private void joinConference (TdApi.GroupCallJoinParameters parameters) {
    if (groupCallId != 0) {
      // (review: a rejoin left the conference silent for good) — join again through its invite link or invite message
      TdApi.InputGroupCall input = groupCall != null && groupCall.inviteLink != null && !groupCall.inviteLink.isEmpty() ? new TdApi.InputGroupCallLink(groupCall.inviteLink) : joinInput;
      if (input == null) {
        joinRequested = false;
        fail(Lang.getString(R.string.Tgx101VoiceChatFailed, "rejoin"));
        return;
      }
      org.thunderdog.challegram.Tgx101Diag.mark("conference: rejoining");
      tdlib.send(new TdApi.JoinGroupCall(input, parameters), (info, error) -> UI.post(() -> {
        joinRequested = false;
        if (state == STATE_ENDED || ptr == 0) return;
        if (error != null) {
          fail(Lang.getString(R.string.Tgx101VoiceChatFailed, TD.toErrorString(error)));
          return;
        }
        nativeSetJoinResponse(ptr, info.joinPayload);
      }));
      return;
    }
    if (joinInput != null) {
      org.thunderdog.challegram.Tgx101Diag.mark("conference: joining by invite");
    } else {
      org.thunderdog.challegram.Tgx101Diag.mark("conference: creating, inviting " + inviteUserIds.length);
    }
    TdApi.Function<TdApi.GroupCallInfo> request = joinInput != null ? new TdApi.JoinGroupCall(joinInput, parameters) : new TdApi.CreateGroupCall(parameters);
    tdlib.send(request, (info, error) -> UI.post(() -> {
      joinRequested = false;
      if (state == STATE_ENDED || ptr == 0) {
        // (review) left before the server answered — don't stay a participant of a call nobody runs here
        if (info != null) tdlib.send(new TdApi.LeaveGroupCall(info.groupCallId), (ok, leaveError) -> { });
        return;
      }
      if (error != null) {
        finishUpgrade(null);
        fail(Lang.getString(R.string.Tgx101VoiceChatFailed, TD.toErrorString(error)));
        return;
      }
      groupCallId = info.groupCallId;
      org.thunderdog.challegram.Tgx101Diag.mark("conference: " + (joinInput != null ? "joined" : "created") + ", call " + groupCallId);
      nativeSetJoinResponse(ptr, info.joinPayload);
      tdlib.send(new TdApi.GetGroupCall(groupCallId), (result, getError) -> UI.post(() -> {
        if (result != null) onGroupCallUpdated(result);
        // the conference's link takes the other side of the 1:1 call along
        finishUpgrade(result != null && result.inviteLink != null && !result.inviteLink.isEmpty() ? result.inviteLink : null);
      }));
      tdlib.send(new TdApi.LoadGroupCallParticipants(groupCallId, 100), (ok, loadError) -> { });
      for (long userId : inviteUserIds) {
        if (userId == upgradePeerUserId && upgradeCallId != 0) continue; // follows by the link
        inviteUser(userId);
      }
      for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
      notifyGlobal();
    }));
  }

  // End-to-end encryption of a conference: tgcalls hands every frame here (its own threads) and waits for the answer

  private final java.util.concurrent.ConcurrentHashMap<Integer, Long> ssrcUsers = new java.util.concurrent.ConcurrentHashMap<>();
  private volatile int e2eFailures;

  @Keep
  private byte[] onNativeE2e (byte[] data, long userId, boolean encrypt, int unencryptedPrefixSize) {
    int callId = groupCallId;
    if (callId == 0 || state == STATE_ENDED) return null;
    TdApi.Function<TdApi.Data> function = encrypt ?
      new TdApi.EncryptGroupCallData(callId, new TdApi.GroupCallDataChannelMain(), data, unencryptedPrefixSize) :
      new TdApi.DecryptGroupCallData(callId, new TdApi.MessageSenderUser(userId), new TdApi.GroupCallDataChannelMain(), data);
    final byte[][] result = new byte[1][];
    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    tdlib.client().send(function, object -> {
      if (object instanceof TdApi.Data) {
        result[0] = ((TdApi.Data) object).data;
      } else if (object instanceof TdApi.Error && e2eFailures++ % 200 == 0) {
        org.thunderdog.challegram.Tgx101Diag.mark("conference: " + (encrypt ? "encrypt" : "decrypt from " + userId) + " failed — " + ((TdApi.Error) object).message + " (" + e2eFailures + ")");
      }
      latch.countDown();
    });
    try {
      latch.await(250, java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (InterruptedException ignored) { }
    return result[0];
  }

  private volatile boolean ssrcRecheckPosted;

  @Keep
  private long onNativeUserIdForSsrc (int ssrc) {
    Long userId = ssrcUsers.get(ssrc);
    if (userId == null && !ssrcRecheckPosted) {
      // (review) a waiting SSRC request is answered on the next participant update — or after 5 s, which needs a nudge
      ssrcRecheckPosted = true;
      UI.post(() -> {
        ssrcRecheckPosted = false;
        if (ptr != 0 && state != STATE_ENDED) nativeParticipantsChanged(ptr);
      }, 5500);
    }
    return userId != null ? userId : 0;
  }

  @Keep
  private void onNativeNetworkState (boolean isConnected) {
    UI.post(() -> {
      if (state == STATE_ENDED) return;
      setState(isConnected ? STATE_CONNECTED : STATE_CONNECTING);
    });
  }

  @Keep
  private void onNativeAudioLevels (int[] ssrcs, float[] levels, boolean[] voice) {
    UI.post(() -> {
      if (state == STATE_ENDED) return;
      boolean changed = false;
      for (int i = 0; i < ssrcs.length; i++) {
        int ssrc = ssrcs[i] == 0 ? mySsrc : ssrcs[i]; // 0 is the current user
        boolean speaking = voice[i] && levels[i] > .1f;
        if (ssrcs[i] == 0 && speaking != selfSpeaking && !muted) {
          selfSpeaking = speaking;
          tdlib.send(new TdApi.SetGroupCallParticipantIsSpeaking(groupCallId, mySsrc, speaking), (ok, error) -> { });
        }
        changed |= speaking ? speakingSsrcs.add(ssrc) : speakingSsrcs.remove(ssrc);
      }
      long now = SystemClock.uptimeMillis();
      if (changed && now - lastSpeakingDispatch > 150) {
        lastSpeakingDispatch = now;
        for (Listener listener : listeners) listener.onGroupCallSpeakingChanged(this);
      }
    });
  }

  // TDLib updates (TDLib thread)

  @Override
  public void onGroupCallUpdated (TdApi.GroupCall groupCall) {
    if (groupCall.id != groupCallId) return;
    UI.post(() -> {
      if (state == STATE_ENDED) return;
      if (this.groupCall == null || this.groupCall.participantCount != groupCall.participantCount || this.groupCall.needRejoin != groupCall.needRejoin) {
        org.thunderdog.challegram.Tgx101Diag.mark("voice chat: " + groupCall.participantCount + " participants" + (groupCall.needRejoin ? ", rejoin needed" : "") + (groupCall.isActive ? "" : ", ended by the chat"));
      }
      this.groupCall = groupCall;
      if (!groupCall.isActive) {
        UI.showToast(R.string.Tgx101VoiceChatEnded, Toast.LENGTH_SHORT);
        end();
        return;
      }
      if (groupCall.needRejoin) {
        requestJoin();
      }
      for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
    notifyGlobal();
    });
  }

  @Override
  public void onGroupCallParticipantUpdated (int groupCallId, TdApi.GroupCallParticipant participant) {
    if (groupCallId != this.groupCallId) return;
    UI.post(() -> {
      if (state == STATE_ENDED) return;
      String key = TD.makeSenderKey(participant.participantId);
      if (participant.order == null || participant.order.isEmpty()) {
        participants.remove(key);
      } else {
        participants.put(key, participant);
      }
      if (participant.participantId instanceof TdApi.MessageSenderUser && participant.audioSourceId != 0) {
        ssrcUsers.put(participant.audioSourceId, ((TdApi.MessageSenderUser) participant.participantId).userId);
        if (isConference && ptr != 0) nativeParticipantsChanged(ptr);
      }
      if (participant.isCurrentUser) {
        boolean forcedMute = participant.isMutedForAllUsers && !participant.canUnmuteSelf;
        if (forcedMute && !muted) {
          applyMuted(true); // an admin turned the microphone off
        }
      }
      for (Listener listener : listeners) listener.onGroupCallParticipantsChanged(this);
      notifyGlobal();
    });
  }

  @Override
  public void onNewGroupCallMessage (int groupCallId, TdApi.GroupCallMessage message) {
    if (groupCallId != this.groupCallId) return;
    UI.post(() -> {
      if (state == STATE_ENDED) return;
      for (TdApi.GroupCallMessage m : messages) if (m.messageId == message.messageId) return;
      messages.add(message);
      if (!(message.senderId instanceof TdApi.MessageSenderUser && ((TdApi.MessageSenderUser) message.senderId).userId == tdlib.myUserId())) unreadMessages++;
      for (Listener listener : listeners) listener.onGroupCallMessagesChanged(this);
    });
  }

  @Override
  public void onGroupCallMessagesDeleted (int groupCallId, int[] messageIds) {
    if (groupCallId != this.groupCallId) return;
    UI.post(() -> {
      for (int id : messageIds) {
        for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).messageId == id) messages.remove(i);
      }
      for (Listener listener : listeners) listener.onGroupCallMessagesChanged(this);
    });
  }

  @Override
  public void onGroupCallVerificationStateChanged (int groupCallId, int generation, String[] emojis) {
    if (groupCallId != this.groupCallId) return;
    UI.post(() -> {
      verificationEmojis = emojis;
      for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
    });
  }

  public List<TdApi.GroupCallMessage> getMessages () {
    return new ArrayList<>(messages);
  }

  public int takeUnreadMessages () {
    int n = unreadMessages;
    unreadMessages = 0;
    return n;
  }

  public int unreadMessages () {
    return unreadMessages;
  }

  public @Nullable String[] getVerificationEmojis () {
    return verificationEmojis;
  }

  /** seconds since joining, for «12:40» */
  public long secondsInCall () {
    return (android.os.SystemClock.elapsedRealtime() - joinedAt) / 1000;
  }

  public @Nullable TdApi.GroupCallParticipant me () {
    for (TdApi.GroupCallParticipant p : participants.values()) if (p.isCurrentUser) return p;
    return null;
  }

  /** an admin keeps my microphone off */
  public boolean mutedByAdmin () {
    TdApi.GroupCallParticipant me = me();
    return me != null && me.isMutedForAllUsers && !me.canUnmuteSelf;
  }

  public boolean canSendMessages () {
    return groupCall != null && groupCall.canSendMessages && (groupCall.areMessagesAllowed || groupCall.canBeManaged);
  }

  public void sendMessage (String text, @Nullable Runnable failed) {
    tdlib.send(new TdApi.SendGroupCallMessage(groupCallId, new TdApi.FormattedText(text, new TdApi.TextEntity[0]), 0), (ok, error) -> {
      if (error != null) UI.post(() -> {
        UI.showError(error);
        if (failed != null) failed.run();
      });
    });
  }

  public void setParticipantMuted (TdApi.MessageSender participant, boolean mute) {
    tdlib.send(new TdApi.ToggleGroupCallParticipantIsMuted(groupCallId, participant, mute), (ok, error) -> {
      if (error != null) UI.post(() -> UI.showError(error));
    });
  }

  /** 1..20000, 10000 = 100 % */
  public void setParticipantVolume (TdApi.MessageSender participant, int level) {
    tdlib.send(new TdApi.SetGroupCallParticipantVolumeLevel(groupCallId, participant, level), (ok, error) -> {
      if (error != null) UI.post(() -> UI.showError(error));
    });
  }

  public void setHandRaised (boolean raised) {
    TdApi.MessageSender me = new TdApi.MessageSenderUser(tdlib.myUserId());
    tdlib.send(new TdApi.ToggleGroupCallParticipantIsHandRaised(groupCallId, me, raised), (ok, error) -> {
      if (error != null) UI.post(() -> UI.showError(error));
    });
  }

  public void setMuteNewParticipants (boolean mute) {
    tdlib.send(new TdApi.ToggleVideoChatMuteNewParticipants(groupCallId, mute), (ok, error) -> {
      if (error != null) UI.post(() -> UI.showError(error));
    });
  }

  public void setMessagesAllowed (boolean allowed) {
    tdlib.send(new TdApi.ToggleGroupCallAreMessagesAllowed(groupCallId, allowed), (ok, error) -> {
      if (error != null) UI.post(() -> UI.showError(error));
    });
  }

  /** removes people from a conference (a voice chat of a group has no such thing — the group's own ban does it) */
  public void removeFromConference (long userId) {
    tdlib.send(new TdApi.BanGroupCallParticipants(groupCallId, new long[] {userId}), (ok, error) -> {
      if (error != null) UI.post(() -> UI.showError(error));
    });
  }

  public void inviteLink (me.vkryl.core.lambda.RunnableData<String> after) {
    if (groupCall != null && groupCall.inviteLink != null && !groupCall.inviteLink.isEmpty()) {
      after.runWithData(groupCall.inviteLink);
      return;
    }
    if (isConference) {
      UI.showToast(R.string.Tgx101VoiceChatNoLink, Toast.LENGTH_SHORT);
      return;
    }
    tdlib.send(new TdApi.GetVideoChatInviteLink(groupCallId, false), (link, error) -> UI.post(() -> {
      if (error != null) UI.showError(error);
      else after.runWithData(link.url);
    }));
  }

  // Controls

  public boolean isMuted () {
    return muted;
  }

  public boolean isSpeakerOn () {
    return speaker;
  }

  public int getState () {
    return state;
  }

  public @Nullable TdApi.GroupCall getGroupCall () {
    return groupCall;
  }

  public boolean isSpeaking (TdApi.GroupCallParticipant participant) {
    return speakingSsrcs.contains(participant.isCurrentUser ? mySsrc : participant.audioSourceId);
  }

  public String getTitle () {
    if (groupCall != null && !groupCall.title.isEmpty()) {
      return groupCall.title;
    }
    return chatId != 0 ? tdlib.chatTitle(chatId) : Lang.getString(R.string.Tgx101VoiceChat);
  }

  public List<TdApi.GroupCallParticipant> getParticipants () {
    List<TdApi.GroupCallParticipant> list = new ArrayList<>(participants.values());
    Collections.sort(list, (a, b) -> {
      int cmp = Integer.compare(b.order.length(), a.order.length());
      return cmp != 0 ? cmp : b.order.compareTo(a.order);
    });
    return list;
  }

  /** Returns false when an admin keeps the microphone off. */
  public boolean toggleMuted () {
    if (muted && !canUnmuteSelf()) {
      return false;
    }
    applyMuted(!muted);
    return true;
  }

  private boolean canUnmuteSelf () {
    for (TdApi.GroupCallParticipant participant : participants.values()) {
      if (participant.isCurrentUser) {
        return !participant.isMutedForAllUsers || participant.canUnmuteSelf;
      }
    }
    return true;
  }

  private void applyMuted (boolean muted) {
    org.thunderdog.challegram.Tgx101Diag.mark("voice chat: microphone " + (muted ? "off" : "on"));
    this.muted = muted;
    if (ptr != 0) {
      nativeSetMuted(ptr, muted);
    }
    if (muted && selfSpeaking) {
      selfSpeaking = false;
      tdlib.send(new TdApi.SetGroupCallParticipantIsSpeaking(groupCallId, mySsrc, false), (ok, error) -> { });
    }
    TdApi.MessageSender me = new TdApi.MessageSenderUser(tdlib.myUserId());
    tdlib.send(new TdApi.ToggleGroupCallParticipantIsMuted(groupCallId, me, muted), (ok, error) -> { });
    for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
    notifyGlobal();
  }

  public void toggleSpeaker () {
    speaker = !speaker;
    final boolean on = speaker;
    Tgx101Audio.run(() -> {
      AudioManager audio = audioManager();
      if (audio != null) audio.setSpeakerphoneOn(on);
    });
    for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
    notifyGlobal();
  }

  public void leave () {
    if (state != STATE_ENDED) org.thunderdog.challegram.Tgx101Diag.mark("voice chat: leave");
    if (state == STATE_ENDED) return;
    tdlib.send(new TdApi.LeaveGroupCall(groupCallId), (ok, error) -> { });
    end();
  }

  /** Ends the voice chat for everyone (admins). */
  public void endForAll () {
    if (state != STATE_ENDED) org.thunderdog.challegram.Tgx101Diag.mark("voice chat: end for everyone");
    if (state == STATE_ENDED) return;
    tdlib.send(new TdApi.EndGroupCall(groupCallId), (ok, error) -> { });
    end();
  }

  public void addListener (Listener listener) {
    listeners.add(listener);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  // End

  private void fail (String message) {
    org.thunderdog.challegram.Tgx101Diag.mark("voice chat: failed — " + message);
    UI.showToast(message, Toast.LENGTH_LONG);
    leave();
  }

  private void setState (int state) {
    if (this.state != state) {
      org.thunderdog.challegram.Tgx101Diag.mark("voice chat: " + (state == STATE_CONNECTED ? "connected" : state == STATE_ENDED ? "ended" : "connecting"));
      this.state = state;
      for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
    notifyGlobal();
    }
  }

  private void end () {
    if (state == STATE_ENDED) return;
    tdlib.listeners().unsubscribeFromGlobalUpdates(this);
    if (ptr != 0) {
      long p = ptr;
      ptr = 0;
      nativeDestroy(p);
    }
    setupAudio(false);
    setState(STATE_ENDED);
    if (current == this) {
      current = null;
    }
    notifyGlobal();
    Tgx101GroupCallService.stop(org.thunderdog.challegram.unsorted.AppContext.get());
  }

  // Audio: communication mode while the voice chat is open, speaker by default

  private int previousAudioMode = AudioManager.MODE_NORMAL;
  private boolean previousSpeaker;

  private static @Nullable AudioManager audioManager () {
    return (AudioManager) org.thunderdog.challegram.unsorted.AppContext.get().getSystemService(Context.AUDIO_SERVICE);
  }

  private void setupAudio (boolean active) {
    Tgx101Audio.run(() -> setupAudioNow(active)); // off the main thread: setMode alone took ~0.9 s on the vivo
  }

  private void setupAudioNow (boolean active) {
    AudioManager audio = audioManager();
    if (audio == null) return;
    try {
      if (active) {
        int mode = audio.getMode();
        // (review) a 1:1 call that grew into this one still holds the communication mode — restore the normal one later
        previousAudioMode = mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL ? AudioManager.MODE_NORMAL : mode;
        previousSpeaker = audio.isSpeakerphoneOn();
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audio.setSpeakerphoneOn(speaker);
        audio.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
      } else {
        audio.abandonAudioFocus(null);
        audio.setSpeakerphoneOn(previousSpeaker);
        audio.setMode(previousAudioMode);
      }
    } catch (Throwable ignored) { }
  }

  // Native

  private native long nativeCreate (String logPath, boolean muted, boolean conference);
  private native void nativeParticipantsChanged (long ptr);
  private native void nativeEmitJoinPayload (long ptr);
  private native void nativeSetJoinResponse (long ptr, String payload);
  private native void nativeSetMuted (long ptr, boolean muted);
  private native void nativeSetVolume (long ptr, int ssrc, double volume);
  private native void nativeDestroy (long ptr);
}
