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
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import tgx.td.Td;

/**
 * TGx101: message filters.
 * Incoming messages whose text or caption matches a rule are shown as a strip «Hidden by filter …»;
 * a tap on it shows the message. Only the display on this phone changes: nothing is deleted, read
 * marks work as usual. Own messages and Telegram's sponsored messages are never hidden (sponsored
 * ones are built by a separate path and never reach {@link #match}).
 */
public final class Tgx101MessageFilters {
  private Tgx101MessageFilters () { }

  public static final int SCOPE_ALL = 0, SCOPE_CHANNELS = 1, SCOPE_CHANNELS_GROUPS = 2, SCOPE_CHAT = 3, SCOPE_GROUPS = 4;

  public static final class Rule {
    /** Without «Pattern» (regex) every line is a word or phrase of its own — the «tags» of the filter screen; a message
     * with any of them is hidden (approved mockup «Фильтры и живые примеры» А, user 2026-10-31) */
    public String text = "";
    /** Stable for the week counter: the list's order changes */
    public long id;
    public boolean regex, matchCase, wholeWord, checkAuthor;
    /** No «Hidden by filter» strip: the message is left out of the chat entirely (4PDA request 2026-10-04) */
    public boolean hideFully;
    public int scope = SCOPE_ALL;
    public long chatId;
    public String chatTitle;

    private Pattern pattern;
    private boolean patternBuilt;

    @Nullable
    private Pattern pattern () {
      if (!patternBuilt) {
        patternBuilt = true;
        try {
          pattern = compile(this);
        } catch (PatternSyntaxException e) {
          pattern = null;
        }
      }
      return pattern;
    }

    /** The tags: the lines of the text (a pattern is one whole tag) */
    public List<String> words () {
      List<String> list = new ArrayList<>();
      if (regex) {
        if (!text.trim().isEmpty()) list.add(text.trim());
        return list;
      }
      for (String line : text.split("\n")) {
        String word = line.trim();
        if (!word.isEmpty() && !list.contains(word)) list.add(word);
      }
      return list;
    }

    public void setWords (List<String> words) {
      StringBuilder b = new StringBuilder();
      for (String word : words) {
        if (b.length() > 0) b.append('\n');
        b.append(word.trim());
      }
      text = b.toString();
      invalidate();
    }

    public void invalidate () {
      patternBuilt = false;
      pattern = null;
    }

    public Rule copy () {
      Rule rule = new Rule();
      rule.text = text;
      rule.id = id;
      rule.regex = regex;
      rule.matchCase = matchCase;
      rule.wholeWord = wholeWord;
      rule.checkAuthor = checkAuthor;
      rule.hideFully = hideFully;
      rule.scope = scope;
      rule.chatId = chatId;
      rule.chatTitle = chatTitle;
      return rule;
    }
  }

  /** @throws PatternSyntaxException when the rule is a broken regular expression */
  public static Pattern compile (Rule rule) {
    String body;
    if (rule.regex) {
      body = rule.text;
    } else {
      StringBuilder b = new StringBuilder();
      for (String word : rule.words()) {
        if (b.length() > 0) b.append('|');
        b.append(Pattern.quote(word));
      }
      body = b.length() > 0 ? b.toString() : Pattern.quote(rule.text);
    }
    if (rule.wholeWord) {
      body = "(?<![\\p{L}\\p{N}_])(?:" + body + ")(?![\\p{L}\\p{N}_])";
    }
    int flags = rule.matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    return Pattern.compile(body, flags);
  }

  private static List<Rule> rules;
  // Messages shown again by a tap on their strip (until the app restarts)
  private static final HashSet<String> revealed = new HashSet<>();

  public static synchronized List<Rule> getRules () {
    if (rules == null) {
      rules = load();
    }
    return rules;
  }

  public static synchronized void setRules (List<Rule> newRules) {
    rules = new ArrayList<>(newRules);
    save(rules);
  }

  public static boolean isEnabled () {
    return Settings.instance().tgx101MessageFiltersEnabled();
  }

  /** Rules or «hide messages from blocked»: anything that can leave a message out */
  public static boolean anyHiding () {
    return isEnabled() || Tgx101BlockedSenders.isEnabled() || Settings.instance().tgx101AdFilter();
  }

  // The smart ad filter (user 2026-10-31): a channel post that marks itself as an ad. Only the marks the law and the
  // channels themselves put — words like «скидка» alone never count, so an ordinary post isn't taken for an ad
  // (2026-10-31: «[.:]» did not compile on Android's ICU and the app fell at start) — a pattern that fails only turns
  // the filter off
  private static final Pattern AD_MARKS = compileAdMarks(
    "(?iu)(\\berid\\s*(?::|=)|[?&]erid=|на\\s+правах\\s+рекламы|#реклама|#ad\\b|#sponsored|\\bpaid\\s+partnership\\b|партн[её]рский\\s+материал|рекламный\\s+пост|^\\s*реклама\\s*(?:\\.|:)|\\bреклама\\b.{0,80}\\bинн\\b|\\bинн\\b.{0,80}\\bреклама\\b)",
    Pattern.MULTILINE);
  private static @Nullable Pattern compileAdMarks (String regex, int flags) {
    try {
      return Pattern.compile(regex, flags);
    } catch (Throwable t) {
      org.thunderdog.challegram.Log.e("ad filter pattern", t);
      return null;
    }
  }

  private static Rule adRule;

  /** The rule the smart ad filter shows in place of a post: hidden fully, or as a strip in «check mode» */
  private static Rule adRule () {
    if (adRule == null) adRule = new Rule();
    adRule.text = org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.Tgx101AdFilterLabel);
    adRule.hideFully = !Settings.instance().tgx101AdFilterCheck();
    return adRule;
  }

  public static boolean isAd (TdApi.Message msg) {
    if (msg == null || msg.content == null) return false;
    TdApi.FormattedText formatted = Td.textOrCaption(msg.content);
    if (formatted == null || formatted.text == null) return false;
    if (AD_MARKS != null && AD_MARKS.matcher(formatted.text).find()) return true;
    if (formatted.entities != null) {
      for (TdApi.TextEntity entity : formatted.entities) {
        if (entity.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) {
          String url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          if (url != null && url.toLowerCase(java.util.Locale.ROOT).contains("erid=")) return true;
        }
      }
    }
    return false;
  }

  private static List<Rule> load () {
    List<Rule> result = new ArrayList<>();
    String json = Settings.instance().getTgx101MessageFilters();
    if (json == null || json.isEmpty()) {
      return result;
    }
    try {
      JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++) {
        JSONObject o = array.getJSONObject(i);
        Rule rule = new Rule();
        rule.text = o.optString("text", "");
        rule.regex = o.optBoolean("regex");
        rule.matchCase = o.optBoolean("case");
        rule.wholeWord = o.optBoolean("word");
        rule.checkAuthor = o.optBoolean("author");
        rule.hideFully = o.optBoolean("hide");
        rule.scope = o.optInt("scope", SCOPE_ALL);
        rule.chatId = o.optLong("chat");
        rule.chatTitle = o.optString("title", null);
        rule.id = o.optLong("id");
        if (rule.id == 0) rule.id = newId();
        if (!rule.text.isEmpty()) {
          result.add(rule);
        }
      }
    } catch (Throwable ignored) {
      // broken settings: no filters
    }
    return result;
  }

  private static void save (List<Rule> list) {
    try {
      JSONArray array = new JSONArray();
      for (Rule rule : list) {
        JSONObject o = new JSONObject();
        if (rule.id == 0) rule.id = newId();
        o.put("id", rule.id);
        o.put("text", rule.text);
        o.put("regex", rule.regex);
        o.put("case", rule.matchCase);
        o.put("word", rule.wholeWord);
        o.put("author", rule.checkAuthor);
        o.put("hide", rule.hideFully);
        o.put("scope", rule.scope);
        if (rule.scope == SCOPE_CHAT) {
          o.put("chat", rule.chatId);
          o.put("title", rule.chatTitle);
        }
        array.put(o);
      }
      Settings.instance().setTgx101MessageFilters(array.length() > 0 ? array.toString() : null);
    } catch (Throwable ignored) { }
  }

  private static String key (long chatId, long messageId) {
    return chatId + "_" + messageId;
  }

  private static boolean inScope (Rule rule, TdApi.Chat chat) {
    switch (rule.scope) {
      case SCOPE_CHAT:
        return chat.id == rule.chatId;
      case SCOPE_CHANNELS:
        return isChannel(chat);
      case SCOPE_GROUPS:
        return (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && !isChannel(chat)) || chat.type.getConstructor() == TdApi.ChatTypeBasicGroup.CONSTRUCTOR;
      case SCOPE_CHANNELS_GROUPS:
        return chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR || chat.type.getConstructor() == TdApi.ChatTypeBasicGroup.CONSTRUCTOR;
      case SCOPE_ALL:
      default:
        return true;
    }
  }

  private static boolean isChannel (TdApi.Chat chat) {
    return chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && ((TdApi.ChatTypeSupergroup) chat.type).isChannel;
  }

  @Nullable
  private static String authorText (Tdlib tdlib, TdApi.Message msg) {
    StringBuilder b = new StringBuilder();
    if (msg.senderId != null) {
      b.append(tdlib.senderName(msg.senderId));
    }
    if (msg.authorSignature != null && !msg.authorSignature.isEmpty()) {
      b.append('\n').append(msg.authorSignature);
    }
    if (msg.forwardInfo != null && msg.forwardInfo.origin != null) {
      TdApi.MessageOrigin origin = msg.forwardInfo.origin;
      switch (origin.getConstructor()) {
        case TdApi.MessageOriginChannel.CONSTRUCTOR:
          b.append('\n').append(tdlib.chatTitle(((TdApi.MessageOriginChannel) origin).chatId));
          break;
        case TdApi.MessageOriginChat.CONSTRUCTOR:
          b.append('\n').append(tdlib.chatTitle(((TdApi.MessageOriginChat) origin).senderChatId));
          break;
        case TdApi.MessageOriginUser.CONSTRUCTOR:
          b.append('\n').append(tdlib.senderName(new TdApi.MessageSenderUser(((TdApi.MessageOriginUser) origin).senderUserId)));
          break;
        case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
          b.append('\n').append(((TdApi.MessageOriginHiddenUser) origin).senderName);
          break;
      }
    }
    return b.length() > 0 ? b.toString() : null;
  }

  /** The rule hiding this message, or null when it is shown as usual */
  @Nullable
  public static Rule match (Tdlib tdlib, TdApi.Message msg, @Nullable TdApi.Chat chat) {
    if (chat == null || msg == null || msg.isOutgoing || msg.content == null) {
      return null;
    }
    boolean ads = Settings.instance().tgx101AdFilter() && isChannel(chat);
    if (!isEnabled() && !ads) {
      return null;
    }
    synchronized (revealed) {
      if (revealed.contains(key(msg.chatId, msg.id))) {
        return null;
      }
    }
    if (ads && isAd(msg)) {
      return adRule();
    }
    if (!isEnabled()) {
      return null;
    }
    List<Rule> list = getRules();
    if (list.isEmpty()) {
      return null;
    }
    TdApi.FormattedText formatted = Td.textOrCaption(msg.content);
    String text = formatted != null ? formatted.text : null;
    String author = null;
    boolean authorLoaded = false;
    for (Rule rule : list) {
      if (rule.text.trim().isEmpty() || !inScope(rule, chat)) continue; // a new filter with no words yet hides nothing
      Pattern pattern = rule.pattern();
      if (pattern == null) continue;
      if (text != null && !text.isEmpty() && pattern.matcher(text).find()) {
        countHit(rule, msg);
        return rule;
      }
      if (rule.checkAuthor) {
        if (!authorLoaded) {
          author = authorText(tdlib, msg);
          authorLoaded = true;
        }
        if (author != null && pattern.matcher(author).find()) {
          countHit(rule, msg);
          return rule;
        }
      }
    }
    return null;
  }

  /** Strip in place of a hidden message, or null when the message isn't filtered */
  @Nullable
  /** A message the user hid with «Hide completely»: not shown at all, not even as a strip */
  public static boolean hiddenFully (MessagesManager manager, TdApi.Message msg) {
    if (manager.controller().isInForceTouchMode()) return false;
    org.thunderdog.challegram.telegram.Tdlib tdlib = manager.controller().tdlib();
    if (Tgx101BlockedSenders.isBlocked(tdlib, msg)) return true; // TGx101: «Hide messages from blocked»
    Rule rule = match(tdlib, msg, tdlib.chat(msg.chatId));
    return rule != null && rule.hideFully;
  }

  static TGMessage placeholder (MessagesManager manager, TdApi.Message msg, @Nullable TdApi.Chat chat) {
    if (manager.controller().isInForceTouchMode()) {
      return null; // previews show the message itself
    }
    Rule rule = match(manager.controller().tdlib(), msg, chat);
    if (rule == null) {
      return null;
    }
    return new TGMessageService(manager, msg, rule.text);
  }

  /** A tap on the strip: the message (with the rest of its album) is shown again */
  static void reveal (TGMessage strip) {
    synchronized (revealed) {
      for (TdApi.Message m : strip.getAllMessages()) {
        revealed.add(key(m.chatId, m.id));
      }
    }
    strip.manager().tgx101ShowFilteredMessage(strip);
  }

  public static long newId () {
    return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
  }

  // The week counter (mockup В «скрыто 34 за неделю»): each hidden message once, by the day it was first hidden.
  // Kept on the phone only, saved a few seconds after the last change

  private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
  private static java.util.HashMap<Long, java.util.LinkedHashMap<String, Long>> hits;
  private static final Runnable saveHits = Tgx101MessageFilters::saveHits;

  private static java.util.HashMap<Long, java.util.LinkedHashMap<String, Long>> hits () {
    if (hits == null) {
      hits = new java.util.HashMap<>();
      try {
        String json = Settings.instance().getTgx101FilterHits();
        if (json != null) {
          JSONObject o = new JSONObject(json);
          long cutoff = System.currentTimeMillis() - WEEK;
          java.util.Iterator<String> keys = o.keys();
          while (keys.hasNext()) {
            String id = keys.next();
            JSONObject list = o.getJSONObject(id);
            java.util.LinkedHashMap<String, Long> map = new java.util.LinkedHashMap<>();
            java.util.Iterator<String> messages = list.keys();
            while (messages.hasNext()) {
              String key = messages.next();
              long time = list.getLong(key);
              if (time >= cutoff) map.put(key, time);
            }
            hits.put(Long.parseLong(id), map);
          }
        }
      } catch (Throwable ignored) { }
    }
    return hits;
  }

  private static void countHit (Rule rule, TdApi.Message msg) {
    if (rule.id == 0) return;
    synchronized (Tgx101MessageFilters.class) {
      java.util.LinkedHashMap<String, Long> map = hits().get(rule.id);
      if (map == null) hits.put(rule.id, map = new java.util.LinkedHashMap<>());
      String key = key(msg.chatId, msg.id);
      if (map.containsKey(key)) return;
      long now = System.currentTimeMillis();
      map.put(key, now);
      // oldest first: drop the ones older than a week, and keep at most 2000
      java.util.Iterator<java.util.Map.Entry<String, Long>> it = map.entrySet().iterator();
      while (it.hasNext()) {
        long time = it.next().getValue();
        if (map.size() > 2000 || time < now - WEEK) it.remove();
        else break;
      }
    }
    org.thunderdog.challegram.tool.UI.removePendingRunnable(saveHits);
    org.thunderdog.challegram.tool.UI.post(saveHits, 5000);
  }

  private static synchronized void saveHits () {
    try {
      JSONObject o = new JSONObject();
      for (java.util.Map.Entry<Long, java.util.LinkedHashMap<String, Long>> e : hits().entrySet()) {
        JSONObject list = new JSONObject();
        for (java.util.Map.Entry<String, Long> m : e.getValue().entrySet()) list.put(m.getKey(), (long) m.getValue());
        o.put(String.valueOf(e.getKey()), list);
      }
      Settings.instance().setTgx101FilterHits(o.toString());
    } catch (Throwable ignored) { }
  }

  /** How many messages the rule hid in the last 7 days */
  public static synchronized int weekCount (Rule rule) {
    java.util.LinkedHashMap<String, Long> map = hits().get(rule.id);
    if (map == null) return 0;
    long cutoff = System.currentTimeMillis() - WEEK;
    int count = 0;
    for (long time : map.values()) if (time >= cutoff) count++;
    return count;
  }

  /** Short label for a rule in lists and on the strip */
  public static String label (String text) {
    String oneLine = text.replace("\n", " · ").trim();
    return oneLine.length() > 40 ? oneLine.substring(0, 39) + "…" : oneLine;
  }

  /** Text of a message for «Hide similar…»: its first line, shortened */
  @Nullable
  public static String suggestion (TdApi.Message msg) {
    TdApi.FormattedText formatted = msg != null && msg.content != null ? Td.textOrCaption(msg.content) : null;
    if (formatted == null || formatted.text == null) return null;
    String text = formatted.text.trim();
    int newLine = text.indexOf('\n');
    if (newLine > 0) text = text.substring(0, newLine).trim();
    if (text.length() > 60) text = text.substring(0, 60).trim();
    return text.isEmpty() ? null : text;
  }
}
