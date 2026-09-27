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
package org.thunderdog.challegram.proxy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TGx101: WEB proxy carrier, compatible with Telegram's WEB proxy protocol v1
 * (github.com/telegramdesktop/tproxy-server, PROTOCOL.md and ANDROID.md).
 *
 * TDLib connects to a local MTProto proxy on 127.0.0.1 and applies the normal MTProxy transform
 * with the user's secret. Every accepted local connection becomes one logical stream that is
 * multiplexed through a private, hidden WebView showing the relay's bridge page, which carries it
 * over ordinary HTTPS to the relay and on to its stock MTProxy. Only transformed bytes leave the
 * app; the secret never reaches the page (it only sees an HMAC-derived capability).
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class Tgx101WebCarrier {
  interface StateListener {
    void onCarrierStateChanged (int state);
  }

  static final int STATE_CONNECTING = 0, STATE_CONNECTED = 1, STATE_UNSUPPORTED = 2, STATE_STOPPED = 3;

  // Shared frames
  private static final int T_OPEN = 0x01, T_DATA = 0x02, T_CLOSE = 0x03, T_WINDOW = 0x04,
    T_PING = 0x05, T_PONG = 0x06, T_HELLO = 0x10, T_WELCOME = 0x11, T_BYE = 0x1f;
  private static final int MAX_PAYLOAD = 1024 * 1024;
  private static final int CHUNK = 64 * 1024;
  private static final int INITIAL_WINDOW = 4 * 1024 * 1024;
  private static final int WINDOW_BATCH = 256 * 1024;
  private static final long HANDSHAKE_TIMEOUT_MS = 45_000;

  private final Context context;
  private final String host, path;
  private final byte[] secret;
  private final int port;
  private final StateListener stateListener;
  private final Handler main = new Handler(Looper.getMainLooper());

  private volatile boolean active;
  private volatile int state = STATE_STOPPED;
  private ServerSocket server;

  // Carrier (main thread)
  private @Nullable WebView webView;
  private @Nullable JavaScriptReplyProxy reply;
  private String nonce;
  private long generation;
  private boolean welcomed;
  private int backoffMs = 2000;

  // Streams
  private final Object lock = new Object();
  private final Map<Integer, Stream> streams = new HashMap<>();
  private final Set<Integer> closedIds = Collections.synchronizedSet(new LinkedHashSet<>());
  private int nextStreamId = 1;

  Tgx101WebCarrier (Context context, String host, String path, byte[] secret, int port, StateListener stateListener) {
    this.context = context.getApplicationContext();
    this.host = host;
    this.path = path;
    this.secret = secret;
    this.port = port;
    this.stateListener = stateListener;
  }

  static boolean isSupported () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
      && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
      && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
      && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
  }

  // Capability (PROTOCOL.md "Bridge URL", BASE_PATH.md)

  static String bridgeUrl (String host, String path, byte[] secret) {
    String base = path.isEmpty() ? "/" : "/" + path + "/";
    String context = path.isEmpty() ? "tdesktop-web-proxy-bridge-v1\n" + host : "tdesktop-web-proxy-bridge-v2\n" + host + "\n" + path;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      byte[] digest = mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
      String bridge = Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
      return "https://" + host + base + "?bridge=" + bridge;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // Lifecycle

  void start () {
    if (active) return;
    active = true;
    if (!isSupported()) {
      setState(STATE_UNSUPPORTED);
      return; // fails closed: TDLib keeps trying the local port and nothing leaves directly
    }
    setState(STATE_CONNECTING);
    new Thread(this::acceptLoop, "TGx101WebAccept").start();
    main.post(this::createWebView);
  }

  void stop () {
    if (!active) return;
    active = false;
    try {
      if (server != null) server.close();
    } catch (IOException ignored) { }
    closeAllStreams();
    main.post(() -> {
      destroyWebView();
      main.removeCallbacksAndMessages(null);
    });
    setState(STATE_STOPPED);
  }

  int getState () {
    return state;
  }

  private void setState (int state) {
    if (this.state != state) {
      this.state = state;
      main.post(() -> stateListener.onCarrierStateChanged(state));
    }
  }

  // Local listener: TDLib's MTProxy connections

  private void acceptLoop () {
    try {
      server = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
      while (active) {
        Socket socket = server.accept();
        socket.setTcpNoDelay(true);
        if (!active) {
          socket.close();
          break;
        }
        openStream(socket);
      }
    } catch (IOException e) {
      if (active) {
        // Port taken: retry shortly
        main.postDelayed(() -> {
          if (active) new Thread(this::acceptLoop, "TGx101WebAccept").start();
        }, 3000);
      }
    }
  }

  private final class Stream {
    final int id;
    final Socket socket;
    int sendCredit = INITIAL_WINDOW;
    int pendingWindow;
    boolean opened, closed;

    Stream (int id, Socket socket) {
      this.id = id;
      this.socket = socket;
    }
  }

  private void openStream (Socket socket) {
    Stream stream;
    synchronized (lock) {
      int id = nextStreamId;
      nextStreamId = nextStreamId >= 0xffffff ? 1 : nextStreamId + 1;
      stream = new Stream(id, socket);
      streams.put(id, stream);
    }
    main.post(() -> {
      if (welcomed) {
        sendOpen(stream);
      } // otherwise opened after WELCOME
    });
  }

  private void sendOpen (Stream stream) {
    synchronized (lock) {
      if (stream.closed || stream.opened) return;
      stream.opened = true;
    }
    send(frame(T_OPEN, stream.id, null, 0, 0));
    new Thread(() -> uplinkLoop(stream), "TGx101WebUp" + stream.id).start();
  }

  private void uplinkLoop (Stream stream) {
    byte[] buffer = new byte[CHUNK];
    try {
      InputStream in = stream.socket.getInputStream();
      while (true) {
        int read = in.read(buffer);
        if (read < 0) break;
        int offset = 0;
        while (offset < read) {
          int chunk;
          synchronized (lock) {
            while (!stream.closed && stream.sendCredit <= 0) {
              lock.wait(1000);
            }
            if (stream.closed) return;
            chunk = Math.min(read - offset, stream.sendCredit);
            stream.sendCredit -= chunk;
          }
          byte[] frame = frame(T_DATA, stream.id, buffer, offset, chunk);
          main.post(() -> send(frame));
          offset += chunk;
        }
      }
    } catch (IOException | InterruptedException ignored) {
    }
    closeStream(stream, true);
  }

  private void closeStream (Stream stream, boolean notifyRelay) {
    synchronized (lock) {
      if (stream.closed) return;
      stream.closed = true;
      streams.remove(stream.id);
      closedIds.add(stream.id);
      if (closedIds.size() > 4096) {
        Integer first = closedIds.iterator().next();
        closedIds.remove(first);
      }
      lock.notifyAll();
    }
    try {
      stream.socket.close();
    } catch (IOException ignored) { }
    if (notifyRelay && stream.opened) {
      main.post(() -> send(frame(T_CLOSE, stream.id, null, 0, 0)));
    }
  }

  private void closeAllStreams () {
    List<Stream> list;
    synchronized (lock) {
      list = new ArrayList<>(streams.values());
    }
    for (Stream stream : list) {
      closeStream(stream, false);
    }
  }

  // Frames

  private static byte[] frame (int type, int streamId, @Nullable byte[] payload, int offset, int length) {
    byte[] frame = new byte[8 + length];
    frame[0] = (byte) type;
    frame[1] = (byte) (streamId >>> 16);
    frame[2] = (byte) (streamId >>> 8);
    frame[3] = (byte) streamId;
    frame[4] = (byte) (length >>> 24);
    frame[5] = (byte) (length >>> 16);
    frame[6] = (byte) (length >>> 8);
    frame[7] = (byte) length;
    if (length > 0) {
      System.arraycopy(payload, offset, frame, 8, length);
    }
    return frame;
  }

  private static byte[] windowPayload (int delta) {
    return new byte[] {(byte) (delta >>> 24), (byte) (delta >>> 16), (byte) (delta >>> 8), (byte) delta};
  }

  /** Main thread. */
  private void send (byte[] frame) {
    if (reply != null && active) {
      try {
        reply.postMessage(frame);
      } catch (Throwable t) {
        failCarrier();
      }
    }
  }

  /** Main thread: one carrier message holds one or more complete frames. */
  private void onRelayMessage (byte[] data) {
    int offset = 0;
    if (data.length == 0) {
      failCarrier();
      return;
    }
    while (offset < data.length) {
      if (data.length - offset < 8) {
        failCarrier();
        return;
      }
      int type = data[offset] & 0xff;
      int streamId = ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
      long length = ((long) (data[offset + 4] & 0xff) << 24) | ((data[offset + 5] & 0xff) << 16) | ((data[offset + 6] & 0xff) << 8) | (data[offset + 7] & 0xff);
      if (length > MAX_PAYLOAD || offset + 8 + length > data.length) {
        failCarrier();
        return;
      }
      if (!handleFrame(type, streamId, data, offset + 8, (int) length)) {
        failCarrier();
        return;
      }
      offset += 8 + (int) length;
    }
  }

  private boolean handleFrame (int type, int streamId, byte[] data, int offset, int length) {
    switch (type) {
      case T_WELCOME: {
        if (welcomed || streamId != 0) return false;
        welcomed = true;
        backoffMs = 2000;
        setState(STATE_CONNECTED);
        List<Stream> pending;
        synchronized (lock) {
          pending = new ArrayList<>(streams.values());
        }
        for (Stream stream : pending) {
          sendOpen(stream);
        }
        return true;
      }
      case T_PING: {
        if (streamId != 0) return false;
        send(frame(T_PONG, 0, data, offset, length));
        return true;
      }
      case T_BYE:
        return false;
      case T_DATA:
      case T_CLOSE:
      case T_WINDOW: {
        if (streamId == 0 || !welcomed) return false;
        Stream stream;
        synchronized (lock) {
          stream = streams.get(streamId);
        }
        if (stream == null) {
          return closedIds.contains(streamId); // close race: ignore late frames
        }
        if (type == T_CLOSE) {
          closeStream(stream, false);
        } else if (type == T_WINDOW) {
          if (length != 4) return false;
          int delta = ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
          synchronized (lock) {
            stream.sendCredit += delta;
            lock.notifyAll();
          }
        } else {
          if (length == 0) return false;
          writeDownlink(stream, data, offset, length);
        }
        return true;
      }
      default:
        return false;
    }
  }

  /** Writes relay bytes to TDLib's local socket, then returns the credit. */
  private void writeDownlink (Stream stream, byte[] data, int offset, int length) {
    byte[] copy = new byte[length];
    System.arraycopy(data, offset, copy, 0, length);
    downlinkExecutor(stream).execute(() -> {
      try {
        OutputStream out = stream.socket.getOutputStream();
        out.write(copy);
        out.flush();
      } catch (IOException e) {
        closeStream(stream, true);
        return;
      }
      main.post(() -> {
        synchronized (lock) {
          if (stream.closed) return;
          stream.pendingWindow += copy.length;
        }
        flushWindow(stream, false);
      });
    });
  }

  private final Map<Integer, java.util.concurrent.ExecutorService> downlinkExecutors = new HashMap<>();

  private java.util.concurrent.ExecutorService downlinkExecutor (Stream stream) {
    synchronized (downlinkExecutors) {
      java.util.concurrent.ExecutorService executor = downlinkExecutors.get(stream.id);
      if (executor == null) {
        executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        downlinkExecutors.put(stream.id, executor);
      }
      return executor;
    }
  }

  /** Main thread. WINDOW grants coalesce up to 256 KiB or 20 ms. */
  private void flushWindow (Stream stream, boolean force) {
    int delta;
    synchronized (lock) {
      if (stream.closed || stream.pendingWindow == 0) return;
      if (!force && stream.pendingWindow < WINDOW_BATCH) {
        main.postDelayed(() -> flushWindow(stream, true), 20);
        return;
      }
      delta = stream.pendingWindow;
      stream.pendingWindow = 0;
    }
    send(frame(T_WINDOW, stream.id, windowPayload(delta), 0, 4));
  }

  // Hidden WebView (main thread), hardened as described in ANDROID.md

  @SuppressLint({"SetJavaScriptEnabled", "RequiresFeature"})
  private void createWebView () {
    if (!active) return;
    final long gen = ++generation;
    welcomed = false;
    reply = null;
    byte[] nonceBytes = new byte[32];
    new SecureRandom().nextBytes(nonceBytes);
    nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    final String origin = "https://" + host;
    final String bridgeUrl = bridgeUrl(host, path, secret);

    WebView view;
    try {
      view = new WebView(context);
    } catch (Throwable t) {
      setState(STATE_UNSUPPORTED);
      return;
    }
    webView = view;
    WebSettings settings = view.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(false);
    settings.setDatabaseEnabled(false);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setLoadsImagesAutomatically(false);
    settings.setBlockNetworkImage(true);
    settings.setGeolocationEnabled(false);
    settings.setSupportMultipleWindows(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setMediaPlaybackRequiresUserGesture(true);
    settings.setSaveFormData(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

    Set<String> rules = Collections.singleton(origin);
    WebViewCompat.addDocumentStartJavaScript(view, hardeningScript(host), rules);
    WebViewCompat.addWebMessageListener(view, "TelegramWebProxy", rules, (webView, message, sourceOrigin, isMainFrame, replyProxy) -> {
      if (gen != generation || !isMainFrame || !origin.equals(sourceOrigin.toString())) return;
      if (message.getType() == WebMessageCompat.TYPE_ARRAY_BUFFER) {
        if (reply == null) return;
        onRelayMessage(message.getArrayBuffer());
      } else {
        onControlMessage(message.getData(), replyProxy);
      }
    });
    view.setWebChromeClient(new WebChromeClient());
    view.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
        return true; // only the one bridge navigation made by loadUrl
      }

      @Override
      public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        boolean allowed = "https".equals(uri.getScheme()) && host.equalsIgnoreCase(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443);
        if (!allowed && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
          return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        }
        return null;
      }

      @Override
      public boolean onRenderProcessGone (WebView view, android.webkit.RenderProcessGoneDetail detail) {
        if (view == webView) {
          webView = null;
          failCarrier();
        }
        return true;
      }
    });
    view.loadUrl(bridgeUrl + "#android=" + nonce);
    main.postDelayed(() -> {
      if (gen == generation && active && !welcomed) {
        failCarrier(); // no WELCOME in time
      }
    }, HANDSHAKE_TIMEOUT_MS);
  }

  private void onControlMessage (@Nullable String data, JavaScriptReplyProxy replyProxy) {
    if (data == null) return;
    try {
      JSONObject json = new JSONObject(data);
      String t = json.optString("t");
      if ("tproxy-android-init".equals(t) && json.optInt("v") == 1 && nonce.equals(json.optString("nonce")) && reply == null) {
        reply = replyProxy;
        send(frame(T_HELLO, 0, new byte[] {1}, 0, 1));
      } else if ("close".equals(t) || "error".equals(t)) {
        failCarrier();
      }
      // "status" and other informational controls are ignored
    } catch (Throwable ignored) { }
  }

  private void failCarrier () {
    if (!active) return;
    closeAllStreams(); // TDLib reconnects through a fresh carrier
    destroyWebView();
    setState(STATE_CONNECTING);
    int delay = backoffMs;
    backoffMs = Math.min(backoffMs * 2, 30_000);
    main.postDelayed(this::createWebView, delay);
  }

  private void destroyWebView () {
    generation++;
    welcomed = false;
    reply = null;
    WebView view = webView;
    webView = null;
    if (view != null) {
      try {
        view.stopLoading();
        view.loadUrl("about:blank");
        view.clearHistory();
        view.destroy();
      } catch (Throwable ignored) { }
    }
    synchronized (downlinkExecutors) {
      for (java.util.concurrent.ExecutorService executor : downlinkExecutors.values()) {
        executor.shutdownNow();
      }
      downlinkExecutors.clear();
    }
  }

  /** Document-start script: a second, strict CSP and inert shims for unused browser APIs. */
  private static String hardeningScript (String host) {
    String csp = "default-src 'none'; base-uri 'none'; child-src 'none'; connect-src https://" + host + " wss://" + host + "; " +
      "font-src 'none'; form-action 'none'; frame-src 'none'; img-src 'none'; manifest-src 'none'; media-src 'none'; " +
      "object-src 'none'; script-src 'unsafe-inline'; style-src 'none'; worker-src 'none'";
    return "(function(){try{" +
      "var h=document.documentElement||document.appendChild(document.createElement('html'));" +
      "var m=document.createElement('meta');m.httpEquiv='Content-Security-Policy';m.content=" + JSONObject.quote(csp) + ";h.appendChild(m);" +
      "var d=document.createElement('meta');d.httpEquiv='x-dns-prefetch-control';d.content='off';h.appendChild(d);" +
      "var off=['localStorage','sessionStorage','indexedDB','caches','Worker','SharedWorker','BroadcastChannel','AudioContext','webkitAudioContext','RTCPeerConnection','webkitRTCPeerConnection','WebTransport','WebAssembly','Notification','open'];" +
      "off.forEach(function(k){try{Object.defineProperty(window,k,{value:undefined,writable:false,configurable:false});}catch(e){}});" +
      "['print','alert','confirm','prompt'].forEach(function(k){try{Object.defineProperty(window,k,{value:function(){},writable:false,configurable:false});}catch(e){}});" +
      "try{Object.defineProperty(document,'cookie',{get:function(){return ''},set:function(){},configurable:false});}catch(e){}" +
      "['clipboard','mediaDevices','geolocation','credentials','serviceWorker','share','wakeLock','permissions','sendBeacon'].forEach(function(k){try{Object.defineProperty(navigator,k,{value:undefined,configurable:false});}catch(e){}});" +
      "}catch(e){}})();";
  }
}
