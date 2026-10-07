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
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.RateLimiter;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.TextView;
import org.thunderdog.challegram.widget.voip.CallControlsLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewHandler;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class CallController extends ViewController<CallController.Arguments> implements TdlibCache.UserDataChangeListener, TdlibCache.CallStateChangeListener, View.OnClickListener, FactorAnimator.Target, Runnable, CallControlsLayout.CallControlCallback, Screen.StatusBarHeightChangeListener {
  private static final boolean DEBUG_FADE_BRANDING = true;

  private static class ButtonView extends View implements FactorAnimator.Target {
    private Drawable icon;
    private float factor;
    private boolean needCross;

    public ButtonView (Context context) {
      super(context);
    }

    public void setIcon (@DrawableRes int icon) {
      this.icon = Drawables.get(icon);
    }

    public void setNeedCross (boolean needCross) {
      this.needCross = needCross;
    }

    public boolean toggleActive () {
      setIsActive(!isActive, true);
      return isActive;
    }

    private boolean isActive;

    public void setIsActive (boolean isActive, boolean animated) {
      if (this.isActive != isActive) {
        this.isActive = isActive;
        if (animated) {
          animateFactor(isActive ? 1f : 0f);
        } else {
          forceFactor(isActive ? 1f : 0f);
        }
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      return getParent() != null && ((View) getParent()).getAlpha() == 1f && super.onTouchEvent(event);
    }

    private FactorAnimator animator;

    private void animateFactor (float toFactor) {
      if (animator == null) {
        animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
      }
      animator.animateTo(toFactor);
    }

    private void forceFactor (float toFactor) {
      if (animator != null) {
        animator.forceFactor(toFactor);
      }
      setFactor(toFactor);
    }

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    protected void onDraw (Canvas c) {
      if (icon == null) {
        return;
      }
      float cx = getMeasuredWidth() / 2;
      float cy = getMeasuredHeight() / 2;
      int backgroundColor = ColorUtils.fromToArgb(0x00ffffff, 0xffffffff, factor);
      if (factor != 0f) {
        c.drawCircle(cx, cy, Screen.dp(18f), Paints.fillingPaint(backgroundColor));
      }
      int iconColor = ColorUtils.fromToArgb(0xffffffff, 0xff000000, factor);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, Paints.getPorterDuffPaint(iconColor));
      if (factor != 0f && needCross) {
        DrawAlgorithms.drawCross(c, cx, cy, factor, iconColor, backgroundColor);
      }
    }
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  public static class Arguments {
    private TdApi.Call call;

    public Arguments (TdApi.Call call) {
      this.call = call;
    }
  }

  public CallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.Call call;
  private @Nullable TdApi.User user;
  private CallSettings callSettings;
  private boolean hadEmojiSinceStart;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.call = args.call;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    this.hadEmojiSinceStart = call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    this.user = tdlib.cache().user(call.userId);
  }

  @Override
  public int getId () {
    return R.id.controller_call;
  }

  private AvatarView avatarView;
  private Tgx101CallBackground callBackground;
  private Tgx101CallVideo callVideo;
  private TextView nameView, stateView;
  private EmojiStatusHelper emojiStatusHelper;
  private float nameTextWidth;
  private TextPaint nameTextPaint;
  private LinearLayout brandWrap;
  private TextView debugView;
  private CallStrengthView strengthView;

  private static class CallStrengthView extends View {
    private final ViewHandler handler;

    public CallStrengthView (Context context) {
      super(context);
      handler = new ViewHandler();
    }

    private int barCount = -1;
    private long lastUpdateTime;

    private static final long MAX_FREQUENCY_MS = 1000l;

    public void setBarsCount (int callStrength) {
      boolean changed = Math.max(this.barCount, 0) != Math.max(callStrength, 0);
      this.barCount = callStrength;
      if (changed) {
        long now = SystemClock.elapsedRealtime();

        if (lastUpdateTime == 0 || now - lastUpdateTime >= MAX_FREQUENCY_MS) {
          lastUpdateTime = now;
          handler.cancelInvalidate(this);
          invalidate();
        } else {
          handler.invalidate(this, MAX_FREQUENCY_MS - (now - lastUpdateTime));
        }
      }
    }

    private static final int MAX_BARS_COUNT = 4;

    @Override
    protected void onDraw (Canvas c) {
      int size = Screen.dp(3f);
      int spacing = Screen.dp(1f);
      int totalSize = size * MAX_BARS_COUNT + spacing * (MAX_BARS_COUNT - 1);
      int startX = getMeasuredWidth() / 2 - totalSize / 2;
      int startY = getMeasuredHeight() / 2 + size * 2;
      int cx = startX;
      for (int i = 0; i < 4; i++) {
        RectF rectF = Paints.getRectF();
        rectF.set(cx, startY - size * (i + 1), cx + size, startY);
        c.drawRoundRect(rectF, spacing, spacing, Paints.fillingPaint(barCount > i ? 0xffffffff : 0x7fffffff));
        cx += size + spacing;
      }
    }
  }

  private TextView emojiViewSmall, emojiViewBig, emojiViewHint;
  private CallControlsLayout callControlsLayout;

  private FrameLayoutFix buttonWrap;
  private ButtonView muteButtonView, speakerButtonView;

  private float lastHeaderFactor;

  @Override
  protected void applyCustomHeaderAnimations (float factor) {
    if (lastHeaderFactor != factor) {
      lastHeaderFactor = factor;
      updateControlsAlpha();
      updateEmojiFactors();
      if (DEBUG_FADE_BRANDING) {
        brandWrap.setAlpha(factor);
      }
      avatarView.invalidate();
    }
  }

  private BoolAnimator strengthAnimator;
  private static final int ANIMATOR_STRENGTH = 6;

  private void updateCallStrength () {
    boolean isVisible = callStrength >= 0 && call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR && callDuration >= 0;
    if (!isVisible == (strengthAnimator != null && strengthAnimator.getValue())) {
      if (strengthAnimator == null) {
        strengthAnimator = new BoolAnimator(ANIMATOR_STRENGTH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      strengthAnimator.setValue(isVisible, strengthView != null && lastHeaderFactor > 0f);
    }
  }

  private int callStrength = -1;

  private void setCallBarsCount (int count) {
    if (this.callStrength != count) {
      this.callStrength = count;
      if (strengthView != null) {
        strengthView.setBarsCount(count);
      }
      updateCallStrength();
    }
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    Views.setPaddingBottom(buttonWrap, extraBottomInset);
    Views.setLayoutHeight(buttonWrap, Screen.dp(76f) + extraBottomInset);
    Views.setPaddingBottom(callControlsLayout, extraBottomInset);
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    int startMargin = Math.max(Screen.dp(18f) + newHeight, Screen.dp(42f));
    Views.setTopMargin(brandWrap, startMargin);
    Views.setTopMargin(nameView, startMargin + Screen.dp(34f));
    Views.setTopMargin(stateView, startMargin + Screen.dp(94f));
    if (photoMode == Settings.CALL_PHOTO_CIRCLE) {
      Views.setTopMargin(avatarView, startMargin + AVATAR_TOP_OFFSET);
    }
  }

  private int photoMode; // TGx101: Settings.CALL_PHOTO_*
  private static final int AVATAR_TOP_OFFSET = Screen.dp(150f); // TGx101: under the name and the call state

  @Override
  protected View onCreateView (final Context context) {
    final FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateEmojiPosition();
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmojiPosition();
      }
    };
    photoMode = Settings.instance().getCallPhotoMode();
    final boolean isCircle = photoMode == Settings.CALL_PHOTO_CIRCLE;
    final boolean isFullScreen = photoMode == Settings.CALL_PHOTO_FULL_SCREEN;

    // TGx101: dark background in the app icon colours with a faint paper plane pattern
    callBackground = new Tgx101CallBackground(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && emojiExpandFactor == 1f && isEmojiExpanded) {
          setEmojiExpanded(false);
        }
        return true;
      }
    };
    callBackground.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(callBackground);

    // TGx101: the photo is a big circle under the name instead of a stretched full-screen picture
    avatarView = new AvatarView(context) {
      private final Drawable topShadow = isFullScreen ? ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.TOP, false) : null;

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        if (!isCircle) { // the original full-screen photo
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          if (topShadow != null) {
            topShadow.setBounds(0, 0, getMeasuredWidth(), Screen.dp(212f));
          }
          return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec) - Screen.dp(76f) - Screen.dp(96f);
        int size = Math.max(0, Math.min((int) (width * .64f), (int) (height * .8f)));
        int spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        super.onMeasure(spec, spec);
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        callBackground.invalidate();
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            return true;
          case MotionEvent.ACTION_UP:
            if (emojiExpandFactor == 1f && isEmojiExpanded) {
              setEmojiExpanded(false);
            }
            return true;
        }
        return false;
      }

      @Override
      protected void onDraw (Canvas c) {
        super.onDraw(c);
        if (topShadow != null) {
          Drawables.setAlpha(topShadow, (int) (255f * lastHeaderFactor * .5f));
          topShadow.draw(c);
        }
      }
    };
    if (isFullScreen) {
      avatarView.setNoRound(true);
      avatarView.setNoPlaceholders(true);
    } else if (!isCircle) {
      avatarView.setVisibility(View.GONE);
    }
    avatarView.setNeedFull(true);
    avatarView.setUser(tdlib, user, false);
    contentView.addView(avatarView);
    callBackground.setPhotoView(isCircle ? avatarView : null);
    callVideo = new Tgx101CallVideo(this, contentView, contentView.indexOfChild(avatarView) + 1, newCallVideoHost(), avatarView); // TGx101: call panel and video

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    // Top-left corner

    int startMargin = Math.max(Screen.dp(18f) + Screen.getStatusBarHeight(), Screen.dp(42f));
    avatarView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    if (isCircle) {
      Views.setTopMargin(avatarView, startMargin + AVATAR_TOP_OFFSET - (Settings.instance().useNewCallScreen() ? Screen.dp(28f) : 0));
    }

    final boolean tgx101New = Settings.instance().useNewCallScreen() && !isFullScreen;
    params.topMargin = tgx101New ? startMargin + Screen.dp(6f) : startMargin + Screen.dp(34f); // TGx101: the name as high as possible
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    final boolean newScreen = Settings.instance().useNewCallScreen() && !isFullScreen;
    nameView = new EmojiTextView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        if (newScreen) {
          // TGx101: the name shrinks down to 75 % to fit one line, then wraps to two lines at most
          int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
          String name = TD.getUserName(user);
          float full = Screen.dp(NAME_TEXT_SIZE), min = Screen.dp(NAME_TEXT_SIZE * .75f);
          nameTextPaint.setTextSize(full);
          float width = U.measureText(name, nameTextPaint);
          float size = width <= available || available <= 0 ? full : Math.max(min, full * available / width);
          float sizeDp = size / Screen.density();
          if (Math.abs(getTextSize() - size) > 1f) {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
          }
          nameTextPaint.setTextSize(size);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (newScreen) {
          onNameLayout(this);
          return;
        }
        nameTextWidth = U.measureText(TD.getUserName(user), nameTextPaint);
        if (nameTextWidth > getMeasuredWidth() - getPaddingRight()) {
          CharSequence text = getText().subSequence(0, getLayout().getEllipsisStart(0)) + "...";
          nameTextWidth = U.measureText(text, nameTextPaint);
        }
      }

      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        if (newScreen) {
          android.text.Layout layout = getLayout();
          if (layout != null && layout.getLineCount() > 0) {
            int last = layout.getLineCount() - 1;
            int x = getPaddingLeft() + (int) layout.getLineRight(last) + Screen.dp(7);
            int lineCenter = getPaddingTop() + (layout.getLineTop(last) + layout.getLineBottom(last)) / 2;
            int emojiSize = emojiStatusHelper.getWidth(0);
            emojiStatusHelper.draw(canvas, Math.min(getMeasuredWidth() - emojiSize, x), lineCenter - emojiSize / 2);
          }
          return;
        }
        int textLeft = isFullScreen ? 0 : (int) Math.max(0, (getMeasuredWidth() - nameTextWidth - emojiStatusHelper.getWidth(0) - Screen.dp(7)) / 2f); // TGx101: centred name
        emojiStatusHelper.draw(canvas, (int) Math.min(getMeasuredWidth() - emojiStatusHelper.getWidth(0), textLeft + nameTextWidth + Screen.dp(7)), Screen.dp(9));
      }
    };
    nameView.setScrollDisabled(true);
    if (newScreen) {
      nameView.setSingleLine(false);
      nameView.setMaxLines(2);
      nameView.setLineSpacing(0, .95f);
    } else {
      nameView.setSingleLine(true);
    }
    nameView.setTextColor(0xffffffff);
    nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
    nameView.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular());
    Views.setSimpleShadow(nameView);
    nameView.setEllipsize(TextUtils.TruncateAt.END);
    nameView.setGravity(isFullScreen ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL);
    nameView.setLayoutParams(params);
    contentView.addView(nameView);

    nameTextPaint = new TextPaint();
    nameTextPaint.setTextSize(Screen.dp(40));
    nameTextPaint.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoRegular());
    emojiStatusHelper = new EmojiStatusHelper(tdlib, nameView, null);
    emojiStatusHelper.setAnimationDisabled(true); // TGx101: animated statuses as a still picture
    emojiStatusHelper.attach(); // TGx101: without it custom (animated) statuses were never loaded

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = tgx101New ? startMargin + Screen.dp(58f) : startMargin + Screen.dp(94f); // TGx101: the timer a little higher
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    stateView = new TextView(context);
    stateView.setScrollDisabled(true);
    // stateView.setSingleLine(true);
    stateView.setMaxLines(2);
    stateView.setLineSpacing(Screen.dp(3f), 1f);
    stateView.setTextColor(0xffffffff);
    stateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, tgx101New ? 16 : 14);
    stateView.setTypeface(tgx101New ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular()); // TGx101: bolder timer
    Views.setSimpleShadow(stateView);
    // stateView.setEllipsize(TextUtils.TruncateAt.END);
    stateView.setGravity(isFullScreen ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL);
    stateView.setLayoutParams(params);
    contentView.addView(stateView);

    Screen.addStatusBarHeightListener(this);
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, (newScreen ? Gravity.RIGHT : isFullScreen ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL) | Gravity.TOP);
    params.topMargin = newScreen ? Screen.getStatusBarHeight() + Screen.dp(4f) : startMargin; // TGx101: the bird in the very top-right corner
    if (newScreen) {
      params.rightMargin = Screen.dp(10f);
    }
    params.leftMargin = params.rightMargin = Screen.dp(18f);
    brandWrap = new LinearLayout(context);
    if (DEBUG_FADE_BRANDING) {
      brandWrap.setAlpha(0f);
    }
    brandWrap.setOrientation(LinearLayout.HORIZONTAL);
    brandWrap.setLayoutParams(params);
    contentView.addView(brandWrap);

    LinearLayout.LayoutParams lp;

    lp = new LinearLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
    lp.topMargin = Screen.dp(2f);

    ImageView brandIcon = new ImageView(context);
    brandIcon.setScaleType(ImageView.ScaleType.CENTER);
    brandIcon.setImageResource(R.drawable.deproko_logo_telegram_18);
    brandIcon.setLayoutParams(lp);
    if (newScreen) {
      // TGx101: no «Звонок через Telegram» text, just the PlumaGram bird in the top-right corner
      brandIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      brandIcon.setImageResource(R.drawable.baseline_plumagram_24);
      brandIcon.setColorFilter(0xffffffff);
      brandIcon.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(28f), Screen.dp(28f)));
      tgx101BrandIcon = brandIcon;
    }
    brandWrap.addView(brandIcon);

    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = Screen.dp(9f);

    TextView brandView = new TextView(context);
    brandView.setScrollDisabled(true);
    brandView.setSingleLine(true);
    brandView.setTextColor(0xffffffff);
    brandView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    brandView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(brandView);
    brandView.setEllipsize(TextUtils.TruncateAt.END);
    brandView.setLayoutParams(lp);
    brandView.setText(Lang.uppercase(Lang.getString(call != null && call.isVideo ? R.string.Tgx101VideoCallBranding : R.string.VoipBranding))); // TGx101 (user 2026-10-07): a video call says so
    if (Log.checkLogLevel(Log.LEVEL_INFO) || BuildConfig.EXPERIMENTAL) {
      brandView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick (View v) {
          TGCallService.markLogViewed();
          if (debugView != null) {
            contentView.removeView(debugView);
            debugView = null;
          } else {
            final TextView view = new TextView(context);
            view.setScrollDisabled(true);
            view.setBackgroundColor(0xaaffffff);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setTextColor(0xff000000);
            view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
            view.post(new Runnable() {
              @Override
              public void run () {
                TGCallService service = TGCallService.currentInstance();

                SpannableStringBuilder b = new SpannableStringBuilder();
                if (service != null) {
                  b.append(service.getLibraryNameAndVersion());
                } else {
                  b.append("service unavailable");
                }
                b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoBold(), 0), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (service != null) {
                  CharSequence log = service.getDebugString();
                  if (!StringUtils.isEmpty(log)) {
                    b.append("\n\n");
                    b.append(log);
                  }
                }
                view.setText(b);
                if (view.getParent() != null) {
                  view.postDelayed(this, 500l);
                }
              }
            });
            debugView = view;
            contentView.addView(debugView);
          }
        }
      });
    }
    brandWrap.addView(brandView);
    if (newScreen) {
      brandView.setVisibility(View.GONE);
      brandIcon.setOnClickListener(v -> brandView.performClick()); // the debug log stays reachable
    }

    lp = new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f));
    // lp.topMargin = Screen.dp(2f);
    lp.leftMargin = Screen.dp(8f);
    strengthView = new CallStrengthView(context);
    strengthView.setLayoutParams(lp);
    if (callStrength < 0) {
      strengthView.setAlpha(0f);
    }
    strengthView.setBarsCount(callStrength);
    brandWrap.addView(strengthView);

    // Emoji corner

    emojiViewSmall = new EmojiTextView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return (event.getAction() != MotionEvent.ACTION_DOWN || emojiExpandFactor == 0f) && super.onTouchEvent(event);
      }
    };
    emojiViewSmall.setScrollDisabled(true);
    emojiViewSmall.setSingleLine(true);
    emojiViewSmall.setTextColor(0xffffffff);
    emojiViewSmall.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    emojiViewSmall.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewSmall);
    emojiViewSmall.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewSmall.setPadding(Screen.dp(18f), Screen.dp(18f), Screen.dp(18f), Screen.dp(18f));
    emojiViewSmall.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    emojiViewSmall.setOnClickListener(this);
    emojiViewSmall.setId(R.id.btn_emoji);
    contentView.addView(emojiViewSmall);

    emojiViewBig = new EmojiTextView(context);
    emojiViewBig.setScrollDisabled(true);
    emojiViewBig.setSingleLine(true);
    emojiViewBig.setScaleX(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setScaleY(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setAlpha(0f);
    emojiViewBig.setTextColor(0xffffffff);
    int emojiBigSize = (int) (16f * DESIRED_EMOJI_EXPAND_FACTOR);
    EMOJI_EXPAND_FACTOR = (float) emojiBigSize / 16f;
    emojiViewBig.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emojiBigSize);
    emojiViewBig.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewBig);
    emojiViewBig.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewBig.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    contentView.addView(emojiViewBig);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(24f) * 2;
    params.rightMargin = params.leftMargin = Screen.dp(48f);

    emojiViewHint = new EmojiTextView(context);
    emojiViewHint.setScrollDisabled(true);
    emojiViewHint.setAlpha(0f);
    emojiViewHint.setTextColor(0xffffffff);
    emojiViewHint.setGravity(Gravity.CENTER);
    emojiViewHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
    emojiViewHint.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewHint);
    emojiViewHint.setLayoutParams(params);
    contentView.addView(emojiViewHint);

    // Call settings buttons

    muteButtonView = new ButtonView(context);
    muteButtonView.setId(R.id.btn_mute);
    muteButtonView.setOnClickListener(this);
    muteButtonView.setIcon(R.drawable.baseline_mic_24);
    muteButtonView.setNeedCross(true);
    muteButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.LEFT | Gravity.BOTTOM));

    ButtonView messageButtonView = new ButtonView(context);
    messageButtonView.setId(R.id.btn_openChat);
    messageButtonView.setOnClickListener(this);
    messageButtonView.setIcon(R.drawable.baseline_chat_bubble_24);
    messageButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

    speakerButtonView = new ButtonView(context);
    speakerButtonView.setId(R.id.btn_speaker);
    speakerButtonView.setOnClickListener(this);
    speakerButtonView.setIcon(R.drawable.baseline_volume_up_24);
    speakerButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.RIGHT | Gravity.BOTTOM));

    buttonWrap = new FrameLayoutFix(context);
    buttonWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(76f) + extraBottomInset, Gravity.BOTTOM));
    buttonWrap.addView(muteButtonView);
    buttonWrap.addView(messageButtonView);
    buttonWrap.addView(speakerButtonView);
    Views.setPaddingBottom(buttonWrap, extraBottomInset);
    Drawable drawable = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.BOTTOM, false);
    drawable.setAlpha((int) (255f * .3f));
    ViewUtils.setBackground(buttonWrap, drawable);
    contentView.addView(buttonWrap);

    // Answer controls

    callControlsLayout = new CallControlsLayout(context, this);
    Views.setPaddingBottom(callControlsLayout, extraBottomInset);
    callControlsLayout.setCallback(this);
    callControlsLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(callControlsLayout);
    callVideo.setOriginalControls(buttonWrap, callControlsLayout); // TGx101: replaced by one panel during outgoing and active calls
    if (newScreen) {
      incomingControls = new Tgx101IncomingControls(context, newIncomingCallback());
      Views.setPaddingBottom(incomingControls, extraBottomInset);
      incomingControls.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      incomingControls.setVisibility(View.GONE);
      contentView.addView(incomingControls);
    }
    callVideo.onCallStateChanged(call);
    callControlsLayout.setCall(tdlib, call, false);
    updateIncomingControls();

    // Data

    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.cache().addUserDataListener(call.userId, this);

    this.callSettings = tdlib.cache().getCallSettings(call.id);

    setTexts();
    updateCallState();

    if (callSettings != null) {
      muteButtonView.setIsActive(callSettings.isMicMuted(), false);
      speakerButtonView.setIsActive(callSettings.isSpeakerModeEnabled(), false);
    }

    return contentView;
  }


  private void setTexts () {
    if (emojiStatusHelper != null) {
      this.emojiStatusHelper.updateEmoji(tdlib, user, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.white);
        }
      }, R.drawable.baseline_premium_star_28, 32);
    }
    if (nameView != null) {
      this.nameView.setText(TD.getUserName(user));
      this.nameView.setPadding(0, 0, user != null && user.isPremium ? emojiStatusHelper.getWidth(Screen.dp(7)) : 0, 0);
      this.nameView.requestLayout();
    }
    if (emojiViewHint != null)
      this.emojiViewHint.setText(Lang.getString(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  @Override
  public void onCallAccept (TdApi.Call call) {
    tdlib.context().calls().acceptCall(context(), tdlib, call.id);
  }

  @Override
  public void onCallDecline (TdApi.Call call, boolean isHangUp) {
    tdlib.context().calls().hangUp(tdlib, call.id);
  }

  @Override
  public void onCallRestart (TdApi.Call call) {
    tdlib.context().calls().makeCall(this, call.userId, null);
  }

  public boolean compareUserId (long userId) {
    return call.userId == userId;
  }

  @Override
  public void onCallClose (TdApi.Call call) {
    closeCall();
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (!UI.isTablet()) {
      context().setOrientation(BaseActivity.getAndroidOrientationPortrait());
    }
  }

  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    if (!UI.isTablet()) {
      context().setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
  }

  private void updateLoop () {
    setIsLooping(!isDestroyed() && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
  }

  private boolean isLooping;

  private void setIsLooping (boolean isLooping) {
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (isLooping) {
        UI.post(this);
      } else {
        UI.removePendingRunnable(this);
      }
    }
  }

  @Override
  public void run () {
    if (!isDestroyed()) {
      updateCallState();
      if (isLooping) {
        UI.post(this, tdlib.context().calls().getTimeTillNextCallDurationUpdate(tdlib, call.id));
      }
    }
  }

  private boolean buttonsVisible;
  private FactorAnimator buttonsAnimator;
  private static final int ANIMATOR_BUTTONS_ID = 0;
  private float buttonsFactor;

  private void setButtonsVisible (boolean areVisible, boolean animated) {
    if (this.buttonsVisible != areVisible) {
      this.buttonsVisible = areVisible;
      if (animated) {
        if (buttonsAnimator == null) {
          buttonsAnimator = new FactorAnimator(ANIMATOR_BUTTONS_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.buttonsFactor);
        }
        buttonsAnimator.animateTo(areVisible ? 1f : 0f);
      } else {
        if (buttonsAnimator != null) {
          buttonsAnimator.forceFactor(areVisible ? 1f : 0f);
        }
        setButtonsFactor(areVisible ? 1f : 0f);
      }
    }
  }

  private void setButtonsFactor (float factor) {
    this.buttonsFactor = factor;
    updateControlsAlpha();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BUTTONS_ID: {
        setButtonsFactor(factor);
        break;
      }
      case ANIMATOR_FLASH_ID: {
        stateView.setAlpha(factor <= .5f ? 1f - (factor / .5f) : (factor - .5f) / .5f);
        break;
      }
      case ANIMATOR_EMOJI_VISIBILITY_ID: {
        setEmojiVisibilityFactor(factor);
        break;
      }
      case ANIMATOR_EMOJI_EXPAND_ID: {
        setEmojiExpandFactor(Math.max(0f, factor));
        break;
      }
      case ANIMATOR_STRENGTH: {
        if (strengthView != null) {
          strengthView.setAlpha(factor);
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FLASH_ID: {
        if (finalFactor == 1f) {
          flashLimiter.run();
        }
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_emoji) {
      if (isEmojiVisible) {
        setEmojiExpanded(true);
      }
    } else if (viewId == R.id.btn_mute) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setMicMuted(((ButtonView) v).toggleActive());
      }
    } else if (viewId == R.id.btn_openChat) {
      tdlib.ui().openPrivateChat(this, call.userId, null);
    } else if (viewId == R.id.btn_speaker) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        if (callSettings.isSpeakerModeEnabled()) {
          callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
        } else {
          callSettings.toggleSpeakerMode(this);
        }
      }
    }
  }

  // TGx101: the call panel uses the call screen's own actions
  private Tgx101CallVideo.Host newCallVideoHost () {
    return new Tgx101CallVideo.Host() {
      @Override
      public boolean isMicMuted () {
        return callSettings != null && callSettings.isMicMuted();
      }

      @Override
      public void toggleMicMuted () {
        if (TD.isFinished(call)) return;
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setMicMuted(!callSettings.isMicMuted());
      }

      @Override
      public boolean isSpeakerOn () {
        return callSettings != null && callSettings.isSpeakerModeEnabled();
      }

      @Override
      public void toggleSpeaker () {
        if (TD.isFinished(call)) return;
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        if (Settings.instance().useNewCallScreen()) {
          // TGx101: with headphones or Bluetooth «Динамик» opens the route picker, otherwise it switches the loudspeaker
          if (hasExternalAudio()) {
            showAudioOutputPicker();
            return;
          }
          callSettings.setSpeakerMode(callSettings.isSpeakerModeEnabled() ? CallSettings.SPEAKER_MODE_EARPIECE : CallSettings.SPEAKER_MODE_SPEAKER);
          return;
        }
        if (callSettings.isSpeakerModeEnabled()) {
          callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
        } else {
          callSettings.toggleSpeakerMode(CallController.this);
        }
      }

      @Override
      public void showAudioOutput () {
        showAudioOutputPicker();
      }

      @Override
      public int getAudioRouteIcon () {
        int mode = callSettings != null ? callSettings.getSpeakerMode() : CallSettings.SPEAKER_MODE_EARPIECE;
        if (mode == CallSettings.SPEAKER_MODE_BLUETOOTH) {
          return R.drawable.baseline_bluetooth_24;
        }
        if (mode != CallSettings.SPEAKER_MODE_SPEAKER && mode != CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT) {
          android.media.AudioManager audio = (android.media.AudioManager) context().getSystemService(Context.AUDIO_SERVICE);
          if (audio != null && audio.isWiredHeadsetOn()) {
            return R.drawable.baseline_headset_24;
          }
        }
        return R.drawable.baseline_volume_up_24;
      }

      @Override
      public boolean isAudioRouteActive () {
        return getAudioRouteIcon() != R.drawable.baseline_volume_up_24 || isSpeakerOn();
      }

      @Override
      public void openChat () {
        tdlib.ui().openPrivateChat(CallController.this, call.userId, null);
      }

      @Override
      public void hangUp () {
        tdlib.context().calls().hangUp(tdlib, call.id);
      }

      @Override
      public boolean onVideoStarted () {
        return enableSpeakerForVideo();
      }

      @Override
      public void onVideoStopped () {
        if (call == null || TD.isFinished(call) || callSettings == null) return;
        if (callSettings.isSpeakerModeEnabled()) {
          callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
        }
      }
    };
  }

  // TGx101: new incoming call controls replace the accept/decline swipe layer while the call rings

  private Tgx101IncomingControls incomingControls;

  private ImageView tgx101BrandIcon;

  private void updateIncomingControls () {
    boolean ringing = call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;
    if (tgx101BrandIcon != null) {
      // TGx101: the bird only on the incoming call screen; during a call that corner belongs to the encryption emoji
      tgx101BrandIcon.setVisibility(ringing ? View.VISIBLE : View.GONE);
    }
    if (incomingControls == null) return;
    boolean incoming = ringing;
    incomingControls.setVisibility(incoming ? View.VISIBLE : View.GONE);
    if (incoming) {
      callControlsLayout.setVisibility(View.GONE);
      buttonWrap.setVisibility(View.GONE);
      incomingControls.bringToFront();
    }
  }

  private Tgx101IncomingControls.Callback newIncomingCallback () {
    return new Tgx101IncomingControls.Callback() {
      @Override
      public void onAnswer () {
        onCallAccept(call);
      }

      @Override
      public void onDecline () {
        onCallDecline(call, false);
      }

      @Override
      public void onSilence () {
        TGCallService service = TGCallService.currentInstance();
        if (service != null) {
          service.silenceRinging();
        }
      }

      @Override
      public void onQuickReply (String text) {
        final long userId = call.userId;
        onCallDecline(call, false);
        // Sent right away, also from the lock screen: it's one of the user's own templates
        tdlib.send(new TdApi.CreatePrivateChat(userId, false), (chat, error) -> {
          if (chat != null) {
            tdlib.send(new TdApi.SendMessage(chat.id, null, null, null, null, new TdApi.InputMessageText(new TdApi.FormattedText(text, new TdApi.TextEntity[0]), null, false)), (message, sendError) -> { });
          }
        });
      }
    };
  }

  private static final float NAME_TEXT_SIZE = 40f;
  private int nameExtraHeight;

  /** TGx101: a two-line name pushes the call state and the photo down */
  private void onNameLayout (android.widget.TextView view) {
    android.text.Layout layout = view.getLayout();
    int extra = layout != null && layout.getLineCount() > 1 ? layout.getLineTop(layout.getLineCount() - 1) : 0;
    if (extra != nameExtraHeight) {
      nameExtraHeight = extra;
      applyTgx101Shifts();
    }
  }

  private int tgx101EmojiShift;
  private static final float TGX101_EMOJI_ROW = 26f;

  /** The timer and the photo move down under a two-line name and under the encryption emoji row */
  private void applyTgx101Shifts () {
    int shift = nameExtraHeight + tgx101EmojiShift;
    stateView.setTranslationY(shift);
    if (photoMode == Settings.CALL_PHOTO_CIRCLE) {
      avatarView.setTranslationY(shift);
      if (callBackground != null) callBackground.invalidate();
    }
  }

  private boolean hasExternalAudio () {
    TGCallService service = TGCallService.currentInstance();
    android.media.AudioManager audio = (android.media.AudioManager) context().getSystemService(Context.AUDIO_SERVICE);
    return (service != null && service.isBluetoothHeadsetConnected()) || (audio != null && audio.isWiredHeadsetOn());
  }

  // TGx101: route picker (opened by «Динамик» with headphones) — phone (or the wired headset when plugged in), Bluetooth when connected, loudspeaker
  private void showAudioOutputPicker () {
    if (call == null || TD.isFinished(call)) return;
    if (callSettings == null) {
      callSettings = new CallSettings(tdlib, call.id);
    }
    TGCallService service = TGCallService.currentInstance();
    android.media.AudioManager audio = (android.media.AudioManager) context().getSystemService(Context.AUDIO_SERVICE);
    boolean wired = audio != null && audio.isWiredHeadsetOn();
    boolean bluetooth = service != null && service.isBluetoothHeadsetConnected();
    int current = callSettings.getSpeakerMode();
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    java.util.List<String> names = new java.util.ArrayList<>();
    java.util.List<Integer> icons = new java.util.ArrayList<>();
    ids.add(R.id.btn_routingEarpiece);
    names.add(Lang.getString(wired ? R.string.Tgx101AudioHeadset : R.string.Tgx101AudioPhone) + (current == CallSettings.SPEAKER_MODE_EARPIECE ? "  ✓" : ""));
    icons.add(wired ? R.drawable.baseline_headset_24 : R.drawable.baseline_phone_in_talk_24);
    if (bluetooth) {
      ids.add(R.id.btn_routingBluetooth);
      names.add(Lang.getString(R.string.Tgx101AudioBluetooth) + (current == CallSettings.SPEAKER_MODE_BLUETOOTH ? "  ✓" : ""));
      icons.add(R.drawable.baseline_bluetooth_24);
    }
    ids.add(R.id.btn_routingSpeaker);
    boolean speaker = current == CallSettings.SPEAKER_MODE_SPEAKER || current == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT;
    names.add(Lang.getString(R.string.Tgx101CallSpeaker) + (speaker ? "  ✓" : ""));
    icons.add(R.drawable.baseline_volume_up_24);
    int[] idArray = new int[ids.size()], iconArray = new int[icons.size()];
    for (int i = 0; i < idArray.length; i++) {
      idArray[i] = ids.get(i);
      iconArray[i] = icons.get(i);
    }
    showOptions(null, idArray, names.toArray(new String[0]), null, iconArray, (itemView, id) -> {
      if (call == null || TD.isFinished(call)) return true;
      if (id == R.id.btn_routingBluetooth) {
        callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_BLUETOOTH);
      } else if (id == R.id.btn_routingEarpiece) {
        callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
      } else if (id == R.id.btn_routingSpeaker) {
        callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_SPEAKER);
      }
      if (callVideo != null) {
        callVideo.updateControls();
      }
      return true;
    });
  }

  // TGx101: video turns the loudspeaker on unless headphones or Bluetooth are in use
  private boolean enableSpeakerForVideo () {
    if (call == null || TD.isFinished(call)) return false;
    android.media.AudioManager audio = (android.media.AudioManager) context().getSystemService(Context.AUDIO_SERVICE);
    if (audio == null || audio.isWiredHeadsetOn() || audio.isBluetoothScoOn() || audio.isBluetoothA2dpOn()) return false;
    if (callSettings == null) {
      callSettings = new CallSettings(tdlib, call.id);
    }
    if (!callSettings.isSpeakerModeEnabled()) {
      callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_SPEAKER);
      return true;
    }
    return false;
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setTexts();
      }
    });
  }

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (!isDestroyed()) {
      updateCall(call);
      updateCallState();
    }
  }

  private boolean isClosed;

  private void closeCall () {
    isClosed = true;
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    if (UI.getUiState() != UI.State.RESUMED && getValue() != null) {
      // TGx101: ended while the app was in the background — the back animation only plays on return,
      // so the ended call flashed before the chats; hide it right away
      getValue().setAlpha(0f);
    }
    navigateBack();
  }

  private TdApi.CallState previousCallState;

  private void updateCall (TdApi.Call call) {
    if (isClosed) {
      return;
    }
    this.previousCallState = this.call.state;
    boolean prevIsActive = this.call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    boolean callEnded = call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR || (call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonHungUp.CONSTRUCTOR);

    this.call = call;
    this.callDuration = 0;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    updateCallStrength();
    if (TD.isCancelled(call) || TD.isAcceptedOnOtherDevice(call) || TD.isDeclined(call) || (prevIsActive && callEnded) || TD.isMissed(call) || call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      closeCall();
    } else {
      callControlsLayout.setCall(tdlib, call, navigationController != null);
      if (callVideo != null) {
        callVideo.onCallStateChanged(call); // TGx101
      }
      updateIncomingControls();
    }
  }

  @Override
  public void onCallStateChanged (final int callId, final int newState) {
    if (!isDestroyed()) {
      updateCallState();
    }
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) {
    if (!isDestroyed() && this.call != null && this.call.id == callId) {
      setCallBarsCount(barsCount);
    }
  }

  @Override
  public void onCallSettingsChanged (final int callId, final CallSettings settings) {
    if (!isDestroyed()) {
      callSettings = settings;
      updateCallButtons();
    }
  }

  private boolean isFlashing;
  private FactorAnimator flashAnimator;
  private static final int ANIMATOR_FLASH_ID = 1;

  public static final long CALL_FLASH_DURATION = 1100;
  public static final long CALL_FLASH_DELAY = 650l;

  private final RateLimiter flashLimiter = new RateLimiter(() -> {
    if (isFlashing) {
      flashAnimator.forceFactor(0f);
      if (isFlashing) {
        flashAnimator.animateTo(1f);
      }
    }
  }, 100l, null);

  private void setFlashing (boolean isFlashing) {
    if (this.isFlashing != isFlashing) {
      this.isFlashing = isFlashing;
      if (isFlashing) {
        if (flashAnimator == null) {
          flashAnimator = new FactorAnimator(ANIMATOR_FLASH_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CALL_FLASH_DURATION);
          flashAnimator.setStartDelay(CALL_FLASH_DELAY);
        }
        if (!flashAnimator.isAnimating()) {
          flashAnimator.forceFactor(0f);
          flashAnimator.animateTo(1f);
        }
      } else {
        if (flashAnimator != null && flashAnimator.getFactor() == 0f) {
          flashAnimator.forceFactor(0f);
        }
      }
    }
  }

  private long callDuration;

  private void updateCallState () {
    updateLoop();
    String str;
    callDuration = tdlib.context().calls().getCallDuration(tdlib, call.id);
    if (previousCallState != null && call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      str = TD.getCallState2(call, previousCallState, callDuration, false);
    } else {
      str = TD.getCallState(call, callDuration, false);
      if (!call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && tdlib.context().isMultiUser()) {
        String longName = tdlib.accountLongName();
        if (longName != null) {
          str = str + "\n" + Lang.getString(R.string.VoipAnsweringAsAccount, longName);
        }
      }
    }
    stateView.setText(Lang.uppercase(str));
    setButtonsVisible(!TD.isFinished(call) && !(call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing), isFocused());
    updateEmoji();
    updateFlashing();
    updateCallStrength();
  }

  private boolean hadFocus;

  private void updateFlashing () {
    hadFocus = isFocused() || hadFocus;
    setFlashing(TD.getCallNeedsFlashing(call) && hadFocus);
  }

  @Override
  protected void onFocusStateChanged () {
    updateFlashing();
  }

  private void updateControlsAlpha () {
    float alpha = lastHeaderFactor * buttonsFactor;
    buttonWrap.setAlpha(alpha);
  }

  private void updateCallButtons () {
    if (buttonWrap != null) {
      muteButtonView.setIsActive(callSettings != null && callSettings.isMicMuted(), isFocused());
      speakerButtonView.setIsActive(callSettings != null && callSettings.isSpeakerModeEnabled(), isFocused());
      if (callVideo != null) {
        callVideo.updateControls(); // TGx101
      }
    }
  }

  private void updateEmoji () {
    boolean emojiVisible = (call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
    if (emojiVisible && StringUtils.isEmpty(emojiViewSmall.getText())) {

      TdApi.CallStateReady ready = (TdApi.CallStateReady) call.state;

      StringBuilder b = new StringBuilder();
      for (String emoji : ready.emojis) {
        if (b.length() > 0) {
          b.append("  ");
        }
        b.append(emoji);
      }

      CharSequence result = Emoji.instance().replaceEmoji(b.toString());

      emojiViewSmall.setText(result);
      emojiViewBig.setText(result);

      if (!hadEmojiSinceStart) {
        showEmojiTooltip();
      }
    }
    updateEmojiFactors();
    setEmojiVisible(emojiVisible, isFocused());
  }

  private void showEmojiTooltip () {
    if (!org.thunderdog.challegram.Tgx101Hints.take(org.thunderdog.challegram.Tgx101Hints.CALL_EMOJI)) { // TGx101: twice at most
      return;
    }
    context().tooltipManager().builder(emojiViewSmall).controller(this).show(tdlib, Lang.getStringBold(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  private boolean isEmojiVisible;
  private FactorAnimator emojiVisibilityAnimator;

  private static final int ANIMATOR_EMOJI_VISIBILITY_ID = 3;
  private static final int ANIMATOR_EMOJI_EXPAND_ID = 4;

  private void setEmojiVisible (boolean isVisible, boolean animated) {
    if (this.isEmojiVisible != isVisible) {
      this.isEmojiVisible = isVisible;
      float toFactor = isVisible ? 1f : 0f;

      if (animated) {
        if (emojiVisibilityAnimator == null) {
          emojiVisibilityAnimator = new FactorAnimator(ANIMATOR_EMOJI_VISIBILITY_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION_EMOJI_VISIBILITY, this.emojiVisibilityFactor);
        }
        emojiVisibilityAnimator.animateTo(toFactor);
      } else {
        if (emojiVisibilityAnimator != null) {
          emojiVisibilityAnimator.forceFactor(toFactor);
        }
        setEmojiVisibilityFactor(toFactor);
      }
    }
  }

  private static final long DURATION_EMOJI_VISIBILITY = 180l;

  private boolean isEmojiExpanded;
  private FactorAnimator emojiExpandAnimator;

  private void setEmojiExpanded (boolean isExpanded) {
    if (this.isEmojiExpanded != isExpanded) {
      this.isEmojiExpanded = isExpanded;
      if (emojiExpandAnimator == null) {
        emojiExpandAnimator = new FactorAnimator(ANIMATOR_EMOJI_EXPAND_ID, this, new OvershootInterpolator(1.02f), 310l, this.emojiExpandFactor);
      }
      emojiExpandAnimator.animateTo(isExpanded ? 1f : 0f);
    }
  }

  private float emojiVisibilityFactor;
  private float emojiExpandFactor;

  private void setEmojiVisibilityFactor (float factor) {
    if (this.emojiVisibilityFactor != factor) {
      this.emojiVisibilityFactor = factor;
      updateEmojiFactors();
    }
  }

  private void setEmojiExpandFactor (float factor) {
    if (this.emojiExpandFactor != factor) {
      this.emojiExpandFactor = factor;
      updateEmojiFactors();
    }
  }

  private static final float DESIRED_EMOJI_EXPAND_FACTOR = 2.25f;
  private static float EMOJI_EXPAND_FACTOR = DESIRED_EMOJI_EXPAND_FACTOR;

  private void updateEmojiFactors () {
    emojiViewSmall.setAlpha(MathUtils.clamp(emojiVisibilityFactor * (1f - Math.max(1f - lastHeaderFactor, emojiExpandFactor >= .5f ? (emojiExpandFactor - .5f) / .5f : 0f))));
    emojiViewSmall.setScaleX(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));
    emojiViewSmall.setScaleY(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));

    float bigAlpha = MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor);
    emojiViewBig.setAlpha(bigAlpha);
    emojiViewHint.setAlpha(bigAlpha);
    float factor = 1f / EMOJI_EXPAND_FACTOR;
    emojiViewBig.setScaleX(factor + (1f - factor) * emojiExpandFactor);
    emojiViewBig.setScaleY(factor + (1f - factor) * emojiExpandFactor);
    avatarView.setMainAlpha(1f - MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor));
    updateEmojiPosition();
  }

  private void updateEmojiPosition () {
    final int parentWidth = ((View) emojiViewBig.getParent()).getMeasuredWidth();
    final int parentHeight = ((View) emojiViewBig.getParent()).getMeasuredHeight();

    final int viewWidth = emojiViewBig.getMeasuredWidth();
    final int viewHeight = emojiViewBig.getMeasuredHeight();

    final int viewWidthSmall = emojiViewSmall.getMeasuredWidth();
    final int viewHeightSmall = emojiViewSmall.getMeasuredHeight();

    // TGx101: new call screen — the encryption emoji sit centred between the name and the call timer
    final boolean emojiUnderName = Settings.instance().useNewCallScreen() && photoMode != Settings.CALL_PHOTO_FULL_SCREEN && nameView != null;
    final int startLeft = emojiUnderName ? (parentWidth - viewWidthSmall) / 2 : parentWidth - viewWidthSmall;
    final int startTop = emojiUnderName ? nameView.getBottom() + Screen.dp(2f) - emojiViewSmall.getPaddingTop() : Screen.dp(42f) - emojiViewSmall.getPaddingTop();
    if (emojiUnderName) {
      // The emoji row's place is reserved from the start, so answering a call doesn't move the timer or the photo
      int shift = Screen.dp(TGX101_EMOJI_ROW);
      if (shift != tgx101EmojiShift) {
        tgx101EmojiShift = shift;
        applyTgx101Shifts();
      }
    }

    final int fromCenterX = startLeft + viewWidthSmall / 2;
    final int fromCenterY = startTop + viewHeightSmall / 2;

    final int toCenterX = parentWidth / 2;
    final int toCenterY = parentHeight / 2 - Screen.dp(24f);

    final int centerX = (int) (fromCenterX + (float) (toCenterX - fromCenterX) * emojiExpandFactor);
    final int centerY = (int) (fromCenterY + (float) (toCenterY - fromCenterY) * emojiExpandFactor);

    int x = centerX - viewWidth / 2;
    int y = centerY - viewHeight / 2;

    emojiViewBig.setTranslationX(x);
    emojiViewBig.setTranslationY(y);

    int xSmall = centerX - viewWidthSmall / 2;
    int ySmall = centerY - viewHeightSmall / 2;

    emojiViewSmall.setTranslationX(xSmall);
    emojiViewSmall.setTranslationY(ySmall);
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    // TGx101 (Vivo 0.1.522, 2026-10-07 13:25: «во время входящего клавиатура налезла на звонок»): the chat's input kept the
    // focus and the system brought its keyboard back over the call screen — take the focus away and hide it
    android.view.View focused = context().getCurrentFocus();
    if (focused != null) {
      focused.clearFocus();
      org.thunderdog.challegram.tool.Keyboard.hide(focused);
    }
    context().getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | (context().getWindow().getAttributes().softInputMode & android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST));
    if (!oneShot) {
      destroyStackItemByIdExcludingLast(R.id.controller_call);
      ViewController<?> c = previousStackItem();
      if (c != null && c.getId() == R.id.controller_contacts) {
        destroyStackItemById(R.id.controller_contacts);
      }
      oneShot = true;
    }
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    context().getWindow().setSoftInputMode(org.thunderdog.challegram.config.Config.DEFAULT_WINDOW_PARAMS); // TGx101: back to normal after the call screen
  }

  @Override
  protected int getPopupRestoreColor () {
    return 0xff000000;
  }

  @Override
  protected boolean forceFadeMode () {
    ViewController<?> c = previousStackItem();
    return c != null && c.getId() == R.id.controller_call;
  }

  public void replaceCall (TdApi.Call call) {
    tdlib.cache().unsubscribeFromCallUpdates(this.call.id, this);
    previousCallState = null;
    updateCall(call);
    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
    updateCallState();
  }

  @Override
  public void destroy () {
    super.destroy();
    Screen.removeStatusBarHeightListener(this);
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    tdlib.cache().removeUserDataListener(call.userId, this);
    avatarView.performDestroy();
    if (emojiStatusHelper != null) {
      emojiStatusHelper.detach();
      emojiStatusHelper.performDestroy();
    }
    if (callVideo != null) {
      callVideo.destroy();
    }
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }
}
