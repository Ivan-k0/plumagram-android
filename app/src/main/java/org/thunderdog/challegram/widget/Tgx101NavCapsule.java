package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101: floating bottom navigation capsule over the chat list (mockup «Капсула», variant B without labels):
 * white ~92 % capsule with a soft shadow, the selected tab sits on a tinted «pill» that slides to the tapped tab.
 */
public class Tgx101NavCapsule extends View {
  public static final int TAB_CHATS = 0, TAB_CONTACTS = 1, TAB_CALLS = 2, TAB_SETTINGS = 3;

  public static final float HEIGHT_DP = 58f, MARGIN_DP = 14f;

  public interface Callback {
    void onTabClick (int tab);
    default boolean onTabLongClick (int tab) { return false; }
  }

  private static final int[] ICONS = {
    R.drawable.baseline_chat_bubble_24,
    R.drawable.baseline_person_24,
    R.drawable.baseline_phone_24,
    R.drawable.baseline_settings_24
  };

  private final Drawable[] icons = new Drawable[ICONS.length];
  private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private Callback callback;
  private float selected = TAB_CHATS; // animated position of the pill
  private ValueAnimator animator;
  private int pressedTab = -1;
  private boolean longPressed;
  private final Runnable longPress = () -> {
    if (pressedTab != -1 && callback != null && callback.onTabLongClick(pressedTab)) {
      longPressed = true;
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
  };

  public Tgx101NavCapsule (Context context) {
    super(context);
    for (int i = 0; i < ICONS.length; i++) {
      icons[i] = Drawables.get(getResources(), ICONS[i]);
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      setLayerType(LAYER_TYPE_SOFTWARE, null); // setShadowLayer on older Android
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  private float padding () {
    return Screen.dp(MARGIN_DP);
  }

  private float tabWidth () {
    return (getMeasuredWidth() - padding() * 2 - Screen.dp(12f)) / ICONS.length;
  }

  private float tabCenterX (float tab) {
    return padding() + Screen.dp(6f) + tabWidth() * (tab + .5f);
  }

  private float capsuleTop () {
    return getMeasuredHeight() - getPaddingBottom() - Screen.dp(MARGIN_DP) - Screen.dp(HEIGHT_DP);
  }

  private int tabAt (float x, float y) {
    float top = capsuleTop();
    if (y < top || y > top + Screen.dp(HEIGHT_DP) || x < padding() || x > getMeasuredWidth() - padding())
      return -1;
    int tab = (int) ((x - padding() - Screen.dp(6f)) / tabWidth());
    return Math.max(0, Math.min(ICONS.length - 1, tab));
  }

  /** Slides the pill; the screen behind the tab opens right after (see MainController). */
  public void setSelectedTab (int tab, boolean animated) {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
    if (!animated) {
      selected = tab;
      invalidate();
      return;
    }
    animator = ValueAnimator.ofFloat(selected, tab);
    animator.setDuration(180);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(a -> {
      selected = (float) a.getAnimatedValue();
      invalidate();
    });
    animator.start();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int h = Screen.dp(HEIGHT_DP + MARGIN_DP) + Screen.dp(8f) + getPaddingBottom(); // + room for the shadow
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        pressedTab = tabAt(e.getX(), e.getY());
        if (pressedTab == -1)
          return false; // chats under the transparent area stay touchable
        longPressed = false;
        postDelayed(longPress, 450);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (pressedTab != -1 && tabAt(e.getX(), e.getY()) != pressedTab) {
          removeCallbacks(longPress);
          pressedTab = -1;
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        removeCallbacks(longPress);
        int tab = pressedTab;
        pressedTab = -1;
        if (tab != -1 && !longPressed && callback != null) {
          playSoundEffect(android.view.SoundEffectConstants.CLICK);
          callback.onTabClick(tab);
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        removeCallbacks(longPress);
        pressedTab = -1;
        return true;
      }
    }
    return true;
  }

  @Override
  protected void onDraw (Canvas c) {
    float top = capsuleTop();
    float h = Screen.dp(HEIGHT_DP);
    float r = h / 2f;
    rect.set(padding(), top, getMeasuredWidth() - padding(), top + h);

    int filling = Theme.getColor(ColorId.filling);
    boolean dark = Theme.isDark();
    shadowPaint.setColor(ColorUtils.alphaColor(dark ? .94f : .92f, filling));
    shadowPaint.setShadowLayer(Screen.dp(10f), 0, Screen.dp(3f), ColorUtils.alphaColor(dark ? .5f : .18f, 0xff000000));
    c.drawRoundRect(rect, r, r, shadowPaint);

    // the pill behind the selected tab
    int active = Theme.getColor(ColorId.iconActive);
    float cx = tabCenterX(selected);
    float pw = Math.min(tabWidth() - Screen.dp(8f), Screen.dp(64f)) / 2f;
    float ph = Screen.dp(22f);
    float cy = top + h / 2f;
    fillPaint.setColor(ColorUtils.alphaColor(dark ? .22f : .13f, active));
    rect.set(cx - pw, cy - ph, cx + pw, cy + ph);
    c.drawRoundRect(rect, ph, ph, fillPaint);

    int inactive = Theme.getColor(ColorId.icon);
    for (int i = 0; i < icons.length; i++) {
      float f = Math.max(0f, 1f - Math.abs(selected - i));
      int color = ColorUtils.fromToArgb(inactive, active, f);
      Drawables.drawCentered(c, icons[i], tabCenterX(i), cy, Paints.getPorterDuffPaint(color));
    }
  }
}
