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
package org.thunderdog.challegram.service;

import android.os.Build;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * TGx101 (4PDA 23.10, Android 4.3 BlackBerry Q20: «проверить обновления — ничего не происходит»): Android 4.1–4.4 can
 * speak TLS 1.2 but keeps it off, and GitHub talks nothing older — the check failed on the handshake. Our own HTTPS
 * connections (update check, APK download) get it turned on there.
 */
public final class Tgx101Tls {
  private Tgx101Tls () { }

  private static SSLSocketFactory factory;

  public static void apply (URLConnection connection) {
    if (Build.VERSION.SDK_INT >= 20 || !(connection instanceof HttpsURLConnection)) return;
    try {
      if (factory == null) {
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, null, null);
        factory = new Modern(context.getSocketFactory());
      }
      ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
    } catch (Throwable ignored) { } // the system's own then
  }

  private static final class Modern extends SSLSocketFactory {
    private final SSLSocketFactory base;

    Modern (SSLSocketFactory base) {
      this.base = base;
    }

    private Socket on (Socket socket) {
      if (socket instanceof SSLSocket) {
        ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});
      }
      return socket;
    }

    @Override public String[] getDefaultCipherSuites () { return base.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites () { return base.getSupportedCipherSuites(); }
    @Override public Socket createSocket (Socket s, String host, int port, boolean autoClose) throws IOException { return on(base.createSocket(s, host, port, autoClose)); }
    @Override public Socket createSocket (String host, int port) throws IOException { return on(base.createSocket(host, port)); }
    @Override public Socket createSocket (String host, int port, InetAddress localHost, int localPort) throws IOException { return on(base.createSocket(host, port, localHost, localPort)); }
    @Override public Socket createSocket (InetAddress host, int port) throws IOException { return on(base.createSocket(host, port)); }
    @Override public Socket createSocket (InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException { return on(base.createSocket(address, port, localAddress, localPort)); }
  }
}
