/*
 * TGx101 / PlumaGram: full-screen story viewer (stage 1 — viewing).
 * Progress bars on top; tap left = previous, right = next; hold = pause; swipe sideways = next / previous person;
 * swipe down = close. Reply (private message), ♡ reaction, Premium incognito («stealth mode») in the header.
 */
package org.thunderdog.challegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.AvatarView;

import java.util.List;

public class Tgx101StoryViewer extends Dialog {
  private static final long PHOTO_DURATION = 5000L;

  private final Tdlib tdlib;
  private final List<TdApi.ChatActiveStories> people;
  private int personIndex, storyIndex;

  private final FrameLayout root;
  private final ImageView imageView;
  private final VideoView videoView;
  private final ProgressBars bars;
  private final AvatarView avatar;
  private final TextView nameView, timeView, captionView, incognitoView;
  private final EditText replyView;
  private final TextView heartView;

  private @Nullable TdApi.Story story;
  private final android.widget.ProgressBar spinner;
  private long startedAt, pausedAt, duration = PHOTO_DURATION, pausedTotal;
  private boolean paused, loading;
  private int openedStoryId;
  private long openedPosterChatId;

  private boolean forceFromFirst;
  private @Nullable Runnable onClosed;

  public void setOnClosed (@Nullable Runnable onClosed) {
    this.onClosed = onClosed;
  }

  public Tgx101StoryViewer (Context context, Tdlib tdlib, List<TdApi.ChatActiveStories> people, int personIndex) {
    this(context, tdlib, people, personIndex, false);
  }

  /** fromFirst: start at the first story even if it was seen (diagnostics) */
  public Tgx101StoryViewer (Context context, Tdlib tdlib, List<TdApi.ChatActiveStories> people, int personIndex, boolean fromFirst) {
    super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    this.tdlib = tdlib;
    this.people = people;
    this.personIndex = personIndex;
    this.forceFromFirst = fromFirst;

    root = new FrameLayout(context);
    root.setBackgroundColor(Color.BLACK);

    imageView = new ImageView(context);
    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    root.addView(imageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    videoView = new VideoView(context);
    FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
    root.addView(videoView, videoParams);
    videoView.setVisibility(View.GONE);

    // TGx101 (user 2026-10-04): a soft spinner while the story loads, then it fades in instead of popping up
    spinner = new android.widget.ProgressBar(context);
    spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xccffffff));
    root.addView(spinner, new FrameLayout.LayoutParams(Screen.dp(36f), Screen.dp(36f), Gravity.CENTER));

    // top gradient for readability
    View shade = new View(context);
    shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x99000000, 0x00000000}));
    root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(120f), Gravity.TOP));

    bars = new ProgressBars(context);
    FrameLayout.LayoutParams barsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(4f), Gravity.TOP);
    barsParams.topMargin = Screen.dp(10f);
    barsParams.leftMargin = barsParams.rightMargin = Screen.dp(8f);
    root.addView(bars, barsParams);

    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    avatar = new AvatarView(context);
    header.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(34f), Screen.dp(34f)));
    LinearLayout names = new LinearLayout(context);
    names.setOrientation(LinearLayout.VERTICAL);
    nameView = text(context, 14f, Color.WHITE);
    nameView.getPaint().setFakeBoldText(true);
    timeView = text(context, 12f, 0xccffffff);
    names.addView(nameView);
    names.addView(timeView);
    LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    namesParams.leftMargin = Screen.dp(10f);
    header.addView(names, namesParams);
    incognitoView = text(context, 13f, Color.WHITE);
    incognitoView.setText(Lang.getString(R.string.Tgx101StoryIncognito));
    incognitoView.setPadding(Screen.dp(10f), Screen.dp(6f), Screen.dp(10f), Screen.dp(6f));
    GradientDrawable pill = new GradientDrawable();
    pill.setCornerRadius(Screen.dp(14f));
    pill.setColor(0x55000000);
    incognitoView.setBackground(pill);
    incognitoView.setOnClickListener(v -> activateIncognito());
    header.addView(incognitoView);
    TextView close = text(context, 20f, Color.WHITE);
    close.setText("✕");
    close.setPadding(Screen.dp(14f), Screen.dp(4f), Screen.dp(6f), Screen.dp(4f));
    close.setOnClickListener(v -> dismiss());
    header.addView(close);
    FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
    headerParams.topMargin = Screen.dp(22f);
    headerParams.leftMargin = Screen.dp(12f);
    headerParams.rightMargin = Screen.dp(8f);
    root.addView(header, headerParams);

    captionView = text(context, 15f, Color.WHITE);
    captionView.setOnClickListener(v -> setCaptionExpanded(!captionExpanded));
    captionView.setOnTouchListener(new View.OnTouchListener() {
      private float downY;
      @Override
      public boolean onTouch (View v, MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) downY = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_UP && captionExpanded && e.getY() - downY > Screen.dp(40f)) {
          setCaptionExpanded(false); // swipe down folds the text
          return true;
        }
        return false;
      }
    });
    FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    captionParams.leftMargin = captionParams.rightMargin = Screen.dp(10f);
    captionParams.bottomMargin = Screen.dp(72f);
    root.addView(captionView, captionParams);

    LinearLayout bottom = new LinearLayout(context);
    bottom.setOrientation(LinearLayout.HORIZONTAL);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    replyView = new EditText(context);
    replyView.setHint(Lang.getString(R.string.Tgx101StoryReply));
    replyView.setHintTextColor(0xbbffffff);
    replyView.setTextColor(Color.WHITE);
    replyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    replyView.setSingleLine(true);
    replyView.setImeOptions(EditorInfo.IME_ACTION_SEND);
    replyView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    replyView.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
    GradientDrawable field = new GradientDrawable();
    field.setCornerRadius(Screen.dp(22f));
    field.setStroke(Screen.dp(1f), 0x88ffffff);
    replyView.setBackground(field);
    replyView.setOnFocusChangeListener((v, hasFocus) -> setPaused(hasFocus));
    replyView.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendReply();
        return true;
      }
      return false;
    });
    bottom.addView(replyView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    heartView = text(context, 26f, Color.WHITE);
    heartView.setText("♡");
    heartView.setPadding(Screen.dp(14f), 0, Screen.dp(6f), 0);
    heartView.setOnClickListener(v -> toggleHeart());
    bottom.addView(heartView);
    FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    bottomParams.leftMargin = Screen.dp(12f);
    bottomParams.rightMargin = Screen.dp(10f);
    bottomParams.bottomMargin = Screen.dp(16f);
    root.addView(bottom, bottomParams);

    root.setOnTouchListener(new GestureHandler());
    // keep the reply field and ♡ above the system navigation bar
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      bottomParams.bottomMargin = Screen.dp(16f) + insets.getSystemWindowInsetBottom();
      bottom.setLayoutParams(bottomParams);
      captionParams.bottomMargin = Screen.dp(72f) + insets.getSystemWindowInsetBottom();
      captionView.setLayoutParams(captionParams);
      return insets;
    });
    setContentView(root);
    // the status bar is shown now (black): keep the header below it
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getStableInsetBottom());
      return insets;
    });
    Window window = getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
      // user 2026-10-04: typing a reply relaid the video (it glitched) — pan the window instead of resizing it
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
      if (sBrightness >= 0f && android.os.SystemClock.uptimeMillis() - sBrightnessAt < BRIGHTNESS_KEEP_MS) {
        WindowManager.LayoutParams wl = window.getAttributes();
        wl.screenBrightness = sBrightness;
        window.setAttributes(wl);
      }
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      // user 2026-10-05: content is on screen — black status and navigation bars with light icons, not the app colour
      // the fullscreen theme left the status bar to the activity (its blue header showed, user's Vivo video 14:05):
      // this window draws its own bars
      window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
      window.setStatusBarColor(Color.BLACK);
      window.setNavigationBarColor(Color.BLACK);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
      }
    }
    setOnDismissListener(d -> {
      Tgx101Diag.mark("story viewer: closed");
      if (onClosed != null) onClosed.run();
      closeOpened();
      root.removeCallbacks(ticker);
      videoView.stopPlayback();
    });
    startPerson(personIndex, true);
  }

  private static TextView text (Context context, float sizeDp, int color) {
    TextView view = new TextView(context);
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
    view.setTextColor(color);
    return view;
  }

  // Navigation

  private void startPerson (int index, boolean fromStart) {
    if (index < 0 || index >= people.size()) {
      dismiss();
      return;
    }
    personIndex = index;
    TdApi.ChatActiveStories person = people.get(index);
    // start from the first unseen story, like the official apps
    int start = 0;
    if (fromStart) {
      if (!forceFromFirst) {
        for (int i = 0; i < person.stories.length; i++) {
          if (person.stories[i].storyId > person.maxReadStoryId) { start = i; break; }
        }
      }
    } else {
      start = person.stories.length - 1;
    }
    TdApi.Chat chat = tdlib.chat(person.chatId);
    avatar.setChat(tdlib, chat);
    nameView.setText(chat != null ? chat.title : "");
    bars.setCount(person.stories.length);
    forceFromFirst = false;
    showStory(start);
  }

  private void showStory (int index) {
    TdApi.ChatActiveStories person = people.get(personIndex);
    Tgx101Diag.mark("story: show " + index + " of " + person.stories.length + " (person " + personIndex + "/" + people.size() + ")");
    if (index < 0) {
      if (personIndex > 0) startPerson(personIndex - 1, false); else restart();
      return;
    }
    if (index >= person.stories.length) {
      startPerson(personIndex + 1, true);
      return;
    }
    storyIndex = index;
    bars.setPosition(index, 0f);
    TdApi.StoryInfo info = person.stories[index];
    loading = true;
    story = null;
    root.removeCallbacks(ticker);
    tdlib.send(new TdApi.GetStory(person.chatId, info.storyId, false), (result, error) -> UI.post(() -> {
      if (!isShowing() || storyIndex != index || people.get(personIndex) != person) return;
      if (result == null) {
        Tgx101Diag.mark("story: load failed " + (error != null ? error.message : ""));
        showStory(index + 1);
        return;
      }
      present(result);
    }));
  }

  private void restart () {
    showStory(0);
  }

  private void present (TdApi.Story story) {
    this.story = story;
    closeOpened();
    openedPosterChatId = story.posterChatId;
    openedStoryId = story.id;
    tdlib.send(new TdApi.OpenStory(story.posterChatId, story.id), (ok, error) -> { });
    timeView.setText(Lang.getRelativeTimestamp(story.date, java.util.concurrent.TimeUnit.SECONDS));
    tgx101SetCaption(story.caption != null ? story.caption.text : "");
    replyView.setVisibility(story.canBeReplied ? View.VISIBLE : View.INVISIBLE);
    heartView.setText(story.chosenReactionType != null ? "♥" : "♡");
    heartView.setTextColor(story.chosenReactionType != null ? 0xffff4d6d : Color.WHITE);
    updateIncognito();

    TdApi.File file = Tgx101Stories.bestFile(story.content);
    boolean isVideo = story.content instanceof TdApi.StoryContentVideo;
    duration = isVideo ? Math.max(1000L, (long) (((TdApi.StoryContentVideo) story.content).video.duration * 1000)) : PHOTO_DURATION;
    imageView.animate().cancel();
    imageView.setAlpha(0f);
    videoView.setAlpha(0f);
    spinner.setVisibility(View.VISIBLE);
    videoView.setVisibility(View.GONE);
    if (file == null) {
      showStory(storyIndex + 1);
      return;
    }
    final int expectStoryId = story.id;
    tdlib.send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), (downloaded, error) -> {
      String path = downloaded != null && downloaded.local != null && downloaded.local.isDownloadingCompleted ? downloaded.local.path : null;
      Bitmap bitmap = !isVideo && path != null ? decode(path) : null;
      UI.post(() -> {
        if (!isShowing() || this.story == null || this.story.id != expectStoryId) return;
        if (path == null) {
          Tgx101Diag.mark("story: download failed");
          showStory(storyIndex + 1);
          return;
        }
        loading = false;
        spinner.setVisibility(View.GONE);
        if (isVideo) {
          videoView.setVisibility(View.VISIBLE);
          videoView.setVideoPath(path);
          videoView.setOnPreparedListener(mp -> {
            duration = Math.max(1000L, mp.getDuration());
            videoView.animate().alpha(1f).setDuration(220).start();
            if (!paused) mp.start();
            startTimer();
          });
          videoView.setOnCompletionListener(mp -> showStory(storyIndex + 1));
          videoView.setOnErrorListener((mp, what, extra) -> {
            Tgx101Diag.mark("story: video error " + what);
            showStory(storyIndex + 1);
            return true;
          });
        } else {
          imageView.setImageBitmap(bitmap);
          imageView.animate().alpha(1f).setDuration(220).start();
          startTimer();
        }
      });
    });
  }

  // Caption (user 2026-10-04): 2 lines with «more» at the bottom; a tap opens the whole text on a dark backdrop,
  // a swipe down (or a tap) folds it back with a light animation

  private String captionText = "";
  private boolean captionExpanded;

  private void tgx101SetCaption (String text) {
    captionText = text != null ? text.trim() : "";
    captionExpanded = false;
    captionView.setVisibility(captionText.isEmpty() ? View.GONE : View.VISIBLE);
    applyCaption(false);
  }

  private void applyCaption (boolean animate) {
    if (captionText.isEmpty()) return;
    if (captionExpanded) {
      captionView.setMaxLines(Integer.MAX_VALUE);
      captionView.setEllipsize(null);
      captionView.setText(captionText);
      captionView.setBackground(tgx101Capsule());
      captionView.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
    } else {
      captionView.setMaxLines(2);
      captionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
      captionView.setBackground(tgx101Capsule());
      captionView.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
      android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(captionText);
      captionView.setText(b);
      captionView.post(() -> {
        android.text.Layout layout = captionView.getLayout();
        if (!captionExpanded && layout != null && (layout.getLineCount() > 2 || (layout.getLineCount() == 2 && layout.getEllipsisCount(1) > 0))) {
          // cut the second line and add «… ещё» in the accent colour
          int end = layout.getLineEnd(1);
          String more = "… " + Lang.getString(R.string.Tgx101StoryMore);
          int cut = Math.max(layout.getLineStart(1), end - more.length() - 2);
          android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(captionText.substring(0, cut).trim());
          int start = sb.length();
          sb.append(more);
          sb.setSpan(new android.text.style.ForegroundColorSpan(0xff8fd0ff), start, sb.length(), 0);
          sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start + 2, sb.length(), 0);
          captionView.setText(sb);
        }
      });
    }
    if (animate) {
      captionView.setAlpha(.4f);
      captionView.setTranslationY(captionExpanded ? Screen.dp(24f) : -Screen.dp(12f));
      captionView.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
  }

  /** The same dark glass capsule as the «Add a caption» field (user 2026-10-04) */
  private static GradientDrawable tgx101Capsule () {
    GradientDrawable capsule = new GradientDrawable();
    capsule.setColor(0xb3141a20);
    capsule.setCornerRadius(Screen.dp(20f));
    capsule.setStroke(Screen.dp(.5f), 0x33ffffff);
    return capsule;
  }

  private void setCaptionExpanded (boolean expanded) {
    if (captionExpanded == expanded || captionText.isEmpty()) return;
    captionExpanded = expanded;
    setPaused(expanded);
    applyCaption(true);
  }

  @Nullable
  private static Bitmap decode (String path) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, bounds);
    int sample = 1;
    int max = Math.max(bounds.outWidth, bounds.outHeight);
    while (max / sample > 2560) sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    return BitmapFactory.decodeFile(path, options);
  }

  private void closeOpened () {
    if (openedStoryId != 0) {
      tdlib.send(new TdApi.CloseStory(openedPosterChatId, openedStoryId), (ok, error) -> { });
      openedStoryId = 0;
    }
  }

  // Progress

  private final Runnable ticker = new Runnable() {
    @Override
    public void run () {
      if (!isShowing() || loading) return;
      long elapsed = SystemClock.uptimeMillis() - startedAt - pausedTotal;
      float progress = Math.min(1f, elapsed / (float) duration);
      bars.setPosition(storyIndex, progress);
      if (progress >= 1f && !(story != null && story.content instanceof TdApi.StoryContentVideo)) {
        showStory(storyIndex + 1);
        return;
      }
      if (!paused) root.postDelayed(this, 16);
    }
  };

  private void startTimer () {
    startedAt = SystemClock.uptimeMillis();
    pausedTotal = 0;
    root.removeCallbacks(ticker);
    if (!paused) root.post(ticker);
  }

  private void setPaused (boolean paused) {
    if (this.paused == paused) return;
    this.paused = paused;
    if (paused) {
      pausedAt = SystemClock.uptimeMillis();
      root.removeCallbacks(ticker);
      if (videoView.isPlaying()) videoView.pause();
    } else {
      pausedTotal += SystemClock.uptimeMillis() - pausedAt;
      if (videoView.getVisibility() == View.VISIBLE) videoView.start();
      root.post(ticker);
    }
  }

  // Actions

  private void sendReply () {
    String text = replyView.getText().toString().trim();
    TdApi.Story story = this.story;
    if (text.isEmpty() || story == null) return;
    TdApi.InputMessageContent content = new TdApi.InputMessageText(new TdApi.FormattedText(text, null), null, false);
    tdlib.send(new TdApi.SendMessage(story.posterChatId, null, new TdApi.InputMessageReplyToStory(story.posterChatId, story.id), null, null, content), (message, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        UI.showToast(R.string.Tgx101StoryReplySent, android.widget.Toast.LENGTH_SHORT);
      }
    }));
    replyView.setText("");
    replyView.clearFocus();
    Keyboard.hide(replyView);
  }

  private void toggleHeart () {
    TdApi.Story story = this.story;
    if (story == null) return;
    boolean set = story.chosenReactionType == null;
    TdApi.ReactionType reaction = set ? new TdApi.ReactionTypeEmoji("❤") : null;
    tdlib.send(new TdApi.SetStoryReaction(story.posterChatId, story.id, reaction, true), (ok, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      story.chosenReactionType = reaction;
      heartView.setText(set ? "♥" : "♡");
      heartView.setTextColor(set ? 0xffff4d6d : Color.WHITE);
    }));
  }

  // Premium incognito: Telegram hides your views for the last 5 and the next 25 minutes

  private void updateIncognito () {
    boolean premium = tdlib.hasPremium();
    incognitoView.setVisibility(premium ? View.VISIBLE : View.GONE);
  }

  private void activateIncognito () {
    tdlib.send(new TdApi.ActivateStoryStealthMode(), (ok, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        Tgx101Diag.mark("story: incognito on");
        UI.showToast(R.string.Tgx101StoryIncognitoOn, android.widget.Toast.LENGTH_LONG);
      }
    }));
  }

  // Gestures

  // Brightness / volume (user 2026-10-04): hold → pause with a light vibration, then move up / down — left half
  // brightness, right half volume. The brightness stays for the next stories and resets after a while.
  private static float sBrightness = -1f;
  private static long sBrightnessAt;
  private static final long BRIGHTNESS_KEEP_MS = 10 * 60 * 1000L;

  private final class GestureHandler implements View.OnTouchListener {
    private float downX, downY, adjustStartY, startBrightness;
    private int startVolume;
    private long downTime;
    private boolean moved, holding, adjusting;

    private final Runnable hold = () -> {
      holding = true;
      setPaused(true);
      root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    };

    private void adjust (View v, float y) {
      float delta = (adjustStartY - y) / (v.getHeight() * .6f);
      if (downX < v.getWidth() / 2f) {
        Window window = getWindow();
        if (window == null) return;
        float value = Math.max(.02f, Math.min(1f, startBrightness + delta));
        WindowManager.LayoutParams wl = window.getAttributes();
        wl.screenBrightness = value;
        window.setAttributes(wl);
        sBrightness = value;
        sBrightnessAt = SystemClock.uptimeMillis();
        showLevel("☀ " + Math.round(value * 100) + " %");
      } else {
        android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int value = Math.max(0, Math.min(max, startVolume + Math.round(delta * max)));
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, value, 0);
        showLevel("🔊 " + Math.round(value * 100f / max) + " %");
      }
    }

    @Override
    public boolean onTouch (View v, MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          adjusting = false;
          Tgx101Diag.mark("story: touch down " + Math.round(e.getX()) + "," + Math.round(e.getY()));
          downX = e.getX();
          downY = e.getY();
          downTime = SystemClock.uptimeMillis();
          moved = holding = false;
          root.postDelayed(hold, 250);
          return true;
        case MotionEvent.ACTION_MOVE: {
          float dx = e.getX() - downX, dy = e.getY() - downY;
          if (holding) {
            if (!adjusting && Math.abs(dy) > Screen.getTouchSlop()) {
              adjusting = true;
              adjustStartY = e.getY();
              Window window = getWindow();
              float current = window != null ? window.getAttributes().screenBrightness : -1f;
              startBrightness = current >= 0f ? current : .5f;
              android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
              startVolume = am != null ? am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) : 0;
            }
            if (adjusting) adjust(v, e.getY());
            return true;
          }
          if (!moved && Math.hypot(dx, dy) > Screen.getTouchSlop()) {
            moved = true;
            root.removeCallbacks(hold);
          }
          if (moved && dy > 0 && Math.abs(dy) > Math.abs(dx)) {
            root.setTranslationY(dy * .8f);
            root.setAlpha(1f - Math.min(.5f, dy / (float) root.getHeight()));
          }
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          root.removeCallbacks(hold);
          float dx = e.getX() - downX, dy = e.getY() - downY;
          if (holding) {
            holding = false;
            adjusting = false;
            hideLevel();
            setPaused(false);
            return true;
          }
          if (moved) {
            if (dy > Screen.dp(120f) && dy > Math.abs(dx)) {
              dismiss();
            } else {
              root.animate().translationY(0f).alpha(1f).setDuration(150).start();
              if (Math.abs(dx) > Screen.dp(80f) && Math.abs(dx) > Math.abs(dy)) {
                startPerson(personIndex + (dx < 0 ? 1 : -1), true);
              }
            }
            return true;
          }
          if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            Tgx101Diag.mark("story: tap " + (downX < v.getWidth() * .3f ? "left" : "right"));
            if (downX < v.getWidth() * .3f) showStory(storyIndex - 1); else showStory(storyIndex + 1);
          }
          return true;
        }
      }
      return false;
    }
  }

  private @Nullable TextView levelView;

  private void showLevel (String text) {
    if (levelView == null) {
      levelView = text(getContext(), 15f, Color.WHITE);
      levelView.setPadding(Screen.dp(14f), Screen.dp(8f), Screen.dp(14f), Screen.dp(8f));
      levelView.setBackground(tgx101Capsule());
      root.addView(levelView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
      ((FrameLayout.LayoutParams) levelView.getLayoutParams()).topMargin = Screen.dp(90f);
    }
    levelView.setText(text);
    levelView.setVisibility(View.VISIBLE);
  }

  private void hideLevel () {
    if (levelView != null) levelView.setVisibility(View.GONE);
  }

  // Progress bars

  private static final class ProgressBars extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int count = 1, position;
    private float progress;

    ProgressBars (Context context) {
      super(context);
    }

    void setCount (int count) {
      this.count = Math.max(1, count);
      invalidate();
    }

    void setPosition (int position, float progress) {
      this.position = position;
      this.progress = progress;
      invalidate();
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      float gap = Screen.dp(3f);
      float width = (getWidth() - gap * (count - 1)) / count;
      float h = Screen.dp(2.5f), top = (getHeight() - h) / 2f, r = h / 2f;
      for (int i = 0; i < count; i++) {
        float left = i * (width + gap);
        rect.set(left, top, left + width, top + h);
        paint.setColor(0x59ffffff);
        c.drawRoundRect(rect, r, r, paint);
        float fill = i < position ? 1f : i == position ? progress : 0f;
        if (fill > 0f) {
          rect.set(left, top, left + width * fill, top + h);
          paint.setColor(Color.WHITE);
          c.drawRoundRect(rect, r, r, paint);
        }
      }
    }
  }
}
