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
package org.thunderdog.challegram.tool;

import androidx.annotation.Nullable;

/**
 * TGx101 (user 2026-10-16 «калькулятор в самом поле ввода, как на iPhone, но лучше»): the whole input is a sum —
 * «12*3+5», «150 × 4 =», «(2+3)^2», «1 200 / 3», «20% of…» as «200*15%» — gives its result. Dates and phone numbers
 * («2026-10-16», «050-123-45-67», «+380…») are left alone.
 */
public final class Tgx101Calc {
  private Tgx101Calc () { }

  /** the result as it is written («41», «0.3333333333», «1.5e12»), or null when the text isn't a sum */
  public static @Nullable String evaluate (@Nullable CharSequence input) {
    if (input == null) return null;
    String raw = input.toString().trim();
    if (raw.length() < 3 || raw.length() > 80) return null;
    boolean asked = raw.endsWith("=");
    if (asked) raw = raw.substring(0, raw.length() - 1).trim();
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case ' ': case ' ': case ' ': case ' ': break; // «1 200» is one number
        case '×': case 'x': case 'X': case 'х': case 'Х': case '*': case '·': b.append('*'); break;
        case '÷': case ':': case '/': b.append('/'); break;
        case '−': case '–': case '—': b.append('-'); break;
        case ',': b.append('.'); break;
        default:
          if ((c >= '0' && c <= '9') || c == '.' || c == '+' || c == '-' || c == '(' || c == ')' || c == '^' || c == '%') b.append(c);
          else return null; // a letter or anything else: it's text
      }
    }
    String s = b.toString();
    if (s.isEmpty() || !Character.isDigit(s.charAt(s.length() - 1)) && s.charAt(s.length() - 1) != ')' && s.charAt(s.length() - 1) != '%') return null;
    // a binary operator between two operands is a must («+380…» or «-5» alone are not sums)
    int operators = 0, minus = 0, numbers = 0;
    boolean inNumber = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean digit = Character.isDigit(c) || c == '.';
      if (digit && !inNumber) numbers++;
      inNumber = digit;
      if (i > 0 && "+-*/^".indexOf(c) >= 0 && (Character.isDigit(s.charAt(i - 1)) || s.charAt(i - 1) == ')' || s.charAt(i - 1) == '%')) {
        operators++;
        if (c == '-') minus++;
      }
    }
    if (operators == 0) return null;
    if (!asked) {
      if (s.startsWith("+")) return null; // a phone number
      if (operators == minus && numbers >= 3) return null; // 2026-10-16, 050-123-45-67
    }
    try {
      Parser p = new Parser(s);
      double v = p.expression();
      if (p.pos != s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
      return format(v);
    } catch (Throwable t) {
      return null;
    }
  }

  private static String format (double v) {
    if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
    java.math.BigDecimal d = new java.math.BigDecimal(v).round(new java.math.MathContext(10));
    String out = d.stripTrailingZeros().toPlainString();
    return out.length() > 18 ? d.stripTrailingZeros().toString() : out;
  }

  /** + − × ÷ ^ %, brackets, unary minus — the usual order */
  private static final class Parser {
    final String s;
    int pos;

    Parser (String s) {
      this.s = s;
    }

    double expression () {
      double v = term();
      while (pos < s.length()) {
        char c = s.charAt(pos);
        if (c == '+' || c == '-') {
          pos++;
          double r = term();
          // «200+15%» is 200 plus 15 % of 200, as on a calculator
          if (lastPercent) r = v * r;
          v = c == '+' ? v + r : v - r;
        } else break;
      }
      return v;
    }

    boolean lastPercent;

    double term () {
      double v = power();
      while (pos < s.length()) {
        char c = s.charAt(pos);
        if (c == '*' || c == '/') {
          pos++;
          double r = power();
          v = c == '*' ? v * r : v / r;
          lastPercent = false;
        } else break;
      }
      return v;
    }

    double power () {
      double v = unary();
      if (pos < s.length() && s.charAt(pos) == '^') {
        pos++;
        v = Math.pow(v, power());
        lastPercent = false;
      }
      return v;
    }

    double unary () {
      if (pos < s.length() && s.charAt(pos) == '-') {
        pos++;
        return -unary();
      }
      if (pos < s.length() && s.charAt(pos) == '+') {
        pos++;
        return unary();
      }
      return primary();
    }

    double primary () {
      lastPercent = false;
      double v;
      if (pos < s.length() && s.charAt(pos) == '(') {
        pos++;
        v = expression();
        if (pos >= s.length() || s.charAt(pos) != ')') throw new IllegalArgumentException();
        pos++;
      } else {
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException();
        v = Double.parseDouble(s.substring(start, pos));
      }
      if (pos < s.length() && s.charAt(pos) == '%') {
        pos++;
        v = v / 100d;
        lastPercent = true;
      }
      return v;
    }
  }
}
