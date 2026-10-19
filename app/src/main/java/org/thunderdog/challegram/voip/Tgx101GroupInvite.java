package org.thunderdog.challegram.voip;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101 (user 2026-10-19 «делай групповые звонки до конца»; TDLib: an incoming messageGroupCall that isn't active, missed
 * or ended means «show the incoming call screen»): an invitation to a group call rings like a call — the ringtone and the
 * vibration, the screen with «Accept» / «Decline» (or a full-screen notification over a locked or dark screen). Accept —
 * joinGroupCall by the message, decline — declineGroupCallInvitation; it stops by itself after 45 s, or when the call
 * became active or missed (answered on another device).
 */
public final class Tgx101GroupInvite {
  public static final String ACTION_OPEN = Intents.ACTION_OPEN_CALL + ".TGX101_GROUP_INVITE";
  private static final String ACTION_DECLINE = "org.thunderdog.challegram.TGX101_GROUP_INVITE_DECLINE";
  private static final String ACTION_SILENCE = "org.thunderdog.challegram.TGX101_GROUP_INVITE_SILENCE";
  private static final String CHANNEL_ID = "tgx101_group_invite";
  private static final int NOTIFICATION_ID = 0x7101;
  private static final long RING_TIMEOUT_MS = 45000;

  private static @Nullable Tgx101GroupInvite current;

  public static @Nullable Tgx101GroupInvite current () {
    return current;
  }

  public final Tdlib tdlib;
  public final TdApi.Message message;
  public final boolean isVideo;
  public final TdApi.MessageSender[] others;

  private @Nullable MediaPlayer player;
  private @Nullable Vibrator vibrator;
  private @Nullable BroadcastReceiver receiver;
  private final Runnable timeout = () -> finish("no answer in " + RING_TIMEOUT_MS / 1000 + " s");

  private Tgx101GroupInvite (Tdlib tdlib, TdApi.Message message) {
    this.tdlib = tdlib;
    this.message = message;
    TdApi.MessageGroupCall content = (TdApi.MessageGroupCall) message.content;
    this.isVideo = content.isVideo;
    this.others = content.otherParticipantIds != null ? content.otherParticipantIds : new TdApi.MessageSender[0];
  }

  private static boolean isRinging (TdApi.MessageContent content) {
    if (content == null || content.getConstructor() != TdApi.MessageGroupCall.CONSTRUCTOR) return false;
    TdApi.MessageGroupCall call = (TdApi.MessageGroupCall) content;
    return !call.isActive && !call.wasMissed && call.duration == 0;
  }

  /** every new message (Tdlib): an invitation that should ring */
  public static void onNewMessage (Tdlib tdlib, TdApi.Message message) {
    if (message == null || message.isOutgoing || !isRinging(message.content)) return;
    if (System.currentTimeMillis() / 1000L - message.date > 60) return; // an old one that came with the history
    if (Tgx101GroupCall.isFollowUpInvite(tdlib, message)) {
      // our 1:1 call has just become a conference: follow it at once, as the official app does, no ringing
      UI.post(() -> {
        Tgx101GroupCall running = Tgx101GroupCall.current();
        if (running != null && running.getState() != Tgx101GroupCall.STATE_ENDED) return;
        org.thunderdog.challegram.Tgx101Diag.mark("group invite: the 1:1 call went on as a conference — joining");
        Tgx101GroupCall.joinConference(tdlib, message.chatId, message.id);
        BaseActivity activity = UI.getUiContext();
        if (activity != null) org.thunderdog.challegram.ui.Tgx101GroupCallController.open(activity);
      });
      return;
    }
    UI.post(() -> show(tdlib, message));
  }

  /** a message's content changed: the ringing invitation became active (answered elsewhere) or missed */
  public static void onContentChanged (Tdlib tdlib, long chatId, long messageId, TdApi.MessageContent content) {
    UI.post(() -> {
      Tgx101GroupInvite invite = current;
      if (invite != null && invite.tdlib.id() == tdlib.id() && invite.message.chatId == chatId && invite.message.id == messageId && !isRinging(content)) {
        invite.finish("the call became " + (((TdApi.MessageGroupCall) content).isActive ? "active" : "missed"));
      }
    });
  }

  @UiThread
  private static void show (Tdlib tdlib, TdApi.Message message) {
    if (current != null) return;
    Tgx101GroupCall running = Tgx101GroupCall.current();
    if (running != null && running.getState() != Tgx101GroupCall.STATE_ENDED) {
      org.thunderdog.challegram.Tgx101Diag.mark("group invite: busy in a group call — the message only");
      return;
    }
    if (tdlib.context().calls().getCurrentCall() != null) {
      org.thunderdog.challegram.Tgx101Diag.mark("group invite: busy in a call — the message only");
      return;
    }
    current = new Tgx101GroupInvite(tdlib, message);
    current.start();
  }

  private void start () {
    org.thunderdog.challegram.Tgx101Diag.mark("group invite: ringing (chat " + message.chatId + ", message " + message.id + (isVideo ? ", video" : "") + ")");
    Context context = UI.getContext();
    receiver = new BroadcastReceiver() {
      @Override
      public void onReceive (Context c, Intent intent) {
        if (ACTION_DECLINE.equals(intent.getAction())) decline();
        else if (ACTION_SILENCE.equals(intent.getAction())) silence();
      }
    };
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_DECLINE);
    filter.addAction(ACTION_SILENCE);
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    startRinging(context);
    showNotification(context);
    UI.post(timeout, RING_TIMEOUT_MS);
    BaseActivity activity = UI.getUiContext();
    if (activity != null && UI.isResumed()) {
      org.thunderdog.challegram.ui.Tgx101GroupInviteController.open(activity);
    }
  }

  // Ringing

  private void startRinging (Context context) {
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    int mode = audio != null ? audio.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
    if (mode != AudioManager.RINGER_MODE_SILENT) {
      vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator != null) {
        long[] pattern = {0, 1000, 1000};
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0),
              new android.os.VibrationAttributes.Builder().setUsage(android.os.VibrationAttributes.USAGE_RINGTONE).build());
          } else {
            vibrator.vibrate(pattern, 0);
          }
        } catch (Throwable ignored) { }
      }
    }
    if (mode == AudioManager.RINGER_MODE_NORMAL) {
      try {
        MediaPlayer p = new MediaPlayer();
        p.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build());
        p.setLooping(true);
        p.setDataSource(context, Uri.parse(tdlib.notifications().getCallRingtone(message.chatId)));
        p.setOnPreparedListener(MediaPlayer::start);
        p.prepareAsync();
        player = p;
      } catch (Throwable t) {
        org.thunderdog.challegram.Tgx101Diag.mark("group invite: ringtone failed — " + t);
      }
    }
  }

  /** the ringing stops, the invitation stays (as «Silence» on a call) */
  public void silence () {
    if (player != null) {
      try {
        player.stop();
      } catch (Throwable ignored) { }
      player.release();
      player = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }
  }

  // Answers

  public void accept (@Nullable BaseActivity activity) {
    finish("accepted");
    Tgx101GroupCall.joinConference(tdlib, message.chatId, message.id);
    if (activity != null) {
      org.thunderdog.challegram.ui.Tgx101GroupCallController.open(activity);
    }
  }

  public void decline () {
    tdlib.send(new TdApi.DeclineGroupCallInvitation(message.chatId, message.id), (ok, error) -> {
      if (error != null) org.thunderdog.challegram.Tgx101Diag.mark("group invite: decline failed — " + TD.toErrorString(error));
    });
    finish("declined");
  }

  /** declined with one of the user's quick replies, sent to the inviter's chat */
  public void declineWithText (String text) {
    long chatId = message.chatId;
    decline();
    tdlib.send(new TdApi.SendMessage(chatId, null, null, null, null, new TdApi.InputMessageText(new TdApi.FormattedText(text, new TdApi.TextEntity[0]), null, false)), (sent, error) -> { });
  }

  @UiThread
  private void finish (String why) {
    if (current != this) return;
    current = null;
    org.thunderdog.challegram.Tgx101Diag.mark("group invite: " + why);
    UI.cancel(timeout);
    silence();
    Context context = UI.getContext();
    NotificationManager m = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) m.cancel(NOTIFICATION_ID);
    if (receiver != null) {
      try {
        context.unregisterReceiver(receiver);
      } catch (Throwable ignored) { }
      receiver = null;
    }
    org.thunderdog.challegram.ui.Tgx101GroupInviteController.closeAll();
  }

  // Notification: a call-style one; over a locked or dark screen it opens the invitation screen by itself

  public String inviterName () {
    return tdlib.senderName(message.senderId);
  }

  private void showNotification (Context context) {
    NotificationManager m = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (m == null) return;
    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = new android.app.NotificationChannel(CHANNEL_ID, Lang.getString(R.string.Tgx101GroupInviteChannel), NotificationManager.IMPORTANCE_HIGH);
      channel.enableVibration(false); // our own ringing does the sound and the vibration
      channel.setSound(null, null);
      try {
        m.createNotificationChannel(channel);
      } catch (Throwable ignored) { }
      builder = new Notification.Builder(context, CHANNEL_ID);
    } else {
      builder = new Notification.Builder(context);
    }
    Intent open = new Intent(context, MainActivity.class);
    open.setAction(ACTION_OPEN);
    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent openIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, open, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent declineIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID + 1, new Intent(ACTION_DECLINE).setPackage(context.getPackageName()), Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent silenceIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID + 2, new Intent(ACTION_SILENCE).setPackage(context.getPackageName()), Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setSmallIcon(R.drawable.baseline_plumagram_24)
      .setContentTitle(inviterName())
      .setContentText(Lang.getString(isVideo ? R.string.Tgx101GroupInviteVideoText : R.string.Tgx101GroupInviteText))
      .setContentIntent(openIntent)
      .setOngoing(true)
      .setAutoCancel(false)
      .addAction(R.drawable.round_call_end_24_white, Lang.getString(R.string.DeclineCall), declineIntent)
      .addAction(R.drawable.round_call_24_white, Lang.getString(R.string.AnswerCall), openIntent)
      .addAction(R.drawable.baseline_volume_off_24_white, Lang.getString(R.string.Tgx101CallSilence), silenceIntent)
      .setPriority(Notification.PRIORITY_MAX)
      .setCategory(Notification.CATEGORY_CALL)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setColor(tdlib.accountColor());
    if (needFullScreen(context)) {
      builder.setFullScreenIntent(openIntent, true);
    }
    try {
      m.notify(NOTIFICATION_ID, builder.build());
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("group invite: notification failed — " + t);
    }
  }

  /** as for a call: only over a locked or dark screen (on an unlocked Vivo it would pull the app up every time) */
  private static boolean needFullScreen (Context context) {
    android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
    android.app.KeyguardManager km = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    boolean interactive = pm == null || pm.isInteractive();
    boolean locked = km != null && km.isKeyguardLocked();
    return !interactive || locked;
  }
}
