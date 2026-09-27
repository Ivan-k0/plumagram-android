/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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
  }

  public static final int STATE_CONNECTING = 0, STATE_CONNECTED = 1, STATE_ENDED = 2;

  private static @Nullable Tgx101GroupCall current;

  public static @Nullable Tgx101GroupCall current () {
    return current;
  }

  public final Tdlib tdlib;
  public final long chatId;
  public final int groupCallId;
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

  private Tgx101GroupCall (Tdlib tdlib, long chatId, int groupCallId, @Nullable String inviteHash) {
    this.tdlib = tdlib;
    this.chatId = chatId;
    this.groupCallId = groupCallId;
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
    return call;
  }

  private void start () {
    tdlib.listeners().subscribeForGlobalUpdates(this);
    tdlib.send(new TdApi.GetGroupCall(groupCallId), (result, error) -> UI.post(() -> {
      if (result != null) {
        onGroupCallUpdated(result);
      }
    }));
    Context context = UI.getAppContext();
    File log = new File(context.getCacheDir(), "tgx101_voice_chat.log");
    ptr = nativeCreate(log.getPath(), muted);
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
      if (participant.isCurrentUser) {
        boolean forcedMute = participant.isMutedForAllUsers && !participant.canUnmuteSelf;
        if (forcedMute && !muted) {
          applyMuted(true); // an admin turned the microphone off
        }
      }
      for (Listener listener : listeners) listener.onGroupCallParticipantsChanged(this);
    });
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
  }

  public void toggleSpeaker () {
    speaker = !speaker;
    AudioManager audio = audioManager();
    if (audio != null) {
      audio.setSpeakerphoneOn(speaker);
    }
    for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
  }

  public void leave () {
    if (state == STATE_ENDED) return;
    tdlib.send(new TdApi.LeaveGroupCall(groupCallId), (ok, error) -> { });
    end();
  }

  /** Ends the voice chat for everyone (admins). */
  public void endForAll () {
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
    UI.showToast(message, Toast.LENGTH_LONG);
    leave();
  }

  private void setState (int state) {
    if (this.state != state) {
      this.state = state;
      for (Listener listener : listeners) listener.onGroupCallStateChanged(this);
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
    Tgx101GroupCallService.stop(UI.getAppContext());
  }

  // Audio: communication mode while the voice chat is open, speaker by default

  private int previousAudioMode = AudioManager.MODE_NORMAL;
  private boolean previousSpeaker;

  private static @Nullable AudioManager audioManager () {
    return (AudioManager) UI.getAppContext().getSystemService(Context.AUDIO_SERVICE);
  }

  private void setupAudio (boolean active) {
    AudioManager audio = audioManager();
    if (audio == null) return;
    try {
      if (active) {
        previousAudioMode = audio.getMode();
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

  private native long nativeCreate (String logPath, boolean muted);
  private native void nativeEmitJoinPayload (long ptr);
  private native void nativeSetJoinResponse (long ptr, String payload);
  private native void nativeSetMuted (long ptr, boolean muted);
  private native void nativeSetVolume (long ptr, int ssrc, double volume);
  private native void nativeDestroy (long ptr);
}
