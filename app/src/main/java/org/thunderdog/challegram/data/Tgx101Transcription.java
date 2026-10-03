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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.core.reference.ReferenceList;

/**
 * TGx101: voice and video message transcription that runs in the background, like in the official apps.
 * The result is shown in the message bubble (TGMessageFile / TGMessageVideo), so the chat stays usable.
 *
 * First asks Telegram (RecognizeSpeech: Premium, weekly trial for everyone else, boosted groups). If the
 * server refuses or fails, the message is recognized on the phone with Android's SpeechRecognizer
 * (Android 13+), one message at a time. Finished texts are kept on the device.
 */
public final class Tgx101Transcription {
  public static final int STATE_NONE = 0, STATE_PENDING = 1, STATE_DONE = 2, STATE_ERROR = 3;

  public static final class Result {
    public final int state;
    public final String text; // partial text while pending, the transcription, or the error message
    public final boolean collapsed;

    Result (int state, String text, boolean collapsed) {
      this.state = state;
      this.text = text != null ? text : "";
      this.collapsed = collapsed;
    }
  }

  public interface Listener {
    void onTranscriptionChanged (long chatId, long messageId);
  }

  private static final int RECOGNIZER_SAMPLE_RATE = 16000;
  private static final long SERVER_POLL_INTERVAL_MS = 1000;
  private static final int SERVER_POLL_MAX_ATTEMPTS = 90;
  private static final int MAX_STORED = 400;
  private static final String PREFS = "tgx101_transcriptions";

  private static final Map<String, Result> results = new ConcurrentHashMap<>();
  private static final ReferenceList<Listener> listeners = new ReferenceList<>(true);
  private static final ArrayDeque<Runnable> deviceQueue = new ArrayDeque<>();
  private static boolean deviceBusy;

  private Tgx101Transcription () { }

  public static boolean canTranscribe (@Nullable TdApi.MessageContent content) {
    return content instanceof TdApi.MessageVoiceNote || content instanceof TdApi.MessageVideoNote;
  }

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  private static String key (Tdlib tdlib, long chatId, long messageId) {
    return tdlib.id() + "_" + chatId + "_" + messageId;
  }

  private static SharedPreferences prefs () {
    return UI.getAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /** Current state for a message, or null if it was never transcribed. */
  @Nullable
  public static Result get (Tdlib tdlib, TdApi.Message message) {
    String key = key(tdlib, message.chatId, message.id);
    Result result = results.get(key);
    if (result != null) {
      return result;
    }
    TdApi.SpeechRecognitionResult server = serverResult(message.content);
    if (server instanceof TdApi.SpeechRecognitionResultText) {
      result = new Result(STATE_DONE, ((TdApi.SpeechRecognitionResultText) server).text, false);
      results.put(key, result);
      return result;
    }
    String stored = prefs().getString(key, null);
    if (stored != null) {
      result = new Result(STATE_DONE, stored, false);
      results.put(key, result);
      return result;
    }
    return null;
  }

  /** The transcription as an italic caption to show in the bubble, or null. */
  @Nullable
  public static TdApi.FormattedText captionFor (Tdlib tdlib, TdApi.Message message) {
    Result result = get(tdlib, message);
    if (result == null || result.collapsed) {
      return null;
    }
    String text;
    switch (result.state) {
      case STATE_PENDING:
        text = result.text.isEmpty() ? Lang.getString(R.string.TranscriptionInProgress) : result.text + "…";
        break;
      case STATE_DONE:
        text = result.text.isEmpty() ? Lang.getString(R.string.TranscriptionEmpty) : result.text;
        break;
      case STATE_ERROR:
        text = result.text;
        break;
      default:
        return null;
    }
    return new TdApi.FormattedText(text, new TdApi.TextEntity[] {new TdApi.TextEntity(0, text.length(), new TdApi.TextEntityTypeItalic())});
  }

  /** Finished transcription text, or null. */
  @Nullable
  public static String doneText (Tdlib tdlib, TdApi.Message message) {
    Result result = get(tdlib, message);
    return result != null && result.state == STATE_DONE && !result.text.isEmpty() ? result.text : null;
  }

  /** The "A" button: starts transcription, or hides / shows the finished text. */
  public static void toggle (Tdlib tdlib, TdApi.Message message, boolean isSecretChat) {
    String key = key(tdlib, message.chatId, message.id);
    Result result = get(tdlib, message);
    if (result == null || result.state == STATE_ERROR) {
      start(tdlib, message, isSecretChat);
    } else if (result.state == STATE_DONE) {
      set(key, message.chatId, message.id, new Result(STATE_DONE, result.text, !result.collapsed));
    }
  }

  public static void start (Tdlib tdlib, TdApi.Message message, boolean isSecretChat) {
    String key = key(tdlib, message.chatId, message.id);
    Result existing = get(tdlib, message);
    if (existing != null && existing.state != STATE_ERROR) {
      if (existing.collapsed) {
        set(key, message.chatId, message.id, new Result(existing.state, existing.text, false));
      }
      return;
    }
    TdApi.File file = fileOf(message.content);
    if (file == null) {
      return;
    }
    Job job = new Job(tdlib, key, message.chatId, message.id, file);
    job.progress(null);
    if (isSecretChat) {
      job.recognizeOnDevice(); // secret chats can't use server recognition
    } else {
      job.recognizeOnServer();
    }
  }

  private static void set (String key, long chatId, long messageId, Result result) {
    results.put(key, result);
    if (result.state == STATE_DONE && !result.text.isEmpty()) {
      SharedPreferences prefs = prefs();
      if (!result.text.equals(prefs.getString(key, null))) {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        if (all.size() >= MAX_STORED) { // keep the storage small: drop some old entries
          int drop = all.size() - MAX_STORED + 50;
          for (String old : all.keySet()) {
            if (drop-- <= 0) break;
            editor.remove(old);
          }
        }
        editor.putString(key, result.text).apply();
      }
    }
    UI.post(() -> {
      for (Listener listener : listeners) {
        listener.onTranscriptionChanged(chatId, messageId);
      }
    });
  }

  @Nullable
  private static TdApi.SpeechRecognitionResult serverResult (TdApi.MessageContent content) {
    if (content instanceof TdApi.MessageVoiceNote) {
      return ((TdApi.MessageVoiceNote) content).voiceNote.speechRecognitionResult;
    } else if (content instanceof TdApi.MessageVideoNote) {
      return ((TdApi.MessageVideoNote) content).videoNote.speechRecognitionResult;
    }
    return null;
  }

  @Nullable
  private static TdApi.File fileOf (TdApi.MessageContent content) {
    if (content instanceof TdApi.MessageVoiceNote) {
      return ((TdApi.MessageVoiceNote) content).voiceNote.voice;
    } else if (content instanceof TdApi.MessageVideoNote) {
      return ((TdApi.MessageVideoNote) content).videoNote.video;
    }
    return null;
  }

  // On-device recognition runs one message at a time

  private static void enqueueDevice (Runnable task) {
    UI.post(() -> {
      if (deviceBusy) {
        deviceQueue.add(task);
      } else {
        deviceBusy = true;
        task.run();
      }
    });
  }

  private static void deviceDone () {
    UI.post(() -> {
      Runnable next = deviceQueue.poll();
      if (next != null) {
        next.run();
      } else {
        deviceBusy = false;
      }
    });
  }

  private static final class Job {
    private final Tdlib tdlib;
    private final String key;
    private final long chatId, messageId;
    private final TdApi.File file;

    Job (Tdlib tdlib, String key, long chatId, long messageId, TdApi.File file) {
      this.tdlib = tdlib;
      this.key = key;
      this.chatId = chatId;
      this.messageId = messageId;
      this.file = file;
    }

    void progress (@Nullable String partialText) {
      set(key, chatId, messageId, new Result(STATE_PENDING, partialText, false));
    }

    void finish (String text) {
      set(key, chatId, messageId, new Result(STATE_DONE, text, false));
    }

    void fail (String error) {
      set(key, chatId, messageId, new Result(STATE_ERROR, error, false));
    }

    // Server

    void recognizeOnServer () {
      if (org.thunderdog.challegram.unsorted.Settings.instance().tgx101FakeNoPremium()) {
        // «As without Premium» test switch: the phone's own recognizer, as for an account whose weekly trial is used up
        org.thunderdog.challegram.Tgx101Diag.mark("transcription: server skipped (as without Premium)");
        recognizeOnDevice();
        return;
      }
      tdlib.client().send(new TdApi.RecognizeSpeech(chatId, messageId), result -> {
        if (result instanceof TdApi.Error) {
          Log.i("Server speech recognition refused: %s", TD.toErrorString(result));
          org.thunderdog.challegram.Tgx101Diag.mark("transcription: server refused: " + TD.toErrorString(result));
          recognizeOnDevice();
        } else {
          pollServerResult(0);
        }
      });
    }

    void pollServerResult (int attempt) {
      if (attempt >= SERVER_POLL_MAX_ATTEMPTS) {
        recognizeOnDevice();
        return;
      }
      tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
        TdApi.SpeechRecognitionResult recognition = result instanceof TdApi.Message ? serverResult(((TdApi.Message) result).content) : null;
        if (recognition instanceof TdApi.SpeechRecognitionResultText) {
          finish(((TdApi.SpeechRecognitionResultText) recognition).text);
        } else if (recognition instanceof TdApi.SpeechRecognitionResultError) {
          Log.i("Server speech recognition failed: %s", TD.toErrorString(((TdApi.SpeechRecognitionResultError) recognition).error));
          org.thunderdog.challegram.Tgx101Diag.mark("transcription: server failed: " + TD.toErrorString(((TdApi.SpeechRecognitionResultError) recognition).error));
          recognizeOnDevice();
        } else {
          if (recognition instanceof TdApi.SpeechRecognitionResultPending) {
            progress(((TdApi.SpeechRecognitionResultPending) recognition).partialText);
          }
          UI.post(() -> pollServerResult(attempt + 1), SERVER_POLL_INTERVAL_MS);
        }
      });
    }

    // On device

    void recognizeOnDevice () {
      if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) {
        Context c = UI.getAppContext();
        boolean available = SpeechRecognizer.isRecognitionAvailable(c);
        boolean onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(c);
        android.content.ComponentName service = null;
        try {
          String s = android.provider.Settings.Secure.getString(c.getContentResolver(), "voice_recognition_service");
          service = s != null ? android.content.ComponentName.unflattenFromString(s) : null;
        } catch (Throwable ignored) { }
        org.thunderdog.challegram.Tgx101Diag.mark("transcription: on device, sdk " + Build.VERSION.SDK_INT + ", recognizer " + available + ", on-device " + onDevice + ", service " + (service != null ? service.getPackageName() : "none") + ", language " + Locale.getDefault().toLanguageTag());
      }
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isRecognitionAvailable(UI.getAppContext())) {
        fail(Lang.getString(R.string.TranscriptionUnavailable));
        return;
      }
      enqueueDevice(() -> tdlib.client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), result -> {
        if (!(result instanceof TdApi.File) || !TD.isFileLoaded((TdApi.File) result)) {
          fail(Lang.getString(R.string.TranscriptionFailed));
          deviceDone();
          return;
        }
        String path = ((TdApi.File) result).local.path;
        new Thread(() -> {
          byte[] pcm;
          try {
            pcm = decodeToPcm16kMono(path);
          } catch (Throwable t) {
            Log.e("Unable to decode audio for transcription", t);
            fail(Lang.getString(R.string.TranscriptionFailed));
            deviceDone();
            return;
          }
          UI.post(() -> startRecognizer(pcm));
        }, "VoiceTranscription").start();
      }));
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void startRecognizer (byte[] pcm) {
      Context context = UI.getAppContext();
      final SpeechRecognizer recognizer = SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ?
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context) :
        SpeechRecognizer.createSpeechRecognizer(context);

      final ParcelFileDescriptor[] pipe;
      try {
        pipe = ParcelFileDescriptor.createPipe();
      } catch (IOException e) {
        recognizer.destroy();
        fail(Lang.getString(R.string.TranscriptionFailed));
        deviceDone();
        return;
      }

      final StringBuilder text = new StringBuilder();
      recognizer.setRecognitionListener(new RecognitionListener() {
        private boolean ended;

        private void appendResults (Bundle results) {
          java.util.ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
          if (matches != null && !matches.isEmpty() && !matches.get(0).isEmpty()) {
            if (text.length() > 0) {
              text.append(' ');
            }
            text.append(matches.get(0));
            progress(text.toString());
          }
        }

        private void end (@Nullable String error) {
          if (ended) return;
          ended = true;
          recognizer.destroy();
          closeQuietly(pipe[0]);
          if (error == null) {
            finish(text.toString());
          } else {
            fail(error);
          }
          deviceDone();
        }

        @Override public void onSegmentResults (Bundle segmentResults) { appendResults(segmentResults); }
        @Override public void onEndOfSegmentedSession () { end(null); }
        @Override public void onResults (Bundle results) { appendResults(results); org.thunderdog.challegram.Tgx101Diag.mark("transcription: on-device done, " + text.length() + " chars"); end(null); }
        @Override public void onError (int error) {
          Log.w("On-device speech recognition error: %d", error);
          org.thunderdog.challegram.Tgx101Diag.mark("transcription: on-device error " + error + (text.length() > 0 ? " after some text" : ""));
          if (text.length() > 0) {
            end(null);
          } else {
            end(Lang.getString(error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ?
              R.string.TranscriptionLanguageUnavailable : R.string.TranscriptionFailed));
          }
        }
        @Override public void onReadyForSpeech (Bundle params) { }
        @Override public void onBeginningOfSpeech () { }
        @Override public void onRmsChanged (float rmsdB) { }
        @Override public void onBufferReceived (byte[] buffer) { }
        @Override public void onEndOfSpeech () { }
        @Override public void onPartialResults (Bundle partialResults) { }
        @Override public void onEvent (int eventType, Bundle params) { }
      });

      Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0]);
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RECOGNIZER_SAMPLE_RATE);
      intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
      // The session ends when the audio source is closed, with results per segment.
      intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
      // Google's speech service answers ERROR_NETWORK for audio fed from another app in its online mode
      // (log 2026-10-03), so the language's downloaded model is used when there is one, or its download is started
      final String language = Locale.getDefault().toLanguageTag();
      final Runnable go = () -> {
        recognizer.startListening(intent);
        new Thread(() -> {
          try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
            out.write(pcm);
          } catch (IOException e) {
            Log.w("Unable to feed audio to the recognizer", e);
          }
        }, "VoiceTranscriptionFeed").start();
      };
      try {
        recognizer.checkRecognitionSupport(intent, UI::post, new android.speech.RecognitionSupportCallback() {
          @Override
          public void onSupportResult (@NonNull android.speech.RecognitionSupport support) {
            boolean installed = hasLanguage(support.getInstalledOnDeviceLanguages(), language);
            boolean downloadable = hasLanguage(support.getSupportedOnDeviceLanguages(), language) || hasLanguage(support.getPendingOnDeviceLanguages(), language);
            org.thunderdog.challegram.Tgx101Diag.mark("transcription: support " + language + " installed " + installed + ", downloadable " + downloadable +
              ", online " + hasLanguage(support.getOnlineLanguages(), language) + " (installed " + support.getInstalledOnDeviceLanguages() + ", pending " + support.getPendingOnDeviceLanguages() + ")");
            if (installed) {
              intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
              go.run();
            } else if (downloadable) {
              try {
                recognizer.triggerModelDownload(intent);
                org.thunderdog.challegram.Tgx101Diag.mark("transcription: model download requested for " + language);
              } catch (Throwable t) {
                Log.w("Unable to request the speech model download", t);
              }
              recognizer.destroy();
              closeQuietly(pipe[0]);
              closeQuietly(pipe[1]);
              fail(Lang.getString(R.string.Tgx101TranscriptionModelDownloading));
              deviceDone();
            } else {
              go.run();
            }
          }

          @Override
          public void onError (int error) {
            org.thunderdog.challegram.Tgx101Diag.mark("transcription: support check error " + error);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            go.run();
          }
        });
      } catch (Throwable t) {
        Log.w("Speech recognition support check failed", t);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        go.run();
      }
    }

    private boolean hasLanguage (@Nullable java.util.List<String> languages, String tag) {
      if (languages == null) return false;
      String lang = tag.contains("-") ? tag.substring(0, tag.indexOf('-')) : tag;
      for (String l : languages) {
        if (l.equalsIgnoreCase(tag) || l.equalsIgnoreCase(lang) || l.toLowerCase(Locale.ROOT).startsWith(lang.toLowerCase(Locale.ROOT) + "-")) {
          return true;
        }
      }
      return false;
    }
  }

  private static void closeQuietly (ParcelFileDescriptor fd) {
    try {
      fd.close();
    } catch (IOException ignored) { }
  }

  /** Decodes any audio track the platform can read (Ogg/Opus voice, MP4/AAC video notes) to 16 kHz mono PCM16. */
  private static byte[] decodeToPcm16kMono (String path) throws IOException {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    try {
      extractor.setDataSource(path);
      int trackIndex = -1;
      MediaFormat format = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat trackFormat = extractor.getTrackFormat(i);
        String mime = trackFormat.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          trackIndex = i;
          format = trackFormat;
          break;
        }
      }
      if (format == null) {
        throw new IOException("No audio track");
      }
      extractor.selectTrack(trackIndex);
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();

      int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      Resampler resampler = new Resampler(sampleRate, channels);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      boolean inputDone = false, outputDone = false;
      while (!outputDone) {
        if (!inputDone) {
          int inIndex = codec.dequeueInputBuffer(10_000);
          if (inIndex >= 0) {
            ByteBuffer input = codec.getInputBuffer(inIndex);
            int size = extractor.readSampleData(input, 0);
            if (size < 0) {
              codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
              extractor.advance();
            }
          }
        }
        int outIndex = codec.dequeueOutputBuffer(info, 10_000);
        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat outFormat = codec.getOutputFormat();
          resampler = new Resampler(outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        } else if (outIndex >= 0) {
          if (info.size > 0) {
            ByteBuffer output = codec.getOutputBuffer(outIndex);
            output.position(info.offset);
            output.limit(info.offset + info.size);
            resampler.process(output.slice().order(ByteOrder.nativeOrder()), out);
          }
          codec.releaseOutputBuffer(outIndex, false);
          if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            outputDone = true;
          }
        }
      }
      return out.toByteArray();
    } finally {
      if (codec != null) {
        try { codec.stop(); } catch (Throwable ignored) { }
        codec.release();
      }
      extractor.release();
    }
  }

  /** Downmixes interleaved PCM16 to mono and resamples it to 16 kHz (linear interpolation). */
  private static class Resampler {
    private final int channels;
    private final double step;
    private double position;
    private short previous;

    Resampler (int sampleRate, int channels) {
      this.channels = Math.max(1, channels);
      this.step = (double) sampleRate / RECOGNIZER_SAMPLE_RATE;
    }

    void process (ByteBuffer pcm, ByteArrayOutputStream out) {
      int frames = pcm.remaining() / 2 / channels;
      for (int frame = 0; frame < frames; frame++) {
        int sum = 0;
        for (int c = 0; c < channels; c++) {
          sum += pcm.getShort();
        }
        short current = (short) (sum / channels);
        // Emit every output sample that falls between the previous and the current input frame.
        while (position <= 1.0) {
          int value = (int) Math.round(previous + (current - previous) * position);
          out.write(value & 0xff);
          out.write((value >> 8) & 0xff);
          position += step;
        }
        position -= 1.0;
        previous = current;
      }
    }
  }
}
