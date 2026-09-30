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
package org.thunderdog.challegram.service;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101: the incoming call card over an unlocked phone (variant 1 — like the system call card).
 * Vivo keeps third-party heads-up notifications hidden even for calls, so the card is an own overlay
 * window («поверх других окон» is granted). Swipe it up to hide; the call notification stays.
 */
final class Tgx101CallPopup {
  private Tgx101CallPopup () { }

  interface Callback {
    void onAnswer ();
    void onDecline ();
    void onOpen ();
  }

  private static View shownView;
  private static int shownCallId;
  private static int hiddenByUserCallId;

  /** Whether the card may be shown right now: overlays allowed, the screen is on and unlocked */
  static boolean canShow (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Settings.canDrawOverlays(context)) {
      return false;
    }
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    boolean interactive = pm != null && pm.isInteractive();
    boolean locked = km != null && km.isKeyguardLocked();
    return interactive && !locked;
  }

  static void show (Context context, int callId, String name, @Nullable Bitmap photo, Callback callback) {
    if (shownView != null && shownCallId == callId) return;
    if (hiddenByUserCallId == callId) return;
    hide(context);
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    if (wm == null) return;

    // follows the system theme: dark card in the dark mode
    boolean dark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    int pad = Screen.dp(14f);
    card.setPadding(pad, pad, pad, pad);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(dark ? 0xff1f2a33 : 0xffffffff);
    bg.setCornerRadius(Screen.dp(22f));
    card.setBackground(bg);
    card.setElevation(Screen.dp(8f));

    LinearLayout top = new LinearLayout(context);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    ImageView avatar = new ImageView(context);
    if (photo != null) {
      avatar.setImageBitmap(photo);
    } else {
      avatar.setImageResource(R.drawable.baseline_plumagram_24);
      avatar.setColorFilter(dark ? 0xff8fb3d9 : 0xff4f7aa3);
    }
    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
    top.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.setPadding(Screen.dp(12f), 0, 0, 0);
    TextView subtitle = new TextView(context);
    subtitle.setText(Lang.getString(R.string.Tgx101CallPopupSubtitle));
    subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    subtitle.setTextColor(dark ? 0xff9fb0bf : 0xff6f7d89);
    subtitle.setTypeface(Fonts.getRobotoRegular());
    texts.addView(subtitle);
    TextView title = new TextView(context);
    title.setText(name);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    title.setTextColor(dark ? 0xffffffff : 0xff1f2a33);
    title.setTypeface(Fonts.getRobotoBold());
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    texts.addView(title);
    top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    card.addView(top);

    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f));
    buttonsParams.topMargin = Screen.dp(12f);
    card.addView(buttons, buttonsParams);
    TextView decline = pill(context, Lang.getString(R.string.DeclineCall), 0xffe5484d);
    TextView answer = pill(context, Lang.getString(R.string.AnswerCall), 0xff2fa855);
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    left.rightMargin = Screen.dp(5f);
    LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    right.leftMargin = Screen.dp(5f);
    buttons.addView(decline, left);
    buttons.addView(answer, right);

    decline.setOnClickListener(v -> { hide(context); callback.onDecline(); });
    answer.setOnClickListener(v -> { hide(context); callback.onAnswer(); });
    card.setOnClickListener(v -> { hide(context); callback.onOpen(); });
    attachSwipeToHide(context, card, callId);

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP;
    params.y = Screen.getStatusBarHeight() + Screen.dp(6f);
    params.horizontalMargin = 0;
    LinearLayout frame = new LinearLayout(context);
    frame.setPadding(Screen.dp(10f), 0, Screen.dp(10f), Screen.dp(10f));
    frame.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    try {
      wm.addView(frame, params);
      shownView = frame;
      shownCallId = callId;
      card.setTranslationY(-Screen.dp(120f));
      card.setAlpha(0f);
      card.animate().translationY(0f).alpha(1f).setDuration(220).start();
    } catch (Throwable t) {
      android.util.Log.w("TGx101", "call popup failed", t);
    }
  }

  static void hide (Context context) {
    View view = shownView;
    shownView = null;
    shownCallId = 0;
    if (view == null) return;
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    try {
      if (wm != null) wm.removeView(view);
    } catch (Throwable ignored) { }
  }

  /** The call is over: forget that the user swiped its card away */
  static void reset (Context context) {
    hide(context);
    hiddenByUserCallId = 0;
  }

  @SuppressLint("ClickableViewAccessibility")
  private static void attachSwipeToHide (Context context, View card, int callId) {
    final float[] downY = new float[1];
    card.setOnTouchListener((v, e) -> {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downY[0] = e.getRawY();
          break;
        case MotionEvent.ACTION_MOVE: {
          float dy = e.getRawY() - downY[0];
          if (dy < 0) v.setTranslationY(dy);
          break;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          float dy = e.getRawY() - downY[0];
          if (dy < -Screen.dp(40f)) {
            hiddenByUserCallId = callId;
            v.animate().translationY(-v.getHeight() - Screen.dp(40f)).alpha(0f).setDuration(160).withEndAction(() -> hide(context)).start();
            return true;
          }
          v.animate().translationY(0f).setDuration(150).start();
          if (Math.abs(dy) > Screen.dp(8f)) return true;
          break;
        }
      }
      return false;
    });
  }

  private static TextView pill (Context context, String text, int color) {
    TextView view = new TextView(context);
    view.setText(text);
    view.setGravity(Gravity.CENTER);
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
    view.setTextColor(0xffffffff);
    view.setTypeface(Fonts.getRobotoMedium());
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(color);
    bg.setCornerRadius(Screen.dp(21f));
    view.setBackground(bg);
    return view;
  }
}
