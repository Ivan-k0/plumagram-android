@file:JvmName("NLoader")

package tgx.flavor

import android.os.SystemClock
import androidx.tracing.trace
import com.getkeepsafe.relinker.ReLinker
import com.getkeepsafe.relinker.ReLinkerInstance
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.N
import org.thunderdog.challegram.unsorted.AppContext

private val lock = Any()
private var messages: MutableList<String>? = null

fun collectLog(): String? {
  synchronized(lock) {
    return messages?.joinToString(
      "\n",
      "==== ReLinker ====\n",
      "\n==== ReLinker END ====\n"
    )?.also {
      messages = null
    }
  }
}

private fun loadLibrary(
  reLinker: ReLinkerInstance,
  library: String,
  version: String?
) {
  val ms = SystemClock.uptimeMillis()
  org.thunderdog.challegram.Tgx101Diag.mark("load lib$library.so …") // TGx101
  trace("load:$library") {
    reLinker.loadLibrary(AppContext.get(), library, version)
  }
  org.thunderdog.challegram.Tgx101Diag.mark("load lib$library.so OK") // TGx101
  android.util.Log.v("tgx", "Loaded $library in ${SystemClock.uptimeMillis() - ms}ms")
}

@Synchronized
fun loadLibraries() {
  try {
    val logger = ReLinker.Logger { message ->
      synchronized(lock) {
        val list = messages ?: mutableListOf<String>().also {
          messages = it
        }
        list.add(message)
      }
    }
    val reLinker = ReLinker.recursively().log(logger)
    if (BuildConfig.SHARED_STL) {
      loadLibrary(reLinker, "c++_shared", BuildConfig.NDK_VERSION)
    }
    loadLibrary(reLinker, "cryptox", BuildConfig.OPENSSL_VERSION_FULL)
    loadLibrary(reLinker, "sslx", BuildConfig.OPENSSL_VERSION_FULL)
    loadLibrary(reLinker, "tdjni", BuildConfig.TDLIB_VERSION)
    loadLibrary(reLinker, "leveldbjni", BuildConfig.LEVELDB_VERSION)
    if (BuildConfig.CALLS_AVAILABLE) {
      loadLibrary(reLinker, "tgcallsjni", BuildConfig.JNI_VERSION)
    }
    loadLibrary(reLinker, "tgxjni", BuildConfig.JNI_VERSION)
    org.thunderdog.challegram.Tgx101Diag.mark("media libraries …") // TGx101
    N.setupLibraries()
    org.thunderdog.challegram.Tgx101Diag.mark("media libraries OK") // TGx101
  } catch (t: Throwable) {
    org.thunderdog.challegram.Tgx101Diag.mark("native libraries FAILED: $t\n${collectLog()}") // TGx101
    val e = IllegalStateException("${collectLog()}\n${t.message}")
    e.stackTrace = t.stackTrace
    throw e
  }
}