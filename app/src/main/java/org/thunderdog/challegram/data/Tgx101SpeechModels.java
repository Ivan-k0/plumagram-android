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

import android.os.Build;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Tgx101Diag;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * TGx101: speech recognition models for {@link Tgx101SpeechEngine}, downloaded file by file into the app's files
 * (no archive unpacking) and chosen in MagiX. Models: sherpa-onnx exports (k2-fsa), mirrored on Hugging Face.
 */
public final class Tgx101SpeechModels {
  public static final int KIND_ONLINE_TRANSDUCER = 0; // streaming zipformer
  public static final int KIND_OFFLINE_NEMO_CTC = 1; // GigaAM CTC

  public static final class Model {
    public final String id;
    public final int nameRes;
    public final int kind;
    public final String repository; // huggingface.co/csukuangfj/<repository>
    public final String[] files;
    public final long[] sizes; // bytes, to check the download and show progress

    Model (String id, int nameRes, int kind, String repository, String[] files, long[] sizes) {
      this.id = id;
      this.nameRes = nameRes;
      this.kind = kind;
      this.repository = repository;
      this.files = files;
      this.sizes = sizes;
    }

    public long totalSize () {
      long total = 0;
      for (long size : sizes) total += size;
      return total;
    }
  }

  public static final Model LIGHT = new Model("ru-light", R.string.Tgx101SpeechModelLight, KIND_ONLINE_TRANSDUCER,
    "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16",
    new String[] {"encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt"},
    new long[] {26214060, 2093080, 259417, 6388});

  public static final Model ACCURATE = new Model("ru-gigaam", R.string.Tgx101SpeechModelAccurate, KIND_OFFLINE_NEMO_CTC,
    "sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16",
    new String[] {"model.int8.onnx", "tokens.txt"},
    new long[] {224893661, 2007});

  public static final Model[] ALL = {LIGHT, ACCURATE};

  public interface Listener {
    void onSpeechModelChanged (Model model);
  }

  private static final List<Listener> listeners = new ArrayList<>();
  private static final java.util.Map<String, Integer> progress = new java.util.HashMap<>(); // id → percent while downloading

  public static boolean isSupported () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }

  public static void addListener (Listener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  public static void removeListener (Listener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  private static void notifyChanged (Model model) {
    UI.post(() -> {
      List<Listener> copy;
      synchronized (listeners) {
        copy = new ArrayList<>(listeners);
      }
      for (Listener listener : copy) listener.onSpeechModelChanged(model);
    });
  }

  public static @Nullable Model find (@Nullable String id) {
    for (Model model : ALL) {
      if (model.id.equals(id)) return model;
    }
    return null;
  }

  public static File dir (Model model) {
    return new File(new File(UI.getAppContext().getFilesDir(), "speech"), model.id);
  }

  public static boolean isDownloaded (Model model) {
    File dir = dir(model);
    for (int i = 0; i < model.files.length; i++) {
      File file = new File(dir, model.files[i]);
      if (!file.exists() || file.length() != model.sizes[i]) return false;
    }
    return true;
  }

  /** -1 when not downloading */
  public static int downloadProgress (Model model) {
    synchronized (progress) {
      Integer value = progress.get(model.id);
      return value != null ? value : -1;
    }
  }

  /** The model voice messages are recognized with: the chosen one if it's on the phone */
  public static @Nullable Model active () {
    if (!isSupported()) return null;
    Model model = find(Settings.instance().tgx101SpeechModel());
    return model != null && isDownloaded(model) ? model : null;
  }

  public static void select (Model model) {
    Settings.instance().setTgx101SpeechModel(model.id);
    Tgx101SpeechEngine.release();
    notifyChanged(model);
  }

  public static void delete (Model model) {
    Tgx101SpeechEngine.release();
    File dir = dir(model);
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }
    //noinspection ResultOfMethodCallIgnored
    dir.delete();
    if (model.id.equals(Settings.instance().tgx101SpeechModel())) {
      Settings.instance().setTgx101SpeechModel(null);
    }
    notifyChanged(model);
  }

  /** Downloads the model's files (resuming none: a broken file is fetched again); selects it when done */
  public static void download (Model model) {
    synchronized (progress) {
      if (progress.containsKey(model.id)) return;
      progress.put(model.id, 0);
    }
    notifyChanged(model);
    new Thread(() -> {
      boolean ok = false;
      try {
        File dir = dir(model);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs");
        long total = model.totalSize(), done = 0;
        for (int i = 0; i < model.files.length; i++) {
          File file = new File(dir, model.files[i]);
          if (file.exists() && file.length() == model.sizes[i]) {
            done += model.sizes[i];
            continue;
          }
          File part = new File(dir, model.files[i] + ".part");
          URL url = new URL("https://huggingface.co/csukuangfj/" + model.repository + "/resolve/main/" + model.files[i]);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setConnectTimeout(20000);
          connection.setReadTimeout(30000);
          connection.setInstanceFollowRedirects(true);
          try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            int read, lastPercent = -1;
            while ((read = in.read(buffer)) != -1) {
              out.write(buffer, 0, read);
              done += read;
              int percent = (int) Math.min(99, done * 100 / total);
              if (percent != lastPercent) {
                lastPercent = percent;
                synchronized (progress) {
                  progress.put(model.id, percent);
                }
                notifyChanged(model);
              }
            }
          } finally {
            connection.disconnect();
          }
          if (part.length() != model.sizes[i] || !part.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("size mismatch: " + model.files[i]);
          }
        }
        ok = true;
      } catch (Throwable t) {
        Log.w("Speech model download failed", t);
        Tgx101Diag.mark("speech model " + model.id + ": download failed " + t.getClass().getSimpleName());
      }
      synchronized (progress) {
        progress.remove(model.id);
      }
      if (ok) {
        Tgx101Diag.mark("speech model " + model.id + ": downloaded");
        UI.post(() -> select(model));
      } else {
        notifyChanged(model);
        UI.showToast(R.string.Tgx101SpeechModelDownloadFailed, android.widget.Toast.LENGTH_SHORT);
      }
    }, "SpeechModelDownload").start();
  }
}
