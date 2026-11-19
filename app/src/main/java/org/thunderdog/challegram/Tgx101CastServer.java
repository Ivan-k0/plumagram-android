package org.thunderdog.challegram;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDataSource;
import org.thunderdog.challegram.telegram.TdlibFilesManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TGx101 (user 2026-11-20 «через три точки в плеере нужно добавить chromecast»): a tiny HTTP server the TV takes the
 * song or the video from. It listens on the Wi‑Fi address only while casting; each file is reachable by a path with a
 * random key in it, nothing else is served. Ranges are honoured (the TV seeks by them), and a file still downloading
 * is read through TdlibDataSource — the bytes asked for are fetched first, just as our own player streams.
 */
public final class Tgx101CastServer {
  private Tgx101CastServer () { }

  private static final class Entry {
    final int accountId;
    final int fileId;
    final String mime;
    final long size;
    @Nullable final String localPath;

    Entry (int accountId, int fileId, String mime, long size, @Nullable String localPath) {
      this.accountId = accountId;
      this.fileId = fileId;
      this.mime = mime;
      this.size = size;
      this.localPath = localPath;
    }
  }

  private static final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private static final SecureRandom random = new SecureRandom();
  private static ServerSocket server;
  private static String host;

  /** The Wi‑Fi (or other local network) IPv4 address the TV can reach, or null when the phone has none */
  public static @Nullable String localAddress () {
    try {
      String fallback = null;
      for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!nif.isUp() || nif.isLoopback()) continue;
        String name = nif.getName() != null ? nif.getName() : "";
        if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") || name.startsWith("dummy")) continue; // mobile data, VPN
        for (InetAddress address : Collections.list(nif.getInetAddresses())) {
          if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
            if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")) {
              return address.getHostAddress();
            }
            if (fallback == null) fallback = address.getHostAddress();
          }
        }
      }
      return fallback;
    } catch (Throwable t) {
      return null;
    }
  }

  private static synchronized boolean ensureStarted () {
    String address = localAddress();
    if (address == null) {
      return false;
    }
    if (server != null && !server.isClosed() && address.equals(host)) {
      return true;
    }
    stopServer();
    try {
      final ServerSocket socket = new ServerSocket(0, 16, InetAddress.getByName(address));
      server = socket;
      host = address;
      Thread thread = new Thread(() -> acceptLoop(socket), "tgx101-cast-http");
      thread.setDaemon(true);
      thread.start();
      Tgx101Diag.mark("cast: server on " + address + ":" + socket.getLocalPort());
      return true;
    } catch (Throwable t) {
      Tgx101Diag.mark("cast: server failed " + t);
      server = null;
      return false;
    }
  }

  private static synchronized void stopServer () {
    if (server != null) {
      try {
        server.close();
      } catch (IOException ignored) { }
      server = null;
    }
  }

  /** Stops serving: called when casting ends */
  public static synchronized void stop () {
    entries.clear();
    stopServer();
  }

  /** A URL the TV can fetch {@code file} by, or null with no local network */
  public static @Nullable String urlFor (Tdlib tdlib, TdApi.File file, String mime) {
    if (file == null || !ensureStarted()) {
      return null;
    }
    byte[] key = new byte[12];
    random.nextBytes(key);
    StringBuilder b = new StringBuilder();
    for (byte k : key) b.append(String.format(Locale.US, "%02x", k));
    String token = b.toString();
    long size = file.size != 0 ? file.size : file.expectedSize;
    String localPath = TD.isFileLoaded(file) ? file.local.path : null;
    entries.put(token, new Entry(tdlib.id(), file.id, mime, size, localPath));
    ServerSocket socket = server;
    return "http://" + host + ":" + (socket != null ? socket.getLocalPort() : 0) + "/" + token + "/" + file.id;
  }

  private static void acceptLoop (ServerSocket socket) {
    while (!socket.isClosed()) {
      try {
        final Socket client = socket.accept();
        Thread t = new Thread(() -> handle(client), "tgx101-cast-conn");
        t.setDaemon(true);
        t.start();
      } catch (IOException e) {
        break;
      }
    }
  }

  private static void handle (Socket client) {
    try (Socket s = client) {
      s.setSoTimeout(30_000);
      BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
      String requestLine = in.readLine();
      if (requestLine == null) return;
      String rangeHeader = null;
      String line;
      while ((line = in.readLine()) != null && !line.isEmpty()) {
        int colon = line.indexOf(':');
        if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Range")) {
          rangeHeader = line.substring(colon + 1).trim();
        }
      }
      String[] parts = requestLine.split(" ");
      OutputStream out = s.getOutputStream();
      if (parts.length < 2) {
        respond(out, "400 Bad Request");
        return;
      }
      String method = parts[0];
      String path = parts[1];
      if (method.equals("OPTIONS")) {
        out.write(("HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
          "Access-Control-Allow-Headers: Range, Content-Type\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        return;
      }
      boolean head = method.equals("HEAD");
      if (!head && !method.equals("GET")) {
        respond(out, "405 Method Not Allowed");
        return;
      }
      String[] segments = path.split("/");
      Entry entry = segments.length >= 2 ? entries.get(segments[1]) : null;
      if (entry == null) {
        respond(out, "404 Not Found");
        return;
      }
      long size = entry.size;
      long start = 0, end = size > 0 ? size - 1 : -1;
      boolean partial = false;
      if (rangeHeader != null && rangeHeader.startsWith("bytes=") && size > 0) {
        String spec = rangeHeader.substring(6);
        int comma = spec.indexOf(',');
        if (comma >= 0) spec = spec.substring(0, comma);
        int dash = spec.indexOf('-');
        try {
          if (dash == 0) {
            long suffix = Long.parseLong(spec.substring(1).trim());
            start = Math.max(0, size - suffix);
          } else if (dash > 0) {
            start = Long.parseLong(spec.substring(0, dash).trim());
            String e = spec.substring(dash + 1).trim();
            if (!e.isEmpty()) end = Math.min(size - 1, Long.parseLong(e));
          }
          partial = true;
        } catch (NumberFormatException ignored) { }
        if (start >= size || start > end) {
          out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + size + "\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
          return;
        }
      }
      long length = end >= start ? end - start + 1 : -1;
      StringBuilder h = new StringBuilder();
      h.append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
      h.append("Content-Type: ").append(entry.mime).append("\r\n");
      h.append("Accept-Ranges: bytes\r\n");
      h.append("Access-Control-Allow-Origin: *\r\n");
      h.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n");
      if (length >= 0) h.append("Content-Length: ").append(length).append("\r\n");
      if (partial) h.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(size).append("\r\n");
      h.append("Connection: close\r\n\r\n");
      out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
      if (head) {
        out.flush();
        return;
      }
      if (BuildConfig.TGX101_DIAG) {
        Tgx101Diag.mark("cast: TV reads file " + entry.fileId + " from " + start + (length >= 0 ? " +" + length : "") + " of " + size);
      }
      sendBody(entry, start, length, out);
      out.flush();
    } catch (Throwable t) {
      // the TV closes connections when it seeks — nothing to report
    }
  }

  private static void respond (OutputStream out, String status) throws IOException {
    out.write(("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
  }

  private static void sendBody (Entry entry, long start, long length, OutputStream out) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    if (entry.localPath != null) {
      try (RandomAccessFile file = new RandomAccessFile(entry.localPath, "r")) {
        file.seek(start);
        long left = length >= 0 ? length : Long.MAX_VALUE;
        while (left > 0) {
          int read = file.read(buffer, 0, (int) Math.min(buffer.length, left));
          if (read <= 0) break;
          out.write(buffer, 0, read);
          left -= read;
        }
        return;
      } catch (IOException e) {
        // the file was moved or removed — read it through TDLib below
      }
    }
    TdlibDataSource source = new TdlibDataSource(entry.accountId, TdlibFilesManager.PRIORITY_STREAMING_DEFAULT, null);
    Uri uri = TdlibDataSource.UriFactory.create(entry.accountId, entry.fileId);
    try {
      source.open(new DataSpec.Builder().setUri(uri).setPosition(start).setLength(length >= 0 ? length : C.LENGTH_UNSET).build());
      while (true) {
        int read = source.read(buffer, 0, buffer.length);
        if (read == C.RESULT_END_OF_INPUT) break;
        if (read > 0) out.write(buffer, 0, read);
      }
    } finally {
      try {
        source.close();
      } catch (IOException ignored) { }
    }
  }
}
