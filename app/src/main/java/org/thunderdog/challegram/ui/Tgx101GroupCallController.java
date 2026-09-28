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

  private @Nullable Tgx101GroupCall call;
  private TextView titleView, statusView, micLabel;
  private ImageView micButton, speakerButton;
  private ParticipantsAdapter adapter;

  @Override
  protected View onCreateView (Context context) {
    call = Tgx101GroupCall.current();

    FrameLayout root = new FrameLayout(context);
    root.addView(new Tgx101CallBackground(context), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(0, Screen.getStatusBarHeight() + Screen.dp(24f), 0, Screen.dp(24f));
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    titleView = newText(context, 22, 0xffffffff, true);
    column.addView(titleView, centered());
    statusView = newText(context, 14, COLOR_SECONDARY, false);
    column.addView(statusView, centered());

    RecyclerView list = new RecyclerView(context);
    list.setLayoutManager(new LinearLayoutManager(context));
    list.setAdapter(adapter = new ParticipantsAdapter());
    list.setOverScrollMode(View.OVER_SCROLL_NEVER);
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    listParams.topMargin = Screen.dp(16f);
    column.addView(list, listParams);

    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.CENTER_VERTICAL);
    column.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    speakerButton = addButton(context, buttons, R.drawable.baseline_volume_up_24, 56, R.string.Tgx101VoiceChatSpeaker, v -> {
      if (call != null) call.toggleSpeaker();
    }, null);
    micButton = addButton(context, buttons, R.drawable.baseline_mic_24, 76, 0, v -> {
      if (call != null && !call.toggleMuted()) {
        UI.showToast(R.string.Tgx101VoiceChatMutedByAdmin, Toast.LENGTH_SHORT);
      }
    }, label -> micLabel = label);
    addButton(context, buttons, R.drawable.baseline_call_end_24, 56, R.string.Tgx101VoiceChatLeave, v -> onLeaveClick(), null).setBackground(circle(COLOR_END));

    if (call != null) {
      call.addListener(this);
    }
    updateAll();
    return root;
  }

  private void onLeaveClick () {
    if (call == null) {
      navigateBack();
      return;
    }
    TdApi.GroupCall groupCall = call.getGroupCall();
    if (groupCall != null && groupCall.canBeManaged) {
      showOptions(null,
        new int[] {R.id.btn_done, R.id.btn_delete},
        new String[] {Lang.getString(R.string.Tgx101VoiceChatLeave), Lang.getString(R.string.Tgx101VoiceChatEndForAll)},
        new int[] {OptionColor.NORMAL, OptionColor.RED},
        new int[] {R.drawable.baseline_call_end_24, R.drawable.baseline_delete_24},
        (itemView, id) -> {
          if (id == R.id.btn_delete) {
            call.endForAll();
          } else {
            call.leave();
          }
          return true;
        });
    } else {
      call.leave();
    }
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
    updateStatus();
  }

  @Override
  public void onGroupCallSpeakingChanged (Tgx101GroupCall call) {
    adapter.notifyItemRangeChanged(0, adapter.getItemCount());
  }

  private void updateAll () {
    if (call == null) {
      titleView.setText(Lang.getString(R.string.Tgx101VoiceChat));
      statusView.setText(Lang.getString(R.string.Tgx101VoiceChatEnded));
      return;
    }
    titleView.setText(call.getTitle());
    updateStatus();
    micButton.setBackground(circle(call.isMuted() ? 0x33ffffff : COLOR_ACCENT));
    micButton.setAlpha(call.isMuted() ? .7f : 1f);
    micLabel.setText(Lang.getString(call.isMuted() ? R.string.Tgx101VoiceChatMicOff : R.string.Tgx101VoiceChatMicOn));
    speakerButton.setBackground(circle(call.isSpeakerOn() ? 0x55ffffff : 0x22ffffff));
    adapter.setItems(call.getParticipants());
  }

  private void updateStatus () {
    if (call == null) return;
    if (call.getState() != Tgx101GroupCall.STATE_CONNECTED) {
      statusView.setText(Lang.getString(R.string.Tgx101VoiceChatConnecting));
    } else {
      TdApi.GroupCall groupCall = call.getGroupCall();
      int count = groupCall != null ? groupCall.participantCount : adapter.getItemCount();
      statusView.setText(Lang.getString(R.string.Tgx101VoiceChatParticipantsCount, count));
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (call != null) {
      call.removeListener(this);
    }
  }

  // Participants

  private final class ParticipantsAdapter extends RecyclerView.Adapter<ParticipantsAdapter.Row> {
    private final List<TdApi.GroupCallParticipant> items = new ArrayList<>();

    void setItems (List<TdApi.GroupCallParticipant> participants) {
      items.clear();
      items.addAll(participants);
      notifyDataSetChanged();
    }

    final class Row extends RecyclerView.ViewHolder {
      final AvatarView avatar;
      final TextView name, status;

      Row (LinearLayout layout, AvatarView avatar, TextView name, TextView status) {
        super(layout);
        this.avatar = avatar;
        this.name = name;
        this.status = status;
      }
    }

    @NonNull
    @Override
    public Row onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      Context context = parent.getContext();
      LinearLayout layout = new LinearLayout(context);
      layout.setOrientation(LinearLayout.HORIZONTAL);
      layout.setGravity(Gravity.CENTER_VERTICAL);
      layout.setPadding(Screen.dp(20f), Screen.dp(8f), Screen.dp(20f), Screen.dp(8f));
      layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
      return new Row(layout, avatar, name, status);
    }

    @Override
    public void onBindViewHolder (@NonNull Row row, int position) {
      TdApi.GroupCallParticipant participant = items.get(position);
      row.avatar.setMessageSender(tdlib, participant.participantId);
      String name = tdlib.senderName(participant.participantId);
      row.name.setText(participant.isCurrentUser ? Lang.getString(R.string.Tgx101VoiceChatYou, name) : name);
      boolean speaking = call != null && call.isSpeaking(participant);
      boolean muted = participant.isMutedForAllUsers || participant.isMutedForCurrentUser || (participant.isCurrentUser && call != null && call.isMuted());
      if (speaking) {
        row.status.setText(Lang.getString(R.string.Tgx101VoiceChatSpeaking));
        row.status.setTextColor(COLOR_SPEAKING);
      } else {
        row.status.setText(Lang.getString(muted ? R.string.Tgx101VoiceChatMicOff : R.string.Tgx101VoiceChatListening));
        row.status.setTextColor(COLOR_SECONDARY);
      }
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }

  // Views

  private static TextView newText (Context context, int sizeSp, int color, boolean bold) {
    TextView view = new TextView(context);
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeSp);
    view.setTextColor(color);
    view.setSingleLine(true);
    view.setEllipsize(TextUtils.TruncateAt.END);
    view.setGravity(Gravity.CENTER_HORIZONTAL);
    if (bold) {
      view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

  private static ImageView addButton (Context context, LinearLayout row, int icon, int sizeDp, int labelRes, View.OnClickListener onClick, @Nullable LabelCallback labelCallback) {
    LinearLayout cell = new LinearLayout(context);
    cell.setOrientation(LinearLayout.VERTICAL);
    cell.setGravity(Gravity.CENTER_HORIZONTAL);
    row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    ImageView button = new ImageView(context);
    button.setImageResource(icon);
    button.setColorFilter(0xffffffff);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setBackground(circle(0x22ffffff));
    button.setOnClickListener(onClick);
    cell.addView(button, new LinearLayout.LayoutParams(Screen.dp(sizeDp), Screen.dp(sizeDp)));
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
