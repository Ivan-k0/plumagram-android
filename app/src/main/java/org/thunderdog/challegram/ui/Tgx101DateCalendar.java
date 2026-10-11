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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-10-11, mockup «Поиск-по-дате» v2, «ок. делай»): «Go to date» as a strip of months in a bottom card
 * of our style — the newest month at the bottom, older ones above (scroll up into the past). Days with photos / videos
 * show the first one in a rounded square (▶ — there was a video), past days are numbers, future days and the days before
 * the chat began are pale. A tap on a day jumps right there; «To the first message» goes to the very beginning.
 *
 * Data: one getChatMessageCalendar request per page (TDLib gives a page of days with media, newest first), thumbnails
 * from the message's minithumbnail at once, then the smallest real thumbnail; nothing animates in the background.
 */
public final class Tgx101DateCalendar {
  public interface Callback {
    /** {@code dayStart} — local midnight of the picked day (unix seconds), 0 — the beginning of the chat */
    void onDatePicked (int dayStart);
  }

  private static final float HANDLE_TOP = 8f, TITLE_HEIGHT = 50f, WEEK_HEIGHT = 26f;
  private static final float BUTTON_HEIGHT = 40f, BUTTON_RADIUS = 14f, BUTTON_SIDE = 16f, BUTTON_TOP = 10f, BUTTON_BOTTOM = 16f, BUTTON_BETWEEN = 10f;
  private static final float GRID_SIDE = 10f, CELL_INSET = 2.5f, CELL_RADIUS = 12f, MONTH_TITLE = 40f;

  private static final class Day {
    TdApi.Message message;
    boolean video;
    @Nullable Bitmap bitmap;
    boolean full;
  }

  public static void show (ViewController<?> controller, Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId, int focusDate, Callback callback) {
    show(controller, tdlib, chatId, topicId, focusDate, new TdApi.SearchMessagesFilterPhotoAndVideo(), false, org.thunderdog.challegram.R.string.Tgx101CalendarFirstMessage, callback);
  }

  /**
   * {@code mediaOnly} (profile «Media», user mockup part 4): only the days with media of {@code filter} can be picked,
   * the rest are pale; {@code firstButton} — the label of the «to the very beginning» button
   */
  public static void show (ViewController<?> controller, Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId, int focusDate,
                           TdApi.SearchMessagesFilter filter, boolean mediaOnly, int firstButton, Callback callback) {
    Context context = controller.context();
    PopupLayout popup = new PopupLayout(context);
    popup.setTag(controller);
    popup.init(true);
    popup.setNeedFullScreen(true);
    int height = Math.round(Screen.currentHeight() * .72f);
    final Runnable[] afterHide = new Runnable[1];
    Card card = new Card(context, tdlib, chatId, topicId, focusDate, filter, mediaOnly, firstButton, day -> {
      // TGx101 (user 2026-10-12 «плавной анимации сворачивания нет»): the jump runs once the card has gone down,
      // so the chat's rebuild doesn't eat the animation
      afterHide[0] = () -> callback.onDatePicked(day);
      popup.hideWindow(true);
    }, () -> popup.hideWindow(true));
    card.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM));
    popup.showAnimatedPopupView(card, new PopupLayout.AnimatedPopupProvider() {
      private android.animation.ValueAnimator animator;
      private float factor;

      private float distance () {
        return (card.getHeight() > 0 ? card.getHeight() : height) + Screen.dp(OptionsLayout.TGX101_CARD_GAP) + Screen.dp(48f);
      }

      private void apply (float f) {
        factor = f;
        card.setTranslationY(distance() * (1f - f));
        popup.setRevealFactor(f);
      }

      private void run (float to, long duration, android.animation.TimeInterpolator interpolator, Runnable end) {
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(factor, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (android.animation.Animator animation) {
            end.run();
          }
        });
        animator.start();
      }

      @Override
      public void prepareShowAnimation () {
        card.setTranslationY(height * 2);
      }

      @Override
      public void launchShowAnimation (PopupLayout p) {
        apply(0f);
        run(1f, 340, new android.view.animation.DecelerateInterpolator(2.2f), p::onCustomShowComplete);
      }

      @Override
      public boolean launchHideAnimation (PopupLayout p, me.vkryl.android.animator.FactorAnimator originalAnimator) {
        run(0f, 300, new androidx.interpolator.view.animation.FastOutSlowInInterpolator(), () -> {
          p.onCustomHideAnimationComplete();
          if (afterHide[0] != null) {
            Runnable r = afterHide[0];
            afterHide[0] = null;
            r.run();
          }
        });
        return true;
      }
    });
    Tgx101Diag.mark("calendar: opened for chat " + chatId);
  }

  private static final class Card extends FrameLayoutFix implements RootFrameLayout.MarginModifier {
    private final Tdlib tdlib;
    private final long chatId;
    private final @Nullable TdApi.MessageTopic topicId;
    private final Callback callback;
    private final SparseArray<Day> days = new SparseArray<>(); // key — yyyymmdd
    private final ArrayList<int[]> months = new ArrayList<>(); // {year, month}; [0] — the current month
    private final RecyclerView list;
    private final MonthAdapter adapter;
    private final int todayKey;
    private int firstKey = 0; // the chat's first message day, 0 — not known yet
    private long nextFromMessageId = 0;
    private boolean loading, loadedAll;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TdApi.SearchMessagesFilter filter;
    private final boolean mediaOnly;
    private final RectF rect = new RectF();
    private final int firstDayOfWeek;

    Card (Context context, Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId, int focusDate, TdApi.SearchMessagesFilter filter, boolean mediaOnly, int firstButton, Callback callback, Runnable cancel) {
      super(context);
      this.filter = filter;
      this.mediaOnly = mediaOnly;
      this.tdlib = tdlib;
      this.chatId = chatId;
      this.topicId = topicId;
      this.callback = callback;
      setWillNotDraw(false);
      firstDayOfWeek = Calendar.getInstance(Lang.locale()).getFirstDayOfWeek();
      Calendar now = Calendar.getInstance();
      todayKey = key(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
      addMonthsBack(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 13);

      TextView title = new TextView(context);
      title.setText(Lang.getString(org.thunderdog.challegram.R.string.Tgx101Calendar));
      title.setTextSize(18f);
      title.setTypeface(Fonts.getRobotoMedium());
      title.setTextColor(Theme.getColor(ColorId.text));
      title.setGravity(Gravity.CENTER_VERTICAL);
      title.setPadding(Screen.dp(18f), 0, Screen.dp(18f), 0);
      addView(title, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(TITLE_HEIGHT), Gravity.TOP, 0, Screen.dp(HANDLE_TOP), 0, 0));

      View week = new View(context) {
        private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        @Override
        protected void onDraw (Canvas c) {
          p.setTextSize(Screen.dp(12f));
          p.setTypeface(Fonts.getRobotoRegular());
          p.setColor(Theme.getColor(ColorId.textLight));
          p.setTextAlign(Paint.Align.CENTER);
          float cell = (getWidth() - Screen.dp(GRID_SIDE) * 2) / 7f;
          String[] names = new java.text.DateFormatSymbols(Lang.locale()).getShortWeekdays();
          for (int i = 0; i < 7; i++) {
            int dow = (firstDayOfWeek - 1 + i) % 7 + 1;
            String name = names[dow];
            if (name.length() > 2) name = name.substring(0, 2);
            if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase(Lang.locale()) + name.substring(1);
            c.drawText(name, Screen.dp(GRID_SIDE) + cell * (i + .5f), getHeight() / 2f + Screen.dp(4f), p);
          }
          p.setColor(ColorUtils.alphaColor(.08f, Theme.getColor(ColorId.text)));
          c.drawRect(0, getHeight() - Math.max(1, Screen.dp(.5f)), getWidth(), getHeight(), p);
        }
      };
      int listTop = Screen.dp(HANDLE_TOP + TITLE_HEIGHT + WEEK_HEIGHT);
      addView(week, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(WEEK_HEIGHT), Gravity.TOP, 0, Screen.dp(HANDLE_TOP + TITLE_HEIGHT), 0, 0));

      list = new RecyclerView(context);
      LinearLayoutManager manager = new LinearLayoutManager(context, RecyclerView.VERTICAL, true); // [0] — this month, at the bottom
      list.setLayoutManager(manager);
      list.setItemAnimator(null);
      list.setOverScrollMode(View.OVER_SCROLL_NEVER);
      adapter = new MonthAdapter();
      list.setAdapter(adapter);
      int footer = Screen.dp(BUTTON_TOP + BUTTON_HEIGHT + BUTTON_BOTTOM);
      addView(list, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP, 0, listTop, 0, footer));
      list.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          int last = manager.findLastVisibleItemPosition();
          if (last >= months.size() - 3) {
            int[] oldest = months.get(months.size() - 1);
            if (firstKey == 0 || key(oldest[0], oldest[1], 1) > firstKey) {
              int before = months.size();
              addMonthsBack(oldest[0], oldest[1] - 1, 12);
              adapter.notifyItemRangeInserted(before, months.size() - before);
            }
          }
          if (!loadedAll && !loading && last >= 0) {
            int[] m = months.get(Math.min(last, months.size() - 1));
            if (oldestLoadedKey == 0 || key(m[0], m[1], 1) <= oldestLoadedKey + 100) loadPage(); // near the oldest loaded day or past it
          }
        }
      });
      // open at the month of the date the chat stands on
      if (focusDate > 0) {
        Calendar f = Calendar.getInstance();
        f.setTimeInMillis(focusDate * 1000L);
        int index = (now.get(Calendar.YEAR) - f.get(Calendar.YEAR)) * 12 + now.get(Calendar.MONTH) - f.get(Calendar.MONTH);
        if (index > 0) {
          if (index >= months.size() - 2) {
            int[] oldest = months.get(months.size() - 1);
            addMonthsBack(oldest[0], oldest[1] - 1, index - months.size() + 4);
          }
          manager.scrollToPositionWithOffset(index, 0);
        }
      }

      LinearLayoutFix buttons = new LinearLayoutFix(context);
      for (int i = 0; i < 2; i++) {
        TextView b = new TextView(context);
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(Screen.dp(BUTTON_RADIUS));
        pill.setColor(i == 1 ? Theme.getColor(ColorId.textNeutral) : ColorUtils.alphaColor(.07f, Theme.getColor(ColorId.text)));
        b.setBackground(pill);
        b.setTextColor(i == 1 ? 0xffffffff : Theme.getColor(ColorId.text));
        b.setTextSize(15f);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
        b.setText(Lang.getString(i == 0 ? org.thunderdog.challegram.R.string.Cancel : firstButton));
        b.setOnClickListener(i == 0 ? v -> cancel.run() : v -> callback.onDatePicked(0));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (i == 1) lp.leftMargin = Screen.dp(BUTTON_BETWEEN);
        buttons.addView(b, lp);
      }
      addView(buttons, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(BUTTON_HEIGHT), Gravity.BOTTOM, Screen.dp(BUTTON_SIDE), 0, Screen.dp(BUTTON_SIDE), Screen.dp(BUTTON_BOTTOM)));

      findFirstDay();
      loadPage();
    }

    private static final class LinearLayoutFix extends android.widget.LinearLayout {
      LinearLayoutFix (Context context) {
        super(context);
        setOrientation(HORIZONTAL);
      }
    }

    @Override
    public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
      int gap = Screen.dp(OptionsLayout.TGX101_CARD_GAP);
      int bottom = insetsWithoutIme.bottom;
      androidx.core.view.WindowInsetsCompat rootInsets = androidx.core.view.ViewCompat.getRootWindowInsets(this);
      if (rootInsets != null) {
        bottom = Math.max(bottom, rootInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom);
      }
      Views.setMargins(params, insets.left + gap, 0, insets.right + gap, bottom + gap);
    }

    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
      super.onLayout(changed, left, top, right, bottom);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && getOutlineProvider() != outline) {
        setOutlineProvider(outline);
        setClipToOutline(true);
      }
    }

    private final android.view.ViewOutlineProvider outline = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP ? new android.view.ViewOutlineProvider() {
      @Override
      public void getOutline (View view, android.graphics.Outline o) {
        o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(OptionsLayout.TGX101_CARD_RADIUS));
      }
    } : null;

    @Override
    protected void onDraw (Canvas c) {
      fill.setColor(Theme.getColor(ColorId.filling));
      rect.set(0, 0, getWidth(), getHeight());
      float r = Screen.dp(OptionsLayout.TGX101_CARD_RADIUS);
      c.drawRoundRect(rect, r, r, fill);
      fill.setColor(ColorUtils.alphaColor(.2f, Theme.getColor(ColorId.text)));
      float hw = Screen.dp(18f), hh = Screen.dp(4f);
      rect.set(getWidth() / 2f - hw, Screen.dp(HANDLE_TOP), getWidth() / 2f + hw, Screen.dp(HANDLE_TOP) + hh);
      c.drawRoundRect(rect, hh / 2, hh / 2, fill);
    }

    private void addMonthsBack (int year, int month, int count) {
      Calendar c = Calendar.getInstance();
      c.clear();
      c.set(year, month, 1);
      for (int i = 0; i < count; i++) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH);
        if (firstKey != 0 && key(y, m, 31) < firstKey) break;
        months.add(new int[] {y, m});
        c.add(Calendar.MONTH, -1);
      }
    }

    private static int key (int year, int month, int day) {
      return year * 10000 + (month + 1) * 100 + day;
    }

    private static int keyOf (int unixSeconds) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(unixSeconds * 1000L);
      return key(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    // the chat's first message — days before it are pale and the strip of months ends there
    private void findFirstDay () {
      tdlib.client().send(new TdApi.GetChatHistory(chatId, 1, -1, 1, false), result -> {
        if (!(result instanceof TdApi.Messages)) return;
        TdApi.Message[] messages = ((TdApi.Messages) result).messages;
        if (messages == null || messages.length == 0) return;
        int first = Integer.MAX_VALUE;
        for (TdApi.Message m : messages) first = Math.min(first, m.date);
        final int k = keyOf(first);
        UI.post(() -> {
          firstKey = k;
          // drop the months before it
          for (int i = months.size() - 1; i > 0; i--) {
            int[] m = months.get(i);
            if (key(m[0], m[1], 31) < firstKey) months.remove(i);
            else break;
          }
          adapter.notifyDataSetChanged();
        });
      });
    }

    private int oldestLoadedKey;

    /** Future days, days before the chat began, and — in «Media» — days known to have no media of the filter */
    private boolean isDisabled (int k, @Nullable Day day) {
      if (k > todayKey || (firstKey != 0 && k < firstKey)) return true;
      return mediaOnly && day == null && (loadedAll || (oldestLoadedKey != 0 && k >= oldestLoadedKey));
    }

    private void loadPage () {
      if (loading || loadedAll) return;
      loading = true;
      final long from = nextFromMessageId;
      tdlib.client().send(new TdApi.GetChatMessageCalendar(chatId, topicId, filter, from), result -> {
        if (!(result instanceof TdApi.MessageCalendar)) {
          UI.post(() -> { loading = false; loadedAll = true; });
          Tgx101Diag.mark("calendar: no media calendar (" + (result instanceof TdApi.Error ? ((TdApi.Error) result).message : result.getClass().getSimpleName()) + ")");
          return;
        }
        TdApi.MessageCalendarDay[] page = ((TdApi.MessageCalendar) result).days;
        UI.post(() -> {
          loading = false;
          long oldestId = from;
          int added = 0;
          if (page != null) {
            for (TdApi.MessageCalendarDay d : page) {
              if (d.message == null) continue;
              int k = keyOf(d.message.date);
              if (days.get(k) == null) {
                Day day = new Day();
                day.message = d.message;
                day.video = d.message.content instanceof TdApi.MessageVideo;
                days.put(k, day);
                loadThumb(day);
                added++;
              }
              if (oldestId == 0 || d.message.id < oldestId) oldestId = d.message.id;
              if (oldestLoadedKey == 0 || k < oldestLoadedKey) oldestLoadedKey = k;
            }
          }
          if (added == 0 || oldestId == from) loadedAll = true;
          nextFromMessageId = oldestId;
          adapter.notifyDataSetChanged();
        });
      });
    }

    private void loadThumb (Day day) {
      TdApi.Minithumbnail mini = null;
      TdApi.File file = null;
      if (day.message.content instanceof TdApi.MessagePhoto) {
        TdApi.Photo photo = ((TdApi.MessagePhoto) day.message.content).photo;
        mini = photo.minithumbnail;
        TdApi.PhotoSize best = null;
        for (TdApi.PhotoSize s : photo.sizes) {
          if (best == null || (s.width >= Screen.dp(40f) && (best.width < Screen.dp(40f) || s.width < best.width))) best = s;
        }
        if (best != null) file = best.photo;
      } else if (day.message.content instanceof TdApi.MessageVideo) {
        TdApi.Video video = ((TdApi.MessageVideo) day.message.content).video;
        mini = video.minithumbnail;
        if (video.thumbnail != null && !(video.thumbnail.format instanceof TdApi.ThumbnailFormatMpeg4)) file = video.thumbnail.file;
      }
      if (mini != null && mini.data != null) {
        try {
          day.bitmap = BitmapFactory.decodeByteArray(mini.data, 0, mini.data.length);
        } catch (Throwable ignored) { }
      }
      if (file == null) return;
      final TdApi.File f = file;
      tdlib.client().send(new TdApi.DownloadFile(f.id, 1, 0, 0, true), result -> {
        if (!(result instanceof TdApi.File)) return;
        TdApi.File done = (TdApi.File) result;
        if (!done.local.isDownloadingCompleted) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(done.local.path, o);
        int target = Screen.dp(56f), sample = 1;
        while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap b;
        try {
          b = BitmapFactory.decodeFile(done.local.path, o);
        } catch (Throwable t) {
          return;
        }
        if (b == null) return;
        UI.post(() -> {
          day.bitmap = b;
          day.full = true;
          list.invalidate();
          for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate();
        });
      });
    }

    private final class MonthAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
      @NonNull
      @Override
      public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        MonthView v = new MonthView(parent.getContext());
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(v) { };
      }

      @Override
      public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
        int[] m = months.get(position);
        ((MonthView) holder.itemView).set(m[0], m[1]);
      }

      @Override
      public int getItemCount () {
        return months.size();
      }
    }

    private final class MonthView extends View {
      private int year, month, offset, daysInMonth, rows;
      private String title;
      private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      private final RectF cell = new RectF();
      private final Matrix matrix = new Matrix();
      private final Path triangle = new Path();
      private float downX, downY;

      MonthView (Context context) {
        super(context);
      }

      void set (int year, int month) {
        this.year = year;
        this.month = month;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        offset = (c.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7;
        rows = (offset + daysInMonth + 6) / 7;
        String t = new SimpleDateFormat("LLLL yyyy", Lang.locale()).format(c.getTime());
        title = t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Lang.locale()) + t.substring(1);
        requestLayout();
        invalidate();
      }

      private float cellSize () {
        return (getMeasuredWidth() - Screen.dp(GRID_SIDE) * 2) / 7f;
      }

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float size = (width - Screen.dp(GRID_SIDE) * 2) / 7f;
        setMeasuredDimension(width, Math.round(Screen.dp(MONTH_TITLE) + size * rows + Screen.dp(6f)));
      }

      @Override
      protected void onDraw (Canvas c) {
        float size = cellSize();
        text.setTypeface(Fonts.getRobotoMedium());
        text.setTextSize(Screen.dp(15f));
        text.setColor(Theme.getColor(ColorId.text));
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(title, Screen.dp(18f), Screen.dp(MONTH_TITLE) - Screen.dp(12f), text);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Screen.dp(14f));
        float inset = Screen.dp(CELL_INSET), radius = Screen.dp(CELL_RADIUS);
        int textColor = Theme.getColor(ColorId.text), pale = ColorUtils.alphaColor(.32f, Theme.getColor(ColorId.text)), accent = Theme.getColor(ColorId.textNeutral);
        for (int d = 1; d <= daysInMonth; d++) {
          int i = offset + d - 1, col = i % 7, row = i / 7;
          float left = Screen.dp(GRID_SIDE) + col * size, top = Screen.dp(MONTH_TITLE) + row * size;
          cell.set(left + inset, top + inset, left + size - inset, top + size - inset);
          int k = key(year, month, d);
          Day day = days.get(k);
          boolean disabled = isDisabled(k, day);
          String num = Integer.toString(d);
          float ty = cell.centerY() + Screen.dp(5f);
          if (day != null && day.bitmap != null && !disabled) {
            Bitmap b = day.bitmap;
            BitmapShader shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(cell.width() / b.getWidth(), cell.height() / b.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(cell.centerX() - b.getWidth() * scale / 2f, cell.centerY() - b.getHeight() * scale / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            c.drawRoundRect(cell, radius, radius, paint);
            paint.setShader(null);
            paint.setColor(0x38000000);
            c.drawRoundRect(cell, radius, radius, paint);
            text.setFakeBoldText(true);
            text.setColor(0xffffffff);
            text.setShadowLayer(Screen.dp(2f), 0, 0, 0x99000000);
            c.drawText(num, cell.centerX(), ty, text);
            text.clearShadowLayer();
            text.setFakeBoldText(false);
            if (day.video) {
              float s = Screen.dp(6f), x = cell.right - Screen.dp(5f), y = cell.top + Screen.dp(5f);
              triangle.reset();
              triangle.moveTo(x - s, y);
              triangle.lineTo(x, y + s / 2f);
              triangle.lineTo(x - s, y + s);
              triangle.close();
              paint.setColor(0xffffffff);
              c.drawPath(triangle, paint);
            }
          } else {
            text.setColor(disabled ? pale : textColor);
            c.drawText(num, cell.centerX(), ty, text);
          }
          if (k == todayKey) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Screen.dp(2f));
            paint.setColor(accent);
            c.drawRoundRect(cell, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
          }
        }
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN:
            downX = e.getX();
            downY = e.getY();
            return true;
          case MotionEvent.ACTION_UP: {
            if (Math.abs(e.getX() - downX) > Screen.dp(8f) || Math.abs(e.getY() - downY) > Screen.dp(8f)) return true;
            float size = cellSize();
            int col = (int) ((e.getX() - Screen.dp(GRID_SIDE)) / size), row = (int) ((e.getY() - Screen.dp(MONTH_TITLE)) / size);
            if (col < 0 || col > 6 || row < 0 || e.getY() < Screen.dp(MONTH_TITLE)) return true;
            int d = row * 7 + col - offset + 1;
            if (d < 1 || d > daysInMonth) return true;
            int k = key(year, month, d);
            if (isDisabled(k, days.get(k))) return true;
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(year, month, d, 0, 0, 0);
            Tgx101Diag.mark("calendar: day picked " + k + (days.get(k) != null ? " (media)" : ""));
            callback.onDatePicked((int) (c.getTimeInMillis() / 1000L));
            return true;
          }
        }
        return true;
      }
    }
  }
}
