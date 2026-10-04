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

  public static final int SCOPE_ALL = 0, SCOPE_CHANNELS = 1, SCOPE_CHANNELS_GROUPS = 2, SCOPE_CHAT = 3;

  public static final class Rule {
    public String text = "";
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

    public void invalidate () {
      patternBuilt = false;
      pattern = null;
    }

    public Rule copy () {
      Rule rule = new Rule();
      rule.text = text;
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
    String body = rule.regex ? rule.text : Pattern.quote(rule.text);
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
    if (chat == null || msg == null || msg.isOutgoing || msg.content == null || !isEnabled()) {
      return null;
    }
    List<Rule> list = getRules();
    if (list.isEmpty()) {
      return null;
    }
    synchronized (revealed) {
      if (revealed.contains(key(msg.chatId, msg.id))) {
        return null;
      }
    }
    TdApi.FormattedText formatted = Td.textOrCaption(msg.content);
    String text = formatted != null ? formatted.text : null;
    String author = null;
    boolean authorLoaded = false;
    for (Rule rule : list) {
      if (!inScope(rule, chat)) continue;
      Pattern pattern = rule.pattern();
      if (pattern == null) continue;
      if (text != null && !text.isEmpty() && pattern.matcher(text).find()) {
        return rule;
      }
      if (rule.checkAuthor) {
        if (!authorLoaded) {
          author = authorText(tdlib, msg);
          authorLoaded = true;
        }
        if (author != null && pattern.matcher(author).find()) {
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

  /** Short label for a rule in lists and on the strip */
  public static String label (String text) {
    String oneLine = text.replace('\n', ' ').trim();
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
