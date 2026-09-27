/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

/**
 * TGx101: paying bot invoices with a bank card (@PremiumBot and other bots).
 * Stripe and Smart Glocal: the card goes straight to the payment system, which returns a one-time
 * token; other systems: their own payment page in a WebView. Nothing is stored on the phone or on
 * Telegram servers: credentials are sent with allowSave = false, order info is not saved, the WebView
 * is wiped after use, and nothing is logged.
 */
public final class Tgx101CardPayment {
  private Tgx101CardPayment () { }

  public static void start (@NonNull ViewController<?> c, @NonNull TdApi.InputInvoice inputInvoice, @NonNull TdApi.PaymentForm form) {
    final TdApi.PaymentFormTypeRegular regular = (TdApi.PaymentFormTypeRegular) form.type;
    final TdApi.Invoice invoice = regular.invoice;
    if (invoice.needShippingAddress) {
      UI.showToast(R.string.Tgx101CardShippingUnsupported, Toast.LENGTH_LONG);
      return;
    }
    requestOrderInfo(c, inputInvoice, regular, orderInfoId ->
      requestCredentials(c, regular, credentials ->
        confirm(c, inputInvoice, form, regular, orderInfoId, credentials)));
  }

  // Name, phone and e-mail the bot asks for

  private static void requestOrderInfo (ViewController<?> c, TdApi.InputInvoice inputInvoice, TdApi.PaymentFormTypeRegular regular, RunnableData<String> after) {
    final TdApi.Invoice invoice = regular.invoice;
    if (!invoice.needName && !invoice.needPhoneNumber && !invoice.needEmailAddress) {
      after.runWithData("");
      return;
    }
    TdApi.OrderInfo saved = regular.savedOrderInfo;
    LinearLayout layout = newForm(c);
    EditText name = invoice.needName ? addField(c, layout, R.string.Tgx101OrderName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME, saved != null ? saved.name : null) : null;
    EditText phone = invoice.needPhoneNumber ? addField(c, layout, R.string.Tgx101OrderPhone, InputType.TYPE_CLASS_PHONE, saved != null ? saved.phoneNumber : null) : null;
    EditText email = invoice.needEmailAddress ? addField(c, layout, R.string.Tgx101OrderEmail, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, saved != null ? saved.emailAddress : null) : null;
    AlertDialog.Builder b = new AlertDialog.Builder(c.context(), Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.Tgx101OrderTitle));
    b.setView(layout);
    b.setPositiveButton(Lang.getString(R.string.Tgx101CardNext), (dialog, which) -> {
      TdApi.OrderInfo info = new TdApi.OrderInfo(
        name != null ? name.getText().toString().trim() : "",
        phone != null ? phone.getText().toString().trim() : "",
        email != null ? email.getText().toString().trim() : "",
        null);
      // allowSave = false: the details are not saved on Telegram servers
      c.tdlib().send(new TdApi.ValidateOrderInfo(inputInvoice, info, false), (result, error) -> UI.post(() -> {
        if (error != null) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_LONG);
        } else {
          after.runWithData(result.orderInfoId);
        }
      }));
    });
    b.setNegativeButton(Lang.getString(R.string.Cancel), null);
    c.showAlert(b);
  }

  // Card

  private static void requestCredentials (ViewController<?> c, TdApi.PaymentFormTypeRegular regular, RunnableData<String> after) {
    TdApi.PaymentProvider provider = regular.paymentProvider;
    switch (provider.getConstructor()) {
      case TdApi.PaymentProviderStripe.CONSTRUCTOR: {
        TdApi.PaymentProviderStripe stripe = (TdApi.PaymentProviderStripe) provider;
        showCardForm(c, stripe.needCardholderName, stripe.needCountry, stripe.needPostalCode, card ->
          tokenize(c, () -> stripeToken(stripe.publishableKey, card), after));
        break;
      }
      case TdApi.PaymentProviderSmartGlocal.CONSTRUCTOR: {
        TdApi.PaymentProviderSmartGlocal glocal = (TdApi.PaymentProviderSmartGlocal) provider;
        showCardForm(c, false, false, false, card ->
          tokenize(c, () -> smartGlocalToken(glocal, regular.invoice.isTest, card), after));
        break;
      }
      case TdApi.PaymentProviderOther.CONSTRUCTOR: {
        showWebForm(c, ((TdApi.PaymentProviderOther) provider).url, after);
        break;
      }
      default:
        UI.showToast(R.string.StarsCardUnsupported, Toast.LENGTH_LONG);
        break;
    }
  }

  private static final class Card {
    String number, name, country, postalCode, cvc;
    int month, year; // year: two digits
  }

  private static void showCardForm (ViewController<?> c, boolean needName, boolean needCountry, boolean needPostalCode, RunnableData<Card> after) {
    LinearLayout layout = newForm(c);
    EditText number = addField(c, layout, R.string.Tgx101CardNumber, InputType.TYPE_CLASS_NUMBER, null);
    EditText expiry = addField(c, layout, R.string.Tgx101CardExpiry, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE, null);
    EditText cvc = addField(c, layout, R.string.Tgx101CardCvc, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, null);
    EditText name = needName ? addField(c, layout, R.string.Tgx101CardName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, null) : null;
    EditText country = needCountry ? addField(c, layout, R.string.Tgx101CardCountry, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, null) : null;
    EditText zip = needPostalCode ? addField(c, layout, R.string.Tgx101CardZip, InputType.TYPE_CLASS_TEXT, null) : null;
    AlertDialog.Builder b = new AlertDialog.Builder(c.context(), Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.Tgx101CardTitle));
    b.setView(layout);
    b.setPositiveButton(Lang.getString(R.string.Tgx101CardNext), (dialog, which) -> {
      Card card = new Card();
      card.number = number.getText().toString().replaceAll("[^0-9]", "");
      card.cvc = cvc.getText().toString().replaceAll("[^0-9]", "");
      String exp = expiry.getText().toString().replaceAll("[^0-9]", "");
      if (exp.length() == 4) {
        card.month = Integer.parseInt(exp.substring(0, 2));
        card.year = Integer.parseInt(exp.substring(2, 4));
      }
      card.name = name != null ? name.getText().toString().trim() : null;
      card.country = country != null ? country.getText().toString().trim() : null;
      card.postalCode = zip != null ? zip.getText().toString().trim() : null;
      boolean valid = card.number.length() >= 12 && card.number.length() <= 19 && luhn(card.number)
        && card.month >= 1 && card.month <= 12 && card.cvc.length() >= 3 && card.cvc.length() <= 4
        && (!needName || !StringUtils.isEmpty(card.name)) && (!needCountry || !StringUtils.isEmpty(card.country)) && (!needPostalCode || !StringUtils.isEmpty(card.postalCode));
      if (!valid) {
        UI.showToast(R.string.Tgx101CardInvalid, Toast.LENGTH_LONG);
        return;
      }
      after.runWithData(card);
    });
    b.setNegativeButton(Lang.getString(R.string.Cancel), null);
    c.showAlert(b);
  }

  private static boolean luhn (String number) {
    int sum = 0;
    boolean alternate = false;
    for (int i = number.length() - 1; i >= 0; i--) {
      int n = number.charAt(i) - '0';
      if (alternate) {
        n *= 2;
        if (n > 9) n -= 9;
      }
      sum += n;
      alternate = !alternate;
    }
    return sum % 10 == 0;
  }

  private interface TokenRequest {
    @Nullable String run () throws Exception;
  }

  private static void tokenize (ViewController<?> c, TokenRequest request, RunnableData<String> after) {
    UI.showToast(R.string.Tgx101CardProcessing, Toast.LENGTH_SHORT);
    new Thread(() -> {
      String credentials;
      try {
        credentials = request.run();
      } catch (Exception e) {
        credentials = null;
      }
      final String result = credentials;
      UI.post(() -> {
        if (c.isDestroyed()) return;
        if (result == null) {
          UI.showToast(R.string.Tgx101CardConnectionFailed, Toast.LENGTH_LONG);
        } else {
          after.runWithData(result);
        }
      });
    }, "TGx101CardToken").start();
  }

  private static String stripeToken (String publishableKey, Card card) throws Exception {
    StringBuilder body = new StringBuilder();
    appendForm(body, "card[number]", card.number);
    appendForm(body, "card[exp_month]", String.valueOf(card.month));
    appendForm(body, "card[exp_year]", String.valueOf(card.year));
    appendForm(body, "card[cvc]", card.cvc);
    if (!StringUtils.isEmpty(card.name)) appendForm(body, "card[name]", card.name);
    if (!StringUtils.isEmpty(card.country)) appendForm(body, "card[address_country]", card.country);
    if (!StringUtils.isEmpty(card.postalCode)) appendForm(body, "card[address_zip]", card.postalCode);
    HttpURLConnection conn = (HttpURLConnection) new URL("https://api.stripe.com/v1/tokens").openConnection();
    try {
      conn.setConnectTimeout(30_000);
      conn.setReadTimeout(60_000);
      conn.setDoOutput(true);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Authorization", "Bearer " + publishableKey);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      try (OutputStream out = conn.getOutputStream()) {
        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
      }
      if (conn.getResponseCode() / 100 != 2) {
        return null;
      }
      JSONObject token = new JSONObject(read(conn.getInputStream()));
      return String.format(Locale.US, "{\"type\":\"%1$s\", \"id\":\"%2$s\"}", token.optString("type", "card"), token.getString("id"));
    } finally {
      conn.disconnect();
    }
  }

  private static String smartGlocalToken (TdApi.PaymentProviderSmartGlocal provider, boolean isTest, Card card) throws Exception {
    String url = provider.tokenizeUrl;
    if (StringUtils.isEmpty(url) || !(url.startsWith("https://") && url.endsWith(".smart-glocal.com/cds/v1/tokenize/card"))) {
      url = isTest ? "https://tgb-playground.smart-glocal.com/cds/v1/tokenize/card" : "https://tgb.smart-glocal.com/cds/v1/tokenize/card";
    }
    JSONObject cardObject = new JSONObject();
    cardObject.put("number", card.number);
    cardObject.put("expiration_month", String.format(Locale.US, "%02d", card.month));
    cardObject.put("expiration_year", String.valueOf(card.year));
    cardObject.put("security_code", card.cvc);
    JSONObject request = new JSONObject();
    request.put("card", cardObject);
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    try {
      conn.setConnectTimeout(30_000);
      conn.setReadTimeout(80_000);
      conn.setDoOutput(true);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("X-PUBLIC-TOKEN", provider.publicToken);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(request.toString().getBytes(StandardCharsets.UTF_8));
      }
      if (conn.getResponseCode() / 100 != 2) {
        return null;
      }
      String token = new JSONObject(read(conn.getInputStream())).getJSONObject("data").getString("token");
      JSONObject result = new JSONObject();
      result.put("token", token);
      result.put("type", "card");
      return result.toString();
    } finally {
      conn.disconnect();
    }
  }

  private static void appendForm (StringBuilder b, String key, String value) throws Exception {
    if (b.length() > 0) b.append('&');
    b.append(URLEncoder.encode(key, "UTF-8")).append('=').append(URLEncoder.encode(value, "UTF-8"));
  }

  private static String read (InputStream in) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toString("UTF-8");
  }

  // Payment page of other payment systems, and 3-D Secure pages

  private static final class WebProxy {
    private final RunnableData<String> onCredentials;

    WebProxy (RunnableData<String> onCredentials) {
      this.onCredentials = onCredentials;
    }

    @Keep
    @JavascriptInterface
    public void postEvent (final String eventName, final String eventData) {
      if (!"payment_form_submit".equals(eventName)) return;
      UI.post(() -> {
        String credentials;
        try {
          credentials = new JSONObject(eventData).getJSONObject("credentials").toString();
        } catch (Throwable t) {
          credentials = eventData;
        }
        onCredentials.runWithData(credentials);
      });
    }
  }

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  private static void showWebForm (ViewController<?> c, String url, @Nullable RunnableData<String> onCredentials) {
    final Dialog dialog = new Dialog(c.context(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    WebView webView = new WebView(c.context());
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setSaveFormData(false);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    }
    if (onCredentials != null) {
      webView.addJavascriptInterface(new WebProxy(credentials -> {
        dialog.dismiss();
        onCredentials.runWithData(credentials);
      }), "TelegramWebviewProxy");
    }
    final java.util.Set<String> visited = new java.util.HashSet<>();
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageStarted (WebView view, String pageUrl, android.graphics.Bitmap favicon) {
        rememberOrigin(visited, pageUrl);
      }

      @Override
      public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
        String next = request.getUrl().toString();
        if (next.startsWith("tg:") || next.startsWith("https://t.me/") || next.startsWith("http://t.me/")) {
          dialog.dismiss(); // payment page finished and returns to Telegram
          return true;
        }
        return false;
      }
    });
    dialog.setContentView(webView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    dialog.setOnDismissListener(d -> {
      // Nothing from the payment page stays on the phone. Only its own sites are wiped:
      // other web pages in the app (bot games, video players) keep their data.
      forgetOrigins(visited);
      webView.clearHistory();
      webView.destroy();
    });
    webView.loadUrl(url);
    dialog.show();
  }

  private static void rememberOrigin (java.util.Set<String> origins, @Nullable String pageUrl) {
    if (pageUrl == null) return;
    android.net.Uri uri = android.net.Uri.parse(pageUrl);
    if (uri.getScheme() != null && uri.getHost() != null && uri.getScheme().startsWith("http")) {
      origins.add(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : ""));
    }
  }

  private static void forgetOrigins (java.util.Set<String> origins) {
    CookieManager cookies = CookieManager.getInstance();
    for (String origin : origins) {
      WebStorage.getInstance().deleteOrigin(origin);
      String cookieString = cookies.getCookie(origin);
      if (cookieString == null) continue;
      for (String cookie : cookieString.split(";")) {
        int eq = cookie.indexOf('=');
        String name = (eq >= 0 ? cookie.substring(0, eq) : cookie).trim();
        if (!name.isEmpty()) {
          cookies.setCookie(origin, name + "=; Max-Age=0; Path=/");
        }
      }
    }
    cookies.flush();
  }

  // Confirm and send

  private static void confirm (ViewController<?> c, TdApi.InputInvoice inputInvoice, TdApi.PaymentForm form, TdApi.PaymentFormTypeRegular regular, String orderInfoId, String credentials) {
    long total = 0;
    for (TdApi.LabeledPricePart part : regular.invoice.priceParts) {
      total += part.amount;
    }
    String amount = CurrencyUtils.buildAmount(regular.invoice.currency, total);
    String title = form.productInfo != null && !StringUtils.isEmpty(form.productInfo.title) ? form.productInfo.title : c.tdlib().cache().userName(form.sellerBotUserId);
    c.showOptions(Lang.getString(R.string.Tgx101CardConfirm, amount, title),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.InvoicePay, amount), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_check_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          send(c, inputInvoice, form, orderInfoId, credentials);
        }
        return true;
      });
  }

  private static void send (ViewController<?> c, TdApi.InputInvoice inputInvoice, TdApi.PaymentForm form, String orderInfoId, String credentials) {
    c.tdlib().send(new TdApi.SendPaymentForm(inputInvoice, form.id, orderInfoId, "", new TdApi.InputCredentialsNew(credentials, false), 0), (result, error) -> UI.post(() -> {
      if (error != null) {
        UI.showToast(Lang.getString(R.string.StarsPayFailed, TD.toErrorString(error)), Toast.LENGTH_LONG);
      } else if (!StringUtils.isEmpty(result.verificationUrl)) {
        showWebForm(c, result.verificationUrl, null); // 3-D Secure
      } else {
        UI.showToast(R.string.StarsPaid, Toast.LENGTH_SHORT);
      }
    }));
  }

  // Form helpers

  private static LinearLayout newForm (ViewController<?> c) {
    LinearLayout layout = new LinearLayout(c.context());
    layout.setOrientation(LinearLayout.VERTICAL);
    int padding = Screen.dp(20f);
    layout.setPadding(padding, Screen.dp(8f), padding, 0);
    return layout;
  }

  private static EditText addField (ViewController<?> c, LinearLayout layout, int hintRes, int inputType, @Nullable String value) {
    EditText field = new EditText(c.context());
    field.setHint(Lang.getString(hintRes));
    field.setInputType(inputType);
    field.setSingleLine(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); // autofill services must not offer to save card data
    }
    if (!StringUtils.isEmpty(value)) {
      field.setText(value);
    }
    layout.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return field;
  }
}
