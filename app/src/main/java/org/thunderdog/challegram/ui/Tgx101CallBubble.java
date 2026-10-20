package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.voip.Tgx101GroupCall;
import org.thunderdog.challegram.widget.AvatarView;

/**
 * TGx101 (approved mockup «Групповой звонок», 8 · «Фоновый режим»; user 2026-10-19 «такая кнопка нужна»): while a call
 * (one-to-one or group) goes on and PlumaGram is in the background, a capsule floats over the other apps — the avatar of
 * who speaks with a green ring, and the microphone. A tap on the avatar brings the call back, a tap on the microphone (or
 * a double tap anywhere) turns it on / off; it can be dragged, and stays at the side it was let go at. Needs «Display
 * over other apps»; switched in the group call's ⋮ («Background mode») and in MagiX → Calls.
 */
public final class Tgx101CallBubble {
  private static @Nullable Tgx101CallBubble shown;
  private static int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;

  public static boolean canDrawOverlays (Context context) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context);
  }

  /** opens the system screen where «Display over other apps» is allowed for PlumaGram */
  public static void requestPermission (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.getPackageName()));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
    } catch (Throwable ignored) { }
  }

  // The app's window came / went (BaseActivity onStart / onStop)

  public static void onAppHidden (Context context) {
    if (!Settings.instance().tgx101CallBubble() || !canDrawOverlays(context) || !hasCall()) return;
    if (shown == null) {
      shown = new Tgx101CallBubble(context.getApplicationContext());
      shown.attach();
    }
  }

  public static void onAppShown () {
    if (shown != null) {
      shown.detach();
      shown = null;
    }
  }

  private static boolean hasCall () {
    TGCallService service = TGCallService.currentInstance();
    if (service != null && service.tgx101ActiveCall() != null) return true;
    Tgx101GroupCall group = Tgx101GroupCall.current();
    return group != null && group.getState() == Tgx101GroupCall.STATE_CONNECTED;
  }

  // The capsule

  private final Context context;
  private final WindowManager windowManager;
  private final WindowManager.LayoutParams params;
  private final LinearLayout pill;
  private final AvatarView avatar;
  private final FrameLayout avatarWrap;
  private final ImageView mic;
  private final Runnable refresher = new Runnable() {
    @Override
    public void run () {
      if (shown != Tgx101CallBubble.this) return;
      if (!hasCall()) {
        onAppShown();
        return;
      }
      refresh();
      UI.post(this, 400);
    }
  };
  private boolean speaking;
  private String avatarKey = "";

  @SuppressLint("ClickableViewAccessibility")
  private Tgx101CallBubble (Context context) {
    this.context = context;
    this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

    pill = new LinearLayout(context);
    pill.setOrientation(LinearLayout.HORIZONTAL);
    pill.setGravity(Gravity.CENTER_VERTICAL);
    pill.setPadding(Screen.dp(4f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xf01f2731);
    bg.setCornerRadius(Screen.dp(30f));
    pill.setBackground(bg);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) pill.setElevation(Screen.dp(8f));

    avatarWrap = new FrameLayout(context) {
      private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

      {
        setWillNotDraw(false);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Screen.dp(2.5f));
        ring.setColor(0xff5fd08c);
      }

      @Override
      protected void dispatchDraw (Canvas c) {
        super.dispatchDraw(c);
        if (speaking) c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f - Screen.dp(1.25f), ring);
      }
    };
    avatar = new AvatarView(context);
    FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(Screen.dp(42f), Screen.dp(42f), Gravity.CENTER);
    avatarWrap.addView(avatar, ap);
    pill.addView(avatarWrap, new LinearLayout.LayoutParams(Screen.dp(50f), Screen.dp(50f)));

    mic = new ImageView(context);
    mic.setScaleType(ImageView.ScaleType.CENTER);
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f));
    mp.leftMargin = Screen.dp(6f);
    pill.addView(mic, mp);

    params = new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.START;
    int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
    int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
    params.x = lastX != Integer.MIN_VALUE ? lastX : screenWidth - Screen.dp(110f) - Screen.dp(12f);
    params.y = lastY != Integer.MIN_VALUE ? lastY : (int) (screenHeight * .32f);

    pill.setOnTouchListener(new View.OnTouchListener() {
      private float downRawX, downRawY;
      private int startX, startY;
      private boolean dragging;
      private long lastTapTime;
      private final int slop = ViewConfiguration.get(context).getScaledTouchSlop();

      @Override
      public boolean onTouch (View v, MotionEvent e) {
        switch (e.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            downRawX = e.getRawX();
            downRawY = e.getRawY();
            startX = params.x;
            startY = params.y;
            dragging = false;
            // (user 2026-10-20 15:35 «доработать анимацию — появление, нажатие, раскрытие»): it gives under the finger
            pill.animate().scaleX(.9f).scaleY(.9f).setDuration(110).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            return true;
          case MotionEvent.ACTION_MOVE: {
            float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
            if (!dragging && Math.hypot(dx, dy) > slop) {
              dragging = true;
              pill.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start(); // lifted while it moves
            }
            if (dragging) {
              params.x = startX + (int) dx;
              params.y = startY + (int) dy;
              update();
            }
            return true;
          }
          case MotionEvent.ACTION_CANCEL:
            pill.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            return true;
          case MotionEvent.ACTION_UP:
            pill.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
            if (dragging) {
              snapToSide();
              return true;
            }
            boolean onMic = e.getX() >= mic.getLeft() - Screen.dp(6f);
            long now = android.os.SystemClock.uptimeMillis();
            if (onMic || now - lastTapTime < 300) {
              lastTapTime = 0;
              UI.cancel(openLater);
              toggleMic();
            } else {
              lastTapTime = now;
              UI.post(openLater, 300); // a second tap within 300 ms is the microphone (mockup: «двойное нажатие — микрофон»)
            }
            return true;
        }
        return false;
      }
    });
  }

  private final Runnable openLater = this::tgx101Open;

  private void tgx101Open () {
    // the opening: the capsule swells and melts while the call screen comes up
    pill.animate().scaleX(1.25f).scaleY(1.25f).alpha(0f).setDuration(180).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
    openCall();
  }

  private void attach () {
    try {
      pill.setScaleX(.4f);
      pill.setScaleY(.4f);
      pill.setAlpha(0f);
      windowManager.addView(pill, params);
      // grows out of a point with a little spring
      pill.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
      org.thunderdog.challegram.Tgx101Diag.mark("call bubble: shown");
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("call bubble: can't show — " + t);
      shown = null;
      return;
    }
    refresh();
    UI.post(refresher, 400);
  }

  private void detach () {
    UI.cancel(refresher);
    UI.cancel(openLater);
    lastX = params.x;
    lastY = params.y;
    // shrinks and fades, then leaves the screen
    pill.animate().cancel();
    pill.animate().scaleX(.4f).scaleY(.4f).alpha(0f).setDuration(160).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
      try {
        windowManager.removeView(pill);
      } catch (Throwable ignored) { }
    }).start();
  }

  private void update () {
    try {
      windowManager.updateViewLayout(pill, params);
    } catch (Throwable ignored) { }
  }

  private void snapToSide () {
    int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
    int width = pill.getWidth();
    int target = params.x + width / 2 < screenWidth / 2 ? Screen.dp(12f) : screenWidth - width - Screen.dp(12f);
    android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(params.x, target);
    a.setDuration(200);
    a.setInterpolator(new android.view.animation.DecelerateInterpolator());
    a.addUpdateListener(an -> {
      params.x = (int) an.getAnimatedValue();
      update();
    });
    a.start();
  }

  private void refresh () {
    boolean muted;
    TGCallService service = TGCallService.currentInstance();
    TdApi.Call call = service != null ? service.tgx101ActiveCall() : null;
    if (call != null && service.tgx101Tdlib() != null) {
      Tdlib tdlib = service.tgx101Tdlib();
      setAvatarUser(tdlib, call.userId);
      muted = service.tgx101IsMicMuted();
      speaking = true;
    } else {
      Tgx101GroupCall group = Tgx101GroupCall.current();
      if (group == null) return;
      muted = group.isMuted();
      TdApi.GroupCallParticipant speaker = null;
      for (TdApi.GroupCallParticipant p : group.getParticipants()) {
        if (!p.isCurrentUser && group.isSpeaking(p)) {
          speaker = p;
          break;
        }
      }
      speaking = speaker != null;
      if (speaker != null) {
        setAvatarSender(group.tdlib, speaker.participantId);
      } else if (group.chatId != 0) {
        String key = "chat" + group.chatId;
        if (!key.equals(avatarKey)) {
          avatarKey = key;
          avatar.setChat(group.tdlib, group.tdlib.chat(group.chatId));
        }
      } else {
        TdApi.GroupCallParticipant me = group.me();
        if (me != null) setAvatarSender(group.tdlib, me.participantId);
      }
    }
    avatarWrap.invalidate();
    mic.setImageResource(muted ? R.drawable.baseline_mic_off_24 : R.drawable.baseline_mic_24);
    mic.setColorFilter(muted ? 0xffffffff : 0xff0d2a18);
    GradientDrawable micBg = new GradientDrawable();
    micBg.setShape(GradientDrawable.OVAL);
    micBg.setColor(muted ? 0xff3a4552 : 0xff5fd08c);
    mic.setBackground(micBg);
  }

  private void setAvatarUser (Tdlib tdlib, long userId) {
    String key = "user" + userId;
    if (!key.equals(avatarKey)) {
      avatarKey = key;
      avatar.setUser(tdlib, userId, false);
    }
  }

  private void setAvatarSender (Tdlib tdlib, TdApi.MessageSender sender) {
    String key = "sender" + org.thunderdog.challegram.data.TD.makeSenderKey(sender);
    if (!key.equals(avatarKey)) {
      avatarKey = key;
      avatar.setMessageSender(tdlib, sender);
    }
  }

  private void toggleMic () {
    TGCallService service = TGCallService.currentInstance();
    if (service != null && service.tgx101ActiveCall() != null) {
      service.tgx101ToggleMic();
    } else {
      Tgx101GroupCall group = Tgx101GroupCall.current();
      if (group != null) group.toggleMuted();
    }
    refresh();
  }

  private void openCall () {
    TGCallService service = TGCallService.currentInstance();
    Intent intent;
    if (service != null && service.tgx101ActiveCall() != null) {
      intent = Intents.valueOfCall();
    } else {
      intent = new Intent(context, MainActivity.class);
      intent.setAction(org.thunderdog.challegram.service.Tgx101GroupCallService.ACTION_OPEN);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
    } catch (Throwable t) {
      org.thunderdog.challegram.Tgx101Diag.mark("call bubble: can't open the call — " + t);
    }
  }
}
