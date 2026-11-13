/*
 * This file is a part of PlumaGram
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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 «Плеер в поле ввода» (user 2026-11-13, mockup approved with changes): while a track plays, a 2 dp line of the
 * accent colour runs along the input field's top edge; a swipe up from the field (or a tap / drag on the line) brings the
 * player out from under the field — cover, title, a heart that saves the message to Saved Messages at once (a second tap
 * takes it back), the slider, speed, previous / play / next, close. Scrolling the chat ~72 dp (or a fling) puts it away.
 */
public final class Tgx101InputPlayer implements TGPlayerController.TrackChangeListener, TGPlayerController.TrackListener {
  private final MessagesController c;
  private final Tdlib tdlib;
  private final ViewGroup host;
  private final View anchor; // the field's wrap: the player lies right above its top

  private final StripView strip;
  private final LinearLayout panel;
  private final ImageView cover, heart, play;
  private final TextView title, subtitle, speedView, timeNow, timeLeft;
  private final SliderView slider;

  private @Nullable TdApi.Message track;
  private int state = TGPlayerController.STATE_NONE;
  private float progress;
  private long position, duration;
  private boolean saved, savingBusy;

  private float expand; // 0 — only the line, 1 — the player out
  private @Nullable ValueAnimator expandAnimator;
  private boolean destroyed;

  private final View field; // the input itself: the line runs along the bottom of its row

  Tgx101InputPlayer (MessagesController c, ViewGroup host, View anchor, View field) {
    this.field = field;
    this.c = c;
    this.tdlib = c.tdlib();
    this.host = host;
    this.anchor = anchor;
    Context context = c.context();

    strip = new StripView(context);
    strip.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(STRIP_DP), Gravity.TOP));
    strip.setVisibility(View.GONE);

    panel = new LinearLayout(context);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setClickable(true);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    float r = Screen.dp(22f);
    bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    panel.setBackground(bg);
    if (android.os.Build.VERSION.SDK_INT >= 21) panel.setElevation(Screen.dp(8f));
    panel.setPadding(0, Screen.dp(8f), 0, Screen.dp(10f));
    panel.setVisibility(View.GONE);

    View handle = new View(context);
    GradientDrawable handleBg = new GradientDrawable();
    handleBg.setColor(ColorUtils.alphaColor(.25f, Theme.textAccentColor())); // the separator colour didn't show on the dark theme
    handleBg.setCornerRadius(Screen.dp(2f));
    handle.setBackground(handleBg);
    LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(4f));
    handleParams.gravity = Gravity.CENTER_HORIZONTAL;
    handleParams.bottomMargin = Screen.dp(8f);
    panel.addView(handle, handleParams);

    // cover · title · heart
    LinearLayout head = new LinearLayout(context);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(Screen.dp(16f), 0, Screen.dp(12f), 0);
    cover = new ImageView(context);
    cover.setScaleType(ImageView.ScaleType.CENTER);
    head.addView(cover, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    title = text(context, 16f, true);
    subtitle = text(context, 13f, false);
    texts.addView(title);
    texts.addView(subtitle);
    LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    textsParams.leftMargin = Screen.dp(12f);
    head.addView(texts, textsParams);
    heart = iconButton(context, R.drawable.tgx101_heart_outline_24, Screen.dp(44f));
    heart.setContentDescription(Lang.getString(R.string.Tgx101InPlayerSave));
    heart.setOnClickListener(v -> toggleSaved());
    head.addView(heart, new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
    panel.addView(head);

    // the slider with its times
    slider = new SliderView(context);
    LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(28f));
    sliderParams.topMargin = Screen.dp(10f);
    sliderParams.leftMargin = sliderParams.rightMargin = Screen.dp(8f);
    panel.addView(slider, sliderParams);
    LinearLayout times = new LinearLayout(context);
    times.setOrientation(LinearLayout.HORIZONTAL);
    times.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    timeNow = text(context, 12f, false);
    timeLeft = text(context, 12f, false);
    timeLeft.setGravity(Gravity.RIGHT);
    times.addView(timeNow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    times.addView(timeLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    panel.addView(times);

    // speed · previous · play · next · close
    LinearLayout controls = new LinearLayout(context);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(68f));
    controlsParams.topMargin = Screen.dp(4f);
    speedView = text(context, 14f, true);
    speedView.setGravity(Gravity.CENTER);
    speedView.setTextColor(Theme.textDecentColor());
    speedView.setContentDescription(Lang.getString(R.string.Tgx101InPlayerSpeed));
    speedView.setOnClickListener(v -> nextSpeed());
    controls.addView(speedView, controlParams(44));
    ImageView prev = iconButton(context, R.drawable.baseline_skip_previous_24_white, Screen.dp(48f));
    prev.setContentDescription(Lang.getString(R.string.Tgx101InPlayerPrev));
    prev.setOnClickListener(v -> tdlib.context().player().skip(false));
    tonal(prev, Screen.dp(16f));
    controls.addView(prev, controlParams(48));
    play = iconButton(context, R.drawable.baseline_pause_24, Screen.dp(64f));
    play.setOnClickListener(v -> tdlib.context().player().playPauseCurrent());
    GradientDrawable playBg = new GradientDrawable();
    playBg.setColor(Theme.getColor(ColorId.textNeutral));
    playBg.setCornerRadius(Screen.dp(22f));
    play.setBackground(playBg);
    play.setColorFilter(Theme.fillingColor());
    controls.addView(play, controlParams(64));
    ImageView next = iconButton(context, R.drawable.baseline_skip_next_24_white, Screen.dp(48f));
    next.setContentDescription(Lang.getString(R.string.Tgx101InPlayerNext));
    next.setOnClickListener(v -> tdlib.context().player().skip(true));
    tonal(next, Screen.dp(16f));
    controls.addView(next, controlParams(48));
    ImageView close = iconButton(context, R.drawable.baseline_close_24, Screen.dp(44f));
    close.setContentDescription(Lang.getString(R.string.Tgx101InPlayerClose));
    close.setColorFilter(Theme.textDecentColor());
    close.setOnClickListener(v -> {
      Tgx101Diag.mark("input player: closed");
      tdlib.context().player().stopPlayback(true);
    });
    controls.addView(close, controlParams(44));
    panel.addView(controls, controlsParams);

    // dragging the open player down puts it back
    panel.setOnTouchListener(new DragListener(false));
    // (user 2026-11-13 «полоса на разделении поля ввода и нижнего отступа»): the line lies on the field's bottom edge and
    // takes no touches there — the field's own taps stay the field's; a swipe up from the field brings the player out
    strip.setClickable(false);

    int at = host.indexOfChild(anchor);
    host.addView(panel, at < 0 ? host.getChildCount() : at, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)); // under the field
    host.addView(strip); // over everything, a thin line
    host.getViewTreeObserver().addOnPreDrawListener(placer);

    tdlib.context().player().addTrackChangeListener(this);
  }

  private static final float STRIP_DP = 12f; // the 2 dp line in its middle, room for its glow
  private static final float SCROLL_HIDE_DP = 72f;

  private final android.view.ViewTreeObserver.OnPreDrawListener placer = () -> {
    place();
    return true;
  };

  /** the line on the field's top edge, the player above it — as far out as {@link #expand} says */
  private void place () {
    if (destroyed) return;
    float top = anchor.getTop() + anchor.getTranslationY();
    if (anchor.getVisibility() != View.VISIBLE) top = host.getHeight();
    // the line: the bottom of the field's row (between the field and the filling under it — the gesture bar's room or the
    // three buttons' — or the emoji / keyboard panel)
    // (user 2026-11-13 «по уму она должна быть на твоём первом месте сверху»): on the field's top edge again — opening,
    // it rises with the player and is its slider, no seam; the bottom is the card's (variant В)
    float line = top;
    strip.setTranslationY(line - strip.getHeight() / 2f);
    int h = panel.getHeight();
    panel.setTranslationY(top - h * expand);
    boolean out = expand > 0f;
    if ((panel.getVisibility() == View.VISIBLE) != out) panel.setVisibility(out ? View.VISIBLE : View.GONE);
    applyMotionLook();
  }

  private float lastLook = -1f;

  /**
   * (user 2026-11-13 «размытие во время поднятия и отпускания, чтобы было бесшовно»): on its way the player's contents are
   * blurred and see-through, sharp once it stands — the GPU blurs on Android 12+, older ones only fade
   */
  private void applyMotionLook () {
    float e = Math.max(0f, Math.min(1f, expand));
    if (e == lastLook) return;
    lastLook = e;
    float shown = Math.min(1f, e * 1.25f);
    for (int i = 0; i < panel.getChildCount(); i++) panel.getChildAt(i).setAlpha(shown);
    if (android.os.Build.VERSION.SDK_INT >= 31) Blur31.apply(panel, e >= 1f || e <= 0f ? 0f : Screen.dp(14f) * (1f - e));
  }

  @androidx.annotation.RequiresApi(31)
  private static final class Blur31 {
    static void apply (View v, float radius) {
      v.setRenderEffect(radius < .5f ? null : android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP));
    }
  }

  private float fieldRowBottom () {
    View v = field;
    while (v != null && v.getParent() != anchor && v.getParent() instanceof View) v = (View) v.getParent();
    if (v == null || v.getParent() != anchor) return anchor.getHeight() - anchor.getPaddingBottom();
    return v.getBottom() + v.getTranslationY();
  }

  // Track

  @Override
  public void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) {
    if (destroyed) return;
    UI.post(() -> setTrack(tdlib == this.tdlib ? newTrack : null, state));
  }

  @Override
  public void onPlaybackSpeedChanged (int newSpeed) {
    if (!destroyed) UI.post(this::updateSpeed);
  }

  private void setTrack (@Nullable TdApi.Message newTrack, int newState) {
    if (destroyed) return;
    TdApi.Message old = track;
    boolean same = old != null && newTrack != null && old.chatId == newTrack.chatId && old.id == newTrack.id;
    if (!same) {
      if (old != null) tdlib.context().player().removeTrackListener(tdlib, old, this);
      track = newTrack;
      progress = 0f;
      position = 0;
      duration = newTrack != null ? durationOf(newTrack) * 1000L : 0;
      saved = false;
      if (newTrack != null) {
        tdlib.context().player().addTrackListener(tdlib, newTrack, this);
        Tgx101Diag.mark("input player: track " + newTrack.chatId + "/" + newTrack.id);
        bindTrack(newTrack);
        checkSaved(newTrack);
      }
    }
    state = newTrack != null ? newState : TGPlayerController.STATE_NONE;
    boolean has = track != null && state != TGPlayerController.STATE_NONE;
    strip.setVisibility(has ? View.VISIBLE : View.GONE);
    if (!has && expand > 0f) setExpanded(false, false);
    updatePlay();
    updateSpeed();
    strip.invalidate();
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    UI.post(() -> {
      if (destroyed || track == null || track.chatId != chatId || track.id != messageId) return;
      this.state = state;
      if (state == TGPlayerController.STATE_NONE) {
        setTrack(null, state);
      } else {
        updatePlay();
        strip.invalidate();
      }
    });
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long position, long totalDuration, boolean isBuffering) {
    UI.post(() -> {
      if (destroyed || track == null || track.chatId != chatId || track.id != messageId) return;
      this.progress = progress;
      if (position >= 0) this.position = position;
      if (totalDuration > 0) this.duration = totalDuration;
      strip.invalidate();
      slider.invalidate();
      updateTimes();
    });
  }

  private static int durationOf (TdApi.Message m) {
    switch (m.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR: return ((TdApi.MessageAudio) m.content).audio.duration;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return ((TdApi.MessageVoiceNote) m.content).voiceNote.duration;
    }
    return 0;
  }

  private void bindTrack (TdApi.Message m) {
    boolean voice = m.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR;
    String name = null, by = null;
    if (m.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR) {
      TdApi.Audio a = ((TdApi.MessageAudio) m.content).audio;
      name = !TextUtils.isEmpty(a.title) ? a.title : a.fileName;
      by = a.performer;
    }
    String sender = tdlib.senderName(m, true, false);
    if (TextUtils.isEmpty(name)) name = voice ? sender : Lang.getString(R.string.Tgx101InPlayerTrack);
    if (TextUtils.isEmpty(by)) by = voice ? Lang.getString(R.string.Tgx101InPlayerVoice) : sender;
    title.setText(name);
    subtitle.setText(by);
    cover.setImageResource(voice ? R.drawable.baseline_mic_24 : R.drawable.baseline_music_note_24);
    cover.setColorFilter(Theme.getColor(ColorId.textNeutral));
    GradientDrawable coverBg = new GradientDrawable();
    coverBg.setColor(ColorUtils.alphaColor(.14f, Theme.getColor(ColorId.textNeutral)));
    coverBg.setCornerRadius(Screen.dp(14f));
    cover.setBackground(coverBg);
    updateTimes();
  }

  private void updatePlay () {
    boolean playing = state == TGPlayerController.STATE_PLAYING || state == TGPlayerController.STATE_LOADING;
    play.setImageResource(playing ? R.drawable.baseline_pause_24 : R.drawable.baseline_play_arrow_36_white);
    play.setContentDescription(Lang.getString(playing ? R.string.Tgx101InPlayerPause : R.string.Tgx101InPlayerPlay));
  }

  private static final int[] SPEEDS = {100, 125, 150, 200};

  private void updateSpeed () {
    int s = tdlib.context().player().getSpeed();
    if (s <= 0) s = 100;
    String text = (s % 100 == 0 ? String.valueOf(s / 100) : String.valueOf(s / 100f)) + "×";
    speedView.setText(text);
    speedView.setTextColor(s == 100 ? Theme.textDecentColor() : Theme.getColor(ColorId.textNeutral));
  }

  private void nextSpeed () {
    int s = tdlib.context().player().getSpeed();
    int next = SPEEDS[0];
    for (int i = 0; i < SPEEDS.length; i++) {
      if (SPEEDS[i] > s) { next = SPEEDS[i]; break; }
    }
    if (s >= SPEEDS[SPEEDS.length - 1]) next = SPEEDS[0];
    tdlib.context().player().setPlaybackSpeed(next);
    Tgx101Diag.mark("input player: speed " + next);
    updateSpeed();
  }

  private void updateTimes () {
    long pos = position > 0 ? position : (long) (progress * duration);
    timeNow.setText(Strings.buildDuration(Math.max(0, pos / 1000)));
    timeLeft.setText(duration > 0 ? "−" + Strings.buildDuration(Math.max(0, (duration - pos) / 1000)) : "");
  }

  // The heart: straight into Saved Messages, a second tap takes it back

  private boolean matches (TdApi.Message saved, TdApi.Message original) {
    return saved.forwardInfo != null && saved.forwardInfo.source != null && saved.forwardInfo.source.chatId == original.chatId && saved.forwardInfo.source.messageId == original.id;
  }

  private void checkSaved (TdApi.Message m) {
    if (m.chatId == tdlib.selfChatId()) {
      saved = true;
      updateHeart(false);
      return;
    }
    updateHeart(false);
    tdlib.client().send(new TdApi.GetChatHistory(tdlib.selfChatId(), 0, 0, 50, false), result -> UI.post(() -> {
      if (destroyed || track != m || !(result instanceof TdApi.Messages)) return;
      for (TdApi.Message s : ((TdApi.Messages) result).messages) {
        if (s != null && matches(s, m)) {
          saved = true;
          updateHeart(false);
          return;
        }
      }
    }));
  }

  private void toggleSaved () {
    final TdApi.Message m = track;
    if (m == null || savingBusy || m.chatId == tdlib.selfChatId()) return;
    savingBusy = true;
    if (!saved) {
      saved = true;
      updateHeart(true);
      Tgx101Diag.mark("input player: saved to Saved Messages");
      tdlib.client().send(new TdApi.ForwardMessages(tdlib.selfChatId(), null, m.chatId, new long[] {m.id}, null, false, false), result -> UI.post(() -> {
        savingBusy = false;
        if (result instanceof TdApi.Error) {
          saved = false;
          updateHeart(false);
          UI.showError(result);
        } else {
          UI.showToast(R.string.Tgx101InPlayerSaved, android.widget.Toast.LENGTH_SHORT);
        }
      }));
    } else {
      saved = false;
      updateHeart(true);
      Tgx101Diag.mark("input player: taken out of Saved Messages");
      tdlib.client().send(new TdApi.GetChatHistory(tdlib.selfChatId(), 0, 0, 50, false), result -> {
        long found = 0;
        if (result instanceof TdApi.Messages) {
          for (TdApi.Message s : ((TdApi.Messages) result).messages) {
            if (s != null && matches(s, m)) { found = s.id; break; }
          }
        }
        if (found != 0) {
          tdlib.client().send(new TdApi.DeleteMessages(tdlib.selfChatId(), new long[] {found}, true), ignored -> UI.post(() -> {
            savingBusy = false;
            UI.showToast(R.string.Tgx101InPlayerUnsaved, android.widget.Toast.LENGTH_SHORT);
          }));
        } else {
          UI.post(() -> savingBusy = false);
        }
      });
    }
  }

  private void updateHeart (boolean animated) {
    heart.setImageResource(saved ? R.drawable.baseline_favorite_24 : R.drawable.tgx101_heart_outline_24);
    heart.setColorFilter(saved ? 0xfff0685a : Theme.textAccentColor());
    heart.setContentDescription(Lang.getString(saved ? R.string.Tgx101InPlayerUnsave : R.string.Tgx101InPlayerSave));
    GradientDrawable d = new GradientDrawable();
    d.setColor(saved ? 0x29e5533f : ColorUtils.alphaColor(.06f, Theme.textAccentColor()));
    d.setCornerRadius(Screen.dp(14f));
    heart.setBackground(d);
    if (animated && saved) {
      // a springy pulse 1 → 1.25 → 1
      heart.animate().cancel();
      heart.setScaleX(1f);
      heart.setScaleY(1f);
      heart.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110).withEndAction(() ->
        heart.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start()).start();
      heart.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
    }
  }

  // Out and back

  public boolean isExpanded () {
    return expand > 0f;
  }

  private boolean targetOut; // where the player is going — a repeated request on the way changes nothing

  public void setExpanded (boolean out, boolean animated) {
    // (user 2026-11-13 «смотри как оно сворачивается… рывки», log 20:50:53: «away» six times 8 ms apart) every scrolled
    // frame started the putting-away over from the start
    if (out == targetOut && (expandAnimator != null || expand == (out ? 1f : 0f)) && !dragging) return;
    targetOut = out;
    if (expandAnimator != null) {
      expandAnimator.cancel();
      expandAnimator = null;
    }
    float to = out ? 1f : 0f;
    if (out) {
      scrolledWhileOut = 0;
      panel.setVisibility(View.VISIBLE);
    }
    Tgx101Diag.mark("input player: " + (out ? "out" : "away"));
    Tgx101Bird.setMadeRoom(c.context(), out);
    if (!animated || expand == to) {
      expand = to;
      place();
      return;
    }
    expandAnimator = ValueAnimator.ofFloat(expand, to);
    // (video 2026-11-13 21:29: putting it away took ~75 ms and began at full speed — a jerk) away: 240 ms, easing in and out
    expandAnimator.setDuration(out ? 280 : 240);
    expandAnimator.setInterpolator(out ? new android.view.animation.OvershootInterpolator(.8f) : new androidx.interpolator.view.animation.FastOutSlowInInterpolator());
    expandAnimator.addUpdateListener(a -> {
      expand = Math.max(0f, (float) a.getAnimatedValue());
      place();
      strip.invalidate();
    });
    expandAnimator.start();
  }

  private int scrolledWhileOut;

  /** the chat scrolled: ~72 dp of it (or a fling) puts the player away, a short touch or a small scroll doesn't */
  public void onChatScrolled (int dy) {
    if (!targetOut || expand <= 0f || dragging) return;
    scrolledWhileOut += Math.abs(dy);
    if (scrolledWhileOut > Screen.dp(SCROLL_HIDE_DP)) {
      setExpanded(false, true);
    }
  }

  // The field's swipe up

  private float fieldDownX, fieldDownY;
  private boolean fieldDragging;

  /** from the field's wrap: true takes the touch — a swipe up while something plays */
  public boolean interceptField (MotionEvent e) {
    if (track == null || state == TGPlayerController.STATE_NONE || destroyed) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        fieldDownX = e.getRawX();
        fieldDownY = e.getRawY();
        fieldDragging = false;
        return false;
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getRawX() - fieldDownX, dy = e.getRawY() - fieldDownY;
        int slop = ViewConfiguration.get(c.context()).getScaledTouchSlop();
        if (!fieldDragging && dy < -slop * 1.5f && Math.abs(dy) > Math.abs(dx) * 2f && expand == 0f) {
          fieldDragging = true;
          startDrag(e.getRawY());
          return true;
        }
        return false;
      }
    }
    return false;
  }

  public boolean onFieldTouch (MotionEvent e) {
    if (!fieldDragging) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE:
        drag(e.getRawY());
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        fieldDragging = false;
        endDrag(e.getActionMasked() == MotionEvent.ACTION_UP);
        return true;
    }
    return true;
  }

  private boolean dragging;
  private float dragStartY, dragStartExpand;
  private float lastY, velocityY; // px per second, up is negative
  private long lastT;

  private void startDrag (float rawY) {
    if (expandAnimator != null) {
      expandAnimator.cancel();
      expandAnimator = null;
    }
    dragging = true;
    dragStartY = rawY;
    dragStartExpand = expand;
    panel.setVisibility(View.VISIBLE);
    if (expand == 0f) Tgx101Bird.setMadeRoom(c.context(), true);
    lastY = rawY;
    lastT = android.os.SystemClock.uptimeMillis();
    velocityY = 0;
  }

  private void drag (float rawY) {
    if (!dragging) return;
    int h = Math.max(1, panel.getHeight() > 0 ? panel.getHeight() : Screen.dp(230f));
    float raw = dragStartExpand + (dragStartY - rawY) / h;
    // a little resistance past the top
    expand = raw > 1f ? 1f + (raw - 1f) * .15f : Math.max(0f, raw);
    long now = android.os.SystemClock.uptimeMillis();
    if (now > lastT) {
      float v = (rawY - lastY) * 1000f / (now - lastT);
      velocityY = velocityY * .4f + v * .6f;
      lastY = rawY;
      lastT = now;
    }
    place();
  }

  private void endDrag (boolean released) {
    if (!dragging) return;
    dragging = false;
    float vy = android.os.SystemClock.uptimeMillis() - lastT > 120 ? 0 : velocityY; // a finger that stopped flings nothing
    boolean out = released && (vy < -Screen.dp(900f) || (vy < Screen.dp(900f) && expand > .4f));
    setExpanded(out, true);
  }

  private class DragListener implements View.OnTouchListener {
    private final boolean isStrip;
    private float downY;
    private boolean moved;

    DragListener (boolean isStrip) {
      this.isStrip = isStrip;
    }

    @Override
    public boolean onTouch (View v, MotionEvent e) {
      int slop = ViewConfiguration.get(c.context()).getScaledTouchSlop();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          if (isStrip && (track == null || expand > 0f)) return false;
          downY = e.getRawY();
          moved = false;
          return true;
        case MotionEvent.ACTION_MOVE:
          if (!moved && Math.abs(e.getRawY() - downY) > slop) {
            moved = true;
            startDrag(e.getRawY());
          }
          if (moved) drag(e.getRawY());
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (moved) {
            endDrag(e.getActionMasked() == MotionEvent.ACTION_UP);
          } else if (isStrip && e.getActionMasked() == MotionEvent.ACTION_UP) {
            setExpanded(true, true); // a tap on the line
          }
          return true;
      }
      return false;
    }
  }

  // Views

  /** the line: 2 dp along the field's top edge, the played part in the accent colour with a soft glow at its end */
  private class StripView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    StripView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (track == null) return;
      float alpha = 1f - Math.min(1f, expand * 2f); // flows into the slider as the player comes out
      if (alpha <= 0f) return;
      boolean paused = state != TGPlayerController.STATE_PLAYING;
      int accent = Theme.getColor(ColorId.textNeutral);
      float lineH = Screen.dp(2f);
      float y = getHeight() / 2f - lineH / 2f;
      int w = getWidth();
      paint.setShader(null);
      paint.setColor(ColorUtils.alphaColor(alpha * .14f, Theme.textAccentColor()));
      c.drawRect(0, y, w, y + lineH, paint);
      float x = w * Math.max(0f, Math.min(1f, progress));
      paint.setColor(ColorUtils.alphaColor(alpha * (paused ? .4f : 1f), accent));
      c.drawRect(0, y, x, y + lineH, paint);
      if (!paused && x > 0) {
        float glow = Screen.dp(8f);
        paint.setShader(new android.graphics.RadialGradient(x, y + lineH / 2f, glow, ColorUtils.alphaColor(alpha * .55f, accent), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(x, y + lineH / 2f, glow, paint);
        paint.setShader(null);
      }
    }
  }

  /** the player's slider: drag to seek, the position is set when the finger lets go */
  private class SliderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float dragProgress = -1f;

    SliderView (Context context) {
      super(context);
    }

    private float left () { return Screen.dp(8f); }
    private float right () { return getWidth() - Screen.dp(8f); }

    @Override
    protected void onDraw (Canvas c) {
      float p = dragProgress >= 0f ? dragProgress : progress;
      float cy = getHeight() / 2f, h = Screen.dp(4f);
      float l = left(), r = right(), x = l + (r - l) * Math.max(0f, Math.min(1f, p));
      paint.setColor(ColorUtils.alphaColor(.12f, Theme.textAccentColor()));
      rect.set(l, cy - h / 2, r, cy + h / 2);
      c.drawRoundRect(rect, h / 2, h / 2, paint);
      paint.setColor(Theme.getColor(ColorId.textNeutral));
      rect.set(l, cy - h / 2, x, cy + h / 2);
      c.drawRoundRect(rect, h / 2, h / 2, paint);
      paint.setColor(0xffffffff);
      c.drawCircle(x, cy, Screen.dp(dragProgress >= 0f ? 10f : 8f), paint);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (track == null) return false;
      float l = left(), r = right();
      float p = Math.max(0f, Math.min(1f, (e.getX() - l) / Math.max(1f, r - l)));
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          getParent().requestDisallowInterceptTouchEvent(true);
          dragProgress = p;
          invalidate();
          return true;
        case MotionEvent.ACTION_MOVE:
          dragProgress = p;
          invalidate();
          return true;
        case MotionEvent.ACTION_UP:
          if (track != null && duration > 0) {
            long to = (long) (p * duration);
            Tgx101Diag.mark("input player: seek to " + to / 1000 + " s");
            tdlib.context().player().seekTrack(track, to);
            progress = p;
            position = to;
            updateTimes();
          }
          // fall through
        case MotionEvent.ACTION_CANCEL:
          dragProgress = -1f;
          invalidate();
          return true;
      }
      return false;
    }
  }

  private static TextView text (Context context, float size, boolean bold) {
    TextView t = new TextView(context);
    t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    t.setTextColor(bold ? Theme.textAccentColor() : Theme.textDecentColor());
    if (bold) t.setTypeface(Fonts.getRobotoMedium());
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    return t;
  }

  private static ImageView iconButton (Context context, int icon, int size) {
    ImageView v = new ImageView(context);
    v.setImageResource(icon);
    v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    int pad = Math.max(0, (size - Screen.dp(26f)) / 2);
    v.setPadding(pad, pad, pad, pad);
    v.setColorFilter(Theme.textAccentColor());
    v.setClickable(true);
    return v;
  }

  private static void tonal (View v, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(ColorUtils.alphaColor(.06f, Theme.textAccentColor()));
    d.setCornerRadius(radius);
    v.setBackground(d);
  }

  private static LinearLayout.LayoutParams controlParams (int dp) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(dp), Screen.dp(dp));
    p.leftMargin = p.rightMargin = Screen.dp(9f);
    return p;
  }

  public void destroy () {
    if (destroyed) return;
    destroyed = true;
    if (expandAnimator != null) expandAnimator.cancel();
    host.getViewTreeObserver().removeOnPreDrawListener(placer);
    TGPlayerController player = tdlib.context().player();
    if (track != null) player.removeTrackListener(tdlib, track, this);
    player.removeTrackChangeListener(this);
    host.removeView(panel);
    host.removeView(strip);
    if (expand > 0f) Tgx101Bird.setMadeRoom(c.context(), false);
  }
}
