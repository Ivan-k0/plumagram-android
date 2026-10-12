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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * TGx101 (user 2026-10-12, mockup «Истории-публикация» approved, step 1): publishing a photo story.
 *
 * The story is built in its own 1080×1920 coordinates (exactly what Telegram takes) and the screen shows it scaled:
 * the photo moves / zooms / turns with fingers over a background made of its own colours (blur or gradient), text
 * layers in 6 fonts, 4 styles, any colour (eyedropper too) are dragged with snapping to the centre and thrown into the
 * bin to delete; undo / redo for everything. «How they'll see it» puts the real viewer's chrome on top and warns about
 * text under it. Published as a 1080×1920 JPEG at the best quality that fits Telegram's 10 MB.
 */
public final class Tgx101StoryEditor extends Dialog {
  private static final int W = 1080, H = 1920;
  private static final float TOP_ZONE = .11f, BOTTOM_ZONE = .10f; // what the viewer's header and reply field cover
  private static final int REQUEST_PICK = 7101;
  private static final int BG_BLUR = 0, BG_GRADIENT = 1, BG_BLACK = 2;
  // TGx101 (user 2026-10-12 «шрифты как в официальном клиенте или exteraGram»): the official set — Roboto, Italic, Serif,
  // Typewriter, a heavy display face, Courier — with free look-alikes where theirs aren't free (MW Bold → Montserrat
  // ExtraBold, Courier New → Cousine, serif → PT Serif), all with Cyrillic; plus our handwritten Caveat. Licences next to them
  private static final String[] FONT_ASSETS = {"fonts/Roboto-Medium.ttf", "fonts/Roboto-Italic.ttf", "fonts/StoryPTSerif-Bold.ttf", "fonts/RobotoMono-Regular.ttf", "fonts/StoryMontserrat.ttf", "fonts/StoryCousine-Bold.ttf", "fonts/Caveat.ttf"};
  private static final String[] FONT_WEIGHTS = {null, null, null, null, "'wght' 800", null, "'wght' 650"};
  private static final String[] FONT_NAMES = {"Roboto", "Italic", "Serif", "Typewriter", "Bold", "Courier", null /* «handwritten», translated */};
  private static final int[] FALLBACK_STYLES = {Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD, Typeface.NORMAL, Typeface.BOLD, Typeface.BOLD, Typeface.BOLD};
  private static final String[] FALLBACK_FAMILIES = {"sans-serif-medium", "sans-serif", "serif", "monospace", "sans-serif-black", "monospace", "serif"};
  private static final Typeface[] TYPEFACES = new Typeface[FONT_ASSETS.length];

  private static Typeface typefaceOf (int font) {
    if (font < 0 || font >= FONT_ASSETS.length) font = 0;
    if (TYPEFACES[font] != null) return TYPEFACES[font];
    Typeface t = null;
    try {
      android.content.res.AssetManager assets = UI.getContext().getAssets();
      if (FONT_WEIGHTS[font] != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        t = new Typeface.Builder(assets, FONT_ASSETS[font]).setFontVariationSettings(FONT_WEIGHTS[font]).build();
      } else {
        t = Typeface.createFromAsset(assets, FONT_ASSETS[font]);
      }
    } catch (Throwable ignored) { }
    if (t == null) t = Typeface.create(FALLBACK_FAMILIES[font], FALLBACK_STYLES[font]);
    return TYPEFACES[font] = t;
  }

  private static final int STYLE_PLAIN = 0, STYLE_PLATE = 1, STYLE_OUTLINE = 2, STYLE_NEON = 3;
  private static final int[] PALETTE = {0xffffffff, 0xff111111, 0xffff5c5c, 0xffffb84d, 0xffffe066, 0xff5cd685, 0xff4dabf7, 0xff9775fa, 0xffff8fc7};

  // ── entry ──────────────────────────────────────────────────────────────────────────────

  /** Picks a photo from the gallery and opens the editor with it */
  public static void pickAndOpen (BaseActivity activity, Tdlib tdlib) {
    Intent intent;
    if (Build.VERSION.SDK_INT >= 33) {
      intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
      intent.setType("image/*");
    } else {
      intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("image/*");
      intent.addCategory(Intent.CATEGORY_OPENABLE);
    }
    activity.putActivityResultHandler(REQUEST_PICK, (requestCode, resultCode, data) -> {
      if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
      Uri uri = data.getData();
      Tgx101Diag.mark("story editor: photo picked");
      new Thread(() -> {
        Bitmap bitmap = decode(activity, uri);
        UI.post(() -> {
          if (bitmap == null) {
            UI.showToast(R.string.Tgx101StoryNoPhoto, Toast.LENGTH_SHORT);
            return;
          }
          new Tgx101StoryEditor(activity, tdlib, bitmap).show();
        });
      }, "story-decode").start();
    });
    try {
      activity.startActivityForResult(intent, REQUEST_PICK);
    } catch (Throwable t) {
      activity.putActivityResultHandler(REQUEST_PICK, null);
      UI.showToast(R.string.Tgx101StoryNoPhoto, Toast.LENGTH_SHORT);
    }
  }

  /** At most 2560 px on the long side — enough for a 1080×1920 story zoomed in, with the EXIF turn applied */
  private static @Nullable Bitmap decode (Context context, Uri uri) {
    try {
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        BitmapFactory.decodeStream(in, null, o);
      }
      int sample = 1;
      while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 2560) sample *= 2;
      o.inJustDecodeBounds = false;
      o.inSampleSize = sample;
      Bitmap b;
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        b = BitmapFactory.decodeStream(in, null, o);
      }
      if (b == null) return null;
      int degrees = 0;
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(in);
        degrees = exif.getRotationDegrees();
      } catch (Throwable ignored) { }
      if (degrees != 0) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        b = r;
      }
      return b;
    } catch (Throwable t) {
      Tgx101Diag.mark("story editor: decode failed " + t.getClass().getSimpleName());
      return null;
    }
  }

  // ── model ──────────────────────────────────────────────────────────────────────────────

  private static final class TextLayer {
    String text = "";
    int font, color = 0xffffffff, style = STYLE_PLATE, align = 1; // 0 left, 1 centre, 2 right
    float size = 92f, cx = W / 2f, cy = H * .42f, scale = 1f, rotation;
    // TGx101: a reaction sticker (suggested reaction area) — not baked into the photo, Telegram apps draw it themselves
    @Nullable String reaction, reactionType;
    boolean dark, flipped;

    TextLayer copy () {
      TextLayer t = new TextLayer();
      t.text = text; t.font = font; t.color = color; t.style = style; t.align = align;
      t.size = size; t.cx = cx; t.cy = cy; t.scale = scale; t.rotation = rotation;
      t.reaction = reaction; t.reactionType = reactionType; t.dark = dark; t.flipped = flipped;
      return t;
    }
  }

  private static final class State {
    final float[] photo = new float[9];
    int background;
    final ArrayList<TextLayer> texts = new ArrayList<>();
  }

  private final Tdlib tdlib;
  private final BaseActivity activity;
  private final Bitmap photo;
  private final Matrix photoMatrix = new Matrix();
  private int background = BG_BLUR;
  private final ArrayList<TextLayer> texts = new ArrayList<>();
  private final ArrayList<State> undo = new ArrayList<>(), redo = new ArrayList<>();
  private Bitmap blurred;
  private int[] gradient = {0xff2b2d42, 0xff0e1116};

  // ── views ──────────────────────────────────────────────────────────────────────────────

  private final FrameLayout root;
  private final CanvasView canvasView;
  private final TextView undoButton, redoButton;
  private final EditText captionView;
  private boolean previewMode;
  private @Nullable View textPanel, publishSheet, confirmCard, textSheet;
  private View topBar, railBar, bottomBar;
  private int imeHeight;

  private Tgx101StoryEditor (BaseActivity activity, Tdlib tdlib, Bitmap photo) {
    super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    this.activity = activity;
    this.tdlib = tdlib;
    this.photo = photo;
    prepareBackgrounds();
    fitPhoto();

    Context context = activity;
    root = new FrameLayout(context);
    root.setBackgroundColor(0xff000000);

    canvasView = new CanvasView(context);
    root.addView(canvasView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // top: close, undo, redo, quality
    LinearLayout top = new LinearLayout(context);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), 0);
    top.addView(glass(context, "✕", v -> confirmClose()), squareParams(36f, 0));
    undoButton = glass(context, "↶", v -> undo());
    top.addView(undoButton, squareParams(36f, 8f));
    redoButton = glass(context, "↷", v -> redo());
    top.addView(redoButton, squareParams(36f, 8f));
    View space = new View(context);
    top.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
    TextView badge = new TextView(context);
    badge.setText("1080×1920 · " + Lang.getString(R.string.Tgx101StoryMaxQuality));
    badge.setTextColor(0xffd7e3ef);
    badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
    badge.setPadding(Screen.dp(9f), Screen.dp(5f), Screen.dp(9f), Screen.dp(5f));
    badge.setBackground(glassBackground(12f));
    top.addView(badge);
    root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
    topBar = top;

    // right rail: text, fit, background, preview
    LinearLayout rail = new LinearLayout(context);
    rail.setOrientation(LinearLayout.VERTICAL);
    rail.addView(glass(context, "Аа", v -> editText(null)), squareParams(44f, 0));
    rail.addView(glass(context, "😊", v -> pickReaction()), squareParams(44f, 8f, true));
    rail.addView(glass(context, "⤢", v -> { pushUndo(); fitPhoto(); canvasView.invalidate(); }), squareParams(44f, 8f, true));
    rail.addView(glass(context, "◐", v -> { pushUndo(); background = (background + 1) % 3; canvasView.invalidate(); toastBackground(); }), squareParams(44f, 8f, true));
    rail.addView(glass(context, "👁", v -> setPreview(!previewMode)), squareParams(44f, 8f, true));
    FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
    railParams.topMargin = Screen.dp(70f);
    railParams.rightMargin = Screen.dp(10f);
    root.addView(rail, railParams);
    railBar = rail;

    // bottom: caption + next
    LinearLayout bottom = new LinearLayout(context);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    bottom.setPadding(Screen.dp(8f), 0, Screen.dp(8f), Screen.dp(8f));
    captionView = new EditText(context);
    captionView.setHint(Lang.getString(R.string.Tgx101StoryCaptionHint));
    captionView.setHintTextColor(0xff9fb0c2);
    captionView.setTextColor(0xffffffff);
    captionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    captionView.setMaxLines(3);
    captionView.setPadding(Screen.dp(14f), Screen.dp(10f), Screen.dp(14f), Screen.dp(10f));
    captionView.setBackground(glassBackground(18f));
    bottom.addView(captionView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView next = pill(context, Lang.getString(R.string.Tgx101StoryNext), true, v -> openPublish());
    LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(46f));
    nextParams.leftMargin = Screen.dp(8f);
    bottom.addView(next, nextParams);
    root.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    bottomBar = bottom;

    setContentView(root);
    Window window = getWindow();
    if (window != null) {
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    // TGx101 (user 2026-10-12 «убрал клавиатуру, хочу продолжить писать — редактор текста перекрыт клавиатурой»): a full-screen
    // dialog isn't resized by the keyboard — the panels rise by its height themselves (window insets, or the visible frame)
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      setImeHeight(insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom);
      return insets;
    });
    root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
      if (root.getHeight() > maxRootHeight) maxRootHeight = root.getHeight();
      applyImeLift();
      android.graphics.Rect r = new android.graphics.Rect();
      root.getWindowVisibleDisplayFrame(r);
      int hidden = root.getRootView().getHeight() - r.bottom;
      if (hidden > Screen.dp(120f) || imeHeight == 0 || hidden < imeHeight) setImeHeight(Math.max(0, hidden > Screen.dp(120f) ? hidden : 0));
    });
    updateUndoButtons();
    Tgx101Diag.mark("story editor: opened, photo " + photo.getWidth() + "x" + photo.getHeight());
  }

  private int maxRootHeight, appliedLift = -1;

  private void setImeHeight (int height) {
    if (imeHeight == height) return;
    imeHeight = height;
    applyImeLift();
  }

  /**
   * On some phones the window itself shrinks above the keyboard, on others it doesn't — lift the panels only by the part
   * of the keyboard the window didn't make room for (user's video 2026-10-12: the caption field flew to mid-screen, lifted twice)
   */
  private int imeLift () {
    int resized = Math.max(0, maxRootHeight - root.getHeight());
    return Math.max(0, imeHeight - resized);
  }

  private void applyImeLift () {
    int lift = imeLift();
    if (lift == appliedLift) return;
    appliedLift = lift;
    if (bottomBar != null) bottomBar.setTranslationY(-lift);
    if (textSheet != null) textSheet.setTranslationY(-lift);
    if (pickerScroll != null && pickerScroll.getParent() != null) {
      ViewGroup.LayoutParams lp = pickerScroll.getLayoutParams();
      lp.height = pickerListHeight();
      pickerScroll.setLayoutParams(lp);
    }
  }

  /** Our confirm card (instead of a system dialog): message, grey «cancel», the action — red when it destroys */
  private void showConfirm (String message, String cancel, String action, boolean danger, Runnable onAction) {
    if (confirmCard != null) return;
    Context context = getContext();
    FrameLayout dim = new FrameLayout(context);
    dim.setBackgroundColor(0x80000000);
    dim.setOnClickListener(v -> closeConfirm());
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    card.setBackground(cardBackground());
    card.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(16f));
    TextView text = new TextView(context);
    text.setText(message);
    text.setTextColor(0xffe9eef4);
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    card.addView(text);
    LinearLayout buttons = new LinearLayout(context);
    buttons.setPadding(0, Screen.dp(16f), 0, 0);
    buttons.addView(pill(context, cancel, false, v -> closeConfirm()), new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f));
    TextView act = pill(context, action, true, v -> { closeConfirm(); onAction.run(); });
    if (danger) {
      GradientDrawable red = new GradientDrawable();
      red.setColor(0xffe5534b);
      red.setCornerRadius(Screen.dp(14f));
      act.setBackground(red);
    }
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
    ap.leftMargin = Screen.dp(10f);
    buttons.addView(act, ap);
    card.addView(buttons);
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    cp.leftMargin = cp.rightMargin = cp.bottomMargin = Screen.dp(8f);
    dim.addView(card, cp);
    root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    confirmCard = dim;
    card.setTranslationY(Screen.dp(240f));
    card.animate().translationY(0f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    dim.setAlpha(0f);
    dim.animate().alpha(1f).setDuration(180).start();
  }

  private void closeConfirm () {
    View card = confirmCard;
    if (card == null) return;
    confirmCard = null;
    card.animate().alpha(0f).setDuration(160).withEndAction(() -> root.removeView(card)).start();
  }

  private void setChromeVisible (boolean visible) {
    int v = visible ? View.VISIBLE : View.INVISIBLE;
    if (topBar != null) topBar.setVisibility(v);
    if (railBar != null) railBar.setVisibility(v);
    if (bottomBar != null) bottomBar.setVisibility(v);
  }

  @Override
  public void onBackPressed () {
    if (confirmCard != null) { closeConfirm(); return; }
    if (publishSheet != null) { closePublish(); return; }
    if (textPanel != null) { finishTextEdit(true); return; }
    if (previewMode) { setPreview(false); return; }
    confirmClose();
  }

  private void confirmClose () {
    if (undo.isEmpty() && texts.isEmpty()) { dismiss(); return; }
    showConfirm(Lang.getString(R.string.Tgx101StoryDiscard), Lang.getString(R.string.Cancel), Lang.getString(R.string.Tgx101StoryDiscardYes), true, this::dismiss);
  }

  // ── backgrounds and photo fit ──────────────────────────────────────────────────────────

  private void prepareBackgrounds () {
    try {
      Bitmap small = Bitmap.createScaledBitmap(photo, 27, 48, true);
      Bitmap tiny = Bitmap.createScaledBitmap(small, 9, 16, true);
      blurred = Bitmap.createScaledBitmap(tiny, 27, 48, true); // a cheap strong blur: down to 9×16 and back
      if (tiny != blurred) tiny.recycle();
      if (small != blurred) small.recycle();
      androidx.palette.graphics.Palette p = androidx.palette.graphics.Palette.from(photo).maximumColorCount(12).generate();
      int a = p.getVibrantColor(p.getDominantColor(0xff2b2d42));
      int b = p.getDarkMutedColor(p.getDarkVibrantColor(0xff0e1116));
      gradient = new int[] {a, b};
    } catch (Throwable ignored) { }
  }

  /** The photo as large as it fits whole into 9:16 (cover if it's close to 9:16) */
  private void fitPhoto () {
    float pw = photo.getWidth(), ph = photo.getHeight();
    float fit = Math.min(W / pw, H / ph), cover = Math.max(W / pw, H / ph);
    float scale = cover / fit < 1.18f ? cover : fit;
    photoMatrix.reset();
    photoMatrix.postScale(scale, scale);
    photoMatrix.postTranslate((W - pw * scale) / 2f, (H - ph * scale) / 2f);
  }

  private void toastBackground () {
    int res = background == BG_BLUR ? R.string.Tgx101StoryBgBlur : background == BG_GRADIENT ? R.string.Tgx101StoryBgGradient : R.string.Tgx101StoryBgBlack;
    UI.showToast(res, Toast.LENGTH_SHORT);
  }

  // ── drawing (story coordinates) ────────────────────────────────────────────────────────

  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  private void drawStory (Canvas c) {
    switch (background) {
      case BG_BLUR:
        if (blurred != null) {
          rect.set(-40, -40, W + 40, H + 40);
          c.drawBitmap(blurred, null, rect, bitmapPaint);
          fill.setShader(null);
          fill.setColor(0x33000000);
          c.drawRect(0, 0, W, H, fill);
          break;
        }
      case BG_GRADIENT:
        fill.setShader(new LinearGradient(0, 0, 0, H, gradient[0], gradient[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
        break;
      default:
        fill.setShader(null);
        fill.setColor(0xff000000);
        c.drawRect(0, 0, W, H, fill);
    }
    c.drawBitmap(photo, photoMatrix, bitmapPaint);
    for (TextLayer t : texts) {
      if (t == editing) continue;
      if (t.reaction != null) {
        if (!exporting) drawReactionLayer(c, t);
        continue;
      }
      drawText(c, t);
    }
  }

  private StaticLayout layoutOf (TextLayer t) {
    textPaint.setTypeface(typefaceOf(t.font));
    textPaint.setTextSize(t.size);
    String text = t.text.isEmpty() ? " " : t.text;
    int width = (int) Math.ceil(Math.min(W * .86f / Math.max(.2f, t.scale), maxLineWidth(text) + 2));
    Layout.Alignment a = t.align == 0 ? Layout.Alignment.ALIGN_NORMAL : t.align == 2 ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_CENTER;
    return new StaticLayout(text, textPaint, Math.max(1, width), a, 1f, 0f, false);
  }

  private float maxLineWidth (String text) {
    float max = 0;
    for (String line : text.split("\n", -1)) max = Math.max(max, textPaint.measureText(line));
    return max;
  }

  private static boolean isLight (int color) {
    return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 > 150;
  }

  /** The layer's box in its own (unrotated, unscaled) coordinates around its centre */
  private void boxOf (TextLayer t, StaticLayout layout, RectF out) {
    float pad = t.style == STYLE_PLATE ? t.size * .32f : t.size * .12f;
    float w = layout.getWidth() + pad * 2, h = layout.getHeight() + pad * 2;
    out.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
  }

  private void drawText (Canvas c, TextLayer t) {
    StaticLayout layout = layoutOf(t);
    int save = c.save();
    c.translate(t.cx, t.cy);
    c.rotate(t.rotation);
    c.scale(t.scale, t.scale);
    if (t.style == STYLE_PLATE) {
      boxOf(t, layout, rect);
      fill.setShader(null);
      fill.setColor(t.color);
      float r = t.size * .3f;
      c.drawRoundRect(rect, r, r, fill);
    }
    c.translate(-layout.getWidth() / 2f, -layout.getHeight() / 2f);
    textPaint.setStyle(Paint.Style.FILL);
    textPaint.clearShadowLayer();
    switch (t.style) {
      case STYLE_PLATE:
        textPaint.setColor(isLight(t.color) ? 0xff111111 : 0xffffffff);
        layout.draw(c);
        break;
      case STYLE_OUTLINE:
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(Math.max(3f, t.size * .06f));
        textPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setColor(t.color);
        layout.draw(c);
        textPaint.setStyle(Paint.Style.FILL);
        break;
      case STYLE_NEON:
        textPaint.setColor(t.color);
        textPaint.setShadowLayer(t.size * .35f, 0, 0, t.color);
        layout.draw(c);
        textPaint.setColor(0xffffffff);
        textPaint.setShadowLayer(t.size * .12f, 0, 0, t.color);
        layout.draw(c);
        textPaint.clearShadowLayer();
        break;
      default:
        textPaint.setColor(t.color);
        textPaint.setShadowLayer(t.size * .08f, 0, t.size * .03f, 0x80000000);
        layout.draw(c);
        textPaint.clearShadowLayer();
    }
    c.restoreToCount(save);
  }

  // ── reaction stickers ─────────────────────────────────────────────────────────────────

  private static final float REACTION_SIZE = 230f; // story px at scale 1
  private boolean exporting;
  private static final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final TextPaint bubbleText = new TextPaint(Paint.ANTI_ALIAS_FLAG);

  /**
   * The reaction bubble as Telegram apps show it: a round bubble with a little tail, the emoji inside, the count under it.
   * Shared with the story viewer, which draws the areas of the stories it shows.
   */
  public static void drawReactionBubble (Canvas c, float cx, float cy, float size, String emoji, boolean dark, boolean flipped, int count) {
    float r = size / 2f;
    bubblePaint.setColor(dark ? 0xcc000000 : 0xffffffff);
    bubblePaint.setShadowLayer(size * .06f, 0, size * .02f, 0x40000000);
    c.drawCircle(cx, cy, r, bubblePaint);
    bubblePaint.clearShadowLayer();
    float dir = flipped ? 1f : -1f;
    c.drawCircle(cx + dir * r * .72f, cy + r * .78f, r * .17f, bubblePaint);
    c.drawCircle(cx + dir * r * .98f, cy + r * 1.06f, r * .09f, bubblePaint);
    bubbleText.setTextAlign(Paint.Align.CENTER);
    bubbleText.setColor(0xff000000);
    bubbleText.setTextSize(size * (count > 0 ? .42f : .5f));
    Paint.FontMetrics fm = bubbleText.getFontMetrics();
    float ey = count > 0 ? cy - size * .04f : cy - (fm.ascent + fm.descent) / 2f;
    c.drawText(emoji, cx, ey, bubbleText);
    if (count > 0) {
      bubbleText.setTextSize(size * .17f);
      bubbleText.setFakeBoldText(true);
      bubbleText.setColor(dark ? 0xffffffff : 0xff000000);
      c.drawText(count >= 1000 ? (count / 100) / 10f + "K" : String.valueOf(count), cx, cy + size * .32f, bubbleText);
      bubbleText.setFakeBoldText(false);
    }
  }

  private void drawReactionLayer (Canvas c, TextLayer t) {
    int save = c.save();
    c.translate(t.cx, t.cy);
    c.rotate(t.rotation);
    drawReactionBubble(c, 0, 0, REACTION_SIZE * t.scale, t.reaction, t.dark, t.flipped, 0);
    c.restoreToCount(save);
  }

  private int reactionCount () {
    int n = 0;
    for (TextLayer t : texts) if (t.reaction != null) n++;
    return n;
  }

  private void pickReaction () {
    if (reactionCount() >= 5) { UI.showToast(R.string.Tgx101StoryReactionLimit, Toast.LENGTH_SHORT); return; }
    tdlib.send(new TdApi.GetStoryAvailableReactions(8), (reactions, error) -> UI.post(() -> {
      ArrayList<String> emoji = new ArrayList<>();
      if (reactions != null) {
        for (TdApi.AvailableReaction[] group : new TdApi.AvailableReaction[][] {reactions.topReactions, reactions.recentReactions, reactions.popularReactions}) {
          if (group == null) continue;
          for (TdApi.AvailableReaction r : group) {
            if (r.type instanceof TdApi.ReactionTypeEmoji && !emoji.contains(((TdApi.ReactionTypeEmoji) r.type).emoji) && emoji.size() < 32) emoji.add(((TdApi.ReactionTypeEmoji) r.type).emoji);
          }
        }
      }
      if (emoji.isEmpty()) for (String e : new String[] {"❤", "👍", "🔥", "😂", "😮", "😢", "🎉", "👏", "🤩", "🙏"}) emoji.add(e);
      Context context = getContext();
      FrameLayout dim = new FrameLayout(context);
      dim.setBackgroundColor(0x66000000);
      dim.setOnClickListener(v -> root.removeView(dim));
      LinearLayout card = new LinearLayout(context);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setClickable(true);
      card.setBackground(cardBackground());
      card.setPadding(Screen.dp(12f), Screen.dp(14f), Screen.dp(12f), Screen.dp(12f));
      TextView title = new TextView(context);
      title.setText(Lang.getString(R.string.Tgx101StoryReactionTitle));
      title.setTextColor(0xffffffff);
      title.setTypeface(Typeface.DEFAULT_BOLD);
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      title.setPadding(Screen.dp(4f), 0, 0, Screen.dp(4f));
      card.addView(title);
      TextView hint = new TextView(context);
      hint.setText(Lang.getString(R.string.Tgx101StoryReactionHint));
      hint.setTextColor(0xff8a9bb0);
      hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
      hint.setPadding(Screen.dp(4f), 0, 0, Screen.dp(8f));
      card.addView(hint);
      android.widget.GridLayout grid = new android.widget.GridLayout(context);
      grid.setColumnCount(8);
      for (String e : emoji) {
        TextView v = new TextView(context);
        v.setText("❤".equals(e) ? "❤️" : e);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26f);
        v.setGravity(Gravity.CENTER);
        v.setOnClickListener(x -> {
          root.removeView(dim);
          pushUndo();
          TextLayer layer = new TextLayer();
          layer.reaction = "❤".equals(e) ? "❤️" : e;
          layer.reactionType = e;
          layer.cx = W * (.3f + .1f * reactionCount());
          layer.cy = H * .62f;
          texts.add(layer);
          canvasView.invalidate();
        });
        android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
        lp.width = Screen.dp(42f);
        lp.height = Screen.dp(46f);
        grid.addView(v, lp);
      }
      card.addView(grid);
      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
      cp.leftMargin = cp.rightMargin = cp.bottomMargin = Screen.dp(8f);
      dim.addView(card, cp);
      root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }));
  }

  private @Nullable TdApi.InputStoryAreas reactionAreas () {
    ArrayList<TdApi.InputStoryArea> areas = new ArrayList<>();
    for (TextLayer t : texts) {
      if (t.reaction == null) continue;
      double size = REACTION_SIZE * t.scale;
      TdApi.StoryAreaPosition p = new TdApi.StoryAreaPosition(t.cx * 100.0 / W, t.cy * 100.0 / H, size * 100.0 / W, size * 100.0 / H, t.rotation, 50);
      areas.add(new TdApi.InputStoryArea(p, new TdApi.InputStoryAreaTypeSuggestedReaction(new TdApi.ReactionTypeEmoji(t.reactionType), t.dark, t.flipped)));
    }
    return areas.isEmpty() ? null : new TdApi.InputStoryAreas(areas.toArray(new TdApi.InputStoryArea[0]));
  }

  /** Corners of the layer on the story in story coordinates — for hit tests and the zone warning */
  private RectF storyBoundsOf (TextLayer t) {
    if (t.reaction != null) {
      float half = REACTION_SIZE * t.scale / 2f;
      return new RectF(t.cx - half, t.cy - half, t.cx + half, t.cy + half * 1.25f);
    }
    StaticLayout layout = layoutOf(t);
    RectF box = new RectF();
    boxOf(t, layout, box);
    Matrix m = new Matrix();
    m.postScale(t.scale, t.scale);
    m.postRotate(t.rotation);
    m.postTranslate(t.cx, t.cy);
    m.mapRect(box);
    return box;
  }

  private @Nullable TextLayer layerAt (float x, float y) {
    for (int i = texts.size() - 1; i >= 0; i--) {
      RectF b = storyBoundsOf(texts.get(i));
      b.inset(-24, -24);
      if (b.contains(x, y)) return texts.get(i);
    }
    return null;
  }

  // ── undo / redo ────────────────────────────────────────────────────────────────────────

  private State snapshot () {
    State s = new State();
    photoMatrix.getValues(s.photo);
    s.background = background;
    for (TextLayer t : texts) s.texts.add(t.copy());
    return s;
  }

  private void restore (State s) {
    photoMatrix.setValues(s.photo);
    background = s.background;
    texts.clear();
    for (TextLayer t : s.texts) texts.add(t.copy());
    canvasView.invalidate();
  }

  private void pushUndo () {
    undo.add(snapshot());
    if (undo.size() > 60) undo.remove(0);
    redo.clear();
    updateUndoButtons();
  }

  private void undo () {
    if (undo.isEmpty()) return;
    redo.add(snapshot());
    restore(undo.remove(undo.size() - 1));
    updateUndoButtons();
  }

  private void redo () {
    if (redo.isEmpty()) return;
    undo.add(snapshot());
    restore(redo.remove(redo.size() - 1));
    updateUndoButtons();
  }

  private void updateUndoButtons () {
    undoButton.setAlpha(undo.isEmpty() ? .4f : 1f);
    redoButton.setAlpha(redo.isEmpty() ? .4f : 1f);
  }

  // ── the canvas view: shows the story scaled, handles gestures ──────────────────────────

  private final class CanvasView extends View {
    private final RectF frame = new RectF();
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private float scale = 1f;
    private boolean snapX, snapY, overBin, draggingText;

    CanvasView (Context context) {
      super(context);
      dash.setStyle(Paint.Style.STROKE);
      dash.setColor(0x8cffffff);
      dash.setStrokeWidth(Screen.dp(1.5f));
      dash.setPathEffect(new DashPathEffect(new float[] {Screen.dp(5f), Screen.dp(4f)}, 0));
      guide.setColor(0xff5fb3ff);
      guide.setStrokeWidth(Screen.dp(1.5f));
      label.setTextSize(Screen.dp(10f));
      label.setColor(0xffffffff);
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      super.onLayout(changed, l, t, r, b);
      float availW = getWidth() - Screen.dp(16f), availH = getHeight() - Screen.dp(16f);
      scale = Math.min(availW / W, availH / H);
      float w = W * scale, h = H * scale;
      frame.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
      clip.reset();
      clip.addRoundRect(frame, Screen.dp(16f), Screen.dp(16f), Path.Direction.CW);
    }

    float toStoryX (float x) { return (x - frame.left) / scale; }
    float toStoryY (float y) { return (y - frame.top) / scale; }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      int save = c.save();
      c.clipPath(clip);
      c.translate(frame.left, frame.top);
      c.scale(scale, scale);
      drawStory(c);
      c.restoreToCount(save);
      float top = frame.top + frame.height() * TOP_ZONE, bottom = frame.bottom - frame.height() * BOTTOM_ZONE;
      if (previewMode) {
        drawViewerChrome(c, top, bottom);
      } else {
        c.drawLine(frame.left, top, frame.right, top, dash);
        c.drawLine(frame.left, bottom, frame.right, bottom, dash);
        label.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Lang.getString(R.string.Tgx101StoryZoneHeader), frame.right - Screen.dp(8f), top - Screen.dp(5f), label);
        c.drawText(Lang.getString(R.string.Tgx101StoryZoneReply), frame.right - Screen.dp(8f), bottom - Screen.dp(5f), label);
      }
      if (snapX) c.drawLine(frame.centerX(), frame.top, frame.centerX(), frame.bottom, guide);
      if (snapY) c.drawLine(frame.left, frame.centerY(), frame.right, frame.centerY(), guide);
      if (draggingText) {
        float cx = frame.centerX(), cy = frame.bottom - Screen.dp(60f), r = Screen.dp(overBin ? 30f : 24f);
        fill.setShader(null);
        fill.setColor(overBin ? 0xffff5c5c : 0x66000000);
        c.drawCircle(cx, cy, r, fill);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Screen.dp(18f));
        c.drawText("🗑", cx, cy + Screen.dp(6f), label);
        label.setTextSize(Screen.dp(10f));
      }
    }

    private void drawViewerChrome (Canvas c, float top, float bottom) {
      Paint p = fill;
      p.setShader(null);
      // red hatched zones
      p.setColor(0x47ff5050);
      c.drawRect(frame.left, frame.top, frame.right, top, p);
      c.drawRect(frame.left, bottom, frame.right, frame.bottom, p);
      // progress bars, avatar, name, reply field
      float x = frame.left + Screen.dp(8f), y = frame.top + Screen.dp(8f), w = frame.width() - Screen.dp(16f);
      p.setColor(0xffffffff);
      c.drawRoundRect(x, y, x + w, y + Screen.dp(2.5f), Screen.dp(1.25f), Screen.dp(1.25f), p);
      p.setColor(0xff3d8fd8);
      c.drawCircle(x + Screen.dp(15f), y + Screen.dp(26f), Screen.dp(15f), p);
      label.setTextAlign(Paint.Align.LEFT);
      label.setTextSize(Screen.dp(13f));
      label.setFakeBoldText(true);
      c.drawText(Lang.getString(R.string.Tgx101StoryYou), x + Screen.dp(38f), y + Screen.dp(23f), label);
      label.setFakeBoldText(false);
      label.setTextSize(Screen.dp(11f));
      c.drawText(Lang.getString(R.string.Tgx101StoryJustNow), x + Screen.dp(38f), y + Screen.dp(37f), label);
      Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeWidth(Screen.dp(1f));
      stroke.setColor(0x99ffffff);
      float ry = frame.bottom - Screen.dp(52f);
      c.drawRoundRect(frame.left + Screen.dp(8f), ry, frame.right - Screen.dp(8f), ry + Screen.dp(44f), Screen.dp(18f), Screen.dp(18f), stroke);
      label.setTextSize(Screen.dp(12.5f));
      c.drawText(Lang.getString(R.string.Tgx101StoryReply), frame.left + Screen.dp(22f), ry + Screen.dp(27f), label);
      label.setTextSize(Screen.dp(10f));
    }

    // gestures
    private @Nullable TextLayer target;
    private float lastX, lastY, lastDist, lastAngle, downX, downY;
    private long downTime;
    private int pointers;
    private boolean moved, eyedropping;

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (previewMode) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) setPreview(false);
        return true;
      }
      if (eyedropper != null) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) pickColorAt(toStoryX(e.getX()), toStoryY(e.getY()));
        return true;
      }
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          target = layerAt(toStoryX(e.getX()), toStoryY(e.getY()));
          lastX = downX = e.getX();
          lastY = downY = e.getY();
          downTime = SystemClock.uptimeMillis();
          moved = false;
          pointers = 1;
          pushUndo();
          if (target != null) { texts.remove(target); texts.add(target); } // to the top
          return true;
        case MotionEvent.ACTION_POINTER_DOWN:
          pointers = e.getPointerCount();
          if (pointers == 2) {
            lastDist = dist(e);
            lastAngle = angle(e);
            if (target == null) {
              TextLayer second = layerAt(toStoryX(e.getX(1)), toStoryY(e.getY(1)));
              if (second != null) target = second;
            }
          }
          return true;
        case MotionEvent.ACTION_MOVE:
          if (e.getPointerCount() >= 2) {
            float d = dist(e), a = angle(e);
            float ds = lastDist > 0 ? d / lastDist : 1f, da = a - lastAngle;
            float mx = toStoryX((e.getX(0) + e.getX(1)) / 2f), my = toStoryY((e.getY(0) + e.getY(1)) / 2f);
            if (target != null) {
              target.scale = Math.max(.25f, Math.min(6f, target.scale * ds));
              target.rotation += da;
              if (Math.abs(target.rotation % 90) < 3) target.rotation -= target.rotation % 90; // snap to straight
            } else {
              photoMatrix.postScale(ds, ds, mx, my);
              photoMatrix.postRotate(da, mx, my);
            }
            lastDist = d;
            lastAngle = a;
            moved = true;
            invalidate();
            return true;
          }
          if (pointers > 1) return true; // a finger was lifted after a pinch — wait for all up
          float dx = (e.getX() - lastX) / scale, dy = (e.getY() - lastY) / scale;
          if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) < Screen.getTouchSlop()) return true;
          moved = true;
          lastX = e.getX();
          lastY = e.getY();
          if (target != null) {
            draggingText = true;
            target.cx += dx;
            target.cy += dy;
            snapX = Math.abs(target.cx - W / 2f) < 14f;
            snapY = Math.abs(target.cy - H / 2f) < 14f;
            if (snapX) target.cx = W / 2f;
            if (snapY) target.cy = H / 2f;
            overBin = Math.hypot(e.getX() - frame.centerX(), e.getY() - (frame.bottom - Screen.dp(60f))) < Screen.dp(40f);
          } else {
            photoMatrix.postTranslate(dx, dy);
          }
          invalidate();
          return true;
        case MotionEvent.ACTION_POINTER_UP:
          pointers = e.getPointerCount() - 1;
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          boolean tap = !moved && SystemClock.uptimeMillis() - downTime < 350 && e.getActionMasked() == MotionEvent.ACTION_UP;
          if (target != null && overBin) {
            texts.remove(target);
            if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101Haptics()) performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
          }
          if (!moved && !undo.isEmpty()) {
            undo.remove(undo.size() - 1); // nothing changed — no undo step
          }
          if (tap && target != null) {
            if (target.reaction != null) {
              pushUndo();
              // a tap on a reaction: light → dark → light flipped → dark flipped
              if (!target.dark) target.dark = true; else { target.dark = false; target.flipped = !target.flipped; }
            } else {
              editText(target);
            }
          }
          draggingText = overBin = snapX = snapY = false;
          target = null;
          updateUndoButtons();
          invalidate();
          return true;
        }
      }
      return true;
    }

    private float dist (MotionEvent e) {
      return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    private float angle (MotionEvent e) {
      return (float) Math.toDegrees(Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
    }
  }

  // ── text editing ───────────────────────────────────────────────────────────────────────

  private @Nullable TextLayer editing, editingOriginal;
  private @Nullable EditText editField;
  private @Nullable Runnable eyedropper;
  private static int lastFont, lastColor = 0xffffffff, lastStyle = STYLE_PLATE, lastAlign = 1;
  private static float lastSize = 92f;

  private void editText (@Nullable TextLayer layer) {
    if (textPanel != null) return;
    pushUndo();
    if (layer == null) {
      layer = new TextLayer();
      layer.font = lastFont; layer.color = lastColor; layer.style = lastStyle; layer.align = lastAlign; layer.size = lastSize;
      layer.cy = H * .42f;
      texts.add(layer);
      editingOriginal = null;
    } else {
      editingOriginal = layer.copy();
    }
    editing = layer;
    canvasView.invalidate();
    setChromeVisible(false); // the editor's own buttons (the blue «Next») showed through under the text panel
    Context context = getContext();

    FrameLayout panel = new FrameLayout(context);
    panel.setBackgroundColor(0x99000000);
    panel.setClickable(true);

    EditText field = new EditText(context);
    editField = field;
    field.setText(layer.text);
    field.setSelection(field.getText().length());
    field.setGravity(Gravity.CENTER);
    field.setBackground(null);
    field.setMinWidth(Screen.dp(60f));
    field.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));
    applyFieldStyle();
    final TextLayer edited = layer;
    field.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int a, int b, int c) { }
      @Override public void onTextChanged (CharSequence s, int a, int b, int c) { }
      @Override public void afterTextChanged (Editable s) { edited.text = s.toString(); }
    });
    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    fp.topMargin = Screen.dp(150f);
    fp.leftMargin = fp.rightMargin = Screen.dp(48f);
    panel.addView(field, fp);

    // top: cancel, done
    LinearLayout top = new LinearLayout(context);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), 0);
    top.addView(glass(context, "✕", v -> finishTextEdit(false)), squareParams(36f, 0));
    View sp = new View(context);
    top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
    top.addView(pill(context, Lang.getString(R.string.Tgx101StoryDone), true, v -> finishTextEdit(true)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(36f)));
    panel.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

    // left: size slider
    SizeSlider slider = new SizeSlider(context, (layer.size - 40f) / 180f, f -> {
      edited.size = 40f + 180f * f;
      applyFieldStyle();
    });
    FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Screen.dp(30f), Screen.dp(220f), Gravity.LEFT | Gravity.TOP);
    sl.leftMargin = Screen.dp(10f);
    sl.topMargin = Screen.dp(120f);
    panel.addView(slider, sl);

    // bottom: fonts, colours, styles
    LinearLayout sheet = new LinearLayout(context);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(0, Screen.dp(10f), 0, Screen.dp(10f));
    sheet.setBackground(cardBackground());
    HorizontalScrollView fontScroll = new HorizontalScrollView(context);
    fontScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout fonts = new LinearLayout(context);
    fonts.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    String[] names = new String[FONT_ASSETS.length];
    for (int i = 0; i < names.length; i++) names[i] = FONT_NAMES[i] != null ? FONT_NAMES[i] : Lang.getString(R.string.Tgx101StoryFontHand);
    for (int i = 0; i < names.length; i++) {
      final int font = i;
      TextView chip = chip(context, names[i], i == layer.font, v -> { edited.font = font; lastFont = font; applyFieldStyle(); refreshChips((ViewGroup) v.getParent(), font); });
      chip.setTypeface(typefaceOf(i));
      chip.setTag(i);
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(34f));
      cp.rightMargin = Screen.dp(6f);
      fonts.addView(chip, cp);
    }
    fontScroll.addView(fonts);
    sheet.addView(fontScroll);

    // the eyedropper first and always whole; the colours scroll beside it (it was cut off on narrow screens)
    LinearLayout colorRow = new LinearLayout(context);
    colorRow.setGravity(Gravity.CENTER_VERTICAL);
    colorRow.setPadding(Screen.dp(12f), Screen.dp(10f), 0, Screen.dp(4f));
    HorizontalScrollView colorScroll = new HorizontalScrollView(context);
    colorScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout colors = new LinearLayout(context);
    colors.setGravity(Gravity.CENTER_VERTICAL);
    colors.setPadding(Screen.dp(4f), Screen.dp(4f), Screen.dp(12f), Screen.dp(4f));
    for (int color : PALETTE) {
      View dot = colorDot(context, color, color == layer.color);
      dot.setOnClickListener(v -> { edited.color = color; lastColor = color; applyFieldStyle(); refreshDots(colors, color); });
      colors.addView(dot, dotParams());
    }
    colorScroll.addView(colors);
    TextView pipette = new TextView(context);
    pipette.setText("⊙");
    pipette.setGravity(Gravity.CENTER);
    pipette.setTextColor(0xffffffff);
    pipette.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    GradientDrawable pd = new GradientDrawable();
    pd.setShape(GradientDrawable.OVAL);
    pd.setStroke(Screen.dp(2f), 0x66ffffff, Screen.dp(3f), Screen.dp(2f));
    pipette.setBackground(pd);
    pipette.setOnClickListener(v -> startEyedropper(colors));
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Screen.dp(30f), Screen.dp(30f));
    pp.rightMargin = Screen.dp(6f);
    colorRow.addView(pipette, pp);
    colorRow.addView(colorScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    sheet.addView(colorRow);

    LinearLayout styles = new LinearLayout(context);
    styles.setPadding(Screen.dp(12f), Screen.dp(6f), Screen.dp(12f), 0);
    String[] styleNames = {"A", "A", "A", "A"};
    for (int i = 0; i < 4; i++) {
      final int style = i;
      TextView s = chip(context, styleNames[i], i == layer.style, v -> { edited.style = style; lastStyle = style; applyFieldStyle(); refreshChips((ViewGroup) v.getParent(), style); });
      s.setTag(i);
      s.setTypeface(Typeface.DEFAULT_BOLD);
      s.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      if (i == STYLE_PLATE) s.setTextColor(0xff111111);
      if (i == STYLE_OUTLINE) s.getPaint().setStyle(Paint.Style.STROKE);
      if (i == STYLE_NEON) s.setShadowLayer(Screen.dp(5f), 0, 0, 0xff4dabf7);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Screen.dp(38f), 1f);
      lp.rightMargin = Screen.dp(6f);
      styles.addView(s, lp);
    }
    TextView align = chip(context, alignLabel(layer.align), false, null);
    align.setOnClickListener(v -> { edited.align = (edited.align + 1) % 3; lastAlign = edited.align; ((TextView) v).setText(alignLabel(edited.align)); applyFieldStyle(); });
    styles.addView(align, new LinearLayout.LayoutParams(0, Screen.dp(38f), 1f));
    sheet.addView(styles);

    FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    sheetParams.leftMargin = sheetParams.rightMargin = sheetParams.bottomMargin = Screen.dp(8f);
    panel.addView(sheet, sheetParams);
    textSheet = sheet;
    sheet.setTranslationY(-imeLift());

    root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    textPanel = panel;
    field.requestFocus();
    field.postDelayed(() -> {
      InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }, 120);
  }

  private static String alignLabel (int align) {
    return align == 0 ? "⇤" : align == 2 ? "⇥" : "≡";
  }

  /** The field shows the text roughly as it'll be on the story: font, colour, style */
  private void applyFieldStyle () {
    EditText field = editField;
    TextLayer t = editing;
    if (field == null || t == null) return;
    field.setTypeface(typefaceOf(t.font));
    float px = t.size * canvasView.scale * t.scale;
    field.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(Screen.dp(14f), px));
    field.setGravity(t.align == 0 ? Gravity.LEFT : t.align == 2 ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
    field.getPaint().setStyle(t.style == STYLE_OUTLINE ? Paint.Style.STROKE : Paint.Style.FILL);
    field.setShadowLayer(t.style == STYLE_NEON ? Screen.dp(8f) : 0, 0, 0, t.color);
    if (t.style == STYLE_PLATE) {
      GradientDrawable plate = new GradientDrawable();
      plate.setColor(t.color);
      plate.setCornerRadius(px * .3f);
      field.setBackground(plate);
      field.setTextColor(isLight(t.color) ? 0xff111111 : 0xffffffff);
    } else {
      field.setBackground(null);
      field.setTextColor(t.style == STYLE_NEON ? 0xffffffff : t.color);
    }
    field.invalidate();
  }

  private void refreshChips (ViewGroup row, int selected) {
    for (int i = 0; i < row.getChildCount(); i++) {
      View v = row.getChildAt(i);
      if (v.getTag() instanceof Integer) v.setBackground(chipBackground(((Integer) v.getTag()) == selected));
    }
  }

  private void refreshDots (ViewGroup row, int color) {
    for (int i = 0; i < row.getChildCount(); i++) {
      View v = row.getChildAt(i);
      if (v.getTag() instanceof Integer) v.setBackground(dotBackground((Integer) v.getTag(), ((Integer) v.getTag()) == color));
    }
  }

  private void startEyedropper (ViewGroup colors) {
    if (textPanel == null) return;
    textPanel.setVisibility(View.GONE);
    hideKeyboard();
    UI.showToast(R.string.Tgx101StoryEyedropper, Toast.LENGTH_SHORT);
    eyedropper = () -> {
      textPanel.setVisibility(View.VISIBLE);
      refreshDots(colors, editing != null ? editing.color : 0);
    };
  }

  private void pickColorAt (float x, float y) {
    Runnable done = eyedropper;
    eyedropper = null;
    if (x >= 0 && y >= 0 && x < W && y < H && editing != null) {
      Bitmap one = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(one);
      c.translate(-x, -y);
      drawStory(c);
      int color = one.getPixel(0, 0) | 0xff000000;
      one.recycle();
      editing.color = color;
      lastColor = color;
      applyFieldStyle();
    }
    if (done != null) done.run();
  }

  private void finishTextEdit (boolean apply) {
    TextLayer t = editing;
    if (t != null) {
      if (!apply) {
        if (editingOriginal == null) texts.remove(t);
        else { int i = texts.indexOf(t); if (i >= 0) texts.set(i, editingOriginal); }
        if (!undo.isEmpty()) undo.remove(undo.size() - 1);
      } else if (t.text.trim().isEmpty()) {
        texts.remove(t);
      } else {
        lastSize = t.size;
      }
    }
    editing = null;
    editingOriginal = null;
    editField = null;
    eyedropper = null;
    hideKeyboard();
    if (textPanel != null) root.removeView(textPanel);
    textPanel = null;
    textSheet = null;
    setChromeVisible(true);
    updateUndoButtons();
    canvasView.invalidate();
  }

  private void hideKeyboard () {
    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
  }

  private interface FloatCallback { void onValue (float f); }

  /** A vertical size slider: top — bigger */
  private static final class SizeSlider extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FloatCallback callback;
    private float value;

    SizeSlider (Context context, float value, FloatCallback callback) {
      super(context);
      this.value = Math.max(0f, Math.min(1f, value));
      this.callback = callback;
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      float w = getWidth(), h = getHeight();
      Path track = new Path();
      track.moveTo(w / 2f - w * .32f, Screen.dp(8f));
      track.lineTo(w / 2f + w * .32f, Screen.dp(8f));
      track.lineTo(w / 2f + 2, h - Screen.dp(8f));
      track.lineTo(w / 2f - 2, h - Screen.dp(8f));
      track.close();
      p.setColor(0x80ffffff);
      c.drawPath(track, p);
      float y = Screen.dp(8f) + (h - Screen.dp(16f)) * (1f - value);
      p.setColor(0xffffffff);
      c.drawCircle(w / 2f, y, Screen.dp(9f), p);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      float h = getHeight() - Screen.dp(16f);
      value = Math.max(0f, Math.min(1f, 1f - (e.getY() - Screen.dp(8f)) / h));
      callback.onValue(value);
      invalidate();
      return true;
    }
  }

  // ── preview «how they'll see it» ───────────────────────────────────────────────────────

  private void setPreview (boolean preview) {
    previewMode = preview;
    canvasView.invalidate();
    if (preview) {
      List<TextLayer> covered = coveredLayers();
      if (!covered.isEmpty()) {
        showConfirm(Lang.getString(R.string.Tgx101StoryCovered), Lang.getString(R.string.Tgx101StoryLeave), Lang.getString(R.string.Tgx101StoryMoveIn), false, () -> { moveIntoSafeZone(covered); canvasView.invalidate(); });
      }
    }
  }

  private List<TextLayer> coveredLayers () {
    List<TextLayer> list = new ArrayList<>();
    for (TextLayer t : texts) {
      RectF b = storyBoundsOf(t);
      if (b.top < H * TOP_ZONE || b.bottom > H * (1f - BOTTOM_ZONE)) list.add(t);
    }
    return list;
  }

  private void moveIntoSafeZone (List<TextLayer> layers) {
    pushUndo();
    for (TextLayer t : layers) {
      RectF b = storyBoundsOf(t);
      float top = H * TOP_ZONE + 16, bottom = H * (1f - BOTTOM_ZONE) - 16;
      if (b.top < top) t.cy += top - b.top;
      else if (b.bottom > bottom) t.cy -= b.bottom - bottom;
    }
    canvasView.invalidate();
  }

  // ── publishing ─────────────────────────────────────────────────────────────────────────

  private int privacy; // 0 everyone, 1 contacts, 2 close friends
  private int period = 86400;
  private boolean inProfile = true, protect;

  private void openPublish () {
    if (publishSheet != null) return;
    hideKeyboard();
    tdlib.send(new TdApi.CanPostStory(tdlib.selfChatId()), (result, error) -> UI.post(() -> {
      if (error != null) { UI.showError(error); return; }
      if (!(result instanceof TdApi.CanPostStoryResultOk)) {
        int res = result instanceof TdApi.CanPostStoryResultPremiumNeeded ? R.string.Tgx101StoryNeedPremium
          : result instanceof TdApi.CanPostStoryResultActiveStoryLimitExceeded ? R.string.Tgx101StoryLimitActive
          : R.string.Tgx101StoryLimitPeriod;
        UI.showToast(res, Toast.LENGTH_LONG);
        return;
      }
      showPublishSheet();
    }));
  }

  // ── audience profiles (user 2026-10-12 «чтобы настройки сохранялись, а лучше 3–5 профилей») ──────────────────────

  private static final int PROFILES = 4;
  private int profileIndex;
  private final String[] profileNames = new String[PROFILES];
  private final org.json.JSONObject[] profiles = new org.json.JSONObject[PROFILES];
  private @Nullable LinearLayout publishCard;

  private static org.json.JSONArray idsJson (List<Long> ids) {
    org.json.JSONArray a = new org.json.JSONArray();
    for (long id : ids) a.put(id);
    return a;
  }

  private static void idsFrom (@Nullable org.json.JSONArray a, List<Long> out) {
    out.clear();
    if (a == null) return;
    for (int i = 0; i < a.length(); i++) out.add(a.optLong(i));
  }

  private void loadProfiles () {
    org.thunderdog.challegram.unsorted.Settings settings = org.thunderdog.challegram.unsorted.Settings.instance();
    org.json.JSONArray all = null;
    try {
      String raw = settings.tgx101StoryProfiles();
      if (raw != null) all = new org.json.JSONArray(raw);
    } catch (Throwable ignored) { }
    for (int i = 0; i < PROFILES; i++) {
      org.json.JSONObject o = all != null ? all.optJSONObject(i) : null;
      profiles[i] = o != null ? o : new org.json.JSONObject();
      profileNames[i] = profiles[i].optString("name", Lang.getString(R.string.Tgx101StoryAudienceProfile) + " " + (i + 1));
    }
    profileIndex = Math.max(0, Math.min(PROFILES - 1, settings.tgx101StoryProfile()));
    applyProfile(profiles[profileIndex]);
  }

  private void applyProfile (org.json.JSONObject o) {
    privacy = o.optInt("privacy", 0);
    period = o.optInt("period", 86400);
    if (period != 86400 && !tdlib.hasPremium()) period = 86400;
    inProfile = o.optBoolean("inProfile", true);
    protect = o.optBoolean("protect", false);
    idsFrom(o.optJSONArray("exceptEveryone"), exceptEveryone);
    idsFrom(o.optJSONArray("exceptContacts"), exceptContacts);
    idsFrom(o.optJSONArray("selected"), selectedUsers);
  }

  /** Every change goes into the current profile at once */
  private void saveProfile () {
    try {
      org.json.JSONObject o = new org.json.JSONObject();
      o.put("name", profileNames[profileIndex]);
      o.put("privacy", privacy);
      o.put("period", period);
      o.put("inProfile", inProfile);
      o.put("protect", protect);
      o.put("exceptEveryone", idsJson(exceptEveryone));
      o.put("exceptContacts", idsJson(exceptContacts));
      o.put("selected", idsJson(selectedUsers));
      profiles[profileIndex] = o;
      org.json.JSONArray all = new org.json.JSONArray();
      for (int i = 0; i < PROFILES; i++) {
        org.json.JSONObject p = profiles[i] != null ? profiles[i] : new org.json.JSONObject();
        p.put("name", profileNames[i]);
        all.put(p);
      }
      org.thunderdog.challegram.unsorted.Settings.instance().setTgx101StoryProfiles(all.toString(), profileIndex);
    } catch (Throwable ignored) { }
  }

  private void showPublishSheet () {
    Context context = getContext();
    loadProfiles();
    FrameLayout dim = new FrameLayout(context);
    dim.setBackgroundColor(0x80000000);
    dim.setOnClickListener(v -> closePublish());
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    card.setBackground(cardBackground());
    card.setPadding(0, Screen.dp(8f), 0, Screen.dp(12f));
    publishCard = card;
    fillPublishCard();
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    cp.leftMargin = cp.rightMargin = cp.bottomMargin = Screen.dp(8f);
    dim.addView(card, cp);
    root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    publishSheet = dim;
    card.setTranslationY(Screen.dp(400f));
    card.animate().translationY(0f).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    dim.setAlpha(0f);
    dim.animate().alpha(1f).setDuration(200).start();
  }

  private void fillPublishCard () {
    LinearLayout card = publishCard;
    if (card == null) return;
    Context context = getContext();
    card.removeAllViews();

    View grab = new View(context);
    GradientDrawable g = new GradientDrawable();
    g.setColor(0x38ffffff);
    g.setCornerRadius(Screen.dp(2f));
    grab.setBackground(g);
    LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(4f));
    gp.gravity = Gravity.CENTER_HORIZONTAL;
    gp.bottomMargin = Screen.dp(8f);
    card.addView(grab, gp);

    TextView title = new TextView(context);
    title.setText(Lang.getString(R.string.Tgx101StoryPublishTitle));
    title.setTextColor(0xffffffff);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
    card.addView(title);

    // profiles: tap — switch, hold — rename
    LinearLayout profileRow = chipRow(context);
    for (int i = 0; i < PROFILES; i++) {
      final int index = i;
      TextView c = chip(context, profileNames[i], i == profileIndex, v -> {
        if (index == profileIndex) return;
        profileIndex = index;
        applyProfile(profiles[index]);
        saveProfile();
        fillPublishCard();
      });
      c.setTag(i);
      c.setOnLongClickListener(v -> { renameProfile(index); return true; });
      profileRow.addView(c, chipParams());
    }
    HorizontalScrollView profileScroll = new HorizontalScrollView(context);
    profileScroll.setHorizontalScrollBarEnabled(false);
    profileScroll.addView(profileRow);
    card.addView(profileScroll);
    TextView hint = new TextView(context);
    hint.setText(Lang.getString(R.string.Tgx101StoryProfileHint));
    hint.setTextColor(0xff6f8396);
    hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
    hint.setPadding(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(4f));
    card.addView(hint);

    audienceList = new LinearLayout(context);
    audienceList.setOrientation(LinearLayout.VERTICAL);
    buildAudience();
    card.addView(audienceList);

    LinearLayout periods = chipRow(context);
    int[] hours = {6, 12, 24, 48};
    boolean premium = tdlib.hasPremium();
    for (int h : hours) {
      final int seconds = h * 3600;
      boolean allowed = h == 24 || premium;
      TextView c = chip(context, h + " " + Lang.getString(R.string.Tgx101StoryHours), seconds == period, v -> {
        if (!allowed) { UI.showToast(R.string.Tgx101StoryPeriodPremium, Toast.LENGTH_SHORT); return; }
        period = seconds;
        saveProfile();
        refreshChips((ViewGroup) v.getParent(), seconds);
      });
      c.setTag(seconds);
      if (!allowed) c.setAlpha(.45f);
      periods.addView(c, chipParams());
    }
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    pp.topMargin = Screen.dp(8f);
    card.addView(periods, pp);

    card.addView(switchRow(context, R.string.Tgx101StoryInProfile, inProfile, on -> { inProfile = on; saveProfile(); }));
    card.addView(switchRow(context, R.string.Tgx101StoryProtect, protect, on -> { protect = on; saveProfile(); }));

    TextView quality = new TextView(context);
    quality.setText(Lang.getString(R.string.Tgx101StoryQualityNote));
    quality.setTextColor(0xffcfe3f6);
    quality.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    quality.setPadding(Screen.dp(12f), Screen.dp(9f), Screen.dp(12f), Screen.dp(9f));
    GradientDrawable q = new GradientDrawable();
    q.setColor(0x1f3d8fd8);
    q.setCornerRadius(Screen.dp(12f));
    quality.setBackground(q);
    LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    qp.leftMargin = qp.rightMargin = Screen.dp(12f);
    qp.topMargin = Screen.dp(6f);
    card.addView(quality, qp);

    LinearLayout buttons = new LinearLayout(context);
    buttons.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), 0);
    buttons.addView(pill(context, Lang.getString(R.string.Tgx101StoryBack), false, v -> closePublish()), new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f));
    LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
    pb.leftMargin = Screen.dp(10f);
    buttons.addView(pill(context, Lang.getString(R.string.Tgx101StoryPublish), true, v -> publish()), pb);
    card.addView(buttons);
  }

  private void renameProfile (int index) {
    Context context = getContext();
    EditText field = new EditText(context);
    field.setText(profileNames[index]);
    field.setSelectAllOnFocus(true);
    field.setSingleLine(true);
    field.setTextColor(0xffffffff);
    field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    field.setPadding(Screen.dp(14f), Screen.dp(10f), Screen.dp(14f), Screen.dp(10f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0x12ffffff);
    bg.setCornerRadius(Screen.dp(14f));
    field.setBackground(bg);
    showInputCard(Lang.getString(R.string.Tgx101StoryProfileRename), field, () -> {
      String name = field.getText().toString().trim();
      if (name.isEmpty()) return;
      profileNames[index] = name.length() > 24 ? name.substring(0, 24) : name;
      int keep = profileIndex;
      profileIndex = index;
      try { profiles[index].put("name", profileNames[index]); } catch (Throwable ignored) { }
      profileIndex = keep;
      saveProfile();
      fillPublishCard();
    });
  }

  /** A card at the top of the screen with a field (the keyboard can't cover it) */
  private void showInputCard (String titleText, EditText field, Runnable onDone) {
    Context context = getContext();
    FrameLayout dim = new FrameLayout(context);
    dim.setBackgroundColor(0x80000000);
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setClickable(true);
    card.setBackground(cardBackground());
    card.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(14f));
    TextView title = new TextView(context);
    title.setText(titleText);
    title.setTextColor(0xffffffff);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    title.setPadding(0, 0, 0, Screen.dp(10f));
    card.addView(title);
    card.addView(field);
    LinearLayout buttons = new LinearLayout(context);
    buttons.setPadding(0, Screen.dp(14f), 0, 0);
    buttons.addView(pill(context, Lang.getString(R.string.Cancel), false, v -> { hideKeyboard(); root.removeView(dim); }), new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f));
    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
    dp.leftMargin = Screen.dp(10f);
    buttons.addView(pill(context, Lang.getString(R.string.Tgx101StoryDone), true, v -> { hideKeyboard(); root.removeView(dim); onDone.run(); }), dp);
    card.addView(buttons);
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
    cp.leftMargin = cp.rightMargin = Screen.dp(8f);
    cp.topMargin = Screen.dp(40f);
    dim.addView(card, cp);
    root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    field.requestFocus();
    field.postDelayed(() -> {
      InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }, 120);
  }

  private void closePublish () {
    View sheet = publishSheet;
    if (sheet == null) return;
    publishSheet = null;
    sheet.animate().alpha(0f).setDuration(180).withEndAction(() -> root.removeView(sheet)).start();
  }

  private boolean publishing;
  private final ArrayList<Long> selectedUsers = new ArrayList<>();

  private static long[] toArray (List<Long> list) {
    long[] a = new long[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }

  private final ArrayList<Long> exceptEveryone = new ArrayList<>(), exceptContacts = new ArrayList<>();
  private int closeFriendsCount = -1;
  private @Nullable LinearLayout audienceList;

  private void buildAudience () {
    LinearLayout list = audienceList;
    if (list == null) return;
    list.removeAllViews();
    if (closeFriendsCount < 0) {
      closeFriendsCount = 0;
      tdlib.send(new TdApi.GetCloseFriends(), (users, error) -> UI.post(() -> {
        if (users != null) { closeFriendsCount = users.userIds.length; buildAudience(); }
      }));
    }
    for (int value : AUDIENCE_ORDER) list.addView(audienceRow(value));
  }

  // 0 everyone, 4 everyone except…, 1 contacts (except…), 2 close friends, 3 selected — in the official app's order
  private static final int[] AUDIENCE_ORDER = {0, 4, 1, 2, 3};

  private String audienceSubtitle (int value) {
    switch (value) {
      case 0: return Lang.getString(R.string.Tgx101StoryAudienceAll);
      case 4: return exceptEveryone.isEmpty() ? Lang.getString(R.string.Tgx101StoryChoosePeople) : Lang.getString(R.string.Tgx101StoryExcept) + " " + exceptEveryone.size() + " " + Lang.getString(R.string.Tgx101StoryPeople);
      case 1: return exceptContacts.isEmpty() ? Lang.getString(R.string.Tgx101StoryAudienceContacts) : Lang.getString(R.string.Tgx101StoryExcept) + " " + exceptContacts.size() + " " + Lang.getString(R.string.Tgx101StoryPeople);
      case 2: return closeFriendsCount > 0 ? closeFriendsCount + " " + Lang.getString(R.string.Tgx101StoryPeople) + " · " + Lang.getString(R.string.Tgx101StoryEditList) : Lang.getString(R.string.Tgx101StoryChoosePeople);
      default: return selectedUsers.isEmpty() ? Lang.getString(R.string.Tgx101StoryChoosePeople) : selectedUsers.size() + " " + Lang.getString(R.string.Tgx101StoryPeople);
    }
  }

  private View audienceRow (int value) {
    Context context = getContext();
    int[] titles = {R.string.Tgx101StoryEveryone, R.string.Tgx101StoryContacts, R.string.Tgx101StoryCloseFriends, R.string.Tgx101StorySelected, R.string.Tgx101StoryEveryoneExcept};
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
    View radio = new View(context);
    GradientDrawable r = new GradientDrawable();
    r.setShape(GradientDrawable.OVAL);
    if (privacy == value) { r.setColor(0xff3d8fd8); r.setStroke(Screen.dp(5f), 0xff1d2733); } else r.setStroke(Screen.dp(2f), 0x66ffffff);
    radio.setBackground(r);
    if (privacy == value) {
      FrameLayout ring = new FrameLayout(context);
      GradientDrawable outer = new GradientDrawable();
      outer.setShape(GradientDrawable.OVAL);
      outer.setStroke(Screen.dp(2f), 0xff3d8fd8);
      ring.setBackground(outer);
      ring.addView(radio, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      row.addView(ring, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    } else {
      row.addView(radio, new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    }
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.setPadding(Screen.dp(14f), 0, Screen.dp(8f), 0);
    TextView title = new TextView(context);
    title.setText(Lang.getString(titles[value]));
    title.setTextColor(0xffe9eef4);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    texts.addView(title);
    TextView sub = new TextView(context);
    sub.setText(audienceSubtitle(value));
    sub.setTextColor(0xff8a9bb0);
    sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
    texts.addView(sub);
    row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    TextView edit = new TextView(context);
    edit.setText("›");
    edit.setGravity(Gravity.CENTER);
    edit.setTextColor(0xff8a9bb0);
    edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f);
    edit.setOnClickListener(v -> editAudience(value));
    if (value == 0) edit.setVisibility(View.INVISIBLE);
    row.addView(edit, new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f)));
    row.setOnClickListener(v -> {
      if ((value == 3 && selectedUsers.isEmpty()) || (value == 2 && closeFriendsCount == 0) || (value == 4 && exceptEveryone.isEmpty())) { editAudience(value); return; }
      privacy = value;
      saveProfile();
      buildAudience();
    });
    return row;
  }

  private void editAudience (int value) {
    switch (value) {
      case 0:
        break;
      case 4:
        pickUsers(Lang.getString(R.string.Tgx101StoryExceptTitle), exceptEveryone, ids -> { exceptEveryone.clear(); exceptEveryone.addAll(ids); privacy = ids.isEmpty() ? 0 : 4; saveProfile(); buildAudience(); });
        break;
      case 1:
        pickUsers(Lang.getString(R.string.Tgx101StoryExceptTitle), exceptContacts, ids -> { exceptContacts.clear(); exceptContacts.addAll(ids); privacy = 1; saveProfile(); buildAudience(); });
        break;
      case 2:
        tdlib.send(new TdApi.GetCloseFriends(), (users, error) -> UI.post(() -> {
          ArrayList<Long> current = new ArrayList<>();
          if (users != null) for (long id : users.userIds) current.add(id);
          pickUsers(Lang.getString(R.string.Tgx101StoryCloseFriends), current, ids -> tdlib.send(new TdApi.SetCloseFriends(toArray(ids)), (ok, err) -> UI.post(() -> {
            if (err != null) { UI.showError(err); return; }
            closeFriendsCount = ids.size();
            if (!ids.isEmpty()) privacy = 2;
            saveProfile();
            buildAudience();
          })));
        }));
        break;
      default:
        pickUsers(Lang.getString(R.string.Tgx101StorySelectedTitle), selectedUsers, ids -> { selectedUsers.clear(); selectedUsers.addAll(ids); if (!ids.isEmpty()) privacy = 3; saveProfile(); buildAudience(); });
    }
  }

  private interface IdsCallback { void onIds (List<Long> ids); }

  /** A card with the contacts to tick, a search field on top (by name) — the same picker for every list */
  private void pickUsers (String titleText, List<Long> initial, IdsCallback done) {
    tdlib.send(new TdApi.GetContacts(), (users, error) -> UI.post(() -> {
      ArrayList<Long> ids = new ArrayList<>();
      if (users != null) for (long id : users.userIds) ids.add(id);
      for (long id : initial) if (!ids.contains(id)) ids.add(0, id); // ticked non-contacts stay on the list
      if (ids.isEmpty()) { UI.showToast(R.string.Tgx101StoryNoContacts, Toast.LENGTH_SHORT); return; }
      Context context = getContext();
      FrameLayout dim = new FrameLayout(context);
      dim.setBackgroundColor(0x80000000);
      LinearLayout card = new LinearLayout(context);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setClickable(true);
      card.setBackground(cardBackground());
      card.setPadding(0, Screen.dp(14f), 0, Screen.dp(12f));
      TextView title = new TextView(context);
      title.setText(titleText);
      title.setTextColor(0xffffffff);
      title.setTypeface(Typeface.DEFAULT_BOLD);
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      title.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      card.addView(title);
      EditText search = new EditText(context);
      search.setHint(Lang.getString(R.string.Tgx101StorySearchPeople));
      search.setHintTextColor(0xff8a9bb0);
      search.setTextColor(0xffffffff);
      search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
      search.setSingleLine(true);
      search.setPadding(Screen.dp(14f), Screen.dp(9f), Screen.dp(14f), Screen.dp(9f));
      GradientDrawable sb = new GradientDrawable();
      sb.setColor(0x12ffffff);
      sb.setCornerRadius(Screen.dp(14f));
      search.setBackground(sb);
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      sp.leftMargin = sp.rightMargin = Screen.dp(12f);
      sp.bottomMargin = Screen.dp(6f);
      card.addView(search, sp);
      android.widget.ScrollView scroll = new android.widget.ScrollView(context);
      LinearLayout list = new LinearLayout(context);
      list.setOrientation(LinearLayout.VERTICAL);
      final ArrayList<Long> chosen = new ArrayList<>(initial);
      final ArrayList<View> rows = new ArrayList<>();
      final ArrayList<String> names = new ArrayList<>();
      for (long userId : ids) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
        String nameText = tdlib.cache().userDisplayName(userId, false, false);
        TextView name = new TextView(context);
        name.setText(nameText);
        name.setTextColor(0xffe9eef4);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.CheckBox box = new android.widget.CheckBox(context);
        box.setChecked(chosen.contains(userId));
        box.setClickable(false);
        row.addView(box);
        row.setOnClickListener(v -> {
          if (chosen.contains(userId)) chosen.remove(userId); else chosen.add(userId);
          box.setChecked(chosen.contains(userId));
        });
        list.addView(row);
        rows.add(row);
        names.add(nameText.toLowerCase(java.util.Locale.ROOT));
      }
      search.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged (CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged (CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged (Editable s) {
          String q = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
          for (int i = 0; i < rows.size(); i++) rows.get(i).setVisibility(q.isEmpty() || names.get(i).contains(q) ? View.VISIBLE : View.GONE);
        }
      });
      scroll.addView(list);
      pickerScroll = scroll;
      pickerRows = ids.size();
      card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pickerListHeight()));
      LinearLayout buttons = new LinearLayout(context);
      buttons.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), 0);
      buttons.addView(pill(context, Lang.getString(R.string.Cancel), false, v -> { hideKeyboard(); root.removeView(dim); }), new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f));
      LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, Screen.dp(40f), 1f);
      dp.leftMargin = Screen.dp(10f);
      buttons.addView(pill(context, Lang.getString(R.string.Tgx101StoryDone), true, v -> { hideKeyboard(); root.removeView(dim); done.onIds(chosen); }), dp);
      card.addView(buttons);
      // at the top: the keyboard opened for the search pushed a bottom card off the screen (user's video 10:49)
      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
      cp.leftMargin = cp.rightMargin = Screen.dp(8f);
      cp.topMargin = Screen.dp(36f);
      dim.addView(card, cp);
      dim.setOnClickListener(v -> { hideKeyboard(); root.removeView(dim); });
      root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }));
  }

  private @Nullable android.widget.ScrollView pickerScroll;
  private int pickerRows;

  /** The picker's list takes what's left between the top and the keyboard (or half the screen without it) */
  private int pickerListHeight () {
    int full = Screen.dp(56f) * Math.max(1, pickerRows);
    int room = imeHeight > 0 ? root.getHeight() - imeLift() - Screen.dp(36f + 170f) : Math.round(root.getHeight() * .55f);
    return Math.max(Screen.dp(112f), Math.min(full, room));
  }

  private void publish () {
    if (publishing) return;
    publishing = true;
    final String caption = captionView.getText().toString().trim();
    final int privacyChoice = privacy, activePeriod = period;
    final boolean postToProfile = inProfile, protectContent = protect;
    UI.showToast(R.string.Tgx101StoryPublishing, Toast.LENGTH_SHORT);
    new Thread(() -> {
      File file = null;
      long started = SystemClock.uptimeMillis();
      try {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        exporting = true;
        try {
          drawStory(new Canvas(out));
        } finally {
          exporting = false;
        }
        File dir = new File(getContext().getCacheDir(), "stories");
        if (!dir.exists()) dir.mkdirs();
        file = new File(dir, "story_" + System.currentTimeMillis() + ".jpg");
        int quality = 97;
        while (true) {
          try (FileOutputStream os = new FileOutputStream(file)) {
            out.compress(Bitmap.CompressFormat.JPEG, quality, os);
          }
          if (file.length() <= 10L * 1024 * 1024 - 64 * 1024 || quality <= 70) break;
          quality -= 5;
        }
        out.recycle();
        Tgx101Diag.mark("story editor: rendered 1080x1920 JPEG q" + quality + ", " + (file.length() / 1024) + " KB in " + (SystemClock.uptimeMillis() - started) + " ms");
      } catch (Throwable t) {
        Tgx101Diag.mark("story editor: render failed " + t.getClass().getSimpleName());
        file = null;
      }
      final File result = file;
      UI.post(() -> {
        if (result == null) {
          publishing = false;
          UI.showToast(R.string.Tgx101StoryFailed, Toast.LENGTH_LONG);
          return;
        }
        TdApi.StoryPrivacySettings privacySettings = privacyChoice == 3 ? new TdApi.StoryPrivacySettingsSelectedUsers(toArray(selectedUsers))
          : privacyChoice == 0 ? new TdApi.StoryPrivacySettingsEveryone(new long[0])
          : privacyChoice == 1 ? new TdApi.StoryPrivacySettingsContacts(toArray(exceptContacts))
          : privacyChoice == 2 ? new TdApi.StoryPrivacySettingsCloseFriends()
          : new TdApi.StoryPrivacySettingsEveryone(toArray(exceptEveryone));
        TdApi.InputStoryContent content = new TdApi.InputStoryContentPhoto(new TdApi.InputFileLocal(result.getAbsolutePath()), new int[0]);
        TdApi.FormattedText text = caption.isEmpty() ? null : new TdApi.FormattedText(caption, new TdApi.TextEntity[0]);
        tdlib.send(new TdApi.PostStory(tdlib.selfChatId(), content, reactionAreas(), text, privacySettings, new int[0], activePeriod, null, postToProfile, protectContent), (story, error) -> UI.post(() -> {
          publishing = false;
          if (error != null) {
            Tgx101Diag.mark("story editor: post failed " + error.message);
            UI.showError(error);
            return;
          }
          Tgx101Diag.mark("story editor: posted (privacy " + privacyChoice + ", " + (activePeriod / 3600) + " h, profile " + postToProfile + ", protect " + protectContent + ")");
          UI.showToast(R.string.Tgx101StoryPosted, Toast.LENGTH_SHORT);
          dismiss();
        }));
      });
    }, "story-render").start();
  }

  // ── small ui helpers ───────────────────────────────────────────────────────────────────

  private static GradientDrawable glassBackground (float radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(0x9e0f141a);
    d.setCornerRadius(Screen.dp(radiusDp));
    d.setStroke(Math.max(1, Screen.dp(.5f)), 0x1affffff);
    return d;
  }

  private static GradientDrawable cardBackground () {
    GradientDrawable d = new GradientDrawable();
    d.setColor(0xff1d2733);
    d.setCornerRadius(Screen.dp(18f));
    return d;
  }

  private static GradientDrawable chipBackground (boolean on) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(on ? 0xff3d8fd8 : 0x12ffffff);
    d.setCornerRadius(Screen.dp(13f));
    return d;
  }

  private static TextView glass (Context context, String text, View.OnClickListener click) {
    TextView v = new TextView(context);
    v.setText(text);
    v.setGravity(Gravity.CENTER);
    v.setTextColor(0xffffffff);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    v.setTypeface(Typeface.DEFAULT_BOLD);
    v.setBackground(glassBackground(14f));
    v.setOnClickListener(click);
    return v;
  }

  private static TextView pill (Context context, String text, boolean primary, View.OnClickListener click) {
    TextView v = new TextView(context);
    v.setText(text);
    v.setGravity(Gravity.CENTER);
    v.setTextColor(0xffffffff);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
    if (primary) v.setTypeface(Typeface.DEFAULT_BOLD);
    v.setPadding(Screen.dp(18f), 0, Screen.dp(18f), 0);
    GradientDrawable d = new GradientDrawable();
    d.setColor(primary ? 0xff3d8fd8 : 0x12ffffff);
    d.setCornerRadius(Screen.dp(14f));
    v.setBackground(d);
    v.setOnClickListener(click);
    return v;
  }

  private static TextView chip (Context context, String text, boolean on, @Nullable View.OnClickListener click) {
    TextView v = new TextView(context);
    v.setText(text);
    v.setGravity(Gravity.CENTER);
    v.setTextColor(0xffdfe8f1);
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    v.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    v.setBackground(chipBackground(on));
    if (click != null) v.setOnClickListener(click);
    return v;
  }

  private static LinearLayout chipRow (Context context) {
    LinearLayout row = new LinearLayout(context);
    row.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    return row;
  }

  private static LinearLayout.LayoutParams chipParams () {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(34f));
    p.rightMargin = Screen.dp(8f);
    return p;
  }

  private static GradientDrawable dotBackground (int color, boolean on) {
    GradientDrawable d = new GradientDrawable();
    d.setShape(GradientDrawable.OVAL);
    d.setColor(color);
    d.setStroke(Screen.dp(on ? 3f : 2f), on ? 0xffffffff : 0x40ffffff);
    return d;
  }

  private static View colorDot (Context context, int color, boolean on) {
    View v = new View(context);
    v.setTag(color);
    v.setBackground(dotBackground(color, on));
    return v;
  }

  private static LinearLayout.LayoutParams dotParams () {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(26f), Screen.dp(26f));
    p.rightMargin = Screen.dp(8f);
    return p;
  }

  private interface BoolCallback { void onValue (boolean on); }

  private static View switchRow (Context context, int title, boolean on, BoolCallback callback) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(4f));
    TextView t = new TextView(context);
    t.setText(Lang.getString(title));
    t.setTextColor(0xffe9eef4);
    t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    // our capsule toggle — the system switch was a barely visible dot on this dark card (user's video 10:49)
    Toggle toggle = new Toggle(context, on);
    row.addView(toggle, new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(24f)));
    row.setOnClickListener(v -> { toggle.set(!toggle.on); callback.onValue(toggle.on); });
    return row;
  }

  private static final class Toggle extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    boolean on;
    private float factor;

    Toggle (Context context, boolean on) {
      super(context);
      this.on = on;
      this.factor = on ? 1f : 0f;
    }

    void set (boolean on) {
      this.on = on;
      android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(factor, on ? 1f : 0f);
      a.setDuration(160);
      a.addUpdateListener(an -> { factor = (float) an.getAnimatedValue(); invalidate(); });
      a.start();
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      float h = getHeight(), w = getWidth();
      r.set(0, 0, w, h);
      p.setColor(blend(0x2effffff, 0xff3d8fd8, factor));
      c.drawRoundRect(r, h / 2f, h / 2f, p);
      p.setColor(0xffffffff);
      float d = h - Screen.dp(4f);
      c.drawCircle(Screen.dp(2f) + d / 2f + (w - d - Screen.dp(4f)) * factor, h / 2f, d / 2f, p);
    }

    private static int blend (int a, int b, float f) {
      int aa = a >>> 24, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
      int ba = b >>> 24, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
      return ((int) (aa + (ba - aa) * f) << 24) | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }
  }

  private static LinearLayout.LayoutParams squareParams (float dp, float topOrLeftMarginDp) {
    return squareParams(dp, topOrLeftMarginDp, false);
  }

  private static LinearLayout.LayoutParams squareParams (float dp, float marginDp, boolean vertical) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(dp), Screen.dp(dp));
    if (vertical) p.topMargin = Screen.dp(marginDp); else p.leftMargin = Screen.dp(marginDp);
    return p;
  }
}
