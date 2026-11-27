/*
 * This file is a part of PlumaGram (a Telegram X fork)
 */
package org.thunderdog.challegram.telegram;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import me.vkryl.core.DateUtils;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * TGx101 (user 2026-11-27 «не вижу, где мы задаём своё время — нет выбора: 1-го числа след. месяца, через год, через
 * месяц, через неделю, через 2 недели, каждый чётный / нечётный понедельник, каждый понедельник»): what one's own time
 * remembers. The message goes once, at the nearest such moment from the tap; no repeat. Rules as stored:
 * 0 — every day; 1–7 — that weekday (Calendar.SUNDAY…SATURDAY); 101–131 — that day of the month; 201–207 / 301–307 —
 * that weekday of an even / odd week (ISO week numbers, as calendars show them); 401 — the 1st of next month;
 * 501 — in a week, 502 — in two weeks, 503 — in a month, 504 — in a year
 */
public final class Tgx101OwnTime {
  public static final String KEY_RULE = "tgx101_schedule_own_rule";
  public static final String KEY_MINUTES = "tgx101_schedule_own_minutes";

  /** What the picker offers, in this order; the day / weekday comes from the date on the wheels */
  public static final int KIND_DAILY = 0, KIND_WEEKDAY = 1, KIND_EVEN_WEEKDAY = 2, KIND_ODD_WEEKDAY = 3, KIND_MONTH_DAY = 4,
    KIND_FIRST_OF_NEXT_MONTH = 5, KIND_IN_WEEK = 6, KIND_IN_2_WEEKS = 7, KIND_IN_MONTH = 8, KIND_IN_YEAR = 9;
  public static final int KIND_COUNT = 10;

  private Tgx101OwnTime () { }

  public static int ruleFor (int kind, long millis) {
    Calendar c = DateUtils.calendarInstance(millis);
    int dow = c.get(Calendar.DAY_OF_WEEK);
    switch (kind) {
      case KIND_WEEKDAY: return dow;
      case KIND_EVEN_WEEKDAY: return 200 + dow;
      case KIND_ODD_WEEKDAY: return 300 + dow;
      case KIND_MONTH_DAY: return 100 + c.get(Calendar.DAY_OF_MONTH);
      case KIND_FIRST_OF_NEXT_MONTH: return 401;
      case KIND_IN_WEEK: return 501;
      case KIND_IN_2_WEEKS: return 502;
      case KIND_IN_MONTH: return 503;
      case KIND_IN_YEAR: return 504;
      default: return 0;
    }
  }

  public static int kindOf (int rule) {
    if (rule >= 1 && rule <= 7) return KIND_WEEKDAY;
    if (rule > 100 && rule <= 131) return KIND_MONTH_DAY;
    if (rule > 200 && rule <= 207) return KIND_EVEN_WEEKDAY;
    if (rule > 300 && rule <= 307) return KIND_ODD_WEEKDAY;
    if (rule == 401) return KIND_FIRST_OF_NEXT_MONTH;
    if (rule == 501) return KIND_IN_WEEK;
    if (rule == 502) return KIND_IN_2_WEEKS;
    if (rule == 503) return KIND_IN_MONTH;
    if (rule == 504) return KIND_IN_YEAR;
    return KIND_DAILY;
  }

  private static final int[] WEEKDAYS = {R.string.Tgx101OwnRuleSun, R.string.Tgx101OwnRuleMon, R.string.Tgx101OwnRuleTue, R.string.Tgx101OwnRuleWed, R.string.Tgx101OwnRuleThu, R.string.Tgx101OwnRuleFri, R.string.Tgx101OwnRuleSat};

  /** «по понедельникам» → «по чётным понедельникам» («on Mondays» → «on even Mondays»): the word after the first one */
  private static String withWord (String name, String word) {
    int space = name.indexOf(' ');
    return space < 0 ? word + " " + name : name.substring(0, space + 1) + word + " " + name.substring(space + 1);
  }

  /** In lower case, as it goes after «В своё время · » */
  public static String name (int rule) {
    if (rule >= 1 && rule <= 7) return Lang.getString(WEEKDAYS[rule - 1]);
    if (rule > 100 && rule <= 131) return Lang.getString(R.string.Tgx101OwnRuleMonthDay, rule - 100);
    if (rule > 200 && rule <= 207) return withWord(Lang.getString(WEEKDAYS[rule - 201]), Lang.getString(R.string.Tgx101OwnRuleEvenWord));
    if (rule > 300 && rule <= 307) return withWord(Lang.getString(WEEKDAYS[rule - 301]), Lang.getString(R.string.Tgx101OwnRuleOddWord));
    switch (rule) {
      case 401: return Lang.getString(R.string.Tgx101OwnRuleFirstNext);
      case 501: return Lang.getString(R.string.Tgx101OwnRuleInWeek);
      case 502: return Lang.getString(R.string.Tgx101OwnRuleIn2Weeks);
      case 503: return Lang.getString(R.string.Tgx101OwnRuleInMonth);
      case 504: return Lang.getString(R.string.Tgx101OwnRuleInYear);
    }
    return Lang.getString(R.string.Tgx101OwnRuleDaily);
  }

  public static String capitalized (int rule) {
    String s = name(rule);
    return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(java.util.Locale.getDefault()) + s.substring(1);
  }

  public static int isoWeek (long millis) {
    Calendar c = DateUtils.calendarInstance(millis);
    c.setFirstDayOfWeek(Calendar.MONDAY);
    c.setMinimalDaysInFirstWeek(4);
    c.setTimeInMillis(millis);
    return c.get(Calendar.WEEK_OF_YEAR);
  }

  private static boolean parityFits (int rule, long millis) {
    if (rule > 200 && rule <= 207) return isoWeek(millis) % 2 == 0;
    if (rule > 300 && rule <= 307) return isoWeek(millis) % 2 == 1;
    return true;
  }

  /** The first message, from the picker: the picked moment — a week later when its week has the other parity; the 1st
   *  of next month / in a week … a year: counted from now, at the picked time (user 2026-11-28 «выбрал 1-го числа —
   *  пишет отправить сегодня») */
  public static long firstSend (int rule, long millis, long now) {
    if (rule > 400) {
      Calendar picked = DateUtils.calendarInstance(millis);
      int minutes = picked.get(Calendar.HOUR_OF_DAY) * 60 + picked.get(Calendar.MINUTE);
      long target = now + TimeUnit.SECONDS.toMillis(secondsUntil(now, minutes, rule));
      return Math.round(target / 60_000d) * 60_000L; // on the minute
    }
    if (parityFits(rule, millis)) return millis;
    Calendar c = DateUtils.calendarInstance(millis);
    c.add(Calendar.DAY_OF_MONTH, 7);
    return c.getTimeInMillis();
  }

  /** Seconds from now to the nearest moment the rule names, at minutesOfDay */
  public static long secondsUntil (long now, int minutesOfDay, int rule) {
    Calendar c = DateUtils.calendarInstance(now);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (rule > 400) {
      switch (rule) {
        case 401: c.add(Calendar.MONTH, 1); c.set(Calendar.DAY_OF_MONTH, 1); break;
        case 501: c.add(Calendar.DAY_OF_MONTH, 7); break;
        case 502: c.add(Calendar.DAY_OF_MONTH, 14); break;
        case 503: c.add(Calendar.MONTH, 1); break;
        case 504: c.add(Calendar.YEAR, 1); break;
      }
      c.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
      c.set(Calendar.MINUTE, minutesOfDay % 60);
      return Math.max(60, TimeUnit.MILLISECONDS.toSeconds(c.getTimeInMillis() - now));
    }
    for (int i = 0; i < 400; i++) {
      boolean fits;
      if (rule <= 0) {
        fits = true;
      } else if (rule <= 7) {
        fits = c.get(Calendar.DAY_OF_WEEK) == rule;
      } else if (rule <= 131) {
        int want = Math.min(rule - 100, c.getActualMaximum(Calendar.DAY_OF_MONTH)); // the 31st in a short month: its last day
        fits = c.get(Calendar.DAY_OF_MONTH) == want;
      } else {
        fits = c.get(Calendar.DAY_OF_WEEK) == rule % 100 && parityFits(rule, c.getTimeInMillis());
      }
      if (fits) {
        c.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        c.set(Calendar.MINUTE, minutesOfDay % 60);
        if (c.getTimeInMillis() > now + 60_000) return TimeUnit.MILLISECONDS.toSeconds(c.getTimeInMillis() - now);
      }
      c.add(Calendar.DAY_OF_MONTH, 1);
      c.set(Calendar.HOUR_OF_DAY, 0);
      c.set(Calendar.MINUTE, 0);
    }
    return 86400;
  }
}
