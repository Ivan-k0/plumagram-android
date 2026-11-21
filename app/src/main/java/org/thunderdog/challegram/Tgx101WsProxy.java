package org.thunderdog.challegram;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * TGx101 (4PDA request, user 2026-10-06 «делай вебсокет»): Telegram through WebSocket, the way the web version connects
 * (like tg-ws-proxy). A tiny SOCKS5 server on 127.0.0.1 takes TDLib's connections; their MTProto bytes (already
 * obfuscated by TDLib) go unchanged in binary frames to wss://kwsN.web.telegram.org/apiws, N being the data center
 * written in the connection's first 64 bytes.
 */
public final class Tgx101WsProxy {
  public static final String HOST = "127.0.0.1";
  public static final int PORT = 18053;

  private static ServerSocket server;
  private static ExecutorService pool;
  private static OkHttpClient client;

  private Tgx101WsProxy () { }

  // TGx101 (user 2026-11-22 «как знать, что он заблокирован»): what the last connections did, shown under the switch on
  // the «Proxy» screen; a toast once per run when the web servers stop taking connections and they go direct instead

  private static volatile long lastWsAt, lastWsFailAt, lastDirectAt, lastDirectFailAt;
  private static volatile String lastFailure;
  private static volatile int wsCount, directCount;
  private static boolean warned;
  private static @androidx.annotation.Nullable Runnable statusListener;

  /** the «Proxy» screen listens while it is open */
  public static void setStatusListener (@androidx.annotation.Nullable Runnable listener) {
    statusListener = listener;
  }

  private static void statusChanged () {
    org.thunderdog.challegram.tool.UI.post(() -> {
      Runnable listener = statusListener;
      if (listener != null) listener.run();
    });
  }

  /** one line for the «Proxy» screen, null when the switch is off */
  public static @androidx.annotation.Nullable String statusText () {
    if (!isRunning()) return null;
    if (lastWsAt == 0 && lastWsFailAt == 0 && lastDirectAt == 0 && lastDirectFailAt == 0) {
      return org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101WsStatusWaiting);
    }
    if (lastWsAt >= lastWsFailAt) {
      return org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101WsStatusOk, wsCount, frontSource());
    }
    String reason = lastFailure != null ? lastFailure : "?";
    if (lastDirectAt >= lastDirectFailAt && lastDirectAt != 0) {
      return org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101WsStatusDirect, reason);
    }
    return org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101WsStatusFailed, reason);
  }

  private static void noteWs (boolean ok, @androidx.annotation.Nullable String failure) {
    if (ok) {
      lastWsAt = System.currentTimeMillis();
      wsCount++;
    } else {
      lastWsFailAt = System.currentTimeMillis();
      lastFailure = failure;
    }
    statusChanged();
  }

  private static void noteDirect (boolean ok) {
    if (ok) {
      lastDirectAt = System.currentTimeMillis();
      directCount++;
    } else {
      lastDirectFailAt = System.currentTimeMillis();
    }
    statusChanged();
    if (!warned && lastWsFailAt > lastWsAt) {
      warned = true;
      org.thunderdog.challegram.tool.UI.showToast(ok ? R.string.Tgx101WsWarnDirect : R.string.Tgx101WsWarnFailed, android.widget.Toast.LENGTH_LONG);
    }
  }

  // TGx101 (user 2026-11-22 «адреса с нашего GitHub»): the web front's address per data center comes from a small file in
  // our repository, read once a day; the built-in one stays when the file can't be read. A changed address then reaches
  // everyone without a new version

  private static final String FRONT_URL = "https://raw.githubusercontent.com/Ivan-k0/plumagram-android/tgx101/config/ws-proxy.json";
  private static final String KEY_FRONT = "tgx101_ws_front", KEY_FRONT_AT = "tgx101_ws_front_at";
  private static final long FRONT_EVERY = 24L * 60 * 60 * 1000;
  private static final java.util.Map<Integer, byte[]> BUILT_IN_FRONT = new java.util.HashMap<>();
  static {
    byte[] ip = {(byte) 149, (byte) 154, (byte) 167, (byte) 220};
    BUILT_IN_FRONT.put(2, ip);
    BUILT_IN_FRONT.put(4, ip);
  }
  private static volatile java.util.Map<Integer, byte[]> front = BUILT_IN_FRONT;
  private static volatile boolean frontFromGitHub;

  private static String frontSource () {
    return org.thunderdog.challegram.core.Lang.getString(frontFromGitHub ? R.string.Tgx101WsFrontGitHub : R.string.Tgx101WsFrontBuiltIn);
  }

  /** {"v":1,"front":{"2":"149.154.167.220","4":"149.154.167.220"}} — data centers the front serves and its address */
  private static @androidx.annotation.Nullable java.util.Map<Integer, byte[]> parseFront (@androidx.annotation.Nullable String json) {
    if (json == null) return null;
    try {
      org.json.JSONObject root = new org.json.JSONObject(json);
      org.json.JSONObject map = root.getJSONObject("front");
      java.util.Map<Integer, byte[]> result = new java.util.HashMap<>();
      java.util.Iterator<String> keys = map.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        int dc = Integer.parseInt(key);
        String[] parts = map.getString(key).split("\\.");
        if (dc < 1 || dc > 5 || parts.length != 4) return null;
        byte[] ip = new byte[4];
        for (int i = 0; i < 4; i++) {
          int b = Integer.parseInt(parts[i]);
          if (b < 0 || b > 255) return null;
          ip[i] = (byte) b;
        }
        result.put(dc, ip);
      }
      return result;
    } catch (Throwable t) {
      return null;
    }
  }

  private static void loadFront () {
    me.vkryl.leveldb.LevelDB pmc = org.thunderdog.challegram.unsorted.Settings.instance().pmc();
    java.util.Map<Integer, byte[]> stored = parseFront(pmc.getString(KEY_FRONT, null));
    if (stored != null) {
      front = stored;
      frontFromGitHub = true;
    }
    if (System.currentTimeMillis() - pmc.getLong(KEY_FRONT_AT, 0) < FRONT_EVERY) return;
    client.newCall(new Request.Builder().url(FRONT_URL).build()).enqueue(new okhttp3.Callback() {
      @Override
      public void onFailure (okhttp3.Call call, IOException e) {
        Tgx101Diag.mark("ws proxy: address list not read: " + e);
      }

      @Override
      public void onResponse (okhttp3.Call call, Response response) {
        try (Response r = response) {
          String body = r.isSuccessful() && r.body() != null ? r.body().string() : null;
          java.util.Map<Integer, byte[]> fresh = parseFront(body);
          if (fresh == null) {
            Tgx101Diag.mark("ws proxy: address list not read: HTTP " + r.code());
            return;
          }
          pmc.putString(KEY_FRONT, body);
          pmc.putLong(KEY_FRONT_AT, System.currentTimeMillis());
          front = fresh;
          frontFromGitHub = true;
          Tgx101Diag.mark("ws proxy: address list from GitHub, DCs " + fresh.keySet());
          statusChanged();
        } catch (Throwable t) {
          Tgx101Diag.mark("ws proxy: address list not read: " + t);
        }
      }
    });
  }

  /** OkHttp needs Android 5+: on Android 4 it throws in its static init (BlackBerry Z30, 0.1.514, 2026-10-07) */
  public static boolean isSupported () {
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
  }

  public static synchronized boolean isRunning () {
    return server != null && !server.isClosed();
  }

  /** The switch on the «Proxy» screen: adds «WebSocket (Telegram Web)» and turns it on, or turns it off */
  public static void setEnabled (boolean enabled) {
    if (!isSupported()) return;
    org.thunderdog.challegram.unsorted.Settings settings = org.thunderdog.challegram.unsorted.Settings.instance();
    settings.setTgx101WsProxy(enabled);
    org.drinkless.tdlib.TdApi.Proxy ws = new org.drinkless.tdlib.TdApi.Proxy(HOST, PORT, new org.drinkless.tdlib.TdApi.ProxyTypeSocks5("", ""));
    if (enabled) {
      start();
      settings.addOrUpdateProxy(ws, org.thunderdog.challegram.core.Lang.getString(R.string.Tgx101WsProxyName), true);
    } else {
      int current = settings.getEffectiveProxyId();
      org.thunderdog.challegram.unsorted.Settings.Proxy p = current != org.thunderdog.challegram.unsorted.Settings.PROXY_ID_NONE ? settings.getProxyConfig(current) : null;
      if (p != null && p.proxy != null && HOST.equals(p.proxy.server) && p.proxy.port == PORT) {
        settings.disableProxy();
      }
      stop();
    }
  }

  /** Never throws: the setting can't be read this early on some starts */
  public static void startIfEnabled () {
    try {
      if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101WsProxy()) start();
    } catch (Throwable t) {
      android.util.Log.w("tgx", "ws proxy start", t);
    }
  }

  public static synchronized void start () {
    if (!isSupported() || isRunning()) return;
    try {
      server = new ServerSocket(PORT, 64, InetAddress.getByName(HOST));
    } catch (IOException e) {
      Tgx101Diag.mark("ws proxy: can't listen on " + PORT + ": " + e);
      server = null;
      return;
    }
    pool = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "tgx-ws-proxy");
      t.setDaemon(true);
      return t;
    });
    if (client == null) {
      client = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        // no WebSocket pings: kwsN closed the connection right after the first one (EOF at 25 s); TDLib keeps it alive itself
        .connectTimeout(8, TimeUnit.SECONDS)
        .dns(WEB_DNS)
        .build();
    }
    lastWsAt = lastWsFailAt = lastDirectAt = lastDirectFailAt = 0;
    wsCount = directCount = 0;
    lastFailure = null;
    warned = false;
    loadFront();
    statusChanged();
    final ServerSocket s = server;
    final ExecutorService p = pool;
    p.execute(() -> {
      while (!s.isClosed()) {
        try {
          Socket socket = s.accept();
          // Any app on the phone can reach a local port: cap the connections (TDLib uses a handful)
          if (CONNECTIONS.incrementAndGet() > MAX_CONNECTIONS) {
            CONNECTIONS.decrementAndGet();
            closeQuietly(socket);
            continue;
          }
          p.execute(() -> {
            try {
              handle(socket);
            } finally {
              CONNECTIONS.decrementAndGet();
            }
          });
        } catch (IOException ignored) {
          break;
        }
      }
    });
    Tgx101Diag.mark("ws proxy: listening on " + HOST + ":" + PORT);
  }

  public static synchronized void stop () {
    try {
      if (server != null) server.close();
    } catch (IOException ignored) { }
    server = null;
    if (pool != null) pool.shutdownNow();
    pool = null;
    statusChanged();
  }

  private static final int MAX_CONNECTIONS = 32;
  private static final java.util.concurrent.atomic.AtomicInteger CONNECTIONS = new java.util.concurrent.atomic.AtomicInteger();

  private static void handle (Socket socket) {
    try {
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(15000); // the SOCKS greeting and TDLib's init come at once — a silent client is dropped
      DataInputStream in = new DataInputStream(socket.getInputStream());
      OutputStream out = socket.getOutputStream();
      // SOCKS5 greeting: no authentication
      if (in.readUnsignedByte() != 5) { socket.close(); return; }
      int methods = in.readUnsignedByte();
      in.readFully(new byte[methods]);
      out.write(new byte[] {5, 0});
      // CONNECT request
      in.readUnsignedByte(); // version
      int cmd = in.readUnsignedByte();
      in.readUnsignedByte(); // reserved
      int atyp = in.readUnsignedByte();
      byte[] address;
      if (atyp == 1) {
        address = new byte[4];
      } else if (atyp == 4) {
        address = new byte[16];
      } else if (atyp == 3) {
        address = new byte[in.readUnsignedByte()];
      } else {
        socket.close();
        return;
      }
      in.readFully(address);
      int port = in.readUnsignedShort();
      if (cmd != 1) {
        out.write(new byte[] {5, 7, 0, 1, 0, 0, 0, 0, 0, 0});
        socket.close();
        return;
      }
      out.write(new byte[] {5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
      out.flush();

      // TDLib's obfuscated init: the data center is in the decrypted bytes 60–61
      byte[] init = new byte[64];
      in.readFully(init);
      socket.setSoTimeout(0); // the bridge itself may stay idle for long (TDLib keeps it alive)
      int dc = dcOf(init);
      if (dc == 0) dc = dcByAddress(atyp, address);
      boolean media = dc < 0;
      dc = Math.abs(dc);
      if (dc == 203) dc = 2; // as tg-ws-proxy: the CDN-like DC 203 is served by kws2
      if (dc < 1 || dc > 5) dc = 2;
      // TGx101 (user 2026-11-21 «github.com/Flowseal/tg-ws-proxy — внедрить»): as tg-ws-proxy does — a media
      // connection tries kwsN-1 first, then kwsN; when no WebSocket opens (a refusal or a redirect), the connection goes
      // straight to the data center TDLib asked for, so a DC the web servers don't take still works
      String[] urls = media
        ? new String[] {"wss://kws" + dc + "-1.web.telegram.org/apiws", "wss://kws" + dc + ".web.telegram.org/apiws"}
        : new String[] {"wss://kws" + dc + ".web.telegram.org/apiws"};
      WebSocketLink link = null;
      for (String url : urls) {
        link = openWs(url, socket, out);
        if (link != null) break;
      }
      if (link != null) {
        bridge(socket, in, link, init);
      } else {
        direct(socket, in, out, init, atyp, address, port, dc, media);
      }
    } catch (Throwable t) {
      try {
        socket.close();
      } catch (IOException ignored) { }
    }
  }

  /** Telegram's web front: the web servers answer at this address for kwsN.web.telegram.org — asked first, so a
   *  blocked or spoofed name lookup doesn't stop the connection (tg-ws-proxy's «2/4 → 149.154.167.220»); the system
   *  lookup comes after it */
  private static final okhttp3.Dns WEB_DNS = hostname -> {
    java.util.List<InetAddress> result = new java.util.ArrayList<>();
    if (hostname.endsWith(".web.telegram.org") && hostname.startsWith("kws") && hostname.length() > 3) {
      int dc = Character.digit(hostname.charAt(3), 10);
      byte[] ip = front.get(dc); // the front serves only the data centers it is listed for (DC 2 and 4 now)
      if (ip != null) result.add(InetAddress.getByAddress(hostname, ip));
    }
    try {
      for (InetAddress address : okhttp3.Dns.SYSTEM.lookup(hostname)) {
        if (!result.contains(address)) result.add(address);
      }
    } catch (java.net.UnknownHostException e) {
      if (result.isEmpty()) throw e;
    }
    return result;
  };

  private static final class WebSocketLink {
    final WebSocket ws;
    final String url;

    WebSocketLink (WebSocket ws, String url) {
      this.ws = ws;
      this.url = url;
    }
  }

  /** Opens the WebSocket, its incoming frames written to TDLib; null when it doesn't open in 8 s */
  private static WebSocketLink openWs (String url, Socket socket, OutputStream out) {
    Request request = new Request.Builder().url(url).header("Sec-WebSocket-Protocol", "binary").build();
    final Object lock = new Object();
    final boolean[] open = {false};
    final boolean[] failed = {false};
    WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
      @Override
      public void onOpen (WebSocket webSocket, Response response) {
        synchronized (lock) {
          open[0] = true;
          lock.notifyAll();
        }
      }

      @Override
      public void onMessage (WebSocket webSocket, ByteString bytes) {
        try {
          synchronized (out) {
            out.write(bytes.toByteArray());
            out.flush();
          }
        } catch (IOException e) {
          webSocket.cancel();
        }
      }

      @Override
      public void onClosing (WebSocket webSocket, int code, String reason) {
        webSocket.close(1000, null);
        closeQuietly(socket);
      }

      @Override
      public void onClosed (WebSocket webSocket, int code, String reason) {
        closeQuietly(socket);
      }

      @Override
      public void onFailure (WebSocket webSocket, Throwable t, Response response) {
        boolean wasOpen;
        synchronized (lock) {
          wasOpen = open[0];
          failed[0] = true;
          lock.notifyAll();
        }
        Tgx101Diag.mark("ws proxy: " + url + (wasOpen ? " broke: " : " didn't open: ") + (response != null ? "HTTP " + response.code() + " " : "") + t);
        if (!wasOpen) noteWs(false, response != null ? "HTTP " + response.code() : t.getClass().getSimpleName());
        if (wasOpen) closeQuietly(socket);
      }
    });
    synchronized (lock) {
      long until = System.currentTimeMillis() + 8000;
      while (!open[0] && !failed[0] && System.currentTimeMillis() < until) {
        try {
          lock.wait(250);
        } catch (InterruptedException e) {
          break;
        }
      }
      if (!open[0]) {
        ws.cancel();
        if (!failed[0]) noteWs(false, "timeout");
        return null;
      }
    }
    noteWs(true, null);
    return new WebSocketLink(ws, url);
  }

  private static void bridge (Socket socket, InputStream in, WebSocketLink link, byte[] init) {
    Tgx101Diag.mark("ws proxy: → " + link.url);
    WebSocket ws = link.ws;
    ws.send(ByteString.of(init));
    // one MTProto packet per frame, as the web version sends them (tg-ws-proxy's MsgSplitter): TDLib's stream is read
    // with its own obfuscation key from the first 64 bytes, only to find where each packet ends
    Splitter splitter = Splitter.of(init);
    byte[] buffer = new byte[64 * 1024];
    try {
      int read;
      while ((read = in.read(buffer)) != -1) {
        if (read <= 0) continue;
        if (splitter != null) {
          boolean ok = true;
          for (byte[] part : splitter.split(buffer, read)) {
            if (!ws.send(ByteString.of(part))) {
              ok = false;
              break;
            }
          }
          if (!ok) break;
        } else if (!ws.send(ByteString.of(buffer, 0, read))) {
          break;
        }
      }
      if (splitter != null) {
        byte[] tail = splitter.flush();
        if (tail != null) ws.send(ByteString.of(tail));
      }
    } catch (IOException ignored) { }
    ws.close(1000, null);
    closeQuietly(socket);
  }

  /** No WebSocket: straight to the address TDLib asked for, the bytes unchanged */
  private static void direct (Socket socket, InputStream in, OutputStream out, byte[] init, int atyp, byte[] address, int port, int dc, boolean media) {
    Socket remote = new Socket();
    try {
      InetAddress target = atyp == 3 ? InetAddress.getByName(new String(address, "US-ASCII")) : InetAddress.getByAddress(address);
      Tgx101Diag.mark("ws proxy: DC" + dc + (media ? " media" : "") + " → direct " + target.getHostAddress() + ":" + port);
      remote.setTcpNoDelay(true);
      remote.connect(new java.net.InetSocketAddress(target, port), 10000);
      OutputStream remoteOut = remote.getOutputStream();
      InputStream remoteIn = remote.getInputStream();
      remoteOut.write(init);
      remoteOut.flush();
      noteDirect(true);
      ExecutorService p = pool;
      if (p == null) throw new IOException("stopped");
      p.execute(() -> {
        byte[] b = new byte[64 * 1024];
        try {
          int r;
          while ((r = remoteIn.read(b)) != -1) {
            out.write(b, 0, r);
            out.flush();
          }
        } catch (IOException ignored) { }
        closeQuietly(socket);
        closeQuietly(remote);
      });
      byte[] b = new byte[64 * 1024];
      int r;
      while ((r = in.read(b)) != -1) {
        remoteOut.write(b, 0, r);
        remoteOut.flush();
      }
    } catch (Throwable t) {
      Tgx101Diag.mark("ws proxy: DC" + dc + " direct failed: " + t);
      noteDirect(false);
    }
    closeQuietly(remote);
    closeQuietly(socket);
  }

  /** Finds MTProto transport packets in TDLib's obfuscated stream (abridged / intermediate / padded intermediate) */
  private static final class Splitter {
    private final Cipher cipher;
    private final boolean abridged;
    private byte[] cipherBuf = new byte[0], plainBuf = new byte[0];
    private boolean disabled;

    private Splitter (Cipher cipher, boolean abridged) {
      this.cipher = cipher;
      this.abridged = abridged;
    }

    static Splitter of (byte[] init) {
      try {
        byte[] key = new byte[32], iv = new byte[16];
        System.arraycopy(init, 8, key, 0, 32);
        System.arraycopy(init, 40, iv, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] plain = cipher.update(init); // the stream goes on from byte 64
        int tag = (plain[56] & 0xff) | (plain[57] & 0xff) << 8 | (plain[58] & 0xff) << 16 | (plain[59] & 0xff) << 24;
        if (tag == 0xefefefef) return new Splitter(cipher, true);
        if (tag == 0xeeeeeeee || tag == 0xdddddddd) return new Splitter(cipher, false);
      } catch (Throwable ignored) { }
      return null;
    }

    java.util.List<byte[]> split (byte[] data, int length) {
      java.util.List<byte[]> parts = new java.util.ArrayList<>();
      byte[] chunk = java.util.Arrays.copyOf(data, length);
      if (disabled) {
        parts.add(chunk);
        return parts;
      }
      byte[] plain = cipher.update(chunk);
      if (plain == null) plain = new byte[0];
      cipherBuf = concat(cipherBuf, chunk);
      plainBuf = concat(plainBuf, plain);
      int offset = 0;
      while (offset < cipherBuf.length) {
        int len = nextLength(offset, cipherBuf.length - offset);
        if (len < 0) break; // not all of it yet
        if (len == 0) { // can't read the length: everything as it is from now on
          parts.add(java.util.Arrays.copyOfRange(cipherBuf, offset, cipherBuf.length));
          offset = cipherBuf.length;
          disabled = true;
          break;
        }
        parts.add(java.util.Arrays.copyOfRange(cipherBuf, offset, offset + len));
        offset += len;
      }
      if (offset > 0) {
        cipherBuf = java.util.Arrays.copyOfRange(cipherBuf, offset, cipherBuf.length);
        plainBuf = java.util.Arrays.copyOfRange(plainBuf, offset, plainBuf.length);
      }
      return parts;
    }

    byte[] flush () {
      if (cipherBuf.length == 0) return null;
      byte[] tail = cipherBuf;
      cipherBuf = plainBuf = new byte[0];
      return tail;
    }

    /** the packet's whole length; -1 when more bytes are needed; 0 when it can't be read */
    private int nextLength (int offset, int available) {
      if (abridged) {
        int first = plainBuf[offset] & 0xff;
        int payload, header;
        if (first == 0x7f || first == 0xff) {
          if (available < 4) return -1;
          payload = ((plainBuf[offset + 1] & 0xff) | (plainBuf[offset + 2] & 0xff) << 8 | (plainBuf[offset + 3] & 0xff) << 16) * 4;
          header = 4;
        } else {
          payload = (first & 0x7f) * 4;
          header = 1;
        }
        if (payload <= 0) return 0;
        return available < header + payload ? -1 : header + payload;
      }
      if (available < 4) return -1;
      int payload = ((plainBuf[offset] & 0xff) | (plainBuf[offset + 1] & 0xff) << 8 | (plainBuf[offset + 2] & 0xff) << 16 | (plainBuf[offset + 3] & 0x7f) << 24);
      if (payload <= 0 || payload > 16 * 1024 * 1024) return 0;
      return available < 4 + payload ? -1 : 4 + payload;
    }

    private static byte[] concat (byte[] a, byte[] b) {
      if (a.length == 0) return b;
      byte[] r = java.util.Arrays.copyOf(a, a.length + b.length);
      System.arraycopy(b, 0, r, a.length, b.length);
      return r;
    }
  }

  /** dc id from TDLib's obfuscated header (AES-256-CTR, key = bytes 8–39, iv = 40–55); negative = media; 0 = unknown */
  private static int dcOf (byte[] init) {
    try {
      byte[] key = new byte[32], iv = new byte[16];
      System.arraycopy(init, 8, key, 0, 32);
      System.arraycopy(init, 40, iv, 0, 16);
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      byte[] plain = cipher.doFinal(init);
      int tag = (plain[56] & 0xff) | (plain[57] & 0xff) << 8 | (plain[58] & 0xff) << 16 | (plain[59] & 0xff) << 24;
      if (tag != 0xefefefef && tag != 0xeeeeeeee && tag != 0xdddddddd) return 0;
      short dc = (short) ((plain[60] & 0xff) | (plain[61] & 0xff) << 8);
      if (Math.abs(dc) > 10000) dc = (short) (dc > 0 ? dc - 10000 : dc + 10000); // test servers
      return dc;
    } catch (Throwable t) {
      return 0;
    }
  }

  /** Telegram's public data center addresses (when the header has no dc) */
  private static int dcByAddress (int atyp, byte[] a) {
    if (atyp != 1) return 0;
    int b1 = a[0] & 0xff, b2 = a[1] & 0xff, b3 = a[2] & 0xff, b4 = a[3] & 0xff;
    if (b1 == 149 && b2 == 154) {
      if (b3 == 175) return b4 >= 100 ? 3 : 1;
      if (b3 == 167) return b4 >= 91 && b4 < 160 ? 4 : 2;
      if (b3 == 171 || b3 == 172) return 1;
      if (b3 == 162 || b3 == 165 || b3 == 166) return 2;
    }
    if (b1 == 91 && b2 == 108) return b3 == 56 ? 5 : b3 == 4 ? 4 : 2;
    return 0;
  }

  private static void closeQuietly (Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) { }
  }
}
