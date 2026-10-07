/*
 * TGx101 / PlumaGram: the strip of story circles above the chat list (variant В1, user 2026-10-04).
 * Circles: blue ring — unseen stories, grey — all seen. Contacts first. A tap opens the viewer, a long press
 * the menu (hide / show less / notify about new stories).
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.SortedList;
import org.thunderdog.challegram.telegram.StoryList;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.Tgx101Stories;

import java.util.ArrayList;
import java.util.List;

public class Tgx101StoriesStrip extends HorizontalScrollView implements SortedList.ListListener<TdApi.ChatActiveStories> {
  public interface Callback {
    void onStoryClick (List<TdApi.ChatActiveStories> ordered, int index);
    void onStoryLongClick (View view, TdApi.ChatActiveStories stories);
    void onStripVisibilityChanged (boolean hasItems);
  }

  public static final float HEIGHT_DP = 94f;

  private final Tdlib tdlib;
  private final Callback callback;
  private final LinearLayout row;
  private final StoryList storyList;
  private List<TdApi.ChatActiveStories> ordered = new ArrayList<>();

  public Tgx101StoriesStrip (Context context, Tdlib tdlib, Callback callback) {
    super(context);
    this.tdlib = tdlib;
    this.callback = callback;
    setHorizontalScrollBarEnabled(false);
    setOverScrollMode(OVER_SCROLL_NEVER);
    row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), 0);
    addView(row, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    setBackgroundColor(Theme.fillingColor());
    storyList = tdlib.getStoryList(new TdApi.StoryListMain());
    storyList.initializeList(null, this, list -> UI.post(() -> scheduleItems(list)), 50, null);
  }

  // TGx101 (user 2026-10-07 23:08 «истории всё равно прыгают… сделай заглушку с пустыми кружками»): the list arrives in a
  // burst (75 rebuilds in a second on 0.1.553) — take the last one after a short pause; until then empty circles hold the place
  private @Nullable List<TdApi.ChatActiveStories> pendingItems;
  private final Runnable applyPending = () -> {
    List<TdApi.ChatActiveStories> items = pendingItems;
    pendingItems = null;
    if (items != null) setItems(items);
  };

  private void scheduleItems (List<TdApi.ChatActiveStories> items) {
    pendingItems = items;
    removeCallbacks(applyPending);
    postDelayed(applyPending, builtKey.isEmpty() || builtKey.equals(PLACEHOLDER_KEY) ? 60 : 150);
  }

  private static final String PLACEHOLDER_KEY = "placeholder";

  /** Empty circles in place of the stories that were here last time, until the real ones load */
  public void showPlaceholders (int count) {
    if (row.getChildCount() > 0 || count <= 0) return;
    builtKey = PLACEHOLDER_KEY;
    for (int i = 0; i < count; i++) {
      LinearLayout item = new LinearLayout(getContext());
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER_HORIZONTAL);
      item.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(66f), ViewGroup.LayoutParams.MATCH_PARENT));
      View circle = new View(getContext()) {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override
        protected void onDraw (@NonNull Canvas c) {
          p.setColor(org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.placeholder));
          c.drawCircle(getWidth() / 2f, getHeight() / 2f, Screen.dp(26f), p);
        }
      };
      item.addView(circle, new LinearLayout.LayoutParams(Screen.dp(60f), Screen.dp(60f)));
      row.addView(item);
    }
  }

  public boolean showsPlaceholders () {
    return PLACEHOLDER_KEY.equals(builtKey);
  }

  public void destroy () {
    storyList.removeListener(this);
  }

  // TGx101 (user 2026-10-05, variant 1): a thin full-width line under the expanded strip separates it from the chats
  private final Paint tgx101LinePaint = new Paint();

  @Override
  public void draw (@NonNull Canvas c) {
    super.draw(c);
    tgx101LinePaint.setColor(Theme.separatorColor());
    float h = Math.max(1f, Screen.dp(.5f));
    c.drawRect(getScrollX(), getHeight() - h, getScrollX() + getWidth(), getHeight(), tgx101LinePaint);
  }

  @Override
  public void onListChanged (SortedList<TdApi.ChatActiveStories> list) {
    list.getList(null, items -> UI.post(() -> scheduleItems(items)));
  }

  private void setItems (List<TdApi.ChatActiveStories> items) {
    List<TdApi.ChatActiveStories> filtered = new ArrayList<>();
    for (TdApi.ChatActiveStories s : items) {
      if (s.stories != null && s.stories.length > 0) filtered.add(s);
    }
    List<TdApi.ChatActiveStories> newOrdered = Tgx101Stories.order(tdlib, filtered);
    // TGx101 (user 2026-10-05 «истории мигают при каждом показе»): the same chats in the same order with the
    // same read state — keep the views, rebuilding reloads every avatar and blinks
    String key = tgx101Key(newOrdered);
    if (key.equals(builtKey) && row.getChildCount() == newOrdered.size()) {
      ordered = newOrdered;
      callback.onStripVisibilityChanged(!ordered.isEmpty());
      return;
    }
    builtKey = key;
    ordered = newOrdered;
    org.thunderdog.challegram.Tgx101Diag.mark("stories strip: " + ordered.size() + " chat(s) with stories (list " + items.size() + ", total " + storyList.totalCount() + ")");
    row.removeAllViews();
    for (int i = 0; i < ordered.size(); i++) {
      row.addView(newItem(ordered.get(i), i));
    }
    callback.onStripVisibilityChanged(!ordered.isEmpty());
  }

  private String builtKey = "";

  private static String tgx101Key (List<TdApi.ChatActiveStories> list) {
    StringBuilder b = new StringBuilder();
    for (TdApi.ChatActiveStories st : list) {
      b.append(st.chatId).append(':').append(st.maxReadStoryId).append(':').append(st.stories.length).append(',');
    }
    return b.toString();
  }

  public void refresh () {
    storyList.getList(null, items -> UI.post(() -> scheduleItems(items)));
  }

  public List<TdApi.ChatActiveStories> ordered () {
    return ordered;
  }

  public boolean hasItems () {
    return !ordered.isEmpty();
  }

  private View newItem (TdApi.ChatActiveStories stories, int index) {
    Context context = getContext();
    LinearLayout item = new LinearLayout(context);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setGravity(Gravity.CENTER_HORIZONTAL);
    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(Screen.dp(66f), ViewGroup.LayoutParams.MATCH_PARENT);
    item.setLayoutParams(itemParams);

    RingView ring = new RingView(context, Tgx101Stories.hasUnread(stories));
    AvatarView avatar = new AvatarView(context);
    TdApi.Chat chat = tdlib.chat(stories.chatId);
    avatar.setChat(tdlib, chat);
    ring.addView(avatar, new FrameLayout.LayoutParams(Screen.dp(52f), Screen.dp(52f), Gravity.CENTER));
    item.addView(ring, new LinearLayout.LayoutParams(Screen.dp(60f), Screen.dp(60f)));

    TextView name = new TextView(context);
    name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
    name.setTextColor(Theme.textAccentColor());
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    name.setGravity(Gravity.CENTER);
    String title = stories.chatId == tdlib.selfChatId() ? org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101MyStory) : (chat != null ? chat.title : "");
    int space = title.indexOf(' ');
    name.setText(space > 0 && tdlib.chatUserId(stories.chatId) != 0 ? title.substring(0, space) : title);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameParams.topMargin = Screen.dp(3f);
    item.addView(name, nameParams);

    item.setOnClickListener(v -> callback.onStoryClick(ordered, index));
    item.setOnLongClickListener(v -> {
      callback.onStoryLongClick(v, stories);
      return true;
    });
    item.setBackgroundResource(org.thunderdog.challegram.R.drawable.bg_btn_header);
    return item;
  }

  private static final class RingView extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean unread;

    RingView (Context context, boolean unread) {
      super(context);
      this.unread = unread;
      setWillNotDraw(false);
      paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      super.onDraw(c);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      paint.setStrokeWidth(Screen.dp(unread ? 2.5f : 1.5f));
      paint.setColor(unread ? 0xff3fa9f5 : 0x80808a94);
      c.drawCircle(cx, cy, Math.min(cx, cy) - Screen.dp(1.5f), paint);
    }
  }
}
