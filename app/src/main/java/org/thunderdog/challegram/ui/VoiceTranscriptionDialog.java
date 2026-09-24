/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice and video message transcription.
 *
 * First asks Telegram (RecognizeSpeech: Premium, weekly trial for everyone else,
 * boosted groups). If the server refuses or fails, the message is recognized on
 * the phone with Android's SpeechRecognizer (Android 13+, audio fed from the file).
 * Results are kept in memory for the session.
 */
public class VoiceTranscriptionDialog {
  private static final int RECOGNIZER_SAMPLE_RATE = 16000;
  private static final long SERVER_POLL_INTERVAL_MS = 1000;
  private static final int SERVER_POLL_MAX_ATTEMPTS = 90;

  private static final Map<String, String> cache = new ConcurrentHashMap<>();

  public static boolean canTranscribe (TdApi.MessageContent content) {
    return content instanceof TdApi.MessageVoiceNote || content instanceof TdApi.MessageVideoNote;
  }

  public static void show (MessagesController controller, Tdlib tdlib, TdApi.Message message, boolean isSecretChat) {
    TdApi.File file;
    TdApi.SpeechRecognitionResult existing;
    if (message.content instanceof TdApi.MessageVoiceNote) {
      TdApi.VoiceNote voiceNote = ((TdApi.MessageVoiceNote) message.content).voiceNote;
      file = voiceNote.voice;
      existing = voiceNote.speechRecognitionResult;
    } else if (message.content instanceof TdApi.MessageVideoNote) {
      TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) message.content).videoNote;
      file = videoNote.video;
      existing = videoNote.speechRecognitionResult;
    } else {
      return;
    }
    new VoiceTranscriptionDialog(controller, tdlib, message, file, isSecretChat).start(existing);
  }

  private final MessagesController controller;
  private final Tdlib tdlib;
  private final TdApi.Message message;
  private final TdApi.File file;
  private final boolean isSecretChat;
  private final String cacheKey;

  private TextView textView;
  private AlertDialog dialog;
  private boolean finished;

  private VoiceTranscriptionDialog (MessagesController controller, Tdlib tdlib, TdApi.Message message, TdApi.File file, boolean isSecretChat) {
    this.controller = controller;
    this.tdlib = tdlib;
    this.message = message;
    this.file = file;
    this.isSecretChat = isSecretChat;
    this.cacheKey = file.remote != null && file.remote.uniqueId != null && !file.remote.uniqueId.isEmpty() ?
      file.remote.uniqueId : tdlib.id() + "_" + message.chatId + "_" + message.id;
  }

  private void start (@Nullable TdApi.SpeechRecognitionResult existing) {
    Context context = controller.context();
    textView = new TextView(context);
    textView.setTextIsSelectable(true);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    textView.setTextColor(Theme.textAccentColor());
    int paddingH = Screen.dp(20f), paddingV = Screen.dp(14f);
    textView.setPadding(paddingH, paddingV, paddingH, paddingV);
    textView.setText(Lang.getString(R.string.TranscriptionInProgress));

    AlertDialog.Builder builder = new AlertDialog.Builder(context, Theme.dialogTheme());
    builder.setTitle(Lang.getString(R.string.Transcribe));
    builder.setView(textView);
    builder.setNegativeButton(Lang.getString(R.string.TranscriptionClose), (d, which) -> d.dismiss());
    builder.setPositiveButton(Lang.getString(R.string.Copy), (d, which) -> {
      if (finished && textView.getText().length() > 0) {
        UI.copyText(textView.getText(), R.string.CopiedText);
      }
    });
    dialog = controller.showAlert(builder);

    String cached = cache.get(cacheKey);
    if (cached != null) {
      finish(cached);
    } else if (existing instanceof TdApi.SpeechRecognitionResultText) {
      finish(((TdApi.SpeechRecognitionResultText) existing).text);
    } else if (isSecretChat) {
      // Secret chats can't use server recognition.
      recognizeOnDevice();
    } else {
      recognizeOnServer();
    }
  }

  private boolean isClosed () {
    return dialog == null || !dialog.isShowing();
  }

  private void showProgress (String partialText) {
    UI.post(() -> {
      if (!finished && !isClosed()) {
        textView.setText(partialText == null || partialText.isEmpty() ? Lang.getString(R.string.TranscriptionInProgress) : partialText + "…");
      }
    });
  }

  private void finish (String text) {
    cache.put(cacheKey, text);
    UI.post(() -> {
      finished = true;
      if (!isClosed()) {
        textView.setText(text.isEmpty() ? Lang.getString(R.string.TranscriptionEmpty) : text);
      }
    });
  }

  private void fail (String error) {
    UI.post(() -> {
      finished = true;
      if (!isClosed()) {
        textView.setText(error);
      }
    });
  }

  // Server

  private void recognizeOnServer () {
    tdlib.client().send(new TdApi.RecognizeSpeech(message.chatId, message.id), result -> {
      if (result instanceof TdApi.Error) {
        Log.i("Server speech recognition refused: %s", TD.toErrorString(result));
        recognizeOnDevice();
      } else {
        pollServerResult(0);
      }
    });
  }

  private void pollServerResult (int attempt) {
    if (isClosed()) {
      return;
    }
    if (attempt >= SERVER_POLL_MAX_ATTEMPTS) {
      recognizeOnDevice();
      return;
    }
    tdlib.client().send(new TdApi.GetMessage(message.chatId, message.id), result -> {
      TdApi.SpeechRecognitionResult recognition = null;
      if (result instanceof TdApi.Message) {
        TdApi.MessageContent content = ((TdApi.Message) result).content;
        if (content instanceof TdApi.MessageVoiceNote) {
          recognition = ((TdApi.MessageVoiceNote) content).voiceNote.speechRecognitionResult;
        } else if (content instanceof TdApi.MessageVideoNote) {
          recognition = ((TdApi.MessageVideoNote) content).videoNote.speechRecognitionResult;
        }
      }
      if (recognition instanceof TdApi.SpeechRecognitionResultText) {
        finish(((TdApi.SpeechRecognitionResultText) recognition).text);
      } else if (recognition instanceof TdApi.SpeechRecognitionResultError) {
        Log.i("Server speech recognition failed: %s", TD.toErrorString(((TdApi.SpeechRecognitionResultError) recognition).error));
        recognizeOnDevice();
      } else {
        if (recognition instanceof TdApi.SpeechRecognitionResultPending) {
          showProgress(((TdApi.SpeechRecognitionResultPending) recognition).partialText);
        }
        UI.post(() -> pollServerResult(attempt + 1), SERVER_POLL_INTERVAL_MS);
      }
    });
  }

  // On device

  private void recognizeOnDevice () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isRecognitionAvailable(UI.getAppContext())) {
      fail(Lang.getString(R.string.TranscriptionUnavailable));
      return;
    }
    showProgress(null);
    tdlib.client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), result -> {
      if (!(result instanceof TdApi.File) || !TD.isFileLoaded((TdApi.File) result)) {
        fail(Lang.getString(R.string.TranscriptionFailed));
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
          return;
        }
        UI.post(() -> startRecognizer(pcm));
      }, "VoiceTranscription").start();
    });
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private void startRecognizer (byte[] pcm) {
    if (isClosed()) {
      return;
    }
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
      return;
    }

    final StringBuilder text = new StringBuilder();
    recognizer.setRecognitionListener(new RecognitionListener() {
      private void appendResults (Bundle results) {
        java.util.ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
        if (matches != null && !matches.isEmpty() && !matches.get(0).isEmpty()) {
          if (text.length() > 0) {
            text.append(' ');
          }
          text.append(matches.get(0));
          showProgress(text.toString());
        }
      }

      private void done () {
        recognizer.destroy();
        closeQuietly(pipe[0]);
        finish(text.toString());
      }

      @Override public void onSegmentResults (Bundle segmentResults) { appendResults(segmentResults); }
      @Override public void onEndOfSegmentedSession () { done(); }
      @Override public void onResults (Bundle results) { appendResults(results); done(); }
      @Override public void onError (int error) {
        Log.w("On-device speech recognition error: %d", error);
        if (text.length() > 0) {
          done();
        } else {
          recognizer.destroy();
          closeQuietly(pipe[0]);
          fail(Lang.getString(error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ?
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
    recognizer.startListening(intent);

    new Thread(() -> {
      try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
        out.write(pcm);
      } catch (IOException e) {
        Log.w("Unable to feed audio to the recognizer", e);
      }
    }, "VoiceTranscriptionFeed").start();
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
