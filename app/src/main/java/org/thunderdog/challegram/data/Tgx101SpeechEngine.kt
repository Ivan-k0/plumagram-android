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
package org.thunderdog.challegram.data

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * TGx101: own speech recognition for voice messages (sherpa-onnx), for phones whose speech service doesn't take audio
 * from a file (Vivo: Google's service answers ERROR_CLIENT / ERROR_NETWORK). The model is downloaded in MagiX
 * ([Tgx101SpeechModels]); one recognizer is kept loaded for the model in use.
 */
object Tgx101SpeechEngine {
  private const val SAMPLE_RATE = 16000

  private var loadedId: String? = null
  private var online: OnlineRecognizer? = null
  private var offline: OfflineRecognizer? = null

  private fun threads (): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

  @Synchronized
  private fun load (model: Tgx101SpeechModels.Model, dir: File) {
    if (loadedId == model.id) return
    release()
    if (model.kind == Tgx101SpeechModels.KIND_ONLINE_TRANSDUCER) {
      val config = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
        modelConfig = OnlineModelConfig(
          transducer = OnlineTransducerModelConfig(
            encoder = File(dir, "encoder.int8.onnx").absolutePath,
            decoder = File(dir, "decoder.onnx").absolutePath,
            joiner = File(dir, "joiner.int8.onnx").absolutePath
          ),
          tokens = File(dir, "tokens.txt").absolutePath,
          numThreads = threads()
        ),
        enableEndpoint = false
      )
      online = OnlineRecognizer(config = config)
    } else {
      val config = OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 64),
        modelConfig = OfflineModelConfig(
          nemo = OfflineNemoEncDecCtcModelConfig(model = File(dir, "model.int8.onnx").absolutePath),
          tokens = File(dir, "tokens.txt").absolutePath,
          numThreads = threads()
        )
      )
      offline = OfflineRecognizer(config = config)
    }
    loadedId = model.id
  }

  /** Frees the loaded model (switching models, deleting it, low memory) */
  @Synchronized
  @JvmStatic
  fun release () {
    online?.release()
    offline?.release()
    online = null
    offline = null
    loadedId = null
  }

  /** Recognizes 16 kHz mono PCM16; runs on the caller's (background) thread */
  @Synchronized
  @JvmStatic
  fun recognize (model: Tgx101SpeechModels.Model, dir: File, pcm16: ByteArray): String {
    load(model, dir)
    val samples = FloatArray(pcm16.size / 2)
    for (i in samples.indices) {
      val lo = pcm16[i * 2].toInt() and 0xff
      val hi = pcm16[i * 2 + 1].toInt()
      samples[i] = ((hi shl 8) or lo).toShort() / 32768f
    }
    online?.let { recognizer ->
      val stream = recognizer.createStream()
      try {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        stream.acceptWaveform(FloatArray(SAMPLE_RATE / 2), SAMPLE_RATE) // tail padding: the last words get decoded
        stream.inputFinished()
        while (recognizer.isReady(stream)) {
          recognizer.decode(stream)
        }
        return recognizer.getResult(stream).text.trim()
      } finally {
        stream.release()
      }
    }
    offline?.let { recognizer ->
      val stream = recognizer.createStream()
      try {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        recognizer.decode(stream)
        return recognizer.getResult(stream).text.trim()
      } finally {
        stream.release()
      }
    }
    return ""
  }
}
