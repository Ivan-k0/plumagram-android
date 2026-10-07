package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.Tgx101NavCapsule;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-10-08, mockup «Меню-в-поле-ввода» variant Б): while a chat slides over the chat list (or back), the
 * bottom menu and the message field are one capsule that doesn't move — the menu icons leave to the left with the list,
 * the field's contents come in from the right with the chat. Drawn over everything for the time of the transition; the
 * real capsule and field are hidden meanwhile.
 */
public class Tgx101CapsuleMorph extends View {
  private static final float CORNER_DP = 18f;

  private @Nullable Tgx101NavCapsule menu;
  private @Nullable View field;
  private @Nullable MessagesController chat;
  private @Nullable Bitmap menuShot, fieldShot;
  private final RectF menuRect = new RectF(), menuLocal = new RectF(), fieldRect = new RectF(), rect = new RectF();
  private final Path clip = new Path();
  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final int[] loc = new int[2], own = new int[2];
  private float shown; // 0 — the list with the menu, 1 — the chat with the field
  private boolean active;

  public Tgx101CapsuleMorph (Context context) {
    super(context);
    setWillNotDraw(false);
  }

  /** Called on every step of a horizontal transition. {@code chatShown}: 0 — the list, 1 — the chat */
  public void update (@Nullable ViewController<?> left, @Nullable ViewController<?> right, float chatShown) {
    if (active && (chatShown <= 0f || chatShown >= 1f)) {
      finish();
      return;
    }
    if (!active) {
      if (!(left instanceof MainController) || !(right instanceof MessagesController) || chatShown <= 0f || chatShown >= 1f) return;
      View m = left.tgx101BottomCapsule();
      if (!(m instanceof Tgx101NavCapsule)) return;
      menu = (Tgx101NavCapsule) m;
      if (!menu.tgx101CapsuleRect(menuRect)) { menu = null; return; }
      menuLocal.set(menuRect);
      chat = (MessagesController) right;
      active = true;
      takeMenuShot(left.getValue().getTranslationX());
    }
    if (field == null || fieldShot == null) {
      takeFieldShot();
    }
    shown = chatShown;
    removeCallbacks(this::finish);
    removeCallbacks(safety);
    postDelayed(safety, 600); // a transition that stopped without reaching its end
    if (menu != null) menu.setAlpha(0f);
    if (field != null) field.setAlpha(0f);
    setVisibility(VISIBLE);
    invalidate();
  }

  /** The transition is over (either way): the real capsule / field are back */
  public void finish () {
    if (!active) return;
    active = false;
    if (menu != null) menu.setAlpha(1f);
    if (field != null) field.setAlpha(1f);
    menu = null;
    field = null;
    chat = null;
    if (menuShot != null) { menuShot.recycle(); menuShot = null; }
    if (fieldShot != null) { fieldShot.recycle(); fieldShot = null; }
    setVisibility(GONE);
  }

  private void takeMenuShot (float listShift) {
    if (menu == null || menu.getWidth() == 0 || menu.getHeight() == 0) return;
    try {
      Bitmap b = Bitmap.createBitmap(menu.getWidth(), menu.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b);
      menu.tgx101IconsOnly = true;
      menu.draw(c);
      menu.tgx101IconsOnly = false;
      menuShot = b;
      menu.getLocationInWindow(loc);
      getLocationInWindow(own);
      menuRect.offset(loc[0] - own[0] - listShift, loc[1] - own[1]); // where it stands without the list's parallax
    } catch (Throwable ignored) {
      menu.tgx101IconsOnly = false;
    }
  }

  private void takeFieldShot () {
    if (chat == null) return;
    View f = chat.tgx101BottomCapsule();
    if (f == null || !f.isLaidOut() || f.getWidth() == 0 || f.getHeight() - f.getPaddingBottom() <= 0) return;
    try {
      int h = f.getHeight() - f.getPaddingBottom();
      Bitmap b = Bitmap.createBitmap(f.getWidth(), h, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b);
      chat.tgx101NoFieldShape = true;
      float alpha = f.getAlpha();
      f.setAlpha(1f);
      f.draw(c);
      f.setAlpha(alpha);
      chat.tgx101NoFieldShape = false;
      fieldShot = b;
      field = f;
      // where the field stands once the chat is in: its own place, without the slide
      f.getLocationInWindow(loc);
      getLocationInWindow(own);
      float x = loc[0] - own[0] - ((View) chat.getValue()).getTranslationX();
      fieldRect.set(x, loc[1] - own[1], x + f.getWidth(), loc[1] - own[1] + h);
      f.setAlpha(0f);
    } catch (Throwable ignored) {
      chat.tgx101NoFieldShape = false;
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    if (!active || menuShot == null) return;
    boolean haveField = fieldShot != null;
    float p = shown;
    if (haveField) {
      rect.set(lerp(menuRect.left, fieldRect.left, p), lerp(menuRect.top, fieldRect.top, p), lerp(menuRect.right, fieldRect.right, p), lerp(menuRect.bottom, fieldRect.bottom, p));
    } else {
      rect.set(menuRect);
    }
    float r = Math.min(rect.height() / 2f, Screen.dp(CORNER_DP));
    c.drawRoundRect(rect, r, r, Paints.fillingPaint(ColorUtils.alphaColor(.92f, Theme.fillingColor())));
    clip.reset();
    clip.addRoundRect(rect, r, r, Path.Direction.CW);
    int save = c.save();
    c.clipPath(clip);
    float w = rect.width();
    // the menu icons leave to the left (the snapshot is the whole capsule view: its capsule lands on the drawn one)
    c.drawBitmap(menuShot, rect.left - p * w - menuLocal.left, rect.centerY() - menuLocal.centerY(), bitmapPaint);
    // the field's contents come in from the right
    if (haveField) {
      c.drawBitmap(fieldShot, rect.left + (1f - p) * w, rect.centerY() - fieldShot.getHeight() / 2f, bitmapPaint);
    }
    c.restoreToCount(save);
    float half = Math.max(1, Screen.dp(.5f)) / 2f;
    rect.inset(half, half);
    c.drawRoundRect(rect, r, r, Paints.strokeSeparatorPaint(Theme.separatorColor()));
  }

  private final Runnable safety = this::finish;

  private static float lerp (float a, float b, float f) {
    return a + (b - a) * f;
  }
}
