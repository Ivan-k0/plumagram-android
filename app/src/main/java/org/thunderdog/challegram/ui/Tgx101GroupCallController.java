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
package org.thunderdog.challegram.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.Tgx101GroupCall;
import org.thunderdog.challegram.widget.AvatarView;

import java.util.ArrayList;
import java.util.List;

/** TGx101: voice chat screen. Participants, who is speaking, microphone, speaker, leave. */
public class Tgx101GroupCallController extends ViewController<Void> implements Tgx101GroupCall.Listener {
  private static final int COLOR_ACCENT = 0xff3f8ae0;
  private static final int COLOR_END = 0xffe5484d;
  private static final int COLOR_SPEAKING = 0xff5fd08c;
  private static final int COLOR_SECONDARY = 0x99ffffff;

  // Entry points

  /** Asks for the microphone if needed, joins the voice chat and opens its screen. */
  public static void join (@NonNull ViewController<?> c, long chatId, int groupCallId, @Nullable String inviteHash) {
    Runnable act = () -> {
      Tgx101GroupCall.join(c.tdlib(), chatId, groupCallId, inviteHash);
      open(c.context());
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(c.context(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      c.context().requestMicPermissionForCall((code, permissions, grantResults, grantCount) -> {
        if (grantCount == permissions.length) {
          act.run();
        } else {
          UI.showToast(R.string.Tgx101VoiceChatNoMic, Toast.LENGTH_LONG);
        }
      });
    } else {
      act.run();
    }
  }

  /** TGx101: joins a group call someone invited us to (their invite message), asking for the microphone if needed */
  public static void joinConference (@NonNull ViewController<?> c, long chatId, long messageId) {
    Runnable act = () -> {
      Tgx101GroupCall.joinConference(c.tdlib(), chatId, messageId);
      open(c.context());
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(c.context(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      c.context().requestMicPermissionForCall((code, permissions, grantResults, grantCount) -> {
        if (grantCount == permissions.length) {
          act.run();
        } else {
          UI.showToast(R.string.Tgx101VoiceChatNoMic, Toast.LENGTH_LONG);
        }
      });
    } else {
      act.run();
    }
  }

  /** Whether the current user may start a voice chat in this group or channel. */
  public static boolean canStart (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null || chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR || chat.type.getConstructor() == TdApi.ChatTypeSecret.CONSTRUCTOR) {
      return false;
    }
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    if (status == null) {
      return false;
    }
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageVideoChats;
    }
    return false;
  }

  /** Starts a voice chat in the chat and joins it. */
  public static void start (@NonNull ViewController<?> c, long chatId) {
    c.tdlib().send(new TdApi.CreateVideoChat(chatId, "", 0, false), (result, error) -> UI.post(() -> {
      if (c.isDestroyed()) return;
      if (error != null) {
        UI.showToast(Lang.getString(R.string.Tgx101VoiceChatFailed, org.thunderdog.challegram.data.TD.toErrorString(error)), Toast.LENGTH_LONG);
      } else {
        join(c, chatId, result.id, null);
      }
    }));
  }

  /** Opens the running voice chat's screen. */
  public static boolean open (BaseActivity context) {
    Tgx101GroupCall call = Tgx101GroupCall.current();
    if (call == null) {
      return false;
    }
    ViewController<?> top = context.navigation().getCurrentStackItem();
    if (top instanceof Tgx101GroupCallController) {
      return true;
    }
    context.navigation().navigateTo(new Tgx101GroupCallController(context, call.tdlib));
    return true;
  }

  public Tgx101GroupCallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101VoiceChat;
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }

  @Override
  protected int getPopupRestoreColor () {
    return 0xff000000;
  }

  // TGx101 (approved mockup «Групповой звонок» 1 · «Список, как в Telegram», 2026-10-14): the order of the official app —
  // the title and the time, the protection emojis, the people (speaking / listening / microphone off / ✋), «Add» and
  // «Invite by link», then Speaker · the big microphone · Message · Leave; ⋮ for sound, background, messages and the
  // admins' rights; a person tapped — their rights; leaving asks under the finger; what is forbidden shows a lock

  private @Nullable Tgx101GroupCall call;
  private TextView titleView, statusView, keysView, micLabel, messageBadge;
  private ImageView micButton, speakerButton, micLock, messageLock;
  private ParticipantsAdapter adapter;
  private @Nullable LinearLayout openMessages;
  private @Nullable Runnable refreshMessages;

  @Override
  protected View onCreateView (Context context) {
    call = Tgx101GroupCall.current();

    FrameLayout root = new FrameLayout(context);
    root.addView(new Tgx101CallBackground(context), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(0, Screen.getStatusBarHeight() + Screen.dp(8f), 0, Screen.dp(20f) + Screen.getNavigationBarHeight());
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // ⌄ · title / status · ⋮
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
    top.addView(iconButton(context, R.drawable.baseline_keyboard_arrow_down_24, R.string.Tgx101VoiceChatMinimize, v -> navigateBack()));
    LinearLayout titles = new LinearLayout(context);
    titles.setOrientation(LinearLayout.VERTICAL);
    titles.setGravity(Gravity.CENTER_HORIZONTAL);
    titleView = newText(context, 18, 0xffffffff, true);
    titles.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    statusView = newText(context, 13, COLOR_SECONDARY, false);
    titles.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    top.addView(iconButton(context, R.drawable.baseline_more_vert_24, R.string.Tgx101SavedMore, v -> showMenu()));
    column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f)));

    keysView = newText(context, 13, COLOR_SECONDARY, false);
    keysView.setVisibility(View.GONE);
    LinearLayout.LayoutParams kp = centered();
    kp.topMargin = Screen.dp(2f);
    column.addView(keysView, kp);

    // the participants' cameras and screens (group call video, 2026-10-19): 1 — full width, 2 — side by side, 3–4 — 2 × 2
    videoGrid = new android.widget.GridLayout(context);
    videoGrid.setColumnCount(2);
    videoGrid.setVisibility(View.GONE);
    LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    gridParams.leftMargin = gridParams.rightMargin = Screen.dp(12f);
    gridParams.topMargin = Screen.dp(10f);
    column.addView(videoGrid, gridParams);

    RecyclerView list = new RecyclerView(context);
    list.setLayoutManager(new LinearLayoutManager(context));
    list.setAdapter(adapter = new ParticipantsAdapter());
    list.setOverScrollMode(View.OVER_SCROLL_NEVER);
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    listParams.topMargin = Screen.dp(10f);
    column.addView(list, listParams);

    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.BOTTOM);
    buttons.setPadding(Screen.dp(8f), Screen.dp(12f), Screen.dp(8f), 0);
    column.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    speakerButton = addButton(context, buttons, R.drawable.baseline_volume_up_24, 52, R.string.Tgx101VoiceChatSpeaker, v -> {
      if (call != null) call.toggleSpeaker();
    }, null, null);
    micButton = addButton(context, buttons, R.drawable.baseline_mic_24, 76, 0, v -> onMicClick(), label -> micLabel = label, lock -> micLock = lock);
    addButton(context, buttons, R.drawable.baseline_chat_bubble_24, 52, R.string.Tgx101VoiceChatMessage, v -> openMessages(), null, lock -> messageLock = lock);
    addButton(context, buttons, R.drawable.baseline_call_end_24, 52, R.string.Tgx101VoiceChatLeave, v -> onLeaveClick(), null, null).setBackground(circle(COLOR_END));
    // the unread count on «Message»
    messageBadge = newText(context, 11, 0xffffffff, true);
    messageBadge.setBackground(circle(COLOR_END));
    messageBadge.setVisibility(View.GONE);
    FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f), Gravity.TOP | Gravity.RIGHT);
    ((FrameLayout) messageLock.getParent()).addView(messageBadge, bp);

    if (call != null) {
      call.addListener(this);
    }
    updateAll();
    ticker.run();
    return root;
  }

  private final Runnable ticker = new Runnable() {
    @Override
    public void run () {
      if (isDestroyed()) return;
      updateStatus();
      if (statusView != null) statusView.postDelayed(this, 1000);
    }
  };

  // The microphone, leaving

  private void onMicClick () {
    if (call == null) return;
    if (call.mutedByAdmin()) {
      // the lock: who forbade what, and the hand instead
      TdApi.GroupCallParticipant me = call.me();
      boolean raised = me != null && me.isHandRaised;
      call.setHandRaised(!raised);
      UI.showToast(raised ? R.string.Tgx101VoiceChatHandDown : R.string.Tgx101VoiceChatHandUp, Toast.LENGTH_SHORT);
      return;
    }
    call.toggleMuted();
  }

  /** under the finger, at the bottom: «Leave», for admins also «End for everyone» (mockup: confirmation under the finger) */
  private void onLeaveClick () {
    if (call == null) {
      navigateBack();
      return;
    }
    TdApi.GroupCall groupCall = call.getGroupCall();
    boolean admin = groupCall != null && groupCall.canBeManaged;
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101VoiceChatLeaveAsk)));
      if (admin) {
        body.addView(bigButton(Lang.getString(R.string.Tgx101VoiceChatEndForAll), COLOR_END, v -> {
          h[0].dismiss();
          call.endForAll();
        }));
      }
      body.addView(bigButton(Lang.getString(R.string.Tgx101VoiceChatLeave), admin ? 0x33808080 : COLOR_END, v -> {
        h[0].dismiss();
        call.leave();
      }));
    });
  }

  private View bigButton (String label, int color, View.OnClickListener click) {
    TextView b = newText(context, 16, color == 0x33808080 ? Theme.textAccentColor() : 0xffffffff, true);
    b.setText(label);
    b.setGravity(Gravity.CENTER);
    b.setBackground(Tgx101SavedHomeController.card(color, 14f));
    b.setOnClickListener(click);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f));
    p.setMargins(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(6f));
    b.setLayoutParams(p);
    return b;
  }

  // ⋮

  private void showMenu () {
    if (call == null) return;
    TdApi.GroupCall groupCall = call.getGroupCall();
    boolean admin = groupCall != null && (groupCall.canBeManaged || groupCall.canToggleMuteNewParticipants || groupCall.canToggleAreMessagesAllowed);
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_volume_up_24, 0, Lang.getString(R.string.Tgx101VoiceChatSound), Lang.getString(call.isSpeakerOn() ? R.string.Tgx101VoiceChatSpeaker : R.string.Tgx101VoiceChatEarpiece), false, v -> {
        h[0].dismiss();
        call.toggleSpeaker();
      }));
      boolean canFloat = Tgx101CallBubble.canDrawOverlays(context);
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_keyboard_arrow_down_24, 0, Lang.getString(R.string.Tgx101VoiceChatBackground), canFloat ? Lang.getString(R.string.Tgx101VoiceChatBackgroundHint) : Lang.getString(R.string.Tgx101CallBubble), false, v -> {
        h[0].dismiss();
        // mockup 8: outside the app the call floats as a button over other apps — allowed once in the system settings
        org.thunderdog.challegram.unsorted.Settings.instance().setTgx101CallBubble(true);
        if (!canFloat) {
          Tgx101CallBubble.requestPermission(context);
        } else {
          navigateBack(); // the bar under the header keeps the call (step 2)
        }
      }));
      int unread = call.unreadMessages();
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_chat_bubble_24, 0, Lang.getString(R.string.Tgx101VoiceChatMessages), unread > 0 ? Lang.getString(R.string.Tgx101VoiceChatNewMessages, unread) : null, false, v -> {
        h[0].dismiss();
        openMessages();
      }));
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_link_24, 0, Lang.getString(R.string.Tgx101VoiceChatInvite), null, false, v -> {
        h[0].dismiss();
        inviteByLink();
      }));
      if (admin) {
        body.addView(Tgx101Sheet.row(context, R.drawable.baseline_lock_24, 0, Lang.getString(R.string.Tgx101VoiceChatRights), Lang.getString(R.string.Tgx101VoiceChatRightsHint), false, v -> {
          h[0].dismiss();
          showRights();
        }));
      }
    });
  }

  /** mockup 6: what everyone may — speak, write; the link; end for everyone */
  private void showRights () {
    if (call == null) return;
    TdApi.GroupCall g = call.getGroupCall();
    if (g == null) return;
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101VoiceChatRightsTitle)));
      if (g.canToggleMuteNewParticipants) {
        body.addView(toggleRow(Lang.getString(R.string.Tgx101VoiceChatAllSpeak), !g.muteNewParticipants, on -> call.setMuteNewParticipants(!on)));
      }
      if (g.canToggleAreMessagesAllowed) {
        body.addView(toggleRow(Lang.getString(R.string.Tgx101VoiceChatAllWrite), g.areMessagesAllowed, call::setMessagesAllowed));
      }
      body.addView(Tgx101Sheet.row(context, R.drawable.baseline_link_24, 0, Lang.getString(R.string.Tgx101VoiceChatInvite), null, false, v -> {
        h[0].dismiss();
        inviteByLink();
      }));
      if (g.canBeManaged) {
        body.addView(bigButton(Lang.getString(R.string.Tgx101VoiceChatEndForAll), COLOR_END, v -> {
          h[0].dismiss();
          call.endForAll();
        }));
      }
    });
  }

  private View toggleRow (String title, boolean value, me.vkryl.core.lambda.RunnableBool changed) {
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(Screen.dp(20f), 0, Screen.dp(12f), 0);
    TextView t = newText(context, 15, Theme.textAccentColor(), false);
    t.setGravity(Gravity.LEFT);
    t.setSingleLine(false);
    t.setText(title);
    r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    android.widget.CheckBox box = new android.widget.CheckBox(context);
    box.setChecked(value);
    box.setOnCheckedChangeListener((b, checked) -> changed.runWithBool(checked));
    r.addView(box);
    r.setOnClickListener(v -> box.toggle());
    r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
    return r;
  }

  private void inviteByLink () {
    if (call == null) return;
    call.inviteLink(link -> {
      if (isDestroyed()) return;
      ShareController share = new ShareController(context, tdlib);
      share.setArguments(new ShareController.Args(link));
      share.show();
    });
  }

  // A person (mockup 5): speak for everyone, my volume for them, the hand, out of the call

  private void showParticipant (TdApi.GroupCallParticipant p) {
    if (call == null) return;
    TdApi.GroupCall g = call.getGroupCall();
    String name = tdlib.senderName(p.participantId);
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, null, body -> {
      body.addView(Tgx101Sheet.title(context, p.isCurrentUser ? Lang.getString(R.string.Tgx101VoiceChatYou, name) : name));
      if (p.isCurrentUser) {
        // (user 2026-10-16 «поднять руку не работает»): as in Telegram, the hand is only for a chat's voice chat where an
        // admin took the word away — a conference grown from a 1:1 call has no hands; there the own row turns the mic
        if (!call.isConference && call.mutedByAdmin()) {
          body.addView(Tgx101Sheet.row(context, 0, 0, "✋  " + Lang.getString(p.isHandRaised ? R.string.Tgx101VoiceChatLowerHand : R.string.Tgx101VoiceChatRaiseHand), null, false, v -> {
            h[0].dismiss();
            call.setHandRaised(!p.isHandRaised);
          }));
        } else {
          body.addView(Tgx101Sheet.row(context, 0, 0, Lang.getString(call.isMuted() ? R.string.Tgx101VoiceChatMicTurnOn : R.string.Tgx101VoiceChatMicTurnOff), null, false, v -> {
            h[0].dismiss();
            call.toggleMuted();
          }));
        }
        return;
      }
      if (p.canBeMutedForAllUsers || p.canBeUnmutedForAllUsers) {
        body.addView(toggleRow(Lang.getString(R.string.Tgx101VoiceChatCanSpeak), !p.isMutedForAllUsers, on -> call.setParticipantMuted(p.participantId, !on)));
      } else if (p.canBeMutedForCurrentUser || p.canBeUnmutedForCurrentUser) {
        body.addView(toggleRow(Lang.getString(R.string.Tgx101VoiceChatHearThem), !p.isMutedForCurrentUser, on -> call.setParticipantMuted(p.participantId, !on)));
      }
      TextView volumeTitle = newText(context, 13, Theme.textDecentColor(), false);
      volumeTitle.setGravity(Gravity.LEFT);
      volumeTitle.setText(Lang.getString(R.string.Tgx101VoiceChatVolume));
      volumeTitle.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(20f), Screen.dp(4f));
      body.addView(volumeTitle);
      LinearLayout volumes = new LinearLayout(context);
      volumes.setOrientation(LinearLayout.HORIZONTAL);
      volumes.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      int current = p.volumeLevel > 0 ? p.volumeLevel : 10000;
      for (int level : new int[] {5000, 10000, 15000, 20000}) {
        TextView chip = newText(context, 14, level == current ? 0xffffffff : Theme.textAccentColor(), true);
        chip.setText((level / 100) + "%");
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(Tgx101SavedHomeController.card(level == current ? COLOR_ACCENT : Theme.backgroundColor(), 18f));
        chip.setOnClickListener(v -> {
          h[0].dismiss();
          call.setParticipantVolume(p.participantId, level);
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Screen.dp(38f), 1f);
        cp.setMargins(Screen.dp(4f), 0, Screen.dp(4f), 0);
        volumes.addView(chip, cp);
      }
      body.addView(volumes);
      if (call.isConference && g != null && g.isOwned && p.participantId instanceof TdApi.MessageSenderUser) {
        body.addView(bigButton(Lang.getString(R.string.Tgx101VoiceChatRemove), COLOR_END, v -> {
          h[0].dismiss();
          call.removeFromConference(((TdApi.MessageSenderUser) p.participantId).userId);
        }));
      }
    });
  }

  // Messages of the call (mockup 9)

  private void openMessages () {
    if (call == null) return;
    call.takeUnreadMessages();
    updateBadge();
    Tgx101Sheet.Handle[] h = new Tgx101Sheet.Handle[1];
    h[0] = Tgx101Sheet.show(this, () -> {
      openMessages = null;
      refreshMessages = null;
    }, body -> {
      body.addView(Tgx101Sheet.title(context, Lang.getString(R.string.Tgx101VoiceChatMessages)));
      LinearLayout list = new LinearLayout(context);
      list.setOrientation(LinearLayout.VERTICAL);
      list.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      body.addView(list);
      openMessages = list;
      refreshMessages = () -> fillMessages(list);
      fillMessages(list);
      if (!call.canSendMessages()) {
        TextView locked = newText(context, 13, Theme.textDecentColor(), false);
        locked.setText("🔒  " + Lang.getString(R.string.Tgx101VoiceChatNoWriting));
        locked.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(20f), Screen.dp(8f));
        body.addView(locked);
        return;
      }
      LinearLayout bar = new LinearLayout(context);
      bar.setOrientation(LinearLayout.HORIZONTAL);
      bar.setGravity(Gravity.CENTER_VERTICAL);
      bar.setBackground(Tgx101SavedHomeController.bubble(true));
      bar.setPadding(Screen.dp(4f), 0, Screen.dp(4f), 0);
      android.widget.EditText field = new android.widget.EditText(context);
      field.setHint(Lang.getString(R.string.Tgx101VoiceChatMessageHint));
      field.setBackground(null);
      field.setTextColor(Theme.textAccentColor());
      field.setHintTextColor(Theme.textDecentColor());
      field.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(8f), Screen.dp(10f));
      bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      ImageView send = new ImageView(context);
      send.setImageResource(R.drawable.baseline_send_24);
      send.setColorFilter(COLOR_ACCENT);
      send.setScaleType(ImageView.ScaleType.CENTER);
      send.setOnClickListener(v -> {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) return;
        field.setText("");
        call.sendMessage(text, () -> field.setText(text));
      });
      bar.addView(send, new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
      LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      bp.setMargins(Screen.dp(12f), 0, Screen.dp(12f), Screen.dp(4f));
      body.addView(bar, bp);
    });
  }

  private void fillMessages (LinearLayout list) {
    list.removeAllViews();
    if (call == null) return;
    List<TdApi.GroupCallMessage> messages = call.getMessages();
    if (messages.isEmpty()) {
      TextView none = newText(context, 14, Theme.textDecentColor(), false);
      none.setText(Lang.getString(R.string.Tgx101VoiceChatNoMessages));
      none.setPadding(0, Screen.dp(12f), 0, Screen.dp(12f));
      list.addView(none);
      return;
    }
    int from = Math.max(0, messages.size() - 40);
    for (int i = from; i < messages.size(); i++) {
      TdApi.GroupCallMessage m = messages.get(i);
      LinearLayout row = new LinearLayout(context);
      row.setOrientation(LinearLayout.VERTICAL);
      row.setPadding(0, Screen.dp(4f), 0, Screen.dp(4f));
      TextView who = newText(context, 12, COLOR_ACCENT, true);
      who.setGravity(Gravity.LEFT);
      who.setText(tdlib.senderName(m.senderId));
      row.addView(who);
      TextView text = newText(context, 15, Theme.textAccentColor(), false);
      text.setGravity(Gravity.LEFT);
      text.setSingleLine(false);
      text.setText(m.text != null ? m.text.text : "");
      row.addView(text);
      list.addView(row);
    }
  }

  private void updateBadge () {
    if (messageBadge == null || call == null) return;
    int n = call.unreadMessages();
    messageBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
    messageBadge.setText(n > 9 ? "9+" : String.valueOf(n));
  }

  // Updates from the voice chat

  @Override
  public void onGroupCallStateChanged (Tgx101GroupCall call) {
    if (call.getState() == Tgx101GroupCall.STATE_ENDED) {
      navigateBack();
      return;
    }
    updateAll();
  }

  @Override
  public void onGroupCallParticipantsChanged (Tgx101GroupCall call) {
    adapter.setItems(call.getParticipants());
    updateVideos();
    updateStatus();
    updateLocks();
  }

  @Override
  public void onGroupCallSpeakingChanged (Tgx101GroupCall call) {
    adapter.notifyItemRangeChanged(0, adapter.getItemCount());
  }

  @Override
  public void onGroupCallMessagesChanged (Tgx101GroupCall call) {
    if (refreshMessages != null) {
      call.takeUnreadMessages();
      refreshMessages.run();
    }
    updateBadge();
  }

  private void updateAll () {
    if (call == null) {
      titleView.setText(Lang.getString(R.string.Tgx101VoiceChat));
      statusView.setText(Lang.getString(R.string.Tgx101VoiceChatEnded));
      return;
    }
    titleView.setText(call.getTitle());
    updateStatus();
    updateVideos();
    String[] emojis = call.getVerificationEmojis();
    if (emojis != null && emojis.length > 0) {
      keysView.setVisibility(View.VISIBLE);
      keysView.setText("🛡 " + Lang.getString(R.string.Tgx101VoiceChatProtected) + " · " + TextUtils.join(" ", emojis));
    } else {
      keysView.setVisibility(View.GONE);
    }
    micButton.setBackground(circle(call.isMuted() ? 0x33ffffff : COLOR_ACCENT));
    micButton.setAlpha(call.isMuted() ? .8f : 1f);
    speakerButton.setBackground(circle(call.isSpeakerOn() ? 0x55ffffff : 0x22ffffff));
    adapter.setItems(call.getParticipants());
    updateLocks();
    updateBadge();
  }

  /** what an admin forbade: a lock on the button, and the microphone becomes «raise the hand» */
  private void updateLocks () {
    if (call == null) return;
    boolean micLocked = call.mutedByAdmin();
    micLock.setVisibility(micLocked ? View.VISIBLE : View.GONE);
    TdApi.GroupCallParticipant me = call.me();
    if (micLocked) {
      micLabel.setText(Lang.getString(me != null && me.isHandRaised ? R.string.Tgx101VoiceChatHandIsUp : R.string.Tgx101VoiceChatRaiseHand));
    } else {
      micLabel.setText(Lang.getString(call.isMuted() ? R.string.Tgx101VoiceChatMicOff : R.string.Tgx101VoiceChatMicOn));
    }
    messageLock.setVisibility(call.getGroupCall() != null && !call.canSendMessages() ? View.VISIBLE : View.GONE);
  }

  private void updateStatus () {
    if (call == null) return;
    if (call.getState() != Tgx101GroupCall.STATE_CONNECTED) {
      statusView.setText(Lang.getString(R.string.Tgx101VoiceChatConnecting));
    } else {
      TdApi.GroupCall groupCall = call.getGroupCall();
      int count = groupCall != null ? groupCall.participantCount : adapter.getItemCount();
      long sec = call.secondsInCall();
      statusView.setText(Lang.getString(R.string.Tgx101VoiceChatParticipantsCount, count) + " · " + String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60));
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (call != null) {
      call.removeListener(this);
    }
    clearVideos();
  }

  // Video tiles

  private android.widget.GridLayout videoGrid;
  private final java.util.LinkedHashMap<String, View> videoTiles = new java.util.LinkedHashMap<>(); // endpoint → tile
  private String videoKey = "";

  private void updateVideos () {
    if (call == null || videoGrid == null || !org.thunderdog.challegram.voip.Tgx101GroupVideo.isSupported()) return;
    java.util.List<Tgx101GroupCall.VideoSource> sources = call.videoSources();
    if (sources.size() > 4) sources = sources.subList(0, 4);
    StringBuilder key = new StringBuilder();
    for (Tgx101GroupCall.VideoSource source : sources) key.append(source.info.endpointId).append(',');
    if (key.toString().equals(videoKey)) return;
    videoKey = key.toString();
    clearVideos();
    if (sources.isEmpty()) {
      videoGrid.setVisibility(View.GONE);
      return;
    }
    videoGrid.setVisibility(View.VISIBLE);
    int width = Screen.currentWidth() - Screen.dp(24f);
    int count = sources.size();
    int cell = count == 1 ? width : width / 2;
    int height = count == 1 ? Math.round(width * .75f) : count == 2 ? Math.round(cell * 1.33f) : cell;
    for (Tgx101GroupCall.VideoSource source : sources) {
      View renderer = org.thunderdog.challegram.voip.Tgx101GroupVideo.createView(context());
      if (renderer == null) continue;
      FrameLayout tile = new FrameLayout(context());
      tile.addView(renderer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      TextView name = newText(context(), 12, 0xffffffff, true);
      name.setText(tdlib.senderName(source.participant.participantId, true) + (source.isScreen ? " · " + Lang.getString(R.string.Tgx101VoiceChatScreen) : ""));
      name.setShadowLayer(Screen.dp(3f), 0, 0, 0xaa000000);
      name.setGravity(Gravity.START);
      FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
      np.leftMargin = np.bottomMargin = Screen.dp(8f);
      tile.addView(name, np);
      android.widget.GridLayout.LayoutParams tp = new android.widget.GridLayout.LayoutParams();
      tp.width = cell - Screen.dp(4f);
      tp.height = height - Screen.dp(4f);
      tp.setMargins(Screen.dp(2f), Screen.dp(2f), Screen.dp(2f), Screen.dp(2f));
      if (count == 1) tp.columnSpec = android.widget.GridLayout.spec(0, 2);
      if (android.os.Build.VERSION.SDK_INT >= 21) {
        tile.setClipToOutline(true);
        tile.setOutlineProvider(new android.view.ViewOutlineProvider() {
          @Override
          public void getOutline (View view, android.graphics.Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(14f));
          }
        });
      }
      tile.setBackgroundColor(0xff1f2731);
      videoGrid.addView(tile, tp);
      videoTiles.put(source.info.endpointId, renderer);
      call.setVideoSink(source.info.endpointId, org.thunderdog.challegram.voip.Tgx101GroupVideo.sinkOf(renderer));
    }
  }

  private void clearVideos () {
    for (java.util.Map.Entry<String, View> entry : videoTiles.entrySet()) {
      if (call != null) call.setVideoSink(entry.getKey(), null);
      org.thunderdog.challegram.voip.Tgx101GroupVideo.release(entry.getValue());
    }
    videoTiles.clear();
    if (videoGrid != null) videoGrid.removeAllViews();
  }

  // Participants

  private final class ParticipantsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<TdApi.GroupCallParticipant> items = new ArrayList<>();
    private static final int TYPE_PERSON = 0, TYPE_ACTION = 1;

    void setItems (List<TdApi.GroupCallParticipant> participants) {
      items.clear();
      items.addAll(participants);
      notifyDataSetChanged();
    }

    final class Row extends RecyclerView.ViewHolder {
      final AvatarView avatar;
      final TextView name, status;
      final ImageView mic;

      Row (LinearLayout layout, AvatarView avatar, TextView name, TextView status, ImageView mic) {
        super(layout);
        this.avatar = avatar;
        this.name = name;
        this.status = status;
        this.mic = mic;
      }
    }

    @Override
    public int getItemViewType (int position) {
      return position < items.size() ? TYPE_PERSON : TYPE_ACTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      Context context = parent.getContext();
      LinearLayout layout = new LinearLayout(context);
      layout.setOrientation(LinearLayout.HORIZONTAL);
      layout.setGravity(Gravity.CENTER_VERTICAL);
      layout.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));
      layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      org.thunderdog.challegram.support.RippleSupport.setTransparentWhiteSelector(layout);
      if (viewType == TYPE_ACTION) {
        ImageView icon = new ImageView(context);
        icon.setColorFilter(COLOR_ACCENT);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        layout.addView(icon, new LinearLayout.LayoutParams(Screen.dp(42f), Screen.dp(42f)));
        TextView label = newText(context, 16, COLOR_ACCENT, false);
        label.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Screen.dp(14f);
        layout.addView(label, lp);
        return new RecyclerView.ViewHolder(layout) { };
      }
      AvatarView avatar = new AvatarView(context);
      layout.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(42f), Screen.dp(42f)));
      LinearLayout texts = new LinearLayout(context);
      texts.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      textsParams.leftMargin = Screen.dp(14f);
      layout.addView(texts, textsParams);
      TextView name = newText(context, 16, 0xffffffff, false);
      name.setGravity(Gravity.LEFT);
      TextView status = newText(context, 13, COLOR_SECONDARY, false);
      status.setGravity(Gravity.LEFT);
      texts.addView(name);
      texts.addView(status);
      ImageView mic = new ImageView(context);
      mic.setScaleType(ImageView.ScaleType.CENTER);
      layout.addView(mic, new LinearLayout.LayoutParams(Screen.dp(32f), Screen.dp(32f)));
      return new Row(layout, avatar, name, status, mic);
    }

    @Override
    public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
      if (!(holder instanceof Row)) {
        LinearLayout layout = (LinearLayout) holder.itemView;
        boolean add = position == items.size();
        ((ImageView) layout.getChildAt(0)).setImageResource(add ? R.drawable.baseline_person_add_24 : R.drawable.baseline_link_24);
        ((TextView) layout.getChildAt(1)).setText(Lang.getString(add ? R.string.Tgx101VoiceChatAdd : R.string.Tgx101VoiceChatInvite));
        layout.setOnClickListener(v -> inviteByLink()); // both: the link goes to whoever should join (a chat picker sends it)
        return;
      }
      Row row = (Row) holder;
      TdApi.GroupCallParticipant participant = items.get(position);
      row.avatar.setMessageSender(tdlib, participant.participantId);
      String name = tdlib.senderName(participant.participantId);
      row.name.setText(participant.isCurrentUser ? Lang.getString(R.string.Tgx101VoiceChatYou, name) : name);
      boolean speaking = call != null && call.isSpeaking(participant);
      boolean muted = participant.isMutedForAllUsers || participant.isMutedForCurrentUser || (participant.isCurrentUser && call != null && call.isMuted());
      if (speaking) {
        row.status.setText(Lang.getString(R.string.Tgx101VoiceChatSpeaking));
        row.status.setTextColor(COLOR_SPEAKING);
      } else if (participant.isHandRaised) {
        row.status.setText("✋ " + Lang.getString(R.string.Tgx101VoiceChatWantsToSpeak));
        row.status.setTextColor(0xffffc857);
      } else if (participant.bio != null && !participant.bio.isEmpty() && !muted) {
        row.status.setText(participant.bio);
        row.status.setTextColor(COLOR_SECONDARY);
      } else {
        row.status.setText(Lang.getString(muted ? R.string.Tgx101VoiceChatMicOff : R.string.Tgx101VoiceChatListening));
        row.status.setTextColor(COLOR_SECONDARY);
      }
      row.mic.setImageResource(muted ? R.drawable.baseline_mic_off_24 : R.drawable.baseline_mic_24);
      row.mic.setColorFilter(speaking ? COLOR_SPEAKING : muted && participant.isMutedForAllUsers ? COLOR_END : COLOR_SECONDARY);
      row.itemView.setOnClickListener(v -> showParticipant(participant));
    }

    @Override
    public int getItemCount () {
      return items.size() + 2; // + «Add» and «Invite by link»
    }
  }

  // Views

  private ImageView iconButton (Context context, int icon, int description, View.OnClickListener click) {
    ImageView b = new ImageView(context);
    b.setImageResource(icon);
    b.setColorFilter(0xffffffff);
    b.setScaleType(ImageView.ScaleType.CENTER);
    b.setContentDescription(Lang.getString(description));
    org.thunderdog.challegram.support.RippleSupport.setTransparentWhiteSelector(b);
    b.setOnClickListener(click);
    b.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    return b;
  }

  private static TextView newText (Context context, int sizeSp, int color, boolean bold) {
    TextView view = new TextView(context);
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeSp);
    view.setTextColor(color);
    view.setSingleLine(true);
    view.setEllipsize(TextUtils.TruncateAt.END);
    view.setGravity(Gravity.CENTER_HORIZONTAL);
    if (bold) {
      view.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    }
    return view;
  }

  private static LinearLayout.LayoutParams centered () {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = params.rightMargin = Screen.dp(24f);
    return params;
  }

  private static GradientDrawable circle (int color) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.OVAL);
    drawable.setColor(color);
    return drawable;
  }

  private interface LabelCallback {
    void onLabel (TextView label);
  }

  private interface LockCallback {
    void onLock (ImageView lock);
  }

  private static ImageView addButton (Context context, LinearLayout row, int icon, int sizeDp, int labelRes, View.OnClickListener onClick, @Nullable LabelCallback labelCallback, @Nullable LockCallback lockCallback) {
    LinearLayout cell = new LinearLayout(context);
    cell.setOrientation(LinearLayout.VERTICAL);
    cell.setGravity(Gravity.CENTER_HORIZONTAL);
    row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    FrameLayout frame = new FrameLayout(context);
    frame.setClipChildren(false);
    ImageView button = new ImageView(context);
    button.setImageResource(icon);
    button.setColorFilter(0xffffffff);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setBackground(circle(0x22ffffff));
    button.setOnClickListener(onClick);
    frame.addView(button, new FrameLayout.LayoutParams(Screen.dp(sizeDp), Screen.dp(sizeDp)));
    // the lock on a forbidden button (mockup 10)
    ImageView lock = new ImageView(context);
    lock.setImageResource(R.drawable.baseline_lock_24);
    lock.setColorFilter(0xffffffff);
    lock.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    lock.setPadding(Screen.dp(3f), Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));
    lock.setBackground(circle(COLOR_END));
    lock.setVisibility(View.GONE);
    frame.addView(lock, new FrameLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f), Gravity.BOTTOM | Gravity.RIGHT));
    cell.addView(frame, new LinearLayout.LayoutParams(Screen.dp(sizeDp), Screen.dp(sizeDp)));
    if (lockCallback != null) lockCallback.onLock(lock);
    TextView label = newText(context, 12, COLOR_SECONDARY, false);
    if (labelRes != 0) {
      label.setText(Lang.getString(labelRes));
    }
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    labelParams.topMargin = Screen.dp(8f);
    cell.addView(label, labelParams);
    if (labelCallback != null) {
      labelCallback.onLabel(label);
    }
    return button;
  }
}
