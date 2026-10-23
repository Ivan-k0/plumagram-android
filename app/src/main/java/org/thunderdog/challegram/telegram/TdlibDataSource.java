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
package org.thunderdog.challegram.telegram;

import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.unit.ByteUnit;
import tgx.td.Td;

public final class TdlibDataSource extends BaseDataSource {
  private static final String SCHEME = "tg";
  private static final String AUTHORITY = "file";
  private static final String PARAM_ACCOUNT = "account";
  private static final String PARAM_FILE_ID = "id";
  private static final String PARAM_PRIORITY = "priority";
  private static final String PARAM_REMOTE_ID = "remote_id";
  private static final String PARAM_FLAGS = "flags";
  private static final String PARAM_DURATION = "duration";

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Flag.NONE,
    Flag.DOWNLOAD_FULLY,
    Flag.DOWNLOAD_PRECISELY,
    Flag.OPTIMIZE_CHUNKS
  }, flag = true)
  public @interface Flag {
    int
      NONE = 0,
      DOWNLOAD_FULLY = 1,
      DOWNLOAD_PRECISELY = 1 << 1,
      OPTIMIZE_CHUNKS = 1 << 2;
  }

  public static final class UriFactory {
    public static Uri create (int accountId, TdApi.File file, int priority, @Flag int flags, long durationMs) {
      return file.id < 0 ? create(accountId, file.remote.id, priority, flags, durationMs) : create(accountId, file.id, priority, flags, durationMs);
    }

    public static Uri create (int accountId, int fileId) {
      return create(accountId, fileId, TdlibFilesManager.PRIORITY_UNSET, Flag.NONE, 0);
    }

    public static Uri create (int accountId, int fileId, int priority, @Flag int flags, long durationMs) {
      Uri.Builder b = new Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY);
      if (accountId != TdlibAccount.NO_ID) {
        b.appendQueryParameter(PARAM_ACCOUNT, Integer.toString(accountId));
      }
      b.appendQueryParameter(PARAM_FILE_ID, Integer.toString(fileId));
      if (priority != TdlibFilesManager.PRIORITY_UNSET) {
        b.appendQueryParameter(PARAM_PRIORITY, Integer.toString(priority));
      }
      if (flags != Flag.NONE) {
        b.appendQueryParameter(PARAM_FLAGS, Integer.toString(flags));
      }
      if (durationMs != 0) {
        b.appendQueryParameter(PARAM_DURATION, Long.toString(durationMs));
      }
      return b.build();
    }

    public static Uri create (int accountId, String remoteId) {
      return create(accountId, remoteId, TdlibFilesManager.PRIORITY_UNSET, Flag.NONE, 0);
    }

    public static Uri create (int accountId, String remoteId, int priority, @Flag int flags, long durationMs) {
      if (accountId == TdlibAccount.NO_ID)
        throw new IllegalArgumentException();
      Uri.Builder b = new Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY);
      if (accountId != TdlibAccount.NO_ID) {
        b.appendQueryParameter(PARAM_ACCOUNT, Integer.toString(accountId));
      }
      b.appendQueryParameter(PARAM_REMOTE_ID, remoteId);
      if (priority != TdlibFilesManager.PRIORITY_UNSET) {
        b.appendQueryParameter(PARAM_PRIORITY, Integer.toString(priority));
      }
      if (flags != Flag.NONE) {
        b.appendQueryParameter(PARAM_FLAGS, Integer.toString(flags));
      }
      if (durationMs != 0) {
        b.appendQueryParameter(PARAM_DURATION, Long.toString(durationMs));
      }
      return b.build();
    }
  }

  public static class TdlibDataSourceException extends IOException {
    public TdlibDataSourceException (String message) {
      super(message);
    }

    public TdlibDataSourceException (Throwable cause) {
      super(cause);
    }

    public TdlibDataSourceException (String message, Throwable cause) {
      super(message, cause);
    }
  }

  public interface RequestModifier {
    default Uri modifyUri (Uri sourceUri) {
      return sourceUri;
    }
    @Nullable
    default DataSource redirectDataSource (Uri uri) {
      return null;
    }
    default int modifyPriority (Uri uri) {
      return TdlibFilesManager.PRIORITY_UNSET;
    }
  }

  public static final class Factory implements DataSource.Factory {
    private final int defaultAccountId, defaultPriority;
    private final RequestModifier requestModifier;

    public Factory () {
      this(TdlibAccount.NO_ID);
    }

    public Factory (int defaultAccountId) {
      this(defaultAccountId, null);
    }

    public Factory (int defaultAccountId, RequestModifier requestModifier) {
      this(defaultAccountId, TdlibFilesManager.PRIORITY_STREAMING_DEFAULT, requestModifier);
    }

    public Factory (int defaultAccountId, int defaultPriority, RequestModifier requestModifier) {
      this.defaultAccountId = defaultAccountId;
      this.defaultPriority = defaultPriority;
      this.requestModifier = requestModifier;
    }

    @Override
    @NonNull
    public DataSource createDataSource () {
      return new TdlibDataSource(defaultAccountId, defaultPriority, requestModifier);
    }
  }

  private final int defaultAccountId, defaultPriority;
  private final RequestModifier requestModifier;
  private Uri uri;
  private int priority;
  private @Flag int flags;
  private long bytesRead;
  private long initialOffset, maxReadLength;
  private Tdlib tdlib;
  private final Object fileLock = new Object();
  private TdApi.File file;
  private DataSource redirectedToDataSource;
  private long durationMs;

  private final FileUpdateListener listener = this::processUpdate;

  private boolean referenceAcquired;
  private static final java.util.concurrent.atomic.AtomicInteger tgx101OpenLogs = new java.util.concurrent.atomic.AtomicInteger();

  public TdlibDataSource (int defaultAccountId, int defaultPriority, RequestModifier requestModifier) {
    super(true);
    this.defaultAccountId = defaultAccountId;
    this.defaultPriority = defaultPriority;
    this.requestModifier = requestModifier;
  }

  @Override
  public long open (@NonNull DataSpec dataSpec) throws IOException {
    final Uri uri = requestModifier != null ? requestModifier.modifyUri(dataSpec.uri) : dataSpec.uri;
    this.redirectedToDataSource = requestModifier != null ? requestModifier.redirectDataSource(uri) : null;
    if (redirectedToDataSource != null) {
      return redirectedToDataSource.open(dataSpec);
    }
    int modifiedPriority = requestModifier != null ? requestModifier.modifyPriority(uri) : TdlibFilesManager.PRIORITY_UNSET;
    if (!SCHEME.equals(uri.getScheme()))
      throw new TdlibDataSourceException("Unsupported URI scheme: " + uri.getScheme());
    if (!AUTHORITY.equals(uri.getAuthority()))
      throw new TdlibDataSourceException("Unsupported URI authority: " + uri.getAuthority());
    final int accountId = StringUtils.parseInt(uri.getQueryParameter(PARAM_ACCOUNT), defaultAccountId);
    if (accountId == TdlibAccount.NO_ID)
      throw new TdlibDataSourceException(PARAM_ACCOUNT + " parameter is missing");
    final int fileId = StringUtils.parseInt(uri.getQueryParameter(PARAM_FILE_ID), -1);
    final String remoteId = uri.getQueryParameter(PARAM_REMOTE_ID);
    if (fileId == -1 && StringUtils.isEmpty(remoteId))
      throw new TdlibDataSourceException(PARAM_FILE_ID + " and " + PARAM_REMOTE_ID + " parameters are missing");
    final int priority = modifiedPriority != TdlibFilesManager.PRIORITY_UNSET ? modifiedPriority : StringUtils.parseInt(uri.getQueryParameter(PARAM_PRIORITY), defaultPriority);
    final @Flag int flags = StringUtils.parseInt(uri.getQueryParameter(PARAM_FLAGS), Flag.NONE);
    final long durationMs = StringUtils.parseLong(uri.getQueryParameter(PARAM_DURATION), 0);

    transferInitializing(dataSpec);

    this.uri = uri;
    this.priority = priority;
    this.flags = flags;
    this.initialOffset = bytesRead = dataSpec.position;
    this.maxReadLength = dataSpec.length;
    this.durationMs = durationMs;
    this.tdlib = TdlibManager.getTdlib(accountId);

    final TdApi.Function<TdApi.File> function = !StringUtils.isEmpty(remoteId) ? new TdApi.GetRemoteFile(remoteId, null) : new TdApi.GetFile(fileId);

    final CountDownLatch openLatch = new CountDownLatch(1);
    final AtomicBoolean isOpening = new AtomicBoolean(true);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();

    tdlib.send(function, (file, error) -> {
      synchronized (isOpening) {
        if (isOpening.getAndSet(false)) {
          if (file != null) {
            tdlib.listeners().addFileListener(file.id, listener);
          }
          response.set(file != null ? file : error);
        }
      }
      openLatch.countDown();
    });
    try {
      openLatch.await();
    } catch (InterruptedException e) {
      synchronized (isOpening) {
        if (!isOpening.getAndSet(false)) {
          TdApi.Object result = response.get();
          if (result instanceof TdApi.File) {
            tdlib.listeners().removeFileListener(((TdApi.File) result).id, listener);
          }
        }
      }
      throw new TdlibDataSourceException(e);
    }
    TdApi.Object result = response.get();
    if (result == null)
      throw new TdlibDataSourceException("getFile failed");
    if (result instanceof TdApi.Error)
      throw new TdlibDataSourceException("getFile failed:" + TD.toErrorString(result));

    synchronized (fileLock) {
      this.file = (TdApi.File) result;
    }
    transferStarted(dataSpec);
    if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG && tgx101OpenLogs.incrementAndGet() <= 40) { // TGx101 (user 2026-10-23 «большие mp4 не воспроизводятся»): the first 40 opens
      TdApi.File f = this.file;
      org.thunderdog.challegram.Tgx101Diag.mark("stream: open file " + f.id + " at " + dataSpec.position + (dataSpec.length != C.LENGTH_UNSET ? " +" + dataSpec.length : "")
        + " of " + f.size + ", have " + f.local.downloadedSize + (f.local.isDownloadingCompleted ? " (complete)" : "") + ", prefix " + f.local.downloadedPrefixSize + " at " + f.local.downloadOffset
        + (f.local.isDownloadingActive ? ", downloading" : "") + (f.local.canBeDownloaded ? "" : ", can't be downloaded") + ", flags " + flags + ", " + durationMs / 1000 + " s");
    }
    // TGx101 (user 2026-10-23 «большие mp4 не воспроизводятся»): the bytes left from the position, not the whole file —
    // an mp4 whose index sits after the video is read from its end, and the whole size there sent the player looking
    // ever further past the end (58 000 opens in 10 s, nothing played)
    final long left = file.size != 0 ? Math.max(0, file.size - dataSpec.position) : C.LENGTH_UNSET;
    if (dataSpec.length != C.LENGTH_UNSET) {
      return left != C.LENGTH_UNSET ? Math.min(dataSpec.length, left) : dataSpec.length;
    } else {
      return left;
    }
  }

  @TdlibThread
  private void processUpdate (TdApi.UpdateFile file) {
    TdApi.File currentFile;
    synchronized (fileLock) {
      currentFile = this.file;
      boolean notify = currentFile != null && currentFile.id == file.file.id && Td.copyTo(file.file, currentFile);
      if (notify && this.latch != null) {
        this.latch.countDown();
        this.latch = null;
      }
    }
  }

  private long getAvailableSize (TdApi.File file, long offset, int length) {
    long available;
    if (file.local.isDownloadingCompleted) {
      available = file.local.downloadedSize - offset;
    } else if (offset >= file.local.downloadOffset && offset < file.local.downloadOffset + file.local.downloadedPrefixSize) {
      available = file.local.downloadedPrefixSize - (offset - file.local.downloadOffset);
    } else {
      TdApi.FileDownloadedPrefixSize downloadedPrefixSize = tdlib.clientExecuteT(new TdApi.GetFileDownloadedPrefixSize(file.id, offset), 1000L, false);
      if (downloadedPrefixSize != null) {
        available = downloadedPrefixSize.size;
      } else {
        return 0;
      }
    }
    long availableBytes = Math.max(0L, Math.min(length, available));
    if (maxReadLength != C.LENGTH_UNSET) {
      long remainingBytes = initialOffset + maxReadLength - offset;
      if (remainingBytes <= 0) {
        return C.RESULT_END_OF_INPUT;
      }
      availableBytes = Math.min(availableBytes, remainingBytes);
    }
    return availableBytes;
  }

  private final TdApi.File localFile = new TdApi.File(0, 0, 0, new TdApi.LocalFile(), new TdApi.RemoteFile());
  private CountDownLatch latch;
  private RandomAccessFile openFile;

  private boolean acquireOrUpdateReference (TdApi.File file, long offset, long limit) {
    if (!referenceAcquired && file.local.canBeDownloaded) {
      referenceAcquired = true;
      tdlib.files().addCloudReference(file, priority, offset, limit, listener, false);
      return true;
    } else if (referenceAcquired) {
      tdlib.files().updateCloudReference(file, listener, priority, offset, limit);
      return true;
    }
    return false;
  }

  private void releaseReference (TdApi.File file) {
    if (referenceAcquired) {
      tdlib.files().removeCloudReference(file, listener);
    }
  }

  private static final long MIN_DOWNLOAD_CHUNK_LENGTH = ByteUnit.KIB.toBytes(64);
  private static final long BIG_DOWNLOAD_CHUNK_LENGTH = ByteUnit.MB.toBytes(5);

  @Override
  public int read (@NonNull byte[] buffer, int bufferOffset, int readLength) throws IOException {
    if (redirectedToDataSource != null) {
      return redirectedToDataSource.read(buffer, bufferOffset, readLength);
    }
    if (readLength == 0) {
      return 0;
    }
    try {
      boolean first = true;
      do {
        final TdApi.File file;
        final CountDownLatch latch;
        synchronized (fileLock) {
          if (this.file != null) {
            Td.copyTo(this.file, localFile);
            this.localFile.id = this.file.id;
            file = localFile;
            if (this.latch != null) {
              latch = this.latch;
            } else {
              this.latch = latch = new CountDownLatch(1);
            }
          } else {
            file = null;
            latch = null;
          }
        }
        if (file == null)
          throw new TdlibDataSourceException("file == null");
        final long offset = bytesRead;
        if (file.size != 0 && (offset >= file.size || (this.maxReadLength != C.LENGTH_UNSET && offset >= this.initialOffset + this.maxReadLength))) {
          return C.RESULT_END_OF_INPUT;
        }

        long downloadLimit = calculateDownloadLimit(file, offset, readLength);
        if (first) {
          first = false;
          if (file.local.isDownloadingCompleted) {
            releaseReference(file);
          } else {
            acquireOrUpdateReference(file, initialOffset, downloadLimit);
          }
        } else if (!file.local.isDownloadingCompleted && !file.local.isDownloadingActive) {
          acquireOrUpdateReference(file, initialOffset, downloadLimit);
        }
        long available = getAvailableSize(file, offset, readLength);
        if (available == C.RESULT_END_OF_INPUT) {
          return C.RESULT_END_OF_INPUT;
        }
        if (available == 0) {
          if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) {
            long waitStart = android.os.SystemClock.uptimeMillis();
            if (!latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
              org.thunderdog.challegram.Tgx101Diag.mark("stream: file " + file.id + " waiting 3 s for bytes at " + offset + " (limit " + downloadLimit + " from " + initialOffset
                + "), have " + file.local.downloadedSize + ", prefix " + file.local.downloadedPrefixSize + " at " + file.local.downloadOffset + (file.local.isDownloadingActive ? ", downloading" : ", NOT downloading")
                + (referenceAcquired ? "" : ", no reference"));
              latch.await();
              org.thunderdog.challegram.Tgx101Diag.mark("stream: file " + file.id + " bytes came after " + (android.os.SystemClock.uptimeMillis() - waitStart) + " ms");
            }
          } else {
            latch.await();
          }
          continue;
        }
        try {
          boolean opened = false;
          synchronized (fileLock) {
            if (openFile == null) {
              openFile = new RandomAccessFile(file.local.path, "r");
              opened = true;
            }
          }
          if (opened && offset > 0) {
            openFile.seek(offset);
          }
          int readCount = openFile.read(buffer, bufferOffset, available > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) available);
          bytesTransferred(readCount);
          bytesRead += readCount;
          return readCount;
        } catch (IOException e) {
          if (acquireOrUpdateReference(file, initialOffset, downloadLimit)) {
            latch.await();
          } else {
            throw new TdlibDataSourceException(e);
          }
        }
      } while (true);
    } catch (InterruptedException e) {
      throw new TdlibDataSourceException(e);
    }
  }

  private static final long PRELOAD_SECONDS = 10;

  private long calculateDownloadLimit (TdApi.File file, long offset, int readLength) {
    long downloadLimit;
    if (BitwiseUtils.hasFlag(flags, Flag.DOWNLOAD_FULLY)) {
      downloadLimit = TdlibFilesManager.LIMIT_MAX;
    } else if (maxReadLength != C.LENGTH_UNSET) {
      downloadLimit = maxReadLength;
    } else {
      downloadLimit = offset - initialOffset + readLength;
      if (!BitwiseUtils.hasFlag(flags, Flag.DOWNLOAD_PRECISELY)) {
        long minDownloadChunk = MIN_DOWNLOAD_CHUNK_LENGTH;
        if (BitwiseUtils.hasFlag(flags, Flag.OPTIMIZE_CHUNKS)) {
          if (file.size != 0 && file.size <= minDownloadChunk * 3) {
            return TdlibFilesManager.LIMIT_MAX;
          } else if (durationMs > 0) {
            if (durationMs <= TimeUnit.SECONDS.toMillis(PRELOAD_SECONDS)) {
              return TdlibFilesManager.LIMIT_MAX;
            }
            double bytesPerSecond = ((double) file.size / (double) durationMs) * 1000.0;
            long bytesPerPeriod = Math.round(bytesPerSecond * (double) PRELOAD_SECONDS);
            long extraBytes = bytesPerPeriod % minDownloadChunk;
            if (extraBytes != 0) {
              bytesPerPeriod += minDownloadChunk - extraBytes;
            }
            minDownloadChunk = Math.max(minDownloadChunk, bytesPerPeriod);
          }
        }
        long remaining = (initialOffset + downloadLimit) % minDownloadChunk;
        downloadLimit += minDownloadChunk - remaining;
        if (file.size != 0) {
          if (initialOffset + downloadLimit > file.size) {
            downloadLimit = file.size - initialOffset;
          }
          long bytesRemaining = file.size - downloadLimit;
          if (bytesRemaining <= minDownloadChunk) {
            downloadLimit = TdlibFilesManager.LIMIT_MAX;
          }
        }
      }
    }
    return downloadLimit;
  }

  @Nullable
  @Override
  public Uri getUri () {
    if (redirectedToDataSource != null) {
      return redirectedToDataSource.getUri();
    }
    return uri;
  }

  @Override
  public void close () throws IOException {
    uri = null;
    if (redirectedToDataSource != null) {
      redirectedToDataSource.close();
      redirectedToDataSource = null;
      return;
    }
    TdApi.File file;
    synchronized (fileLock) {
      file = this.file;
      this.file = null;
      if (this.latch != null) {
        this.latch.countDown();
        this.latch = null;
      }
      if (this.openFile != null) {
        U.closeFile(this.openFile);
        this.openFile = null;
      }
    }
    if (tdlib != null && file != null) {
      if (referenceAcquired) {
        tdlib.files().removeCloudReference(file, listener);
        referenceAcquired = false;
      }
      tdlib.listeners().removeFileListener(file.id, listener);
      transferEnded();
    }
    tdlib = null;
  }
}
