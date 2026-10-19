/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/08/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.disposable.DisposableMediaViewController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.RoundVideoController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.io.File;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class TGMessageVideo extends TGMessage implements FileProgressComponent.SimpleListener, TGPlayerController.TrackListener, Tgx101Transcription.Listener {
  private TdApi.VideoNote videoNote;
  private boolean notViewed;

  public static final int RESIZE_DEFAULT_DURATION = 225;
  public static final Interpolator RESIZE_INTERPOLATOR = AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR;  // AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR
  private static final int FULL_SIZE_ANIMATOR_ID = 10001;

  private final BoolAnimator isFullSizeAnimator = new BoolAnimator(FULL_SIZE_ANIMATOR_ID, this, RESIZE_INTERPOLATOR, RESIZE_DEFAULT_DURATION);
  private long fullSizeAnimatorDuration = RESIZE_DEFAULT_DURATION;

  private FileProgressComponent fileProgress;
  private ImageFile miniThumbnail, previewFile;
  private GifFile mutedVideoFile;

  private int sourceDuration;

  private int duration;
  private String durationStr;
  private float durationWidth;

  public TGMessageVideo (MessagesManager context, TdApi.Message msg, TdApi.VideoNote videoNote, boolean isViewed) {
    super(context, msg);
    setVideoNote(videoNote);
    setNotViewed(!isViewed, false);
    Tgx101Transcription.addListener(this); // TGx101
  }

  private void setVideoNote (TdApi.VideoNote videoNote) {
    this.videoNote = videoNote;
    this.fileProgress = new FileProgressComponent(context(), tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE, true, getChatId(), getId());
    this.fileProgress.setSimpleListener(this);
    this.fileProgress.setViewProvider(overlayViews);
    this.fileProgress.setFile(videoNote.video, getMessage());
    if (TD.isSelfDestructTypeImmediately(getMessage())) {
      this.fileProgress.setIgnorePlayPauseClicks(true);
      this.fileProgress.setDownloadedIconRes(R.drawable.baseline_hot_once_24);
    }

    if (videoNote.minithumbnail != null) {
      this.miniThumbnail = new ImageFileLocal(videoNote.minithumbnail);
    }
    this.previewFile = newPreviewFile(tdlib, videoNote);

    this.mutedVideoFile = new GifFile(tdlib, videoNote.video, GifFile.TYPE_MPEG4);
    this.mutedVideoFile.setIsRoundVideo(msg.chatId, msg.id);
    this.mutedVideoFile.setRequestedSize(Screen.dp(200f));
    if (!Settings.instance().needAutoplayGIFs()) {
      this.mutedVideoFile.setIsStill(true);
    }

    this.sourceDuration = videoNote.duration;

    setDuration(videoNote.duration);
  }

  public static ImageFile newPreviewFile (Tdlib tdlib, TdApi.VideoNote videoNote) {
    if (videoNote != null && videoNote.thumbnail != null) {
      ImageFile imageFile = TD.toImageFile(tdlib, videoNote.thumbnail);
      if (imageFile != null) {
        imageFile.setSize(Screen.dp(200f));
      }
      return imageFile;
    }
    return null;
  }

  @Override
  protected void onMessageAttachStateChange (boolean isAttached) {
    checkTrackListenerAttached();
    if (!isAttached && isFullSizeAnimator.isAnimating()) {
      isFullSizeAnimator.setValue(isFullSizeAnimator.getValue(), false);
    }
  }

  private void checkTrackListenerAttached () {
    setTrackListenerAttached((isUnmuted || isAttachedToView()) && !isDestroyed());
  }

  private boolean isTrackListenerAttached;

  private void setTrackListenerAttached (boolean attach) {
    if (isTrackListenerAttached != attach) {
      isTrackListenerAttached = attach;
      if (attach) {
        tdlib.context().player().addTrackListener(tdlib, getMessage(), this);
      } else {
        tdlib.context().player().removeTrackListener(tdlib, getMessage(), this);
      }
    }
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    setNotViewed(!((TdApi.MessageVideoNote) newContent).isViewed, true);
    if (TD.isSelfDestructTypeImmediately(message)) {
      if (newContent != null && newContent.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR) {
        final var videoNote = ((TdApi.MessageVideoNote) newContent).videoNote;
        if (videoNote.minithumbnail != null) {
          this.miniThumbnail = new ImageFileLocal(videoNote.minithumbnail);
          invalidatePreviewReceiver();
        }
      }
    }

    return false;
  }

  @Override
  protected void onMessageContentOpened (long messageId) {
    setNotViewed(false, true);
  }

  private void setDuration (int duration) {
    if (StringUtils.isEmpty(durationStr) || this.duration != duration) {
      this.duration = duration;
      this.durationStr = Strings.buildDuration(duration);
      this.durationWidth = U.measureText(durationStr, useBubbles() ? mTimeBubble() : mTime(false));
      invalidateOverlay();
    }
  }

  public int getVideoMessageTargetHeight (boolean isExpanded) {
    return height - videoSize + (isExpanded ? + videoFullSize : videoSmallSize);
  }

  // View

  private static final int ANIMATOR_VIEW = 0;
  private FactorAnimator viewAnimator;
  private float viewFactor;

  private void setNotViewed (boolean isViewed, boolean animated) {
    if (this.notViewed != isViewed && isOutgoing()) {
      this.notViewed = isViewed;
      final float toFactor = isViewed ? 1f : 0f;
      if (animated && overlayViews.hasAnyTargetToInvalidate()) {
        if (viewAnimator == null) {
          viewAnimator = new FactorAnimator(ANIMATOR_VIEW, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.viewFactor);
        }
        viewAnimator.animateTo(toFactor);
      } else {
        if (viewAnimator != null) {
          viewAnimator.forceFactor(toFactor);
        }
        setViewFactor(toFactor);
      }
    }
  }

  private void setViewFactor (float factor) {
    if (this.viewFactor != factor) {
      this.viewFactor = factor;
      invalidateOverlay();
    }
  }

  @Override
  protected final void onChildFactorChanged (int id, float factor, float fraction) {
    switch (id) {
      case ANIMATOR_VIEW: {
        setViewFactor(factor);
        break;
      }
      case ANIMATOR_UNMUTE: {
        setUnmuteFactor(factor);
        break;
      }
      case TEXT_ANIMATOR_ID: {
        if (isLayoutBuilt()) {
          rebuildContent();
          requestLayout();
          invalidateOverlay();
          invalidate();
        }
        break;
      }
      case FULL_SIZE_ANIMATOR_ID: {
        videoSize = MathUtils.fromTo(videoSmallSize, videoFullSize, isFullSizeAnimator.getFloatValue());
        if (isLayoutBuilt()) {
          rebuildContent();
          requestLayout();
          invalidateOverlay();
          invalidate();
        }
        break;
      }
    }
  }

  @Override
  public int getForwardLineColor () {
    return ColorUtils.alphaColor(1f - isFullSizeAnimator.getFloatValue(), super.getForwardLineColor());
  }

  @Override
  public int getForwardAuthorNameLeft () {
    return (int) (super.getForwardAuthorNameLeft() - Screen.dp(11f) * isFullSizeAnimator.getFloatValue());
  }

  public void setFullSizeAnimatorDuration (long fullSizeAnimatorDuration) {
    this.fullSizeAnimatorDuration = fullSizeAnimatorDuration;
  }

  @Override
  protected void onChildFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id ==  FULL_SIZE_ANIMATOR_ID) {
      fullSizeAnimatorDuration = RESIZE_DEFAULT_DURATION;
    }
    if (id == TEXT_ANIMATOR_ID && finalFactor == 0f) {
      rebuildAndUpdateContent(); // back to the circle entirely
    }
  }

  // File utils

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    fileProgress.downloadAutomatically(type);
  }

  @Override
  public boolean onPlayPauseClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    return onClick(context, view, file, messageId);
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    if (Config.ROUND_VIDEOS_PLAYBACK_SUPPORTED) {
      if (view.getParent() instanceof MessageViewGroup) {
        if (TD.isSelfDestructTypeImmediately(getMessage())) {
          DisposableMediaViewController.openMediaOrShowTooltip(view, this, (targetView, outRect) -> fileProgress.toRect(outRect));
        } else {
          tdlib.context().player().playPauseMessage(tdlib, msg, manager);
        }
      }
    } else {
      U.openFile(manager.controller(), "video.mp4", new File(file.local.path), "video/mp4", 0);
      readContent();
    }
    return true;
  }

  private static final int ANIMATOR_UNMUTE = 1;
  private boolean isUnmuted;
  private float unmuteFactor;
  private FactorAnimator unmuteAnimator;

  private boolean hasValidAttachedView () {
    for (View v : currentViews) {
      if (Views.isValid(v)) {
        if (v instanceof MessageView) {
          if (!((MessageView) v).isAttached()) {
            continue;
          }
        }

        return true;
      }
    }
    return false;
  }

  private float scrollCompensationFactor;

  public float getScrollCompensationFactor () {
    if (tgx101TextAnimator != null && tgx101TextAnimator.isAnimating() && !isFullSizeAnimator.isAnimating()) return 0f;
    return scrollCompensationFactor;
  }

  private void setUnmuted (boolean unmuted) {
    if (this.isUnmuted != unmuted) {
      this.isUnmuted = unmuted;

      scrollCompensationFactor = 0.5f;
      if (!unmuted) {
        MessagesController c = messagesController();
        if (c != null) {
          RecyclerView recyclerView = c.getMessagesView();
          if (recyclerView != null) {
            final int range = recyclerView.computeVerticalScrollRange();
            final int offset = recyclerView.computeVerticalScrollOffset();
            final int extent = recyclerView.computeVerticalScrollExtent();
            if (range != extent) {
              scrollCompensationFactor = MathUtils.clamp((offset) / (float)(range - extent));
            } else {
              scrollCompensationFactor = 0.5f;
            }
          }
        }
      }

      if (!unmuted) tgx101SmallPlay = false; // the playback ended: the next one is the usual one again
      this.isFullSizeAnimator.setDuration(fullSizeAnimatorDuration);
      this.isFullSizeAnimator.setValue(unmuted && !tgx101SmallPlay, UI.inUiThread() && hasValidAttachedView());
      final float toFactor = unmuted ? 1f : 0f;
      boolean animated = currentViews.hasAnyTargetToInvalidate();
      if (animated) {
        if (unmuteAnimator == null) {
          unmuteAnimator = new FactorAnimator(ANIMATOR_UNMUTE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.unmuteFactor);
        }
        unmuteAnimator.animateTo(toFactor);
      } else {
        if (unmuteAnimator != null) {
          unmuteAnimator.forceFactor(toFactor);
        }
        setUnmuteFactor(toFactor);
      }
    }
  }

  private void setUnmuteFactor (float factor) {
    if (this.unmuteFactor != factor) {
      this.unmuteFactor = factor;
      invalidateOverlay();
    }
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    boolean unmuted = state != TGPlayerController.STATE_NONE;
    setUnmuted(unmuted);
    checkTrackListenerAttached();
    if (!unmuted) {
      setDuration(sourceDuration);
    }
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (isUnmuted) {
      setDuration(playPosition != -1 && playDuration != -1 ? Math.min(sourceDuration, U.calculateRemainingSeconds(playDuration - playPosition)) : sourceDuration);
    }
  }

  // View utils

  private int videoSmallSize;
  private int videoSize;
  private int videoFullSize;

  @Override
  protected void buildContent (int maxWidth) {
    videoSmallSize = Math.min(Screen.dp(200f), maxWidth);
    videoFullSize = getVideoSize();
    videoSize = MathUtils.fromTo(videoSmallSize, videoFullSize, isFullSizeAnimator.getFloatValue());
    if (tgx101TextMode()) tgx101BuildText(maxWidth);
  }

  // TGx101 (approved mockup «Кружок в текст», user 2026-10-19; test builds first): one tap on «A» turns the round video into
  // a bubble like a voice message's — a small circle with ▶, «Video message · 0:13», «A» on the right — and the text right
  // away in a plate under them; «A» again brings the big circle back, a tap on the small circle plays it

  private static final java.util.Set<String> tgx101TextModes = new java.util.HashSet<>();
  private static final float TM_MINI = 40f, TM_GAP = 6f, TM_PAD = 9f, TM_A = 28f;
  private @androidx.annotation.Nullable android.text.StaticLayout tgx101Text;
  private int tgx101TextW, tgx101TextH;
  private @androidx.annotation.Nullable String tgx101TextKey;
  private boolean tgx101ACaught, tgx101MiniCaught;
  private final RectF tgx101ARect = new RectF(), tgx101MiniRect = new RectF();

  private String tgx101Key () {
    return msg.chatId + "_" + msg.id;
  }

  // (user 2026-10-19 «переключение из текста в кружок очень резкое, посмотри как в экстере»): one ~0.3 s move — the circle
  // shrinks into the corner while the bubble grows round it and the text comes up; back the same way
  private static final int TEXT_ANIMATOR_ID = 10002;
  private @androidx.annotation.Nullable BoolAnimator tgx101TextAnimator;

  private BoolAnimator tgx101TextAnimator () {
    if (tgx101TextAnimator == null) {
      tgx101TextAnimator = new BoolAnimator(TEXT_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 450L, // exteraGram takes about twice as long as 280 ms (recorded side by side 2026-10-19)
        tgx101TextModes.contains(tgx101Key()) && !TD.isSelfDestructTypeImmediately(getMessage()));
    }
    return tgx101TextAnimator;
  }

  /** 0 — the circle, 1 — the bubble with the text */
  private float tgx101TextFactor () {
    return tgx101TextAnimator().getFloatValue();
  }

  /** laid out as the bubble (fully or on the way) */
  private boolean tgx101TextMode () {
    return tgx101TextFactor() > 0f || tgx101TextAnimator().getValue();
  }

  /** the bubble with the text is what's asked for (touches go to it) */
  private boolean tgx101TextTarget () {
    return tgx101TextAnimator().getValue();
  }

  private void tgx101SetTextMode (boolean on) {
    if (on) tgx101TextModes.add(tgx101Key()); else tgx101TextModes.remove(tgx101Key());
    boolean animated = isLayoutBuilt() && hasValidAttachedView() && UI.inUiThread();
    if (on) tgx101TextAnimator().setValue(false, false); // starts from the circle with the text laid out
    tgx101TextAnimator().setValue(on, animated);
    if (!animated) {
      rebuildAndUpdateContent();
      invalidate();
    } else if (on) {
      rebuildContent(); // the text's size is known before the first frame
    }
  }

  private static int tgx101Lerp (int from, int to, float f) {
    return Math.round(from + (to - from) * f);
  }

  // (user 2026-10-20) the message text's own size (Settings → text size), as a voice message's transcription — 14 dp
  // was smaller; and its own paint: the shared one handed back by Paints could be another object after re-entering
  // the chat, so the colour set before drawing missed the layout's paint and the text came out black
  private final android.text.TextPaint tgx101Paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);

  private android.text.TextPaint tgx101TextPaint () {
    tgx101Paint.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular());
    tgx101Paint.setTextSize(getTextStyleProvider().getTextSizeInPixels());
    tgx101Paint.setColor(getTextColor());
    return tgx101Paint;
  }

  private void tgx101BuildText (int maxWidth) {
    Tgx101Transcription.Result result = Tgx101Transcription.get(tdlib, msg);
    String text;
    if (result == null || result.state == Tgx101Transcription.STATE_NONE || (result.state == Tgx101Transcription.STATE_PENDING && android.text.TextUtils.isEmpty(result.text))) {
      text = "…";
    } else {
      text = result.text != null ? result.text : "";
    }
    android.text.TextPaint paint = tgx101TextPaint();
    // (user's video 2026-10-20 «рывки»): this ran on every frame of the move — a new text layout each time stalled the
    // list for ~6 frames midway; the same text at the same width and size is laid out once
    String key = text + '\u0000' + maxWidth + '\u0000' + paint.getTextSize();
    if (tgx101Text != null && key.equals(tgx101TextKey)) return;
    tgx101TextKey = key;
    int maxText = Math.max(Screen.dp(120f), Math.min(maxWidth, Screen.dp(280f)) - Screen.dp(TM_PAD) * 2);
    int desired = (int) Math.ceil(android.text.Layout.getDesiredWidth(text, paint));
    int textWidth = Math.min(maxText, Math.max(desired, Screen.dp(170f)));
    tgx101Text = new android.text.StaticLayout(text, paint, textWidth, android.text.Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false);
    tgx101TextW = textWidth + Screen.dp(TM_PAD) * 2;
    tgx101TextH = Screen.dp(TM_MINI) + Screen.dp(TM_GAP) + tgx101Text.getHeight() + Screen.dp(TM_PAD) * 2 - Screen.dp(4f);
  }

  private void tgx101DrawText (Canvas c, int x, int y, Receiver preview) {
    float f = tgx101TextFactor();
    int mini = Screen.dp(TM_MINI);
    int size = tgx101Lerp(videoSize, mini, f); // the circle shrinks into the corner
    tgx101MiniRect.set(x, y, x + mini, y + mini);
    // (user's video 2026-10-19 vs exteraGram): the text, the title and «A» come up only in the second half, once the
    // circle has mostly shrunk — drawn under it, they no longer show through the big circle at the start
    float partsAlpha = MathUtils.clamp((f - .45f) / .55f);
    if (partsAlpha >= 1f) {
      tgx101DrawTextParts(c, x, y);
    } else if (partsAlpha > 0f) {
      int save = c.saveLayerAlpha(x, y, x + Math.max(tgx101TextW, size), y + Math.max(tgx101TextH, size), (int) (255 * partsAlpha));
      tgx101DrawTextParts(c, x, y);
      c.restoreToCount(save);
    }
    preview.setBounds(x, y, x + size, y + size);
    preview.setRadius(size / 2);
    if (preview.needPlaceholder()) preview.drawPlaceholderRounded(c, size / 2f);
    preview.draw(c);
  }

  private void tgx101DrawTextParts (Canvas c, int x, int y) {
    int mini = Screen.dp(TM_MINI);
    float cx = x + mini / 2f, cy = y + mini / 2f;
    c.drawCircle(cx, cy, mini / 2f, Paints.fillingPaint(0x33000000));
    android.graphics.Path play = new android.graphics.Path();
    play.moveTo(cx - Screen.dp(4f), cy - Screen.dp(6.5f));
    play.lineTo(cx + Screen.dp(7f), cy);
    play.lineTo(cx - Screen.dp(4f), cy + Screen.dp(6.5f));
    play.close();
    c.drawPath(play, Paints.fillingPaint(0xffffffff));

    int accent = getTextLinkColor();
    int a = Screen.dp(TM_A);
    float ax = x + tgx101TextW - a, ay = cy - a / 2f;
    tgx101ARect.set(ax, ay, ax + a, ay + a);
    c.drawRoundRect(tgx101ARect, Screen.dp(8f), Screen.dp(8f), Paints.fillingPaint(ColorUtils.alphaColor(.14f, accent)));
    android.text.TextPaint aPaint = Paints.getBoldPaint14(false, accent);
    c.drawText("A", tgx101ARect.centerX() - aPaint.measureText("A") / 2f, tgx101ARect.centerY() + Screen.dp(5f), aPaint);

    android.text.TextPaint titlePaint = Paints.getMediumTextPaint(13f, getTextColor(), false);
    float tx = x + mini + Screen.dp(9f);
    float maxTitle = ax - Screen.dp(8f) - tx;
    CharSequence title = android.text.TextUtils.ellipsize(org.thunderdog.challegram.core.Lang.getString(R.string.ChatContentRoundVideo), titlePaint, Math.max(0, maxTitle), android.text.TextUtils.TruncateAt.END);
    c.drawText(title, 0, title.length(), tx, cy - Screen.dp(2f), titlePaint);
    android.text.TextPaint subPaint = Paints.getRegularTextPaint(12.5f, getDecentColor());
    c.drawText(Strings.buildDuration(sourceDuration), tx, cy + Screen.dp(14f), subPaint);

    if (tgx101Text != null) {
      float top = y + mini + Screen.dp(TM_GAP);
      RectF plate = Paints.getRectF();
      plate.set(x, top, x + tgx101TextW, top + tgx101Text.getHeight() + Screen.dp(TM_PAD) * 2 - Screen.dp(4f));
      c.drawRoundRect(plate, Screen.dp(10f), Screen.dp(10f), Paints.fillingPaint(ColorUtils.alphaColor(.12f, accent)));
      tgx101TextPaint().setColor(getTextColor());
      int save = Views.save(c);
      c.translate(x + Screen.dp(TM_PAD), top + Screen.dp(TM_PAD) - Screen.dp(2f));
      tgx101Text.draw(c);
      Views.restore(c, save);
    }
  }

  private boolean tgx101OnTextTouch (MessageView view, MotionEvent e) {
    float x = e.getX(), y = e.getY(), slop = Screen.dp(12f);
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN:
        tgx101ACaught = x >= tgx101ARect.left - slop && x <= tgx101ARect.right + slop && y >= tgx101ARect.top - slop && y <= tgx101ARect.bottom + slop;
        tgx101MiniCaught = !tgx101ACaught && tgx101MiniRect.contains(x, y);
        return tgx101ACaught || tgx101MiniCaught;
      case MotionEvent.ACTION_MOVE:
        return tgx101ACaught || tgx101MiniCaught;
      case MotionEvent.ACTION_UP: {
        boolean a = tgx101ACaught, mini = tgx101MiniCaught;
        tgx101ACaught = tgx101MiniCaught = false;
        if (!a && !mini) return false;
        performClickSoundFeedback();
        tgx101SetTextMode(false); // «A»: back to the big circle; the small circle: back to it and play
        org.thunderdog.challegram.Tgx101Diag.mark("round video: text mode off by " + (mini ? "the small circle (play)" : "«A»"));
        if (mini) {
          // once the big circle stands in its place again — the same way as a tap on it (user 2026-10-19: 120 ms was too soon)
          view.postDelayed(() -> {
            if (!isDestroyed()) {
              org.thunderdog.challegram.Tgx101Diag.mark("round video: play after the text mode");
              onClick(fileProgress, view, videoNote.video, msg.id);
            }
          }, 350);
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        boolean caught = tgx101ACaught || tgx101MiniCaught;
        tgx101ACaught = tgx101MiniCaught = false;
        return caught;
      }
    }
    return false;
  }

  // TGx101: "A" button on the round video starts the background transcription; when it's ready, the
  // text opens in a window (the round video player covers the whole content area, so no text under it)

  private static final float TRANSCRIBE_BUTTON_RADIUS = 13f;

  // TGx101 (user 2026-10-19 «как включить звук, но так чтобы не раскрывался кружок»): a tap on the 🔇 mark plays the
  // circle with sound where it is, small; a tap anywhere else plays it the usual way, enlarged
  private boolean tgx101SmallPlay, tgx101MuteCaught;
  private float tgx101MuteX, tgx101MuteY;
  private float transcribeButtonX, transcribeButtonY;
  private boolean transcribeCaught;

  @Override
  public void onTranscriptionChanged (long chatId, long messageId) {
    if (chatId != msg.chatId || messageId != msg.id || isDestroyed()) return;
    if (tgx101TextTarget() && !tgx101TextAnimator().isAnimating()) {
      rebuildAndUpdateContent(); // the text (or its progress) changed the bubble's size
    }
    invalidate(); // the "A" button state
  }

  public static int getVideoSize () {
    return Math.min(Screen.smallestSide() - Screen.dp(32), Screen.dp(640));
  }

  public boolean inSizeAnimation () {
    // (user 2026-10-20, option 1 «как в exteraGram»): the circle ↔ text move keeps what's above in place — the list is
    // scrolled by the whole height change (scroll compensation factor 0), only what's below moves
    return isFullSizeAnimator.isAnimating() || (tgx101TextAnimator != null && tgx101TextAnimator.isAnimating());
  }

  public float getPlayerScale () {
    return (float) videoSize / getVideoSize();
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return tgx101TextMode() ? tgx101Lerp(videoSize, Screen.dp(TM_MINI), tgx101TextFactor()) / 2 : videoSize / 2;
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    receiver.requestFile(miniThumbnail, previewFile);
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    receiver.requestFile(TD.isFileLoaded(videoNote.video) ? mutedVideoFile : null);
  }

  @Override
  protected int getContentWidth () {
    return tgx101TextMode() ? tgx101Lerp(videoSize, tgx101TextW, tgx101TextFactor()) : videoSize;
  }

  @Override
  protected int getContentHeight () {
    return tgx101TextMode() ? tgx101Lerp(videoSize, tgx101TextH, tgx101TextFactor()) : videoSize;
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  protected int getBubbleContentPadding () {
    return Screen.dp(2f);
  }

  @Override
  protected boolean useCircleBubble () {
    return !tgx101TextMode();
  }

  @Override
  protected boolean separateReplyFromBubble () {
    return true;
  }

  @Override
  protected boolean allowBubbleHorizontalExtend () {
    return false;
  }

  @Override
  protected boolean drawBubbleTimeOverContent () {
    // (user 2026-10-20 «время, дата при переходе прыгают и меняют цвет»): in the text mode the time is the bubble's own
    // from the very start of the move and rides with its corner — it switched style and place halfway
    return !tgx101TextMode();
  }

  @Override
  protected int getAbsolutelyRealRightContentEdge (View view, int timePartWidth) {
    if (tgx101TextMode()) return super.getAbsolutelyRealRightContentEdge(view, timePartWidth);
    // ImageReceiver receiver = view.getPreviewReceiver();
    int left = getContentX();
    int right = left + videoSize;
    if (isOutgoingBubble()) {
      return right - timePartWidth;
    } else {
      return Math.min(
        (left + right) / 2 + (int) ((float) ((double) (videoSize / 2) * Math.sin(Math.toRadians(45f))) + Screen.dp(6f)),
        width - (width - videoFullSize) / 2 - timePartWidth
      );
    }
  }

  @Override
  public boolean needViewGroup () {
    return true;
  }

  @Override
  protected void onMessageAttachedToOverlayView (@NonNull View view, boolean attached) {
    fileProgress.notifyInvalidateTargetsChanged();
  }

  @Override
  public int getContentX () {
    if (tgx101TextMode()) {
      // (user's video 2026-10-20 «рывки»): the circle's bubble and the text bubble place the content differently — the
      // circle jumped sideways when the move started and when it came back. The move goes from the circle's place
      int textX = super.getContentX();
      return tgx101CircleX == Integer.MIN_VALUE ? textX : tgx101Lerp(tgx101CircleX, textX, tgx101TextFactor());
    }
    int x = (int) MathUtils.fromTo(super.getContentX(), ((width - videoSize) / 2f), isFullSizeAnimator.getFloatValue());
    tgx101CircleX = x;
    return x;
  }

  private int tgx101CircleX = Integer.MIN_VALUE;

  @Override
  public int getChildrenLeft () {
    return getContentX();
  }

  // TGx101 (user 2026-10-19 «кружок до сих пор не рабочий»): the round video's own layer (MessageViewGroup) plays in the
  // small circle in the text mode — it took the whole bubble and covered the circle, «A» and the text
  @Override
  public int getChildrenWidth () {
    return tgx101TextMode() ? tgx101Lerp(videoSize, Screen.dp(TM_MINI), tgx101TextFactor()) : super.getChildrenWidth();
  }

  @Override
  public int getChildrenHeight () {
    return tgx101TextMode() ? tgx101Lerp(videoSize, Screen.dp(TM_MINI), tgx101TextFactor()) : super.getChildrenHeight();
  }

  @Override
  protected void drawBubble (Canvas c, Paint paint, boolean stroke, int padding) {
    if (tgx101TextMode()) {
      float f = tgx101TextFactor();
      float bubbleAlpha = MathUtils.clamp(f * 4f); // solid almost at once, then it grows round the circle as in exteraGram
      if (bubbleAlpha < 1f) { // the bubble comes up round the shrinking circle
        int save = c.saveLayerAlpha(bubblePathRect.left - padding, bubblePathRect.top - padding, bubblePathRect.right + padding, bubblePathRect.bottom + padding, (int) (255 * bubbleAlpha));
        super.drawBubble(c, paint, stroke, padding);
        c.restoreToCount(save);
      } else {
        super.drawBubble(c, paint, stroke, padding);
      }
      return;
    }
    final int s = Views.save(c);
    final float cx = getContentX() + videoSize / 2f;
    final float offset = cx - bubblePathRect.centerX();
    c.translate(offset, 0);

    super.drawBubble(c, paint, stroke, padding);

    Views.restore(c, s);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    if (tgx101TextMode()) {
      tgx101DrawText(c, getContentX(), startY, preview);
      return;
    }
    startX = getContentX();
    preview.setBounds(startX, startY, startX + videoSize, startY + videoSize);
    preview.setRadius(getImageContentRadius(true));
    if (preview.needPlaceholder()) {
      preview.drawPlaceholderRounded(c, videoSize / 2f);
    }

    final boolean drawSpoiler = TD.isSelfDestructTypeImmediately(getMessage());
    final float radius = videoSize / 2f;
    final float cx = preview.centerX(), cy = preview.centerY();

    if (drawSpoiler) {
      Receiver spoilerReceiver;
      if (preview instanceof DoubleImageReceiver) {
        spoilerReceiver = ((DoubleImageReceiver) preview).getPreview();
      } else {
        spoilerReceiver = preview;
      }
      if (spoilerReceiver.isEmpty()) {
        spoilerReceiver = preview;
      }
      spoilerReceiver.draw(c);

      c.drawCircle(cx, cy, radius, Paints.fillingPaint(Theme.getColor(ColorId.spoilerMediaOverlay)));
    } else {
      preview.draw(c);
    }
  }

  @Override
  protected void drawOverlay (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    if (tgx101TextMode()) {
      transcribeButtonX = transcribeButtonY = 0;
      return;
    }
    startX = getContentX();
    fileProgress.setBounds(startX, startY, startX + videoSize, startY + videoSize);
    fileProgress.draw(view, c);

    Receiver receiver = view.getPreviewReceiver();
    final int centerX = receiver.centerX();

    int x = centerX - (int) ((float) ((double) (receiver.getWidth() / 2) * Math.sin(Math.toRadians(45f))));

    int circleX;
    int contentWidth = (int) (durationWidth + Screen.dp(5f) * viewFactor);
    int textY = receiver.getBottom() - getBubbleTimePartHeight() - Screen.dp(8f);
    boolean useBubbles = useBubbles();
    if (useBubbles) {
      textY -= Screen.dp(3.5f);
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(6f);
      rectF.set(x - contentWidth - padding * 2, textY, x, textY + Screen.dp(21f));
      c.drawRoundRect(rectF, Screen.dp(12f), Screen.dp(12f), Paints.fillingPaint(getBubbleTimeColor()));
      textY -= Screen.dp(1f);
      c.drawText(durationStr, x - contentWidth - padding, textY + Screen.dp(15.5f), Paints.colorPaint(mTimeBubble(), getBubbleTimeTextColor()));
      circleX = x - Screen.dp(7f);
    } else {
      c.drawText(durationStr, x - contentWidth, textY + Screen.dp(15f), mTime(true));
      circleX = x;
    }

    if (viewFactor > 0f) {
      c.drawCircle(circleX, textY + Screen.dp(11.5f), Screen.dp(1.5f), Paints.fillingPaint(ColorUtils.alphaColor(viewFactor, useBubbles ? 0xffffffff : Theme.getColor(ColorId.online))));
    }

    // TGx101: "A" transcription button at the top right of the circle (the bottom has the duration, the
    // sound icon and, on outgoing ones, the time), hidden while the video plays full size
    if (!TD.isSelfDestructTypeImmediately(getMessage())) {
      float circleRadius = receiver.getWidth() / 2f;
      transcribeButtonX = centerX + circleRadius * 0.72f;
      transcribeButtonY = receiver.centerY() - circleRadius * 0.72f;
      Tgx101Transcription.Result result = Tgx101Transcription.get(tdlib, msg);
      boolean shown = result != null && result.state == Tgx101Transcription.STATE_DONE;
      c.drawCircle(transcribeButtonX, transcribeButtonY, Screen.dp(TRANSCRIBE_BUTTON_RADIUS), Paints.fillingPaint(shown ? 0xf0ffffff : (useBubbles() ? getBubbleTimeColor() : 0x80000000)));
      String label = result != null && result.state == Tgx101Transcription.STATE_PENDING ? "…" : "A";
      android.text.TextPaint labelPaint = Paints.getBoldPaint14(false, shown ? 0xff222222 : 0xffffffff);
      c.drawText(label, transcribeButtonX - labelPaint.measureText(label) / 2f, transcribeButtonY + Screen.dp(5f), labelPaint);
    } else {
      transcribeButtonX = transcribeButtonY = 0;
    }

    final boolean drawSpoiler = TD.isSelfDestructTypeImmediately(getMessage());
    float alpha = (1f - unmuteFactor) * (1f - fileProgress.getBackgroundAlpha());
    if (alpha > 0f && !drawSpoiler) {
      int radius = Screen.dp(12f);
      int centerY = receiver.getBottom() - radius - Screen.dp(10f);
      tgx101MuteX = centerX;
      tgx101MuteY = centerY;

      final float scale = .6f + (1f - unmuteFactor) * .4f;
      final boolean needScale = scale != 1f;
      int restoreToCount;
      if (needScale) {
        restoreToCount = Views.save(c);
        c.scale(scale, scale, centerX, centerY);
      } else {
        restoreToCount = -1;
      }
      c.drawCircle(centerX, centerY, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0x40000000)));
      Paint paint = Paints.whitePorterDuffPaint();
      paint.setAlpha((int) (255f * alpha));
      Drawable drawable = view.getSparseDrawable(R.drawable.deproko_baseline_sound_muted_24, 0);
      Drawables.draw(c, drawable, centerX - drawable.getMinimumWidth() / 2f, centerY - drawable.getMinimumHeight() / 2f, paint);
      paint.setAlpha(255);
      if (needScale) {
        Views.restore(c, restoreToCount);
      }
    }
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (tgx101TextMode()) {
      return tgx101TextTarget() && tgx101OnTextTouch(view, e) || super.onTouchEvent(view, e);
    }
    // TGx101: the 🔇 mark — sound on, the circle stays small
    if (!isUnmuted && tgx101MuteY != 0 && !TD.isSelfDestructTypeImmediately(getMessage())) {
      float area = Screen.dp(30f); // generous: the mark itself is small (user «тоже легко промахнуться»)
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          tgx101MuteCaught = Math.abs(e.getX() - tgx101MuteX) <= area && Math.abs(e.getY() - tgx101MuteY) <= area;
          if (tgx101MuteCaught) return true;
          break;
        case MotionEvent.ACTION_MOVE:
          if (tgx101MuteCaught) return true;
          break;
        case MotionEvent.ACTION_UP:
          if (tgx101MuteCaught) {
            tgx101MuteCaught = false;
            performClickSoundFeedback();
            tgx101SmallPlay = true;
            org.thunderdog.challegram.Tgx101Diag.mark("round video: sound on in place (small)");
            onClick(fileProgress, view, videoNote.video, msg.id);
            return true;
          }
          break;
        case MotionEvent.ACTION_CANCEL:
          if (tgx101MuteCaught) {
            tgx101MuteCaught = false;
            return true;
          }
          break;
      }
    }
    // TGx101: "A" button
    if (transcribeButtonX != 0) {
      float touch = Screen.dp(TRANSCRIBE_BUTTON_RADIUS + 16f); // a bigger hit area (user 2026-10-19 «буква А должна иметь чуть большую область нажатия»)
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          transcribeCaught = Math.abs(e.getX() - transcribeButtonX) <= touch && Math.abs(e.getY() - transcribeButtonY) <= touch;
          if (transcribeCaught) return true;
          break;
        case MotionEvent.ACTION_MOVE:
          if (transcribeCaught) return true;
          break;
        case MotionEvent.ACTION_UP:
          if (transcribeCaught) {
            transcribeCaught = false;
            performClickSoundFeedback();
            // TGx101: first tap starts the background transcription; when it's ready, a tap opens the text
            String done = Tgx101Transcription.doneText(tdlib, msg);
            if (true) { // the bubble with the text, right away (all builds since 2026-10-19)
              if (done == null) Tgx101Transcription.start(tdlib, msg, isSecretChat());
              tgx101SetTextMode(true);
            } else if (done != null && messagesController() != null) {
              org.thunderdog.challegram.ui.SelectTextForQuoteDialog.showForTranscription(messagesController(), tdlib, this, done);
            } else {
              Tgx101Transcription.start(tdlib, msg, isSecretChat());
            }
            return true;
          }
          break;
        case MotionEvent.ACTION_CANCEL:
          if (transcribeCaught) {
            transcribeCaught = false;
            return true;
          }
          break;
      }
    }
    return super.onTouchEvent(view, e) || fileProgress.onTouchEvent(view, e);
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    fileProgress.updateMessageId(oldMessageId, newMessageId, success);
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    BaseActivity context = UI.getContext(view.getContext());
    RoundVideoController c = context.getRoundVideoController();
    if (c.comparePlayingObject(tdlib, msg)) {
      ViewController<?> m = ViewController.findRoot(view);
      if (m instanceof MessagesController) {
        ((MessagesController) m).checkRoundVideo();
      } else {
        // Log.w("attach/detach, but MessageController not found");
      }
    }
    fileProgress.notifyInvalidateTargetsChanged();
  }
}
