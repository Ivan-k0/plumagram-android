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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * TGx101: Telegram Mini Apps inside the app — a WebView with the Telegram.WebApp bridge (TelegramWebviewProxy.postEvent
 * from the page, Telegram.WebView.receiveEvent back): theme, main / back / settings buttons, links, popups, data for
 * the bot, custom bot methods. Payments and phone / write access requests are declined.
 */
public class Tgx101WebAppController extends ViewController<Tgx101WebAppController.Args> implements Menu, MoreDelegate {
  // TGx101 (user 2026-10-04): a mini app closes only by its ✕ — a swipe right closed it by accident
  @Override
  protected boolean swipeNavigationEnabled () {
    return false;
  }

  public static class Args {
    public final String title;
    public final String url;
    public final long botUserId;
    public final long launchId; // 0 when opened without OpenWebApp
    public final @Nullable String sendDataButtonText; // a keyboard button's mini app may send data to the bot

    public Args (String title, String url, long botUserId, long launchId, @Nullable String sendDataButtonText) {
      this.title = title;
      this.url = url;
      this.botUserId = botUserId;
      this.launchId = launchId;
      this.sendDataButtonText = sendDataButtonText;
    }
  }

  // Opening

  public static TdApi.WebAppOpenParameters openParameters () {
    return new TdApi.WebAppOpenParameters(themeParameters(), "android", new TdApi.WebAppOpenModeFullSize());
  }

  private static TdApi.ThemeParameters themeParameters () {
    int filling = Theme.getColor(ColorId.filling), background = Theme.getColor(ColorId.background);
    return new TdApi.ThemeParameters(filling, background, Theme.getColor(ColorId.headerBackground), filling, filling,
      Theme.getColor(ColorId.separator), Theme.getColor(ColorId.text), Theme.getColor(ColorId.textLink), Theme.getColor(ColorId.textLink),
      Theme.getColor(ColorId.textLight), Theme.getColor(ColorId.textNegative), Theme.getColor(ColorId.textLight), Theme.getColor(ColorId.textLink),
      Theme.getColor(ColorId.fillingPositive), Theme.getColor(ColorId.fillingPositiveContent));
  }

  /** A mini app button under a message (or a bot's menu / attachment menu url) */
  public static void openInChat (TdlibDelegate context, long chatId, long botUserId, String url) {
    Tdlib tdlib = context.tdlib();
    tdlib.send(new TdApi.OpenWebApp(chatId, botUserId, url, null, null, openParameters()), (info, error) -> UI.post(() -> {
      if (info == null) {
        UI.showError(error);
        return;
      }
      show(context, botUserId, info.url.url, info.launchId, null);
    }));
  }

  /** A keyboard button: the mini app may send data back to the bot */
  public static void openKeyboardButton (TdlibDelegate context, long botUserId, String url, String buttonText) {
    context.tdlib().send(new TdApi.GetWebAppUrl(botUserId, url, openParameters()), (result, error) -> UI.post(() -> {
      if (result == null) {
        UI.showError(error);
        return;
      }
      show(context, botUserId, result.url, 0, buttonText);
    }));
  }

  /** t.me/bot/app and t.me/bot?startapp links */
  public static void openLink (TdlibDelegate context, long chatId, String botUsername, @Nullable String webAppShortName, @Nullable String startParameter) {
    Tdlib tdlib = context.tdlib();
    tdlib.send(new TdApi.SearchPublicChat(botUsername), (chat, error) -> {
      long botUserId = chat != null ? TD.getUserId(chat) : 0;
      if (botUserId == 0) {
        UI.post(() -> UI.showError(error));
        return;
      }
      long targetChatId = chatId != 0 ? chatId : chat.id;
      if (webAppShortName != null && !webAppShortName.isEmpty()) {
        tdlib.send(new TdApi.GetWebAppLinkUrl(targetChatId, botUserId, webAppShortName, startParameter, false, openParameters()), (url, urlError) -> UI.post(() -> {
          if (url == null) {
            UI.showError(urlError);
          } else {
            show(context, botUserId, url.url, 0, null);
          }
        }));
      } else {
        tdlib.send(new TdApi.GetMainWebApp(targetChatId, botUserId, startParameter, openParameters()), (app, appError) -> UI.post(() -> {
          if (app == null) {
            UI.showError(appError);
          } else {
            show(context, botUserId, app.url.url, 0, null);
          }
        }));
      }
    });
  }

  private static void show (TdlibDelegate context, long botUserId, String url, long launchId, @Nullable String sendDataButtonText) {
    Tgx101Diag.mark("mini app: opened (bot " + botUserId + ")");
    Tgx101WebAppController c = new Tgx101WebAppController(context.context(), context.tdlib());
    c.setArguments(new Args(context.tdlib().cache().userName(botUserId), url, botUserId, launchId, sendDataButtonText));
    context.context().navigation().navigateTo(c);
  }

  // Controller

  public Tgx101WebAppController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private WebView webView;
  private DoubleHeaderView headerCell;
  private TextView mainButton;
  private boolean backButtonVisible, settingsButtonVisible, closed;

  @Override
  public int getId () {
    return R.id.controller_tgx101WebApp;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_game;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_game) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      if (settingsButtonVisible) {
        showMore(new int[] {R.id.btn_settings, R.id.btn_reload, R.id.btn_openLink}, new String[] {Lang.getString(R.string.Settings), Lang.getString(R.string.Tgx101WebAppReload), Lang.getString(R.string.OpenInExternalApp)}, 0);
      } else {
        showMore(new int[] {R.id.btn_reload, R.id.btn_openLink}, new String[] {Lang.getString(R.string.Tgx101WebAppReload), Lang.getString(R.string.OpenInExternalApp)}, 0);
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_settings) {
      sendEvent("settings_button_pressed", null);
    } else if (id == R.id.btn_reload) {
      webView.reload();
    } else if (id == R.id.btn_openLink) {
      Intents.openUri(getArgumentsStrict().url);
    }
  }

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override
  protected View onCreateView (Context context) {
    headerCell = new DoubleHeaderView(context());
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);
    headerCell.setTitle(getArgumentsStrict().title);
    headerCell.setSubtitle(Lang.getString(R.string.Tgx101WebAppSubtitle));

    FrameLayoutFix contentView = new FrameLayoutFix(context);
    contentView.setBackgroundColor(Theme.getColor(ColorId.filling));
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    webView = new WebView(context);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
    }
    webView.addJavascriptInterface(new Bridge(), "TelegramWebviewProxy");
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (url.startsWith("tg:") || url.startsWith("https://t.me/") || url.startsWith("http://t.me/")) {
          openTelegramLink(url);
          return true;
        }
        return false;
      }
    });
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged (WebView view, int newProgress) {
        headerCell.animateProgress(newProgress / 100f);
      }
    });
    webView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(webView);

    mainButton = new TextView(context);
    mainButton.setGravity(Gravity.CENTER);
    mainButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    mainButton.setTypeface(Fonts.getRobotoMedium());
    mainButton.setVisibility(View.GONE);
    mainButton.setOnClickListener(v -> sendEvent("main_button_pressed", null));
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f), Gravity.BOTTOM);
    params.leftMargin = params.rightMargin = params.bottomMargin = Screen.dp(12f);
    contentView.addView(mainButton, params);

    webView.loadUrl(getArgumentsStrict().url);
    return contentView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return webView;
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (backButtonVisible) {
      if (commit) sendEvent("back_button_pressed", null);
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  @Override
  public void destroy () {
    super.destroy();
    closed = true;
    if (getArguments() != null && getArguments().launchId != 0) {
      tdlib.send(new TdApi.CloseWebApp(getArguments().launchId), tdlib.typedOkHandler());
    }
    if (webView != null) webView.destroy();
  }

  // Bridge

  private final class Bridge {
    @JavascriptInterface
    public void postEvent (String eventType, String eventData) {
      UI.post(() -> {
        if (!closed) onEvent(eventType, eventData);
      });
    }
  }

  private void sendEvent (String type, @Nullable JSONObject data) {
    if (webView == null || closed) return;
    String js = "window.Telegram && window.Telegram.WebView && window.Telegram.WebView.receiveEvent(" + JSONObject.quote(type) + ", " + (data != null ? data.toString() : "null") + ");";
    webView.evaluateJavascript(js, null);
  }

  private static String hex (int color) {
    return String.format("#%06x", color & 0xffffff);
  }

  private JSONObject themeJson () throws Exception {
    JSONObject theme = new JSONObject();
    theme.put("bg_color", hex(Theme.getColor(ColorId.filling)));
    theme.put("secondary_bg_color", hex(Theme.getColor(ColorId.background)));
    theme.put("text_color", hex(Theme.getColor(ColorId.text)));
    theme.put("hint_color", hex(Theme.getColor(ColorId.textLight)));
    theme.put("link_color", hex(Theme.getColor(ColorId.textLink)));
    theme.put("button_color", hex(Theme.getColor(ColorId.fillingPositive)));
    theme.put("button_text_color", hex(Theme.getColor(ColorId.fillingPositiveContent)));
    theme.put("header_bg_color", hex(Theme.getColor(ColorId.headerBackground)));
    theme.put("accent_text_color", hex(Theme.getColor(ColorId.textLink)));
    theme.put("section_bg_color", hex(Theme.getColor(ColorId.filling)));
    theme.put("section_header_text_color", hex(Theme.getColor(ColorId.textLink)));
    theme.put("subtitle_text_color", hex(Theme.getColor(ColorId.textLight)));
    theme.put("destructive_text_color", hex(Theme.getColor(ColorId.textNegative)));
    theme.put("bottom_bar_bg_color", hex(Theme.getColor(ColorId.filling)));
    theme.put("section_separator_color", hex(Theme.getColor(ColorId.separator)));
    return theme;
  }

  private void onEvent (String type, @Nullable String raw) {
    JSONObject data;
    try {
      data = raw != null && !raw.isEmpty() ? new JSONObject(raw) : new JSONObject();
    } catch (Throwable t) {
      data = new JSONObject();
    }
    try {
      switch (type) {
        case "web_app_close":
          navigateBack();
          break;
        case "web_app_request_theme":
          sendEvent("theme_changed", new JSONObject().put("theme_params", themeJson()));
          break;
        case "web_app_request_viewport": {
          int height = webView != null ? (int) (webView.getHeight() / Screen.density()) : 0;
          sendEvent("viewport_changed", new JSONObject().put("height", height).put("is_expanded", true).put("is_state_stable", true));
          break;
        }
        case "web_app_setup_main_button":
          setupMainButton(data);
          break;
        case "web_app_setup_back_button":
          backButtonVisible = data.optBoolean("is_visible");
          break;
        case "web_app_setup_settings_button":
          settingsButtonVisible = data.optBoolean("is_visible");
          break;
        case "web_app_open_link":
          Intents.openUri(data.optString("url"));
          break;
        case "web_app_open_tg_link":
          openTelegramLink("https://t.me" + data.optString("path_full"));
          break;
        case "web_app_open_popup":
          showPopup(data);
          break;
        case "web_app_data_send": {
          Args args = getArgumentsStrict();
          if (args.sendDataButtonText != null) {
            tdlib.send(new TdApi.SendWebAppData(args.botUserId, args.sendDataButtonText, data.optString("data")), tdlib.typedOkHandler());
            navigateBack();
          }
          break;
        }
        case "web_app_invoke_custom_method": {
          String requestId = data.optString("req_id");
          tdlib.send(new TdApi.SendWebAppCustomRequest(getArgumentsStrict().botUserId, data.optString("method"), data.optJSONObject("params") != null ? data.optJSONObject("params").toString() : "{}"), (result, error) -> UI.post(() -> {
            try {
              JSONObject answer = new JSONObject().put("req_id", requestId);
              if (result != null) {
                answer.put("result", new org.json.JSONTokener(result.result).nextValue());
              } else {
                answer.put("error", TD.toErrorString(error));
              }
              sendEvent("custom_method_invoked", answer);
            } catch (Throwable ignored) { }
          }));
          break;
        }
        case "web_app_request_phone":
          sendEvent("phone_requested", new JSONObject().put("status", "cancelled"));
          break;
        case "web_app_request_write_access":
          sendEvent("write_access_requested", new JSONObject().put("status", "cancelled"));
          break;
        case "web_app_open_invoice":
          UI.showToast(R.string.Tgx101WebAppNoPayments, Toast.LENGTH_SHORT);
          sendEvent("invoice_closed", new JSONObject().put("slug", data.optString("slug")).put("status", "failed"));
          break;
        case "web_app_read_text_from_clipboard":
          sendEvent("clipboard_text_received", new JSONObject().put("req_id", data.optString("req_id")));
          break;
        case "web_app_trigger_haptic_feedback":
          if (webView != null) webView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
          break;
        default:
          break; // web_app_ready, web_app_expand, colours, swipes: nothing to do
      }
    } catch (Throwable t) {
      Tgx101Diag.mark("mini app: event " + type + " failed " + t.getClass().getSimpleName());
    }
  }

  private void setupMainButton (JSONObject data) {
    boolean visible = data.optBoolean("is_visible") && !data.optString("text").isEmpty();
    mainButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (!visible) {
      ((ViewGroup.MarginLayoutParams) webView.getLayoutParams()).bottomMargin = 0;
      webView.requestLayout();
      return;
    }
    int color = parseColor(data.optString("color"), Theme.getColor(ColorId.fillingPositive));
    int textColor = parseColor(data.optString("text_color"), Theme.getColor(ColorId.fillingPositiveContent));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(color);
    bg.setCornerRadius(Screen.dp(12f));
    mainButton.setBackground(bg);
    mainButton.setTextColor(textColor);
    mainButton.setText(data.optBoolean("is_progress_visible") ? "…" : data.optString("text"));
    mainButton.setEnabled(data.optBoolean("is_active", true));
    mainButton.setAlpha(mainButton.isEnabled() ? 1f : .6f);
    ((ViewGroup.MarginLayoutParams) webView.getLayoutParams()).bottomMargin = Screen.dp(72f);
    webView.requestLayout();
  }

  private static int parseColor (String value, int fallback) {
    try {
      return value != null && value.startsWith("#") ? android.graphics.Color.parseColor(value) : fallback;
    } catch (Throwable t) {
      return fallback;
    }
  }

  private void showPopup (JSONObject data) {
    JSONArray buttons = data.optJSONArray("buttons");
    int count = buttons != null ? Math.min(buttons.length(), 3) : 0;
    android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(context(), Theme.dialogTheme());
    String title = data.optString("title");
    if (!title.isEmpty()) b.setTitle(title);
    b.setMessage(data.optString("message"));
    final boolean[] answered = new boolean[1];
    for (int i = 0; i < count; i++) {
      JSONObject button = buttons.optJSONObject(i);
      if (button == null) continue;
      String id = button.optString("id");
      String kind = button.optString("type", "default");
      String text = "ok".equals(kind) ? Lang.getString(R.string.OK) : "close".equals(kind) ? Lang.getString(R.string.Tgx101WebAppClose) : "cancel".equals(kind) ? Lang.getString(R.string.Cancel) : button.optString("text");
      android.content.DialogInterface.OnClickListener listener = (dialog, which) -> {
        answered[0] = true;
        try {
          sendEvent("popup_closed", new JSONObject().put("button_id", id));
        } catch (Throwable ignored) { }
      };
      if (i == 0) b.setPositiveButton(text, listener); else if (i == 1) b.setNegativeButton(text, listener); else b.setNeutralButton(text, listener);
    }
    b.setOnDismissListener(dialog -> {
      if (!answered[0]) sendEvent("popup_closed", new JSONObject());
    });
    showAlert(b);
  }

  private void openTelegramLink (String url) {
    tdlib.ui().openUrl(this, url, new org.thunderdog.challegram.telegram.TdlibUi.UrlOpenParameters());
  }
}
