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
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (user 2026-11-13, mockup «Новый вход в аккаунт», «как в оф телеграм… безопасность прежде всего»): someone logged
 * in to the account from a new device — a card pops up: device, place, time, «Это я» / «Это был не я».
 * «Это я» confirms the session (TDLib confirmSession) and the card is gone. «Это был не я» ends that session at once
 * (terminateSession) and shows «Сеанс завершён» with what to do next: log out everywhere else (asked again — it logs out
 * the person's own computers too), a cloud password, the list of devices, and why it happened (a code lured out).
 * Several new logins at once: one card, each login with its own two buttons.
 */
public final class Tgx101NewLogin {
  private Tgx101NewLogin () { }

  private static final int RED = 0xffe5533f, RED_TEXT = 0xfff08a7c, GREEN = 0xff5cc48a;

  /** «accountId:sessionId» closed with Back this run — not shown again until the app starts anew */
  private static final Set<String> putAside = new HashSet<>();
  private static final Set<Integer> showing = new HashSet<>();
  private static final Set<Integer> loading = new HashSet<>();

  private static void diag (String what) {
    org.thunderdog.challegram.Tgx101Diag.mark("new login: " + what);
  }

  /** From Tdlib on every updateUnconfirmedSession, on the main thread */
  public static void onUpdate (Tdlib tdlib, @Nullable TdApi.UnconfirmedSession session, int count) {
    diag("account " + tdlib.id() + (session != null ? ": " + count + " unconfirmed, newest " + session.deviceModel : ": none"));
    if (session == null) return;
    tryShow(tdlib);
  }

  /** The main screen came to the front: a login that came while it was away is shown now */
  public static void onMainFocus (ViewController<?> c) {
    Tdlib tdlib = c.tdlib();
    if (tdlib != null && tdlib.tgx101UnconfirmedSession() != null) tryShow(tdlib);
  }

  private static void tryShow (Tdlib tdlib) {
    final int accountId = tdlib.id();
    if (showing.contains(accountId) || loading.contains(accountId)) return;
    BaseActivity activity = UI.getUiContext();
    ViewController<?> c = activity != null ? activity.navigation().getCurrentStackItem() : null;
    if (c == null || activity.getActivityState() != UI.State.RESUMED) return; // shown on the next return to the chats
    loading.add(accountId);
    tdlib.send(new TdApi.GetActiveSessions(), (result, error) -> UI.post(() -> {
      loading.remove(accountId);
      if (error != null || result == null) {
        diag("sessions not read — " + (error != null ? error.message : "nothing"));
        return;
      }
      List<TdApi.Session> fresh = new ArrayList<>();
      int others = 0;
      for (TdApi.Session s : result.sessions) {
        if (s.isUnconfirmed && !s.isCurrent && !putAside.contains(accountId + ":" + s.id)) fresh.add(s);
        if (!s.isCurrent) others++;
      }
      if (fresh.isEmpty()) return;
      BaseActivity a = UI.getUiContext();
      ViewController<?> now = a != null ? a.navigation().getCurrentStackItem() : null;
      if (now == null) return;
      showCard(now, tdlib, fresh);
    }));
  }

  // The card

  private static void showCard (ViewController<?> c, Tdlib tdlib, List<TdApi.Session> sessions) {
    final int accountId = tdlib.id();
    final Context context = c.context();
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    final boolean[] answered = {false};
    showing.add(accountId);
    diag("card shown, " + sessions.size() + " login(s)");
    handle[0] = Tgx101Sheet.show(c, () -> {
      showing.remove(accountId);
      if (!answered[0]) {
        for (TdApi.Session s : sessions) putAside.add(accountId + ":" + s.id);
        diag("card closed without an answer");
      }
    }, body -> {
      int pad = Screen.dp(18f);
      LinearLayout head = new LinearLayout(context);
      head.setOrientation(LinearLayout.HORIZONTAL);
      head.setGravity(Gravity.CENTER_VERTICAL);
      head.setPadding(pad, Screen.dp(6f), pad, 0);
      head.addView(iconTile(context, R.drawable.baseline_security_24, ColorUtils.alphaColor(.16f, RED), RED_TEXT, 44, 13), new LinearLayout.LayoutParams(Screen.dp(44f), Screen.dp(44f)));
      LinearLayout titles = new LinearLayout(context);
      titles.setOrientation(LinearLayout.VERTICAL);
      titles.setPadding(Screen.dp(12f), 0, 0, 0);
      titles.addView(text(context, sessions.size() == 1 ? Lang.getString(R.string.Tgx101NewLoginTitle) : Lang.getString(R.string.Tgx101NewLoginTitleMany, sessions.size()), 18f, Theme.textAccentColor(), true));
      if (multipleAccounts(tdlib)) {
        titles.addView(text(context, Lang.getString(R.string.Tgx101NewLoginAccount, tdlib.accountName()), 13f, Theme.textDecentColor(), false));
      }
      head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      body.addView(head);

      final List<View> rows = new ArrayList<>();
      for (TdApi.Session s : sessions) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(pad, Screen.dp(14f), pad, 0);
        TextView info = text(context, describe(s) + (sessions.size() == 1 ? "\n" + Lang.getString(R.string.Tgx101NewLoginQuestion) : ""), 15f, Theme.textDecentColor(), false);
        info.setLineSpacing(Screen.dp(2f), 1f);
        row.addView(info);
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, Screen.dp(12f), 0, 0);
        int blue = Theme.getColor(org.thunderdog.challegram.theme.ColorId.textNeutral);
        TextView me = button(context, Lang.getString(R.string.Tgx101NewLoginMe), 0, ColorUtils.alphaColor(.9f, blue), blue);
        TextView notMe = button(context, Lang.getString(R.string.Tgx101NewLoginNotMe), RED, 0, 0xffffffff);
        buttons.addView(me, new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f);
        p.leftMargin = Screen.dp(10f);
        buttons.addView(notMe, p);
        row.addView(buttons);
        body.addView(row);
        rows.add(row);

        final Runnable gone = () -> {
          row.setVisibility(View.GONE);
          boolean any = false;
          for (View r : rows) if (r.getVisibility() == View.VISIBLE) any = true;
          if (!any) {
            answered[0] = true;
            handle[0].dismiss();
          }
        };
        me.setOnClickListener(v -> {
          me.setEnabled(false);
          notMe.setEnabled(false);
          tdlib.send(new TdApi.ConfirmSession(s.id), (ok, error) -> UI.post(() -> {
            if (error != null) {
              diag("confirm failed — " + error.message);
              UI.showError(error);
              me.setEnabled(true);
              notMe.setEnabled(true);
              return;
            }
            diag("confirmed " + s.id);
            UI.showToast(R.string.Tgx101NewLoginConfirmed, Toast.LENGTH_SHORT);
            gone.run();
          }));
        });
        notMe.setOnClickListener(v -> {
          me.setEnabled(false);
          notMe.setEnabled(false);
          tdlib.send(new TdApi.TerminateSession(s.id), (ok, error) -> UI.post(() -> {
            if (error != null) {
              diag("terminate failed — " + error.message);
              UI.showError(error);
              me.setEnabled(true);
              notMe.setEnabled(true);
              return;
            }
            diag("terminated " + s.id);
            gone.run();
            BaseActivity a = UI.getUiContext();
            ViewController<?> now = a != null ? a.navigation().getCurrentStackItem() : null;
            if (now != null) showEnded(now, tdlib, s);
          }));
        });
      }
    }, true);
  }

  // «Сеанс завершён»

  private static void showEnded (ViewController<?> c, Tdlib tdlib, TdApi.Session ended) {
    final Context context = c.context();
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    tdlib.send(new TdApi.GetActiveSessions(), (sessions, e1) -> tdlib.send(new TdApi.GetPasswordState(), (password, e2) -> UI.post(() -> {
      final List<TdApi.Session> others = new ArrayList<>();
      if (sessions != null) for (TdApi.Session s : sessions.sessions) if (!s.isCurrent) others.add(s);
      final boolean hasPassword = password != null && password.hasPassword;
      handle[0] = Tgx101Sheet.show(c, null, body -> {
        int pad = Screen.dp(18f);
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(pad, Screen.dp(6f), pad, 0);
        top.addView(iconTile(context, R.drawable.baseline_check_circle_24, ColorUtils.alphaColor(.14f, GREEN), GREEN, 60, 19), new LinearLayout.LayoutParams(Screen.dp(60f), Screen.dp(60f)));
        TextView title = text(context, Lang.getString(R.string.Tgx101NewLoginEnded), 20f, Theme.textAccentColor(), true);
        title.setPadding(0, Screen.dp(10f), 0, 0);
        top.addView(title);
        TextView sub = text(context, Lang.getString(R.string.Tgx101NewLoginEndedText, device(ended)), 14f, Theme.textDecentColor(), false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Screen.dp(6f), 0, 0);
        top.addView(sub);
        body.addView(top);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Screen.dp(16f));
        bg.setColor(ColorUtils.alphaColor(.05f, Theme.textAccentColor()));
        card.setBackground(bg);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = cp.rightMargin = Screen.dp(14f);
        cp.topMargin = Screen.dp(16f);
        int blue = Theme.getColor(org.thunderdog.challegram.theme.ColorId.textNeutral);
        if (!others.isEmpty()) {
          card.addView(action(context, R.drawable.baseline_logout_24, ColorUtils.alphaColor(.16f, RED), RED_TEXT, Lang.getString(R.string.Tgx101NewLoginOutAll), Lang.getString(R.string.Tgx101NewLoginOutAllHint, others.size()), v -> confirmOutAll(c, tdlib, others, handle[0])));
        }
        card.addView(action(context, R.drawable.baseline_lock_24, ColorUtils.alphaColor(.16f, blue), blue,
          Lang.getString(hasPassword ? R.string.Tgx101NewLoginPasswordChange : R.string.Tgx101NewLoginPassword),
          Lang.getString(hasPassword ? R.string.Tgx101NewLoginPasswordChangeHint : R.string.Tgx101NewLoginPasswordHint), v -> {
            handle[0].dismiss();
            openPassword(c, tdlib, password);
          }));
        card.addView(action(context, R.drawable.baseline_devices_other_24, ColorUtils.alphaColor(.08f, Theme.textAccentColor()), Theme.textAccentColor(),
          Lang.getString(R.string.Tgx101NewLoginDevices), Lang.getString(R.string.Tgx101NewLoginDevicesHint), v -> {
            handle[0].dismiss();
            openDevices(c, tdlib);
          }));
        body.addView(card, cp);

        TextView tip = text(context, Lang.getString(R.string.Tgx101NewLoginTip), 13f, Theme.textDecentColor(), false);
        tip.setPadding(pad, Screen.dp(14f), pad, 0);
        tip.setLineSpacing(Screen.dp(2f), 1f);
        body.addView(tip);

        TextView done = button(context, Lang.getString(R.string.Tgx101NewLoginDone), 0, ColorUtils.alphaColor(.9f, blue), blue);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(44f));
        dp.leftMargin = dp.rightMargin = Screen.dp(14f);
        dp.topMargin = Screen.dp(16f);
        done.setOnClickListener(v -> handle[0].dismiss());
        body.addView(done, dp);
      }, true);
    })));
  }

  private static void confirmOutAll (ViewController<?> c, Tdlib tdlib, List<TdApi.Session> others, Tgx101Sheet.Handle parent) {
    final Context context = c.context();
    final Tgx101Sheet.Handle[] handle = new Tgx101Sheet.Handle[1];
    handle[0] = Tgx101Sheet.show(c, null, body -> {
      int pad = Screen.dp(18f);
      LinearLayout top = new LinearLayout(context);
      top.setOrientation(LinearLayout.VERTICAL);
      top.setGravity(Gravity.CENTER_HORIZONTAL);
      top.setPadding(pad, Screen.dp(6f), pad, 0);
      top.addView(iconTile(context, R.drawable.baseline_logout_24, ColorUtils.alphaColor(.16f, RED), RED_TEXT, 48, 15), new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
      TextView title = text(context, Lang.getString(R.string.Tgx101NewLoginOutAllTitle), 18f, Theme.textAccentColor(), true);
      title.setGravity(Gravity.CENTER);
      title.setPadding(0, Screen.dp(10f), 0, 0);
      top.addView(title);
      body.addView(top);
      int shown = 0;
      for (TdApi.Session s : others) {
        if (shown++ == 6) {
          TextView more = text(context, "…", 14f, Theme.textDecentColor(), false);
          more.setPadding(pad, Screen.dp(4f), pad, 0);
          body.addView(more);
          break;
        }
        TextView row = text(context, "•  " + device(s) + "  ·  " + Lang.getRelativeTimestampShort(s.lastActiveDate, TimeUnit.SECONDS), 14f, Theme.textAccentColor(), false);
        row.setPadding(pad, Screen.dp(8f), pad, 0);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(row);
      }
      TextView hint = text(context, Lang.getString(R.string.Tgx101NewLoginOutAllText), 13f, Theme.textDecentColor(), false);
      hint.setPadding(pad, Screen.dp(12f), pad, 0);
      body.addView(hint);

      LinearLayout buttons = new LinearLayout(context);
      buttons.setOrientation(LinearLayout.HORIZONTAL);
      buttons.setPadding(Screen.dp(14f), Screen.dp(16f), Screen.dp(14f), 0);
      TextView cancel = button(context, Lang.getString(R.string.Cancel), 0, ColorUtils.alphaColor(.25f, Theme.textAccentColor()), Theme.textAccentColor());
      TextView out = button(context, Lang.getString(R.string.Tgx101NewLoginOutAllDo), RED, 0, 0xffffffff);
      buttons.addView(cancel, new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Screen.dp(42f), 1f);
      p.leftMargin = Screen.dp(10f);
      buttons.addView(out, p);
      body.addView(buttons);
      cancel.setOnClickListener(v -> handle[0].dismiss());
      out.setOnClickListener(v -> {
        out.setEnabled(false);
        tdlib.send(new TdApi.TerminateAllOtherSessions(), (ok, error) -> UI.post(() -> {
          if (error != null) {
            diag("log out everywhere failed — " + error.message);
            UI.showError(error); // e.g. a session younger than a day may not end the others
            out.setEnabled(true);
            return;
          }
          diag("logged out on " + others.size() + " other device(s)");
          UI.showToast(R.string.Tgx101NewLoginOutAllDone, Toast.LENGTH_SHORT);
          handle[0].dismiss();
          parent.dismiss();
        }));
      });
    }, true);
  }

  private static void openPassword (ViewController<?> c, Tdlib tdlib, @Nullable TdApi.PasswordState state) {
    if (state == null) {
      openPrivacy(c, tdlib);
      return;
    }
    if (!state.hasPassword) {
      Settings2FAController controller = new Settings2FAController(c.context(), tdlib);
      controller.setArguments(new Settings2FAController.Args(state));
      c.navigateTo(controller);
    } else {
      PasswordController controller = new PasswordController(c.context(), tdlib);
      controller.setArguments(new PasswordController.Args(PasswordController.MODE_UNLOCK_EDIT, state));
      c.navigateTo(controller);
    }
  }

  private static void openPrivacy (ViewController<?> c, Tdlib tdlib) {
    c.navigateTo(new SettingsPrivacyController(c.context(), tdlib));
  }

  private static void openDevices (ViewController<?> c, Tdlib tdlib) {
    c.navigateTo(new SettingsSessionsController(c.context(), tdlib));
  }

  // Texts

  private static boolean multipleAccounts (Tdlib tdlib) {
    try {
      return tdlib.context().getActiveAccounts().size() > 1;
    } catch (Throwable t) {
      return false;
    }
  }

  private static String device (TdApi.Session s) {
    String model = !TextUtils.isEmpty(s.deviceModel) ? s.deviceModel : s.applicationName;
    if (!TextUtils.isEmpty(s.applicationName) && !TextUtils.isEmpty(s.deviceModel) && !s.deviceModel.contains(s.applicationName)) {
      model = s.applicationName + " · " + s.deviceModel;
    }
    return model;
  }

  private static String describe (TdApi.Session s) {
    StringBuilder b = new StringBuilder(device(s));
    if (!TextUtils.isEmpty(s.location)) b.append(" · ").append(s.location);
    b.append("\n").append(Lang.getRelativeTimestamp(s.logInDate, TimeUnit.SECONDS));
    if (!TextUtils.isEmpty(s.ipAddress)) b.append(" · IP ").append(s.ipAddress);
    return b.toString();
  }

  // Views

  private static TextView text (Context context, CharSequence s, float sizeDp, int color, boolean medium) {
    TextView v = new TextView(context);
    v.setText(s);
    v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp);
    v.setTextColor(color);
    v.setTypeface(medium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    return v;
  }

  private static GradientDrawable box (int fill, int stroke) {
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(14f));
    bg.setColor(fill);
    if (stroke != 0) bg.setStroke(Screen.dp(1.2f), stroke);
    return bg;
  }

  private static TextView button (Context context, CharSequence s, int fill, int stroke, int textColor) {
    TextView v = text(context, s, 15f, textColor, false);
    v.setGravity(Gravity.CENTER);
    v.setSingleLine(true);
    v.setEllipsize(TextUtils.TruncateAt.END);
    v.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    android.graphics.drawable.Drawable bg = box(fill, stroke);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      bg = new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ColorUtils.alphaColor(.16f, textColor)), bg, box(0xffffffff, 0));
    }
    v.setBackground(bg);
    v.setClickable(true);
    v.setFocusable(true);
    return v;
  }

  private static View iconTile (Context context, int icon, int fill, int tint, int sizeDp, int radiusDp) {
    android.widget.ImageView v = new android.widget.ImageView(context);
    v.setImageResource(icon);
    v.setColorFilter(tint);
    v.setScaleType(android.widget.ImageView.ScaleType.CENTER);
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(Screen.dp(radiusDp));
    bg.setColor(fill);
    v.setBackground(bg);
    return v;
  }

  private static View action (Context context, int icon, int fill, int tint, String title, String hint, View.OnClickListener click) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(14f), Screen.dp(12f), Screen.dp(14f), Screen.dp(12f));
    row.addView(iconTile(context, icon, fill, tint, 36, 11), new LinearLayout.LayoutParams(Screen.dp(36f), Screen.dp(36f)));
    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.setPadding(Screen.dp(12f), 0, 0, 0);
    texts.addView(text(context, title, 15f, Theme.textAccentColor(), false));
    texts.addView(text(context, hint, 13f, Theme.textDecentColor(), false));
    row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ColorUtils.alphaColor(.1f, Theme.textAccentColor())), null, box(0xffffffff, 0)));
    }
    row.setClickable(true);
    row.setOnClickListener(click);
    return row;
  }
}
