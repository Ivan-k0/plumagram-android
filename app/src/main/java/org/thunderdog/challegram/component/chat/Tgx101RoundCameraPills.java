package org.thunderdog.challegram.component.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Tgx101Haptics;
import org.thunderdog.challegram.tool.UI;

/**
 * TGx101 (user 2026-11-25 «выбор фронт или зад не отрывая палец… характерной анимацией», mockup variant А):
 * holding the round button brings up two pills above it — «Передняя» / «Задняя». The finger slides to one (it fills
 * with the accent, a tick, its camera icon turns over like a coin) and letting go there starts the round with that
 * camera, hands-free. Let go on the button itself — the pills stay and a tap picks; a tap elsewhere closes them.
 */
public class Tgx101RoundCameraPills extends View {
  public interface Callback {
    void onPicked (boolean front);
    void onDismissed ();
  }

  private static final int NONE = -1, FRONT = 0, REAR = 1;

  private final Callback callback;
  private final View anchor;
  private final TextPaint textPaint;
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF tagRect = new RectF();
  private final Drawable[] icons = new Drawable[2];
  private final String[] titles = new String[2];
  private final RectF[] rects = {new RectF(), new RectF()};
  private final float[] selectFactor = new float[2];
  private final float[] flipFactor = {1f, 1f};
  private final ValueAnimator[] selectAnimators = new ValueAnimator[2];
  private final ValueAnimator[] flipAnimators = new ValueAnimator[2];
  private final int[] anchorLoc = new int[2], selfLoc = new int[2];

  private float appear; // 0 → 1
  private ValueAnimator appearAnimator;
  private int selected = NONE;
  private boolean tracking = true; // the finger that opened the pills is still down
  private boolean finished;
  private int pickedForOut = NONE;

  private Tgx101RoundCameraPills (Context context, View anchor, Callback callback) {
    super(context);
    this.anchor = anchor;
    this.callback = callback;
    this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    this.textPaint.setTypeface(Fonts.getRobotoMedium());
    this.textPaint.setTextSize(Screen.dp(13f));
    this.tagPaint.setColor(0xff000000);
    this.shadowPaint.setColor(0x33000000);
    titles[FRONT] = Lang.getString(R.string.Tgx101RoundFront);
    titles[REAR] = Lang.getString(R.string.Tgx101RoundRear);
    icons[FRONT] = Drawables.get(getResources(), R.drawable.baseline_camera_front_24);
    icons[REAR] = Drawables.get(getResources(), R.drawable.baseline_camera_rear_24);
    for (int i = 0; i < 2; i++) {
      if (icons[i] != null) icons[i] = icons[i].mutate();
    }
  }

  /** Shows the pills over the window above {@code anchor}; null when there is no window to add them to. */
  public static Tgx101RoundCameraPills show (View anchor, Callback callback) {
    BaseActivity activity = UI.getContext(anchor.getContext());
    if (activity == null) return null;
    Tgx101RoundCameraPills pills = new Tgx101RoundCameraPills(anchor.getContext(), anchor, callback);
    pills.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    activity.addToRoot(pills, true);
    pills.animateAppear(true);
    Tgx101Haptics.play(Tgx101Haptics.CLICK);
    return pills;
  }

  private static float scale (Context context) {
    float s = U.getAnimationScale(context);
    return s > 0f ? s : 1f;
  }

  private long dur (long ms) {
    return (long) (ms / scale(getContext()));
  }

  private void animateAppear (boolean show) {
    if (appearAnimator != null) appearAnimator.cancel();
    appearAnimator = ValueAnimator.ofFloat(appear, show ? 1f : 0f);
    appearAnimator.setDuration(dur(show ? 320 : 200));
    appearAnimator.setInterpolator(show ? androidx.core.view.animation.PathInterpolatorCompat.create(.2f, 0f, 0f, 1f) : androidx.core.view.animation.PathInterpolatorCompat.create(.4f, 0f, 1f, 1f));
    appearAnimator.addUpdateListener(a -> {
      appear = (float) a.getAnimatedValue();
      invalidate();
    });
    if (!show) {
      appearAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (android.animation.Animator animation) {
          BaseActivity activity = UI.getContext(getContext());
          if (activity != null) activity.removeFromRoot(Tgx101RoundCameraPills.this);
        }
      });
    }
    appearAnimator.start();
  }

  private void animateSelect (int index, boolean on) {
    if (selectAnimators[index] != null) selectAnimators[index].cancel();
    ValueAnimator a = ValueAnimator.ofFloat(selectFactor[index], on ? 1f : 0f);
    a.setDuration(dur(on ? 220 : 160));
    a.setInterpolator(androidx.core.view.animation.PathInterpolatorCompat.create(.2f, 0f, 0f, 1f));
    a.addUpdateListener(v -> {
      selectFactor[index] = (float) v.getAnimatedValue();
      invalidate();
    });
    selectAnimators[index] = a;
    a.start();
    if (on) {
      // the coin turn of the camera icon
      if (flipAnimators[index] != null) flipAnimators[index].cancel();
      ValueAnimator f = ValueAnimator.ofFloat(0f, 1f);
      f.setDuration(dur(360));
      f.setInterpolator(androidx.core.view.animation.PathInterpolatorCompat.create(.3f, 0f, .2f, 1f));
      f.addUpdateListener(v -> {
        flipFactor[index] = (float) v.getAnimatedValue();
        invalidate();
      });
      flipAnimators[index] = f;
      f.start();
    }
  }

  private void setSelected (int index) {
    if (selected == index) return;
    if (selected != NONE) animateSelect(selected, false);
    selected = index;
    if (index != NONE) {
      animateSelect(index, true);
      Tgx101Haptics.play(Tgx101Haptics.TICK);
    }
  }

  // TGx101 (user 2026-11-25 «пилюли огромные… ставь друг над другом или переделай дизайн»): two round icons
  // in a column over the button — the front one nearer, the rear one above; a label only by the one under the finger
  private final RectF capsule = new RectF();

  // (user 2026-11-25 «разнеси кнопки выше и дальше… вид не очень… или предложи свой вариант»): one see-through
  // capsule with both icons, lifted off the button and the screen edge; an accent circle slides to the one picked
  private void layoutPills () {
    anchor.getLocationOnScreen(anchorLoc);
    getLocationOnScreen(selfLoc);
    float size = Screen.dp(40f), pad = Screen.dp(5f), gap = Screen.dp(6f);
    float w = size + pad * 2;
    float cx = anchorLoc[0] - selfLoc[0] + anchor.getWidth() / 2f;
    cx = Math.min(cx, getWidth() - w / 2f - Screen.dp(12f));
    float bottom = anchorLoc[1] - selfLoc[1] - Screen.dp(28f);
    capsule.set(cx - w / 2f, bottom - (size * 2 + gap + pad * 2), cx + w / 2f, bottom);
    float y = bottom - pad;
    rects[FRONT].set(cx - size / 2f, y - size, cx + size / 2f, y);
    y -= size + gap;
    rects[REAR].set(cx - size / 2f, y - size, cx + size / 2f, y);
  }

  private int pillAt (float x, float y, boolean forgiving) {
    float slop = Screen.dp(forgiving ? 14f : 6f);
    for (int i = 0; i < 2; i++) {
      RectF r = rects[i];
      if (x >= r.left - slop && x <= r.right + slop && y >= r.top - slop && y <= r.bottom + slop) return i;
    }
    if (forgiving) {
      // anywhere in the column above the button: the nearer of the two
      float cx = rects[FRONT].centerX();
      if (Math.abs(x - cx) < Screen.dp(70f) && y < rects[FRONT].bottom + Screen.dp(6f) && y > rects[REAR].top - Screen.dp(70f)) {
        float mid = (rects[FRONT].top + rects[REAR].bottom) / 2f;
        return y > mid ? FRONT : REAR;
      }
    }
    return NONE;
  }

  private void toLocal (float rawX, float rawY, float[] out) {
    getLocationOnScreen(selfLoc);
    out[0] = rawX - selfLoc[0];
    out[1] = rawY - selfLoc[1];
  }

  private final float[] tmp = new float[2];

  /** The finger that opened the pills, still down on the button. Returns true while it is being followed. */
  public boolean onAnchorTouch (MotionEvent e) {
    if (finished || !tracking) return false;
    toLocal(e.getRawX(), e.getRawY(), tmp);
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE:
        setSelected(pillAt(tmp[0], tmp[1], true));
        return true;
      case MotionEvent.ACTION_UP: {
        tracking = false;
        int at = pillAt(tmp[0], tmp[1], true);
        if (at != NONE) {
          pick(at);
        } else {
          setSelected(NONE); // stays open for a tap
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        tracking = false;
        dismiss();
        return true;
    }
    return true;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (finished || tracking) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        setSelected(pillAt(e.getX(), e.getY(), false));
        return true;
      case MotionEvent.ACTION_MOVE:
        setSelected(pillAt(e.getX(), e.getY(), false));
        return true;
      case MotionEvent.ACTION_UP: {
        int at = pillAt(e.getX(), e.getY(), false);
        if (at != NONE) pick(at);
        else dismiss();
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        dismiss();
        return true;
    }
    return true;
  }

  private void pick (int index) {
    if (finished) return;
    finished = true;
    pickedForOut = index;
    if (selected != index) {
      selected = index;
      selectFactor[index] = 1f;
    }
    Tgx101Haptics.play(Tgx101Haptics.CLICK);
    animateAppear(false);
    callback.onPicked(index == FRONT);
  }

  public void dismiss () {
    if (finished) return;
    finished = true;
    animateAppear(false);
    callback.onDismissed();
  }

  public boolean isFinished () {
    return finished;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (appear <= 0f) return;
    layoutPills();
    anchor.getLocationOnScreen(anchorLoc);
    getLocationOnScreen(selfLoc);
    float fromY = anchorLoc[1] - selfLoc[1] + anchor.getHeight() / 2f;

    int bg = Theme.getColor(ColorId.filling);
    int icon = Theme.getColor(ColorId.icon);
    int accent = Theme.getColor(ColorId.circleButtonRegular);
    int accentText = Theme.getColor(ColorId.circleButtonRegularIcon);
    int line = Theme.getColor(ColorId.separator);

    float t = appear;
    float out = finished ? 1f : 0f;
    int alpha = (int) (255 * Math.min(1f, t * 1.6f));
    // the capsule rises out of the button and opens upwards, with a light overshoot
    float overshoot = 1f + (float) Math.sin(t * Math.PI) * .05f * (1f - out);
    float sy = (.4f + .6f * t) * overshoot, sx = (.75f + .25f * t) * overshoot;
    float ty = (fromY - capsule.bottom) * (1f - t) * .5f;

    int save = c.save();
    c.translate(0, ty);
    c.scale(sx, sy, capsule.centerX(), capsule.bottom);
    float rad = capsule.width() / 2f;
    shadowPaint.setAlpha((int) (0x30 * (alpha / 255f)));
    c.drawRoundRect(capsule.left, capsule.top + Screen.dp(2f), capsule.right, capsule.bottom + Screen.dp(2f), rad, rad, shadowPaint);
    fillPaint.setColor(bg);
    fillPaint.setAlpha((int) (alpha * .94f));
    c.drawRoundRect(capsule, rad, rad, fillPaint);
    fillPaint.setColor(line);
    fillPaint.setAlpha(alpha);
    fillPaint.setStyle(Paint.Style.STROKE);
    fillPaint.setStrokeWidth(Math.max(1, Screen.dp(.5f)));
    c.drawRoundRect(capsule, rad, rad, fillPaint);
    fillPaint.setStyle(Paint.Style.FILL);

    // the accent circle: slides between the two, follows the finger
    float sum = selectFactor[FRONT] + selectFactor[REAR];
    if (sum > 0f) {
      float hy = (rects[FRONT].centerY() * selectFactor[FRONT] + rects[REAR].centerY() * selectFactor[REAR]) / sum;
      float hf = Math.min(1f, sum);
      float pop = finished && pickedForOut != NONE ? (1f - appear) * .15f : 0f;
      fillPaint.setColor(accent);
      fillPaint.setAlpha((int) (alpha * hf));
      c.drawCircle(capsule.centerX(), hy, rects[FRONT].width() / 2f * (.7f + .3f * hf + pop), fillPaint);
    }

    for (int i = 0; i < 2; i++) {
      RectF r = rects[i];
      float cx = r.centerX(), cy = r.centerY();
      Drawable d = icons[i];
      if (d == null) continue;
      int iSave = c.save();
      // coin turn of the picked icon
      float flip = (float) Math.cos(flipFactor[i] * Math.PI * 2f);
      c.scale(Math.max(.05f, Math.abs(flip)) / sx * sy, 1f, cx, cy); // icons keep their shape while the capsule opens
      d.setColorFilter(new PorterDuffColorFilter(blend(icon, accentText, selectFactor[i]), PorterDuff.Mode.SRC_IN));
      d.setAlpha(alpha);
      int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
      d.setBounds((int) (cx - iw / 2f), (int) (cy - ih / 2f), (int) (cx + iw / 2f), (int) (cy + ih / 2f));
      d.draw(c);
      c.restoreToCount(iSave);
    }
    c.restoreToCount(save);

    // the label of the one under the finger, outside the capsule on the left
    for (int i = 0; i < 2; i++) {
      float lf = selectFactor[i];
      if (lf <= 0f || finished) continue;
      RectF r = rects[i];
      float cy = r.centerY();
      textPaint.setColor(accentText);
      textPaint.setAlpha((int) (alpha * lf));
      float tw = textPaint.measureText(titles[i]);
      float th = Screen.dp(28f), pad = Screen.dp(10f);
      float right = capsule.left - Screen.dp(8f) + Screen.dp(10f) * (1f - lf);
      tagRect.set(right - tw - pad * 2, cy - th / 2f, right, cy + th / 2f);
      tagPaint.setColor(accent);
      tagPaint.setAlpha((int) (alpha * lf));
      c.drawRoundRect(tagRect, th / 2f, th / 2f, tagPaint);
      Paint.FontMetrics fm = textPaint.getFontMetrics();
      c.drawText(titles[i], tagRect.left + pad, cy - (fm.ascent + fm.descent) / 2f, textPaint);
    }
  }

  private static int blend (int a, int b, float f) {
    if (f <= 0f) return a;
    if (f >= 1f) return b;
    int aa = (a >>> 24), ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
    int ba = (b >>> 24), br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
    return ((int) (aa + (ba - aa) * f) << 24) | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
  }
}
