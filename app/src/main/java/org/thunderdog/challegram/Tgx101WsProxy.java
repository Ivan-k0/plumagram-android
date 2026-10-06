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

  public static synchronized boolean isRunning () {
    return server != null && !server.isClosed();
  }

  /** The switch on the «Proxy» screen: adds «WebSocket (Telegram Web)» and turns it on, or turns it off */
  public static void setEnabled (boolean enabled) {
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
    if (isRunning()) return;
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
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build();
    }
    final ServerSocket s = server;
    final ExecutorService p = pool;
    p.execute(() -> {
      while (!s.isClosed()) {
        try {
          Socket socket = s.accept();
          p.execute(() -> handle(socket));
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
  }

  private static void handle (Socket socket) {
    try {
      socket.setTcpNoDelay(true);
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
      in.readUnsignedShort(); // port
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
      int dc = dcOf(init);
      if (dc == 0) dc = dcByAddress(atyp, address);
      boolean media = dc < 0;
      dc = Math.abs(dc);
      if (dc < 1 || dc > 5) dc = 2;
      String url = "wss://kws" + dc + (media ? "-1" : "") + ".web.telegram.org/apiws";
      bridge(socket, in, out, init, url);
    } catch (Throwable t) {
      try {
        socket.close();
      } catch (IOException ignored) { }
    }
  }

  private static void bridge (Socket socket, InputStream in, OutputStream out, byte[] init, String url) {
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
          out.write(bytes.toByteArray());
          out.flush();
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
        synchronized (lock) {
          failed[0] = true;
          lock.notifyAll();
        }
        Tgx101Diag.mark("ws proxy: " + url + " failed: " + t);
        closeQuietly(socket);
      }
    });
    synchronized (lock) {
      long until = System.currentTimeMillis() + 15000;
      while (!open[0] && !failed[0] && System.currentTimeMillis() < until) {
        try {
          lock.wait(500);
        } catch (InterruptedException e) {
          break;
        }
      }
    }
    if (!open[0]) {
      ws.cancel();
      closeQuietly(socket);
      return;
    }
    ws.send(ByteString.of(init));
    byte[] buffer = new byte[64 * 1024];
    try {
      int read;
      while ((read = in.read(buffer)) != -1) {
        if (read > 0 && !ws.send(ByteString.of(buffer, 0, read))) break;
      }
    } catch (IOException ignored) { }
    ws.close(1000, null);
    closeQuietly(socket);
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
