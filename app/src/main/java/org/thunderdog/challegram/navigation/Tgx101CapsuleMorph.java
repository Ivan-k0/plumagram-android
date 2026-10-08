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
      finishAt(chatShown);
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
      org.thunderdog.challegram.Tgx101Diag.mark("menu morph: start at " + Math.round(chatShown * 100) + " %, menu " + Math.round(menuRect.width()) + "x" + Math.round(menuRect.height()));
      takeMenuShot(0f); // the menu lives over all screens, not inside the list
    }
    if (field == null || fieldShot == null) {
      takeFieldShot();
    }
    shown = chatShown;
    removeCallbacks(this::finish);
    removeCallbacks(safety);
    postDelayed(safety, 600); // a transition that stopped without reaching its end
    // the real menu fades away / in by itself under this one; the field is hidden until the end
    if (field != null) field.setAlpha(0f);
    setVisibility(VISIBLE);
    invalidate();
  }

  /** The transition is over (either way): the real capsule / field are back */
  public void finish () {
    finishAt(shown);
  }

  private void finishAt (float chatShown) {
    if (!active) return;
    active = false;
    org.thunderdog.challegram.Tgx101Diag.mark("menu morph: end on the " + (chatShown < .5f ? "list" : "chat") + (fieldShot == null ? " (no field snapshot)" : ""));
    // back on the list: the real menu must be there at once (it would fade in only after the slide — the seam)
    if (menu != null && chatShown < .5f && menu.tgx101OnMorphBack != null) menu.tgx101OnMorphBack.run();
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
      // where it stands without the list's parallax and without its own hide animation (slides 24 dp down as it fades)
      menuRect.offset(loc[0] - own[0] - listShift - menu.getTranslationX(), loc[1] - own[1] - menu.getTranslationY());
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
    // user 2026-10-08 10:26 «поменяем на А или добавим к текущей, чтобы ещё плавнее»: Б + А — they slide and dissolve
    bitmapPaint.setAlpha(Math.round(255 * Math.max(0f, 1f - p * 1.6f)));
    c.drawBitmap(menuShot, rect.left - p * w * .5f - menuLocal.left, rect.centerY() - menuLocal.centerY(), bitmapPaint);
    // the field's contents come in from the right
    if (haveField) {
      bitmapPaint.setAlpha(Math.round(255 * Math.max(0f, (p - .3f) / .7f)));
      c.drawBitmap(fieldShot, rect.left + (1f - p) * w * .5f, rect.centerY() - fieldShot.getHeight() / 2f, bitmapPaint);
      bitmapPaint.setAlpha(255);
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
