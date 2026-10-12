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

  // TGx101: where the person's circle is (kept for later use; the open/close animation is a calm fade now)

  public interface TargetProvider {
    /** The on-screen bounds of this chat's story circle, or null */
    @Nullable android.graphics.Rect targetFor (long chatId);
  }

  private @Nullable TargetProvider targetProvider;
  private boolean closing;

  public void setTargetProvider (@Nullable TargetProvider provider) {
    this.targetProvider = provider;
  }

  // TGx101 (user's video 2026-10-12 12:31 «лучше без анимаций, чем такие»): the grow-from / shrink-into-the-circle jumped
  // (an offset rounded box, then a jump into place). Calm instead: a quick fade in; a quick fade out; after a swipe down
  // the story goes on down with the finger and fades

  private void animateOpen () {
    root.setAlpha(0f);
    root.animate().alpha(1f).setDuration(140).start();
  }

  @Override
  public void dismiss () {
    if (closing || !isShowing() || root.getWidth() == 0) {
      if (!closing) super.dismiss();
      return;
    }
    closing = true;
    setPaused(true);
    Window window = getWindow();
    if (window != null && android.os.Build.VERSION.SDK_INT >= 21) {
      window.setStatusBarColor(Color.TRANSPARENT);
      window.setNavigationBarColor(Color.TRANSPARENT);
    }
    boolean swiped = root.getTranslationY() > Screen.dp(24f);
    root.animate()
      .translationY(swiped ? root.getHeight() * .6f : root.getTranslationY())
      .alpha(0f)
      .setDuration(swiped ? 200 : 140)
      .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
      .withEndAction(super::dismiss)
      .start();
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

    // TGx101: reaction stickers (suggested reaction areas) — apps draw them over the media themselves
    areasView = new AreasView(context);
    root.addView(areasView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // TGx101 (user 2026-10-04): a soft spinner while the story loads, then it fades in instead of popping up
    spinner = new android.widget.ProgressBar(context);
    if (android.os.Build.VERSION.SDK_INT >= 21) spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xccffffff));
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
    // TGx101 (user 2026-10-12): a button of a fixed size — the reply field no longer moves when the reaction changes;
    // the empty one is a proper outlined heart, not a thin «♡» glyph
    heartButton = new FrameLayout(context);
    heartIcon = new android.widget.ImageView(context);
    heartIcon.setImageResource(R.drawable.tgx101_heart_outline_24);
    heartIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    heartButton.addView(heartIcon, new FrameLayout.LayoutParams(Screen.dp(28f), Screen.dp(28f), Gravity.CENTER));
    heartView = text(context, 24f, Color.WHITE);
    heartView.setGravity(Gravity.CENTER);
    heartView.setIncludeFontPadding(false);
    heartView.setVisibility(View.GONE);
    heartButton.addView(heartView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    heartButton.setOnClickListener(v -> toggleHeart());
    heartButton.setOnLongClickListener(v -> { showReactionPicker(); return true; }); // any reaction, as in the official app
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f));
    hp.leftMargin = Screen.dp(6f);
    bottom.addView(heartButton, hp);
    FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    bottomParams.leftMargin = Screen.dp(12f);
    bottomParams.rightMargin = Screen.dp(10f);
    bottomParams.bottomMargin = Screen.dp(16f);
    root.addView(bottom, bottomParams);

    root.setOnTouchListener(new GestureHandler());
    // keep the reply field and ♡ above the system navigation bar
    if (android.os.Build.VERSION.SDK_INT >= 21) root.setOnApplyWindowInsetsListener((v, insets) -> {
      bottomParams.bottomMargin = Screen.dp(16f) + insets.getSystemWindowInsetBottom();
      bottom.setLayoutParams(bottomParams);
      captionParams.bottomMargin = Screen.dp(72f) + insets.getSystemWindowInsetBottom();
      captionView.setLayoutParams(captionParams);
      return insets;
    });
    setContentView(root);
    // the status bar is shown now (black): keep the header below it
    if (android.os.Build.VERSION.SDK_INT >= 21) root.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getStableInsetBottom());
      return insets;
    });
    Window window = getWindow();
    if (window != null) {
      // transparent window, black content: the closing story shrinks into its circle over the chat list
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
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
      if (android.os.Build.VERSION.SDK_INT >= 21) {
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
      }
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
    animateOpen();
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
    tgx101ShowStartedAt = android.os.SystemClock.uptimeMillis();
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

  private long tgx101ShowStartedAt;
  private final java.util.HashSet<Integer> tgx101Prefetched = new java.util.HashSet<>();

  // TGx101: the next two stories of this person and the first one of the next person are downloaded ahead (low priority),
  // so swiping on shows them at once like in the official app
  private void tgx101Prefetch () {
    TdApi.ChatActiveStories person = personIndex < people.size() ? people.get(personIndex) : null;
    if (person == null) return;
    for (int i = storyIndex + 1; i <= storyIndex + 2 && i < person.stories.length; i++) {
      tgx101PrefetchStory(person.chatId, person.stories[i].storyId);
    }
    if (personIndex + 1 < people.size()) {
      TdApi.ChatActiveStories next = people.get(personIndex + 1);
      if (next.stories.length > 0) {
        int first = 0;
        for (int i = 0; i < next.stories.length; i++) {
          if (next.stories[i].storyId > next.maxReadStoryId) { first = i; break; }
        }
        tgx101PrefetchStory(next.chatId, next.stories[first].storyId);
      }
    }
  }

  private void tgx101PrefetchStory (long chatId, int storyId) {
    if (!tgx101Prefetched.add(storyId ^ (int) chatId)) return;
    tdlib.send(new TdApi.GetStory(chatId, storyId, false), (result, error) -> {
      if (result == null) return;
      TdApi.File file = Tgx101Stories.bestFile(result.content);
      if (file != null && !file.local.isDownloadingCompleted) {
        tdlib.send(new TdApi.DownloadFile(file.id, 1, 0, 0, false), (ok, err) -> { });
      }
    });
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
    showChosenReaction(story.chosenReactionType);
    areasView.setStory(story);
    updateIncognito();

    TdApi.File file = Tgx101Stories.bestFile(story.content);
    boolean isVideo = story.content instanceof TdApi.StoryContentVideo;
    duration = isVideo ? Math.max(1000L, (long) (((TdApi.StoryContentVideo) story.content).video.duration * 1000)) : PHOTO_DURATION;
    // TGx101 (user 2026-10-12 «превью нужно убрать, как в экстере»): no blurry preview while switching — the previous
    // picture stays until the next one is ready and is swapped at once; only a slow load turns to black with a spinner
    imageView.animate().cancel();
    videoView.setAlpha(0f);
    videoView.setVisibility(View.GONE);
    spinner.setVisibility(View.GONE);
    final int waitingStoryId = story.id;
    UI.post(() -> {
      if (this.story == null || this.story.id != waitingStoryId || !loading) return;
      imageView.animate().alpha(0f).setDuration(120).start();
      spinner.setVisibility(View.VISIBLE);
    }, 350);
    tgx101Prefetch();
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
        Tgx101Diag.mark("story: shown in " + (android.os.SystemClock.uptimeMillis() - tgx101ShowStartedAt) + " ms (" + (isVideo ? "video" : "photo") + ", " + (downloaded.size / 1024) + " KB)");
        if (isVideo) {
          videoView.setVisibility(View.VISIBLE);
          videoView.setVideoPath(path);
          videoView.setOnPreparedListener(mp -> {
            duration = Math.max(1000L, mp.getDuration());
            videoView.animate().alpha(1f).setDuration(220).start();
            mp.setOnInfoListener((player, what, extra) -> {
              if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) imageView.animate().alpha(0f).setDuration(120).start(); // the preview under the video goes
              return false;
            });
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
          imageView.animate().cancel();
          imageView.setImageBitmap(bitmap);
          imageView.setAlpha(1f);
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

  // TGx101 (user 2026-10-12 «реакцию переделай как в официальном»): a tap — ❤️ with a big heart bursting in the middle,
  // again — off; a hold — the row of reactions to choose any; the button shows the reaction chosen

  private void toggleHeart () {
    TdApi.Story story = this.story;
    if (story == null) return;
    setReaction(story.chosenReactionType == null ? new TdApi.ReactionTypeEmoji("❤") : null);
  }

  private void setReaction (@Nullable TdApi.ReactionType reaction) {
    TdApi.Story story = this.story;
    if (story == null) return;
    TdApi.ReactionType previous = story.chosenReactionType;
    story.chosenReactionType = reaction;
    showChosenReaction(reaction);
    if (reaction != null) burst(reactionText(reaction));
    tdlib.send(new TdApi.SetStoryReaction(story.posterChatId, story.id, reaction, true), (ok, error) -> UI.post(() -> {
      if (error != null) {
        story.chosenReactionType = previous;
        showChosenReaction(previous);
        UI.showError(error);
      }
    }));
  }

  private static String reactionText (@Nullable TdApi.ReactionType reaction) {
    if (reaction instanceof TdApi.ReactionTypeEmoji) {
      String e = ((TdApi.ReactionTypeEmoji) reaction).emoji;
      return "❤".equals(e) ? "❤️" : e;
    }
    return "❤️";
  }

  private AreasView areasView;

  /** Draws the story's reaction stickers where the author put them and reacts on a tap */
  private final class AreasView extends View {
    private @Nullable TdApi.Story shown;
    private final android.graphics.RectF media = new android.graphics.RectF();

    AreasView (Context context) {
      super(context);
    }

    void setStory (TdApi.Story story) {
      shown = story;
      invalidate();
    }

    private boolean hasAreas () {
      return shown != null && shown.areas != null && shown.areas.length > 0;
    }

    /** Where the 9:16 media is on screen (fit, centred) */
    private void measureMedia () {
      float w = getWidth(), h = getHeight();
      float mw = 9f, mh = 16f;
      if (shown != null && shown.content instanceof TdApi.StoryContentPhoto) {
        TdApi.PhotoSize[] sizes = ((TdApi.StoryContentPhoto) shown.content).photo.sizes;
        if (sizes != null && sizes.length > 0) { mw = sizes[sizes.length - 1].width; mh = sizes[sizes.length - 1].height; }
      } else if (shown != null && shown.content instanceof TdApi.StoryContentVideo) {
        TdApi.StoryVideo v = ((TdApi.StoryContentVideo) shown.content).video;
        if (v != null && v.width > 0) { mw = v.width; mh = v.height; }
      }
      float scale = Math.min(w / mw, h / mh);
      float dw = mw * scale, dh = mh * scale;
      media.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (!hasAreas()) return;
      measureMedia();
      for (TdApi.StoryArea area : shown.areas) {
        if (area.type instanceof TdApi.StoryAreaTypeWeather) {
          // the weather sticker is drawn by the apps, like reactions
          TdApi.StoryAreaTypeWeather w = (TdApi.StoryAreaTypeWeather) area.type;
          float wx = media.left + media.width() * (float) area.position.xPercentage / 100f;
          float wy = media.top + media.height() * (float) area.position.yPercentage / 100f;
          float wh = media.height() * (float) area.position.heightPercentage / 100f;
          int ws = c.save();
          c.rotate((float) area.position.rotationAngle, wx, wy);
          Tgx101StoryEditor.drawWeather(c, wx, wy, wh, w.emoji, w.temperature, w.backgroundColor);
          c.restoreToCount(ws);
          continue;
        }
        if (!(area.type instanceof TdApi.StoryAreaTypeSuggestedReaction)) continue;
        TdApi.StoryAreaTypeSuggestedReaction r = (TdApi.StoryAreaTypeSuggestedReaction) area.type;
        float cx = media.left + media.width() * (float) area.position.xPercentage / 100f;
        float cy = media.top + media.height() * (float) area.position.yPercentage / 100f;
        float size = media.width() * (float) area.position.widthPercentage / 100f;
        int save = c.save();
        c.rotate((float) area.position.rotationAngle, cx, cy);
        Tgx101StoryEditor.drawReactionBubble(c, cx, cy, size, reactionText(r.reactionType), r.isDark, r.isFlipped, r.totalCount);
        c.restoreToCount(save);
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!hasAreas()) return false;
      measureMedia();
      for (TdApi.StoryArea area : shown.areas) {
        if (area.type instanceof TdApi.StoryAreaTypeLink || area.type instanceof TdApi.StoryAreaTypeLocation || area.type instanceof TdApi.StoryAreaTypeVenue) {
          // a link / place sticker baked into the picture: its area opens it
          float ax = media.left + media.width() * (float) area.position.xPercentage / 100f;
          float ay = media.top + media.height() * (float) area.position.yPercentage / 100f;
          float hw = media.width() * (float) area.position.widthPercentage / 200f, hh = media.height() * (float) area.position.heightPercentage / 200f;
          if (Math.abs(e.getX() - ax) <= hw && Math.abs(e.getY() - ay) <= hh) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) openArea(area.type);
            return true;
          }
          continue;
        }
        if (!(area.type instanceof TdApi.StoryAreaTypeSuggestedReaction)) continue;
        float cx = media.left + media.width() * (float) area.position.xPercentage / 100f;
        float cy = media.top + media.height() * (float) area.position.yPercentage / 100f;
        float half = media.width() * (float) area.position.widthPercentage / 200f;
        if (Math.abs(e.getX() - cx) <= half && Math.abs(e.getY() - cy) <= half) {
          if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            TdApi.StoryAreaTypeSuggestedReaction r = (TdApi.StoryAreaTypeSuggestedReaction) area.type;
            boolean same = shown.chosenReactionType != null && reactionText(shown.chosenReactionType).equals(reactionText(r.reactionType));
            if (!same) { r.totalCount++; setReaction(r.reactionType); }
            invalidate();
          }
          return true;
        }
      }
      return false;
    }
  }

  private void openArea (TdApi.StoryAreaType type) {
    String url = null;
    if (type instanceof TdApi.StoryAreaTypeLink) {
      url = ((TdApi.StoryAreaTypeLink) type).url;
    } else {
      TdApi.Location l = type instanceof TdApi.StoryAreaTypeLocation ? ((TdApi.StoryAreaTypeLocation) type).location
        : ((TdApi.StoryAreaTypeVenue) type).venue.location;
      url = "geo:" + l.latitude + "," + l.longitude + "?q=" + l.latitude + "," + l.longitude;
    }
    Tgx101Diag.mark("story: area opened " + type.getClass().getSimpleName());
    final String target = url;
    dismiss();
    UI.post(() -> {
      if (target.startsWith("geo:")) {
        try {
          getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
      } else {
        UI.openUrl(target);
      }
    }, 160);
  }

  private FrameLayout heartButton;
  private android.widget.ImageView heartIcon;

  private void showChosenReaction (@Nullable TdApi.ReactionType reaction) {
    heartIcon.setVisibility(reaction == null ? View.VISIBLE : View.GONE);
    heartView.setVisibility(reaction == null ? View.GONE : View.VISIBLE);
    if (reaction != null) heartView.setText(reactionText(reaction));
    heartButton.setScaleX(.6f);
    heartButton.setScaleY(.6f);
    heartButton.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
  }

  /** The chosen reaction flies up big in the middle and melts */
  private void burst (String emoji) {
    TextView big = new TextView(getContext());
    big.setText(emoji);
    big.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 110f);
    big.setGravity(Gravity.CENTER);
    root.addView(big, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    big.setScaleX(.3f);
    big.setScaleY(.3f);
    big.setAlpha(0f);
    big.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(240).setInterpolator(new android.view.animation.OvershootInterpolator(2f))
      .withEndAction(() -> big.animate().translationY(-Screen.dp(120f)).alpha(0f).scaleX(.8f).scaleY(.8f).setStartDelay(200).setDuration(420)
        .withEndAction(() -> root.removeView(big)).start()).start();
  }

  private @Nullable View reactionPicker;

  private void showReactionPicker () {
    TdApi.Story story = this.story;
    if (story == null || reactionPicker != null) return;
    setPaused(true);
    tdlib.send(new TdApi.GetStoryAvailableReactions(8), (reactions, error) -> UI.post(() -> {
      java.util.ArrayList<TdApi.ReactionType> types = new java.util.ArrayList<>();
      if (reactions != null) {
        for (TdApi.AvailableReaction[] group : new TdApi.AvailableReaction[][] {reactions.topReactions, reactions.recentReactions, reactions.popularReactions}) {
          if (group == null) continue;
          for (TdApi.AvailableReaction r : group) {
            if (r.type instanceof TdApi.ReactionTypeEmoji && types.size() < 24) {
              boolean dup = false;
              for (TdApi.ReactionType t : types) dup |= ((TdApi.ReactionTypeEmoji) t).emoji.equals(((TdApi.ReactionTypeEmoji) r.type).emoji);
              if (!dup) types.add(r.type);
            }
          }
        }
      }
      if (types.isEmpty()) for (String e : new String[] {"❤", "👍", "🔥", "😂", "😮", "😢", "🎉", "👏"}) types.add(new TdApi.ReactionTypeEmoji(e));
      android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(getContext());
      scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout row = new LinearLayout(getContext());
      row.setPadding(Screen.dp(8f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
      for (TdApi.ReactionType type : types) {
        TextView e = new TextView(getContext());
        e.setText(reactionText(type));
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28f);
        e.setGravity(Gravity.CENTER);
        e.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
        e.setOnClickListener(v -> { hideReactionPicker(); setReaction(type); });
        row.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(52f)));
      }
      scroll.addView(row);
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(0xe61d2733);
      bg.setCornerRadius(Screen.dp(26f));
      scroll.setBackground(bg);
      FrameLayout dim = new FrameLayout(getContext());
      dim.setOnClickListener(v -> hideReactionPicker());
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(60f), Gravity.BOTTOM);
      lp.leftMargin = lp.rightMargin = Screen.dp(12f);
      lp.bottomMargin = Screen.dp(76f);
      dim.addView(scroll, lp);
      root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      reactionPicker = dim;
      scroll.setTranslationY(Screen.dp(20f));
      scroll.setAlpha(0f);
      scroll.animate().translationY(0f).alpha(1f).setDuration(200).start();
    }));
  }

  private void hideReactionPicker () {
    View p = reactionPicker;
    if (p == null) return;
    reactionPicker = null;
    View row = p instanceof ViewGroup && ((ViewGroup) p).getChildCount() > 0 ? ((ViewGroup) p).getChildAt(0) : null;
    if (row != null) row.animate().translationY(Screen.dp(16f)).alpha(0f).setDuration(160).withEndAction(() -> root.removeView(p)).start();
    else root.removeView(p);
    setPaused(false);
  }

  // Premium incognito: Telegram hides your views for the last 5 and the next 25 minutes

  private void updateIncognito () {
    boolean premium = tdlib.hasPremium();
    incognitoView.setVisibility(premium ? View.VISIBLE : View.GONE);
    // highlighted top right while incognito is on (user 2026-10-05)
    boolean on = premium && tdlib.tgx101StoryStealthActive();
    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
    bg.setCornerRadius(Screen.dp(16f));
    bg.setColor(on ? 0xff3fa9f5 : 0x55000000);
    incognitoView.setBackground(bg);
    incognitoView.setText(Lang.getString(on ? R.string.Tgx101StoryIncognitoActive : R.string.Tgx101StoryIncognito));
  }

  private void activateIncognito () {
    tdlib.send(new TdApi.ActivateStoryStealthMode(), (ok, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        Tgx101Diag.mark("story: incognito on");
        UI.post(this::updateIncognito, 500);
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
      org.thunderdog.challegram.tool.Tgx101Haptics.play(org.thunderdog.challegram.tool.Tgx101Haptics.CLICK);
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
