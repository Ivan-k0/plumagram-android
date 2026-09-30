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
 *
 * File created on 06/09/2017
 */
package org.thunderdog.challegram.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.receiver.VoIPMediaButtonReceiver;
import org.thunderdog.challegram.telegram.PrivateCallListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationChannelGroup;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.SoundPoolMap;
import org.thunderdog.challegram.voip.ConnectionStateListener;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.Socks5Proxy;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.voip.gui.VoIPFeedbackActivity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import tgx.td.ChatId;

public class TGCallService extends Service implements
  TdlibCache.CallStateChangeListener,
  AudioManager.OnAudioFocusChangeListener,
  SensorEventListener, UI.StateListener {
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  private @Nullable Tdlib tdlib;
  private @Nullable TdApi.Call call;
  private @Nullable String callChannelId;
  private TdApi.User user;

  private boolean callInitialized;

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (Log.isEnabled(Log.TAG_VOIP)) {
      Log.i(Log.TAG_VOIP, "TGCallService.onStartCommand received, intent: %s", intent);
    }
    final int oldCallId = callId();
    int accountId, callId;
    if (intent != null) {
      accountId = intent.getIntExtra("account_id", TdlibAccount.NO_ID);
      callId = intent.getIntExtra("call_id", 0);
    } else {
      accountId = TdlibAccount.NO_ID;
      callId = 0;
    }
    if (accountId == TdlibAccount.NO_ID || callId == 0) {
      setCallId(null, 0);
    } else {
      setCallId(TdlibManager.getTdlib(accountId), callId);
    }
    if (call == null || user == null) {
      Log.w(Log.TAG_VOIP, "TGCallService.onStartCommand: failed because call or other party not found, call: %s, user: %s", call, user);
      stopSelf();
      return START_NOT_STICKY;
    }
    if (callInitialized) {
      if (oldCallId != 0 && oldCallId != callId)
        throw new IllegalStateException();
    } else {
      initCall(tdlib, call);
    }
    updateCall(call);
    return START_NOT_STICKY;
  }

  public int callId () {
    return call != null ? call.id : 0;
  }

  public int callTdlib () {
    return tdlib != null ? tdlib.id() : TdlibAccount.NO_ID;
  }

  public long getCallDuration () {
    return tgcalls != null ? tgcalls.getCallDuration() : VoIPInstance.DURATION_UNKNOWN;
  }

  private void setCallId (Tdlib tdlib, int callId) {
    if (this.tdlib != tdlib || callId != callId()) {
      if (this.call != null) {
        this.tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
        UI.removeStateListener(this);
      }
      this.tdlib = tdlib;
      setCall(tdlib != null ? tdlib.cache().getCall(callId) : null);
      this.callBarsCount = -1;
      this.user = call != null ? tdlib.cache().user(call.userId) : null;
      if (call != null) {
        tdlib.cache().subscribeToCallUpdates(call.id, this);
        UI.addStateListener(this);
      }
      /*if (controller != null) {
        controller.setCallId(callId);
      }*/
    }
  }

  private SoundPoolMap soundPoolMap;
  private @Nullable VoIPInstance tgcalls;
  private @Nullable PrivateCallListener callListener;
  private PowerManager.WakeLock cpuWakelock;
  private BluetoothAdapter btAdapter;

  private boolean isProximityNear, isHeadsetPlugged;

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive (Context context, Intent intent) {
      final String action = intent.getAction();

      if (ACTION_HEADSET_PLUG.equals(action)) {
        isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
        if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock.isHeld()) {
          proximityWakelock.release();
        }
        isProximityNear = false;
        updateOutputGainControlState();
        onTgx101HeadsetChanged(isHeadsetPlugged);
        return;
      }

      if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
        updateNetworkType(true);
        return;
      }

      if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
        updateBluetoothHeadsetState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0) == BluetoothProfile.STATE_CONNECTED);
        return;
      }

      if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
        notifyAudioSettingsChanged();
        return;
      }

      if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
          hangUp();
        }
        return;
      }

      if (Intent.ACTION_SCREEN_OFF.equals(action)) {
        // TGx101: some lock screens (Vivo) show only notifications posted after locking,
        // so the ongoing call notification is posted again when the screen goes off
        refreshOngoingNotification();
        return;
      }

      if (Intents.ACTION_DECLINE_CALL.equals(action)) {
        declineIncomingCall();
        return;
      }

      if (Intents.ACTION_END_CALL.equals(action)) {
        hangUp();
        return;
      }

      if (Intents.ACTION_ANSWER_CALL.equals(action)) {
        acceptIncomingCall();
        return;
      }

      if (Intents.ACTION_TOGGLE_CALL_SPEAKER.equals(action)) { // TGx101: speaker button in the call notification
        CallSettings settings = getCallSettings();
        if (settings != null) {
          // Speaker on, or back to the headset (Bluetooth) / earpiece (wired headset is picked there too)
          int current = settings.getSpeakerMode();
          boolean speakerNow = current == CallSettings.SPEAKER_MODE_SPEAKER || current == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT;
          settings.setSpeakerMode(!speakerNow ? CallSettings.SPEAKER_MODE_SPEAKER : isBluetoothHeadsetConnected() ? CallSettings.SPEAKER_MODE_BLUETOOTH : CallSettings.SPEAKER_MODE_EARPIECE);
        }
        return;
      }
    }
  };

  public static final String ACTION_HEADSET_PLUG;

  static {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      ACTION_HEADSET_PLUG = AudioManager.ACTION_HEADSET_PLUG;
    } else {
      ACTION_HEADSET_PLUG = Intent.ACTION_HEADSET_PLUG;
    }
  }

  private static volatile WeakReference<TGCallService> reference;

  private boolean audioGainControlEnabled;
  private int echoCancellationStrength;

  // TGx101: getCommunicationDevice() is a slow system call on some phones (up to ~150 ms on Vivo);
  // on the main thread it froze the call screen animation, so it runs in the background
  private static java.util.concurrent.ExecutorService audioQueryExecutor;

  public void updateOutputGainControlState () {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    boolean var;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (am == null) return;
      synchronized (TGCallService.class) {
        if (audioQueryExecutor == null) audioQueryExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
      }
      audioQueryExecutor.execute(() -> {
        android.media.AudioDeviceInfo deviceInfo = am.getCommunicationDevice();
        boolean earpiece = deviceInfo == null || deviceInfo.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
        UI.post(() -> applyOutputGainControlState(earpiece));
      });
      return;
    } else {
      var = hasEarpiece() && am != null && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged;
    }
    applyOutputGainControlState(var);
  }

  private void applyOutputGainControlState (boolean var) {
    this.audioGainControlEnabled = var;
    this.echoCancellationStrength = isHeadsetPlugged || var ? 0 : 1;
    if (tgcalls != null) {
      tgcalls.setAudioOutputGainControlEnabled(audioGainControlEnabled);
      tgcalls.setEchoCancellationStrength(echoCancellationStrength);
    }
  }

  public boolean compareCall (Tdlib tdlib, int callId) {
    return call != null && callTdlib() == tdlib.id() && callId == call.id;
  }

  @Override
  public void onCreate () {
    super.onCreate();
    UI.initApp(getApplicationContext());
    reference = new WeakReference<>(this);

    soundPoolMap = new SoundPoolMap(AudioManager.STREAM_VOICE_CALL);
    soundPoolMap.prepare(R.raw.voip_connecting, R.raw.voip_ringback, R.raw.voip_fail, R.raw.voip_end, R.raw.voip_busy);

    VoIP.initialize(this);
  }

  private Object communicationDeviceChangedListener;

  private void initCall (Tdlib tdlib, TdApi.Call call) {
    if (callInitialized) {
      throw new IllegalStateException();
    }
    Log.v(Log.TAG_VOIP, "TGCallService.onCreate");
    callInitialized = true;
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    try {
      if (cpuWakelock == null) {
        cpuWakelock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgx:voip");
        cpuWakelock.acquire();
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

      } else {
        btAdapter = am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;
      }

      IntentFilter filter = new IntentFilter();
      filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
      } else {
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
      }
      if (btAdapter != null) {
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
      }
      filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
      filter.addAction(Intent.ACTION_SCREEN_OFF); // TGx101: re-post the call notification for the lock screen
      /*filter.addAction(Intents.ACTION_END_CALL);
      filter.addAction(Intents.ACTION_DECLINE_CALL);
      filter.addAction(Intents.ACTION_ANSWER_CALL);*/
      registerReceiver(receiver, filter);

      am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (communicationDeviceChangedListener == null) {
          communicationDeviceChangedListener = (AudioManager.OnCommunicationDeviceChangedListener) device -> UI.post(this::notifyAudioSettingsChanged);
        }
        am.addOnCommunicationDeviceChangedListener(Executors.newSingleThreadExecutor(), (AudioManager.OnCommunicationDeviceChangedListener) communicationDeviceChangedListener);
        notifyAudioSettingsChanged();
      } else {
        if (btAdapter != null && btAdapter.isEnabled()) {
          //noinspection MissingPermission
          int headsetState = btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
          updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED);
          if (headsetState == BluetoothProfile.STATE_CONNECTED) {
            am.setBluetoothScoOn(true);
          }
          notifyAudioSettingsChanged();
        }
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Error initializing call", t);
    }
  }

  private boolean isDestroyed;
  private static int amChangeCounter;

  private void releaseAudioFocus () {
    if (cpuWakelock == null) {
      return;
    }
    cpuWakelock.release();
    final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    boolean isBtHeadsetConnected = this.isBtHeadsetConnected;
    RunnableBool disconnectBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S && isBtHeadsetConnected ? (delayed) -> {
      am.stopBluetoothSco();
      Log.d(Log.TAG_VOIP, "AudioManager.stopBluetoothSco (in onDestroy), delayed: %b", delayed);
      am.setSpeakerphoneOn(false);
      Log.d(Log.TAG_VOIP, "AudioManager.setSpeakerphoneOn(false) (in onDestroy), delayed: %b", delayed);
    } : null;
    try {
      if (!soundPoolMap.isProbablyPlaying()) {
        if (disconnectBt != null) {
          disconnectBt.runWithBool(false);
        }
        // TGx101: the call end already switched to MODE_NORMAL; a second switch blocks the UI thread (~100 ms on Vivo)
        if (!tgx101ModeNormalSet) {
          am.setMode(AudioManager.MODE_NORMAL);
          Log.d(Log.TAG_VOIP, "AudioManager.setMode(AudioManager.MODE_NORMAL) (in onDestroy)");
        }
      } else {
        final int amChangeCounterFinal = amChangeCounter;
        UI.post(() -> {
          if (amChangeCounterFinal == amChangeCounter) {
            try {
              if (disconnectBt != null) {
                disconnectBt.runWithBool(true);
              }
              Log.d(Log.TAG_VOIP, "AudioManager.setMode(AudioManager.MODE_NORMAL) (in onDestroy, delayed)");
              am.setMode(AudioManager.MODE_NORMAL);
            } catch (Throwable ignored) { }
          }
        }, 5000);
      }
    } catch (Throwable ignored) { }
    if (haveAudioFocus) {
      am.abandonAudioFocus(this);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (communicationDeviceChangedListener != null) {
        am.removeOnCommunicationDeviceChangedListener((AudioManager.OnCommunicationDeviceChangedListener) communicationDeviceChangedListener);
      }
    }
    am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
    if (haveAudioFocus) {
      am.abandonAudioFocus(this);
    }
  }

  private void setCall (TdApi.Call call) {
    boolean sameCall = this.call != null && call != null && this.call.id == call.id;
    this.call = call;
    this.callChannelId = sameCall && this.callChannelId != null ? this.callChannelId : this.call != null ? "call_" + this.call.id + "_" + System.currentTimeMillis() : null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
    }
  }

  @Override
  public void onDestroy () {
    this.isDestroyed = true;
    Log.v(Log.TAG_VOIP, "TGCallService.onDestroy");

    setCall(null);
    updateCurrentState();
    try {
      unregisterReceiver(receiver);
    } catch (Throwable t) {
      Log.w(Log.TAG_VOIP, "Cannot unregister receiver", t);
    }

    if (reference != null && reference.get() == this) {
      reference = null;
    }

    super.onDestroy();

    releaseAudioFocus();

    if (!soundPoolMap.isProbablyPlaying()) {
      soundPoolMap.release();
    }

    setCallId(null, 0);
  }

  // Call state updates

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (!isDestroyed) {
      updateCall(call);
    }
  }

  private void updateCall (TdApi.Call call) {
    setCall(call);
    updateCurrentState();
  }

  private boolean sentDebugLog;
  private boolean sentRating;

  private void updateCurrentState () {
    if (call != null && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR) {
      updateStats();
      if (!sentDebugLog && ((TdApi.CallStateDiscarded) call.state).needDebugInformation && !StringUtils.isEmpty(lastDebugLog)) {
        sentDebugLog = true;
        tdlib.send(new TdApi.SendCallDebugInformation(new TdApi.InputCallDiscarded(call.id), lastDebugLog.toString()), tdlib.typedOkHandler());
      }
      if (!sentRating && (((TdApi.CallStateDiscarded) call.state).needRating || BuildConfig.EXPERIMENTAL)) {
        sentRating = true;
        // TODO new feedback pop-up
        startRatingActivity();
      }
    }
    configureDeviceForCall();
    updateCurrentSound();
    showNotification();
    updateStats();
    checkInitiated();
  }

  private void startRatingActivity () {
    if (tdlib != null && call != null) {
      try {
        PendingIntent.getActivity(TGCallService.this, 0, new Intent(TGCallService.this, VoIPFeedbackActivity.class)
          .setAction("RATE_CALL_" + call.id)
          .putExtra("account_id", tdlib.id())
          .putExtra("call_id", call.id)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), Intents.mutabilityFlags(false)).send();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Error starting rate activity", t);
      }
    }
  }

  @Override
  public void onCallStateChanged (int callId, int newState) { }

  private int callBarsCount = -1;

  public int getCallBarsCount () {
    return callBarsCount;
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) {
    if (this.call != null && this.call.id == callId) {
      this.callBarsCount = barsCount;
    }
  }

  private int lastAudioMode;

  private void setAudioMode (int mode) {
    Log.d(Log.TAG_VOIP, "setAudioMode: %s", mode == CallSettings.SPEAKER_MODE_BLUETOOTH ? "SPEAKER_MODE_BLUETOOTH" : mode == CallSettings.SPEAKER_MODE_EARPIECE ? "SPEAKER_MODE_NONE" : mode == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT ? "SPEAKER_MODE_SPEAKER_DEFAULT" : Integer.toString(mode));
    lastAudioMode = mode;
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      android.media.AudioDeviceInfo selectedAudioDevice = null;
      Filter<android.media.AudioDeviceInfo> filter = null;
      switch (mode) {
        case CallSettings.SPEAKER_MODE_EARPIECE: {
          // TGx101: a plugged-in wired headset wins over the earpiece
          boolean wired = false;
          for (android.media.AudioDeviceInfo device : am.getAvailableCommunicationDevices()) {
            int type = device.getType();
            if (type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) {
              wired = true;
              break;
            }
          }
          final boolean preferWired = wired;
          filter = device -> switch (device.getType()) {
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
              !preferWired;
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
              android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
              android.media.AudioDeviceInfo.TYPE_USB_HEADSET ->
              preferWired;
            default ->
              false;
          };
          break;
        }

        case CallSettings.SPEAKER_MODE_BLUETOOTH: {
          filter = device -> switch (device.getType()) {
            case
              android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
              android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
              android.media.AudioDeviceInfo.TYPE_BLE_HEADSET/*,
              android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
              android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST*/ ->
              true;
            default ->
              false;
          };
          break;
        }

        case CallSettings.SPEAKER_MODE_SPEAKER: {
          filter = device -> switch (device.getType()) {
            case
              android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
              android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
              android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ->
              true;
            default ->
              false;
          };
          break;
        }

        case CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT: {
          if (hasEarpiece()) {
            filter = device -> switch (device.getType()) {
              case
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
                android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                true;
              default ->
                false;
            };
          } else {
            filter = device -> switch (device.getType()) {
              case
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET/*,
              android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
              android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST*/ ->
                true;
              default ->
                false;
            };
          }
          break;
        }
        default:
          throw new UnsupportedOperationException();
      }
      if (filter != null) {
        List<android.media.AudioDeviceInfo> devices = am.getAvailableCommunicationDevices();
        for (android.media.AudioDeviceInfo device : devices) {
          if (filter.accept(device)) {
            selectedAudioDevice = device;
            break;
          }
        }
      }
      if (selectedAudioDevice != null) {
        am.setCommunicationDevice(selectedAudioDevice);
      } else {
        am.clearCommunicationDevice();
      }
    } else {
      switch (mode) {
        case CallSettings.SPEAKER_MODE_BLUETOOTH: {
          am.setBluetoothScoOn(true);
          am.setSpeakerphoneOn(false);
          break;
        }
        case CallSettings.SPEAKER_MODE_EARPIECE: {
          am.setBluetoothScoOn(false);
          am.setSpeakerphoneOn(false);
          break;
        }
        case CallSettings.SPEAKER_MODE_SPEAKER: {
          am.setBluetoothScoOn(false);
          am.setSpeakerphoneOn(true);
          break;
        }
        case CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT: {
          if (hasEarpiece()) {
            am.setSpeakerphoneOn(true);
          } else {
            am.setBluetoothScoOn(true);
          }
          break;
        }
      }
    }
  }

  private CallSettings postponedCallSettings;

  @Override
  public void onCallSettingsChanged (int callId, CallSettings settings) {
    this.postponedCallSettings = settings;
    if (tgcalls != null) {
      tgcalls.setMicDisabled(settings != null && settings.isMicMuted());
    }
    setAudioMode(settings != null ? settings.getSpeakerMode() : CallSettings.SPEAKER_MODE_EARPIECE);
    UI.post(this::refreshOngoingNotification); // TGx101: speaker button label
  }

  // Implementation

  private void acceptIncomingCall () {
    if (call != null) {
      tdlib.context().calls().acceptCall(this, tdlib, call.id);
      if (UI.getUiState() != UI.State.RESUMED) {
        bringCallToFront();
      }
    }
  }

  private void declineIncomingCall () {
    if (call != null) {
      tdlib.context().calls().hangUp(tdlib, call.id, false, 0);
    }
  }

  public long getConnectionId () {
    return tgcalls != null ? tgcalls.getConnectionId() : 0;
  }

  private void hangUp () {
    if (call != null) {
      tdlib.context().calls().hangUp(tdlib, call.id, false, getConnectionId());
    }
  }

  @Override
  public void onUiStateChanged (int newState) {
    updateCurrentState();
    boolean isPendingIncoming = call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;
    if (isPendingIncoming) {
      if (newState != UI.State.RESUMED && needShowIncomingNotification) {
        needShowIncomingNotification = false;
        showIncomingNotification();
      } else if (newState == UI.State.RESUMED) {
        needShowIncomingNotification = true;
        cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        U.stopForeground(this, true, TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
        incomingNotification = null;
      }
    }
  }

  // Configuration

  private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;

  private PowerManager.WakeLock proximityWakelock;
  private boolean haveAudioFocus;
  private boolean isConfigured;
  private boolean tgx101ModeNormalSet; // TGx101: the call end already switched the audio mode back

  private void configureDeviceForCall () {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

    if (TD.isActive(call) && !isConfigured) {
      isConfigured = true;

      Log.i(Log.TAG_VOIP, "Configuring device for call...");

      am.setMode(AudioManager.MODE_IN_COMMUNICATION);
      tgx101ModeNormalSet = false;
      amChangeCounter++;
      // TGx101: audio calls start on Bluetooth if connected, otherwise on the earpiece (a wired headset wins over it,
      // see setAudioMode). The loudspeaker is only turned on by the user or by video (Tgx101CallVideo).
      // Posted: we are inside the call update dispatch, and changing the settings notifies the same listener list
      // (a direct call crashed with IllegalStateException on answer). onCallSettingsChanged applies the route.
      final CallSettings startSettings = getCallSettings();
      if (startSettings != null && startSettings.getSpeakerMode() != CallSettings.SPEAKER_MODE_SPEAKER) {
        final int startMode = isBluetoothHeadsetConnected() ? CallSettings.SPEAKER_MODE_BLUETOOTH : CallSettings.SPEAKER_MODE_EARPIECE;
        UI.post(() -> {
          if (isConfigured && startSettings.getSpeakerMode() != CallSettings.SPEAKER_MODE_SPEAKER) {
            lastAudioMode = startMode;
            startSettings.setSpeakerMode(startMode);
          }
        });
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

      } else {
        am.setSpeakerphoneOn(false);
      }
      am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
      updateOutputGainControlState();

      SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
      Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
      try {
        if (proximity != null) {
          proximityWakelock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tgx:voip-proximity");
          sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Error initializing proximity sensor", t);
      }
    } else if (!TD.isActive(call) && isConfigured) {
      isConfigured = false;

      Log.i(Log.TAG_VOIP, "Unconfiguring device from call...");

      am.setMode(AudioManager.MODE_NORMAL);
      tgx101ModeNormalSet = true;
      // Audio focus changed in the onDestroy

      SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
      Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
      if (proximity != null) {
        sm.unregisterListener(this);
      }
      if (proximityWakelock != null && proximityWakelock.isHeld()) {
        proximityWakelock.release();
      }
    }
  }

  @Override
  public void onAudioFocusChange (int focusChange) {
    haveAudioFocus = focusChange == AudioManager.AUDIOFOCUS_GAIN;
    Log.i(Log.TAG_VOIP, "onAudioFocusChange, focusChange: %d, haveAudioFocus: %b", focusChange, haveAudioFocus);
  }

  @Override
  @SuppressWarnings("NewApi")
  public void onSensorChanged (SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (isHeadsetPlugged || am.isSpeakerphoneOn() || (isBtHeadsetConnected && am.isBluetoothScoOn())) {
        return;
      }
      if (org.thunderdog.challegram.voip.Tgx101Video.isCameraEnabled() || org.thunderdog.challegram.voip.Tgx101Video.isRemoteVideoActive()) {
        // TGx101: video is on, the screen must stay on
        if (proximityWakelock != null && proximityWakelock.isHeld()) {
          try {
            proximityWakelock.release(1);
          } catch (Throwable ignored) { }
        }
        isProximityNear = false;
        return;
      }
      boolean newIsNear = event.values[0] < Math.min(event.sensor.getMaximumRange(), 3);
      if (newIsNear != isProximityNear) {
        if (Log.isEnabled(Log.TAG_VOIP)) {
          Log.v(Log.TAG_VOIP, "Proximity state changed, isNear: %b", newIsNear);
        }
        isProximityNear = newIsNear;
        try {
          if (isProximityNear) {
            proximityWakelock.acquire();
          } else {
            proximityWakelock.release(1); // this is non-public API before L
          }
        } catch (Throwable t) {
          Log.e(Log.TAG_VOIP, "Failed to acquire/release proximity wakelock, isNear: %b", t, newIsNear);
        }
      }
    }
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) {

  }

  // Notification

  private Notification ongoingCallNotification;

  private static final @DrawableRes int CALL_ICON_RES = R.drawable.baseline_plumagram_24; // TGx101: the PlumaGram bird

  /** TGx101: the caller for Android 12+ CallStyle notifications (system call card, status bar call chip) */
  @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
  private android.app.Person tgx101Caller (TdApi.User user, @Nullable Bitmap photo) {
    android.app.Person.Builder person = new android.app.Person.Builder()
      .setName(user != null ? TD.getUserName(user) : "")
      .setImportant(true);
    if (photo != null) {
      person.setIcon(android.graphics.drawable.Icon.createWithBitmap(photo));
    }
    return person.build();
  }

  private void showNotification () {
    boolean needNotification = call != null && (call.isOutgoing || call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR || call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR) && !TD.isFinished(call);

    if (needNotification == (ongoingCallNotification != null)) {
      return;
    }

    if (!needNotification) {
      cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
      U.stopForeground(this, true, TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
      incomingNotification = ongoingCallNotification = null;
      return;
    }



    Notification.Builder builder;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      cleanupChannels(m);
      // final String channelId = "call_" + call.id + "_" + System.currentTimeMillis();
      // TGx101: DEFAULT (still silent) so the call stays visible on the lock screen
      android.app.NotificationChannel channel = new android.app.NotificationChannel(callChannelId, Lang.getString(R.string.NotificationChannelOutgoingCall), NotificationManager.IMPORTANCE_HIGH);
      channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
      channel.enableVibration(false);
      channel.enableLights(false);
      channel.setSound(null, null);
      try {
        m.createNotificationChannel(channel);
      } catch (Throwable t) {
        Log.v("Unable to create notification channel for call", new TdlibNotificationChannelGroup.ChannelCreationFailureException(t));
      }
      builder = new Notification.Builder(this, callChannelId);
    } else {
      builder = new Notification.Builder(this);
    }

    builder
      .setContentTitle(Lang.getString(R.string.OutgoingCall))
      .setContentText(TD.getUserName(user))
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
    if (tdlib.context().isMultiUser()) {
      String shortName = tdlib.accountShortName();
      if (shortName != null) {
        builder.setSubText(shortName);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      Intent endIntent = new Intent();
      Intents.secureIntent(endIntent, false);
      endIntent.setAction(Intents.ACTION_END_CALL);
      PendingIntent endPendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, Intents.mutabilityFlags(false));
      // TGx101: loudspeaker button next to "End call"
      Intent speakerIntent = new Intent();
      Intents.secureIntent(speakerIntent, false);
      speakerIntent.setAction(Intents.ACTION_TOGGLE_CALL_SPEAKER);
      PendingIntent speakerPendingIntent = PendingIntent.getBroadcast(this, 1, speakerIntent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false));
      CallSettings speakerSettings = getCallSettings();
      boolean speakerOn = speakerSettings != null && (speakerSettings.getSpeakerMode() == CallSettings.SPEAKER_MODE_SPEAKER || speakerSettings.getSpeakerMode() == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT);
      String speakerTitle = Lang.getString(speakerOn ? R.string.Tgx101CallSpeakerOff : R.string.Tgx101CallSpeakerOn);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // TGx101: Android 12+ system call card — the status bar then shows the call chip with the timer; tap returns to the call
        Bitmap photo = TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true);
        builder.setStyle(Notification.CallStyle.forOngoingCall(tgx101Caller(user, photo), endPendingIntent));
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.baseline_volume_up_24_white), speakerTitle, speakerPendingIntent).build());
        long callDuration = getCallDuration();
        if (callDuration > 0) {
          builder.setUsesChronometer(true);
          builder.setWhen(System.currentTimeMillis() - callDuration);
        } else {
          builder.setContentText(Lang.getString(R.string.OutgoingCall));
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // Own layout: firmwares like Vivo draw CallStyle buttons as plain text without icons
        android.widget.RemoteViews views = new android.widget.RemoteViews(getPackageName(), R.layout.tgx101_call_notification);
        views.setTextViewText(R.id.tgx101_call_name, TD.getUserName(user));
        long callDuration = getCallDuration();
        long base = android.os.SystemClock.elapsedRealtime() - (callDuration > 0 ? callDuration : 0);
        views.setChronometer(R.id.tgx101_call_timer, base, null, callDuration > 0);
        if (callDuration <= 0) {
          views.setTextViewText(R.id.tgx101_call_timer, Lang.getString(R.string.OutgoingCall));
        }
        Bitmap photo = TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true);
        if (photo != null) {
          views.setImageViewBitmap(R.id.tgx101_call_photo, photo);
        }
        views.setInt(R.id.tgx101_call_speaker, "setBackgroundResource", speakerOn ? R.drawable.tgx101_call_button_speaker_on : R.drawable.tgx101_call_button_speaker);
        views.setContentDescription(R.id.tgx101_call_speaker, speakerTitle);
        views.setContentDescription(R.id.tgx101_call_end, Lang.getString(R.string.VoipEndCall));
        views.setOnClickPendingIntent(R.id.tgx101_call_speaker, speakerPendingIntent);
        views.setOnClickPendingIntent(R.id.tgx101_call_end, endPendingIntent);
        builder.setCustomContentView(views);
        builder.setCustomBigContentView(views);
        builder.setCustomHeadsUpContentView(views);
        builder.setStyle(new Notification.DecoratedCustomViewStyle());
      } else {
        builder.addAction(R.drawable.round_call_end_24_white, Lang.getString(R.string.VoipEndCall), endPendingIntent);
        builder.addAction(R.drawable.baseline_volume_up_24_white, speakerTitle, speakerPendingIntent);
      }
      builder.setOnlyAlertOnce(true);
      builder.setPriority(Notification.PRIORITY_MAX);
      builder.setOngoing(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        builder.setCategory(Notification.CATEGORY_CALL);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getCallDuration() > 0); // TGx101: the call timer
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(tdlib.accountColor());
    }
    Bitmap bitmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? null : // TGx101: the own layout / the caller already shows the photo
      TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true);
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      ongoingCallNotification = builder.build();
    } else {
      ongoingCallNotification = builder.getNotification();
    }
    U.startForeground(this, TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION, ongoingCallNotification);
  }

  // Sound

  private boolean isRinging;

  private void setIsRinging (boolean isRinging) {
    if (this.isRinging != isRinging) {
      this.isRinging = isRinging;
      if (isRinging) {
        startRinging();
      } else {
        stopRinging();
      }
    }
  }

  private MediaPlayer ringtonePlayer;
  private Vibrator vibrator;
  private Notification incomingNotification;

  private boolean needShowIncomingNotification;

  private boolean showIncomingNotification () {
    boolean needNotification = call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;

    if (!needNotification && incomingNotification == null) {
      return false;
    }

    needNotification = needNotification && NotificationManagerCompat.from(this).areNotificationsEnabled();

    if (needNotification == (incomingNotification != null)) {
      return needNotification;
    }

    if (UI.getUiState() == UI.State.RESUMED) {
      needShowIncomingNotification = true;
      Log.i("No need to show incoming notification right now, but may in future.");
      return true;
    }

    /*if (!needNotification) {
      stopForeground(true);
      incomingNotification = ongoingCallNotification = null;
      return false;
    }*/

    Log.i("Showing incoming notification");

    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      cleanupChannels(m);
      // final String channelId = "call_" + call.id + "_" + System.currentTimeMillis();
      android.app.NotificationChannel channel = new android.app.NotificationChannel(callChannelId, Lang.getString(R.string.NotificationChannelCall), NotificationManager.IMPORTANCE_HIGH);
      channel.enableVibration(false);
      channel.enableLights(false);
      channel.setSound(null, null);
      try {
        m.createNotificationChannel(channel);
      } catch (Throwable t) {
        Log.v("Unable to create notification channel for call", new TdlibNotificationChannelGroup.ChannelCreationFailureException(t));
      }
      builder = new Notification.Builder(this, callChannelId);
    } else {
      builder = new Notification.Builder(this);
    }

    builder
      .setContentTitle(Lang.getString(R.string.CallBrandingIncoming))
      .setContentText(TD.getUserName(user))
      .setSmallIcon(CALL_ICON_RES)
      .setContentIntent(PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false)));
    if (tdlib.context().isMultiUser()) {
      String shortName = tdlib.accountShortName();
      if (shortName != null) {
        builder.setSubText(shortName);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      Intent endIntent = new Intent();
      Intents.secureIntent(endIntent, false);
      endIntent.setAction(Intents.ACTION_DECLINE_CALL);
      CharSequence endTitle = Lang.getString(R.string.DeclineCall);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        endTitle = new SpannableString(endTitle);
        ((SpannableString) endTitle).setSpan(new ForegroundColorSpan(Theme.getColor(ColorId.circleButtonNegative)), 0, endTitle.length(), 0);
      }
      PendingIntent declinePendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false));
      Intent answerIntent = new Intent();
      Intents.secureIntent(answerIntent, false);
      answerIntent.setAction(Intents.ACTION_ANSWER_CALL);
      PendingIntent answerPendingIntent = PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // TGx101: Android 12+ system incoming call card — shown as a pop-up over an unlocked phone too
        Bitmap photo = user != null ? TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true) : null;
        builder.setStyle(Notification.CallStyle.forIncomingCall(tgx101Caller(user, photo), declinePendingIntent, answerPendingIntent));
      } else {
        builder.addAction(R.drawable.round_call_end_24_white, endTitle, declinePendingIntent);
        CharSequence answerTitle = Lang.getString(R.string.AnswerCall);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          answerTitle = new SpannableString(answerTitle);
          ((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(Theme.getColor(ColorId.circleButtonPositive)), 0, answerTitle.length(), 0);
        }
        builder.addAction(R.drawable.round_call_24_white, answerTitle, answerPendingIntent);
      }
      builder.setPriority(Notification.PRIORITY_MAX);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setColor(tdlib.accountColor());
      builder.setVibrate(new long[0]);
      builder.setCategory(Notification.CATEGORY_CALL);
      // TGx101: only over a locked or dark screen. On an unlocked phone some firmwares (Vivo) launch
      // the full-screen intent right away, so the app came back every time it was minimized.
      if (needFullScreenIntent()) {
        builder.setFullScreenIntent(PendingIntent.getActivity(this, PendingIntent.FLAG_ONE_SHOT, Intents.valueOfCall(), Intents.mutabilityFlags(false)), true);
      }
      builder.setVisibility(Notification.VISIBILITY_PUBLIC);
    }
    Bitmap bitmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? null : user != null ? TdlibNotificationUtils.buildLargeIcon(tdlib, user.profilePhoto != null ? user.profilePhoto.small : null, tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true) : null;
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      incomingNotification = builder.build();
    } else {
      incomingNotification = builder.getNotification();
    }
    U.startForeground(this, TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION, incomingNotification);
    return true;
  }

  private boolean needFullScreenIntent () {
    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
    android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    boolean interactive = pm == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH ? pm.isInteractive() : pm.isScreenOn());
    boolean locked = km != null && km.isKeyguardLocked();
    return !interactive || locked;
  }

  // TGx101: rebuild the ongoing call notification (speaker button label)
  private void refreshOngoingNotification () {
    if (ongoingCallNotification != null) {
      ongoingCallNotification = null;
      showNotification();
    }
  }

  // Loudness is perceived logarithmically: the ramp goes evenly in decibels (-26 dB ≈ 20 % of the perceived volume → 0 dB)
  private static final float RING_RAMP_START_DB = -26f;
  private static final float RING_RAMP_START = (float) Math.pow(10, RING_RAMP_START_DB / 20f);
  private static final long RING_RAMP_DURATION_MS = 30_000, RING_RAMP_STEP_MS = 250;

  private void startRingRamp (MediaPlayer player) {
    final long start = android.os.SystemClock.uptimeMillis();
    UI.post(new Runnable() {
      @Override
      public void run () {
        if (ringtonePlayer != player) {
          return; // stopped or restarted
        }
        float progress = Math.min(1f, (android.os.SystemClock.uptimeMillis() - start) / (float) RING_RAMP_DURATION_MS);
        float volume = (float) Math.pow(10, RING_RAMP_START_DB * (1f - progress) / 20f);
        try {
          player.setVolume(volume, volume);
        } catch (Throwable ignored) {
          return;
        }
        if (progress < 1f) {
          UI.post(this, RING_RAMP_STEP_MS);
        }
      }
    });
  }

  private void startRinging () {
    Log.i(Log.TAG_VOIP, "startRinging");
    TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_TELEGRAM_CALL);
    ringtonePlayer = new MediaPlayer();
    final boolean ramp = Settings.instance().isRingRampEnabled(); // TGx101: 20 % → 100 % over 20 s
    if (ramp) {
      ringtonePlayer.setVolume(RING_RAMP_START, RING_RAMP_START);
    }
    ringtonePlayer.setOnPreparedListener(mediaPlayer -> {
      ringtonePlayer.start();
      if (ramp) {
        startRingRamp(mediaPlayer);
      }
    });
    ringtonePlayer.setLooping(true);
    ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
    try {
      String notificationUri = tdlib.notifications().getCallRingtone(ChatId.fromUserId(user.id));
      ringtonePlayer.setDataSource(this, Uri.parse(notificationUri));
      ringtonePlayer.prepareAsync();
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Failed to start ringing", t);
      if (ringtonePlayer != null) {
        ringtonePlayer.release();
        ringtonePlayer = null;
      }
    }

    int vibrateMode = tdlib.notifications().getCallVibrateMode(ChatId.fromUserId(user.id));
    if (vibrateMode != TdlibNotificationManager.VIBRATE_MODE_DISABLED) {
      vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      if (vibrator != null) {
        switch (vibrateMode) {
          case TdlibNotificationManager.VIBRATE_MODE_SHORT:
            vibrator.vibrate(TdlibNotificationManager.VIBRATE_CALL_SHORT_PATTERN, 0);
            break;
          case TdlibNotificationManager.VIBRATE_MODE_LONG:
            vibrator.vibrate(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN, 0);
            break;
          default:
            // TGx101: like the official app, vibrate unless the phone is on silent; many firmwares
            // (Vivo) keep the system "vibrate when ringing" switch at 0 while vibrating on calls
            AudioManager ringerAudio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if ((ringerAudio != null && ringerAudio.getRingerMode() != AudioManager.RINGER_MODE_SILENT) || tdlib.notifications().needVibrateWhenRinging()) {
              vibrator.vibrate(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN, 0);
            }
            break;
        }
      }
    }

    if (!showIncomingNotification()) {
      Log.v(Log.TAG_VOIP, "Starting incall activity for incoming call");
      if (UI.getUiState() != UI.State.RESUMED) {
        bringCallToFront();
      }
    }
  }

  private void bringCallToFront () {
    /*try {
      PendingIntent.getActivity(UI.getContext(), 0, Intents.valueOfCall(), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT).send();
    } catch (Exception x) {
      Log.e(Log.TAG_VOIP, "Error starting incall activity", x);
    }*/
  }

  private void cleanupChannels (NotificationManager m) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m != null) {
      List<android.app.NotificationChannel> channels = m.getNotificationChannels();
      for (android.app.NotificationChannel channel : channels) {
        String id = channel.getId();
        if (id.startsWith("call_") && !(callChannelId != null && callChannelId.equals(id))) {
          try {
            m.deleteNotificationChannel(channel.getId());
          } catch (Throwable t) {
            Log.e("Unable to delete notification channel", t);
          }
        }
      }
    }
  }

  /** TGx101: «Без звука» on the incoming call screen — mutes our ringtone and vibration, the call keeps ringing for the caller */
  public void silenceRinging () {
    if (ringtonePlayer != null) {
      try {
        ringtonePlayer.stop();
        ringtonePlayer.release();
      } catch (Throwable ignored) { }
      ringtonePlayer = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }
  }

  private void stopRinging () {
    cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
    U.stopForeground(this, true, TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
    incomingNotification = ongoingCallNotification = null;
    if (ringtonePlayer != null) {
      ringtonePlayer.stop();
      ringtonePlayer.release();
      ringtonePlayer = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }
  }

  private void updateCurrentSound () {
    int callSound;

    if (call != null && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing) {
      callSound = 0;
      setIsRinging(true);
    } else {
      callSound = call != null ? TD.getCallStateSound(call) : 0;
      setIsRinging(false);
    }
    if (callSound != 0) {
      if (callSound == R.raw.voip_end || callSound == R.raw.voip_fail) {
        soundPoolMap.playUnique(callSound, 1, 1, 0, 0, 1);
      } else if (callSound == R.raw.voip_busy) {
        soundPoolMap.playUnique(callSound, 1, 1, 0, 2, 1);
      } else {
        soundPoolMap.playUnique(callSound, 1, 1, 0, call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR ? 0 : -1, 1);
      }
    } else {
      soundPoolMap.stopLastSound();
    }
  }

  // Audio

  public void onMediaButtonEvent (KeyEvent event) {
    // TODO
  }

  public boolean isBluetoothHeadsetConnected () {
    return isBtHeadsetConnected;
  }

  private Boolean mHasEarpiece = null;

  public boolean hasEarpiece () {
    if (((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getPhoneType() != TelephonyManager.PHONE_TYPE_NONE)
      return true;
    if (mHasEarpiece != null) {
      return mHasEarpiece;
    }

    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      for (android.media.AudioDeviceInfo deviceInfo : am.getAvailableCommunicationDevices()) {
        if (deviceInfo.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
          mHasEarpiece = true;
          return true;
        }
      }
    }

    // not calculated yet, do it now
    try {
      Method method = AudioManager.class.getMethod("getDevicesForStream", Integer.TYPE);
      Field field = AudioManager.class.getField("DEVICE_OUT_EARPIECE");
      int earpieceFlag = field.getInt(null);
      int bitmaskResult = (int) method.invoke(am, AudioManager.STREAM_VOICE_CALL);

      // check if masked by the earpiece flag
      if ((bitmaskResult & earpieceFlag) == earpieceFlag) {
        mHasEarpiece = Boolean.TRUE;
      } else {
        mHasEarpiece = Boolean.FALSE;
      }
    } catch (Throwable error) {
      Log.e(Log.TAG_VOIP, "Error while checking earpiece! ", error);
      mHasEarpiece = Boolean.TRUE;
    }

    return mHasEarpiece;
  }

  private boolean isBtHeadsetConnected;

  private void updateBluetoothHeadsetState (boolean isConnected) {
    if (this.isBtHeadsetConnected != isConnected) {
      this.isBtHeadsetConnected = isConnected;
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        onTgx101BluetoothChanged(isConnected);
      } else {
        if (isConnected) {
          Log.d(Log.TAG_VOIP, "AudioManager.startBluetoothSco()");
          am.startBluetoothSco();
        } else {
          Log.d(Log.TAG_VOIP, "AudioManager.stopBluetoothSco()");
          am.stopBluetoothSco();
        }
      }
      notifyAudioSettingsChanged();
    }
  }

  // TGx101: routing follows headsets connected or removed during the call, like the phone's own dialer

  private void onTgx101HeadsetChanged (boolean plugged) {
    UI.post(() -> applyTgx101HeadsetChanged(plugged));
  }

  private void applyTgx101HeadsetChanged (boolean plugged) {
    CallSettings settings = isConfigured ? getCallSettings() : null;
    if (settings == null) return;
    int mode = settings.getSpeakerMode();
    if (plugged && (mode == CallSettings.SPEAKER_MODE_SPEAKER || mode == CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT)) {
      settings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE); // → the wired headset
    } else if (mode == CallSettings.SPEAKER_MODE_EARPIECE) {
      setAudioMode(CallSettings.SPEAKER_MODE_EARPIECE); // re-pick: headset when plugged, earpiece when removed
    }
  }

  private void onTgx101BluetoothChanged (boolean connected) {
    CallSettings settings = isConfigured ? getCallSettings() : null;
    if (settings == null) return;
    int mode = settings.getSpeakerMode();
    if (connected && mode != CallSettings.SPEAKER_MODE_BLUETOOTH) {
      // the headset shows up among communication devices a moment after it connects
      UI.post(() -> {
        if (isConfigured && isBtHeadsetConnected && settings.getSpeakerMode() != CallSettings.SPEAKER_MODE_BLUETOOTH) {
          settings.setSpeakerMode(CallSettings.SPEAKER_MODE_BLUETOOTH);
        }
      }, 1000);
    } else if (!connected && mode == CallSettings.SPEAKER_MODE_BLUETOOTH) {
      settings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
    }
  }

  private CallSettings getCallSettings () {
    if (call != null) {
      CallSettings settings = tdlib.cache().getCallSettings(call.id);
      if (settings == null) {
        settings = new CallSettings(tdlib, call.id);
      }
      return settings;
    }
    return null;
  }

  private void notifyAudioSettingsChanged () {
    Log.d(Log.TAG_VOIP, "notifyAudioSettingsChanged");

    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    int mode;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      List<android.media.AudioDeviceInfo> audioDevices = am.getAvailableCommunicationDevices();
      isBtHeadsetConnected = false;
      for (android.media.AudioDeviceInfo audioDevice : audioDevices) {
        switch (audioDevice.getType()) {
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
          case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET:
            isBtHeadsetConnected = true;
            break;
        }
      }
      android.media.AudioDeviceInfo deviceInfo = am.getCommunicationDevice();
      mode = CallSettings.SPEAKER_MODE_EARPIECE;
      if (deviceInfo != null) {
        switch (deviceInfo.getType()) {
          case android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER:
          case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
          case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE: {
            mode = CallSettings.SPEAKER_MODE_SPEAKER;
            isHeadsetPlugged = false;
            break;
          }
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
          case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET: {
            mode = CallSettings.SPEAKER_MODE_BLUETOOTH;
            isHeadsetPlugged = true;
            break;
          }
          case android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: {
            mode = CallSettings.SPEAKER_MODE_EARPIECE;
            isHeadsetPlugged = false;
            break;
          }
        }
      }
    } else {
      mode = isBluetoothHeadsetConnected() && am.isBluetoothScoOn() ? CallSettings.SPEAKER_MODE_BLUETOOTH : am.isSpeakerphoneOn() ? CallSettings.SPEAKER_MODE_SPEAKER : CallSettings.SPEAKER_MODE_EARPIECE;
    }
    if (!isConfigured) {
      // TGx101: before the call audio mode is set, the system reports the media route (often the loudspeaker);
      // adopting it turned audio calls on speaker. The call picks its own route in configureDeviceForCall.
      return;
    }
    if (this.lastAudioMode != mode) {
      CallSettings settings = getCallSettings();
      if (settings != null) {
        this.lastAudioMode = mode;
        settings.setSpeakerMode(mode);
      }
    }
  }

  // Network type

  private NetworkInfo lastNetInfo;
  private @CallNetworkType int lastNetworkType = CallNetworkType.UNKNOWN;

  private void updateNetworkType (boolean dispatchToTgCalls) {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    NetworkInfo info = cm.getActiveNetworkInfo();
    lastNetInfo = info;
    @CallNetworkType int type = lastNetworkType;
    if (info != null) {
      switch (info.getType()) {
        case ConnectivityManager.TYPE_MOBILE:
          switch (info.getSubtype()) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
              type = CallNetworkType.MOBILE_GPRS;
              break;
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
              type = CallNetworkType.MOBILE_EDGE;
              break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
              type = CallNetworkType.MOBILE_3G;
              break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
              type = CallNetworkType.MOBILE_HSPA;
              break;
            case TelephonyManager.NETWORK_TYPE_LTE:
              type = CallNetworkType.MOBILE_LTE;
              break;
            default:
              type = CallNetworkType.OTHER_MOBILE;
              break;
          }
          break;
        case ConnectivityManager.TYPE_WIFI:
          type = CallNetworkType.WIFI;
          break;
        case ConnectivityManager.TYPE_ETHERNET:
          type = CallNetworkType.ETHERNET;
          break;
      }
    }
    this.lastNetworkType = type;
    if (dispatchToTgCalls && tgcalls != null) {
      tgcalls.setNetworkType(type);
    }
  }

  private NetworkStats stats = new NetworkStats(), prevStats = new NetworkStats();
  private long prevDuration;

  private void updateStats () {
    if (tgcalls == null) {
      return;
    }
    tgcalls.getNetworkStats(stats);

    long newDuration = getCallDuration();
    if (newDuration == VoIPInstance.DURATION_UNKNOWN) {
      newDuration = 0;
    }

    long wifiSentDiff = stats.bytesSentWifi - prevStats.bytesSentWifi;
    long wifiReceivedDiff = stats.bytesRecvdWifi - prevStats.bytesRecvdWifi;
    long mobileSentDiff = stats.bytesSentMobile - prevStats.bytesSentMobile;
    long mobileReceivedDiff = stats.bytesRecvdMobile - prevStats.bytesRecvdMobile;
    double durationDiff = (double) Math.max(0, newDuration - prevDuration) / 1000d;

    NetworkStats tmp = stats;
    stats = prevStats;
    prevStats = tmp;
    prevDuration = newDuration;

    if (wifiSentDiff > 0 || wifiReceivedDiff > 0 || durationDiff > 0) {
      tdlib.send(new TdApi.AddNetworkStatistics(new TdApi.NetworkStatisticsEntryCall(new TdApi.NetworkTypeWiFi(), wifiSentDiff, wifiReceivedDiff, durationDiff)), tdlib.typedOkHandler());
    }

    if (mobileSentDiff > 0 || mobileReceivedDiff > 0 || durationDiff > 0) {
      TdApi.NetworkType type = lastNetInfo != null && lastNetInfo.isRoaming() ? new TdApi.NetworkTypeMobileRoaming() : new TdApi.NetworkTypeMobile();
      tdlib.send(new TdApi.AddNetworkStatistics(new TdApi.NetworkStatisticsEntryCall(type, mobileSentDiff, mobileReceivedDiff, durationDiff)), tdlib.typedOkHandler());
    }
  }

  // VoIP

  private CharSequence lastDebugLog;

  private void releaseTgCalls (@Nullable Tdlib tdlib, @Nullable TdApi.Call call) {
    if (tgcalls != null) {
      if (call == null) {
        call = tgcalls.getCall();
      }
      if (tdlib == null) {
        tdlib = tgcalls.tdlib();
      }
      lastDebugLog = tgcalls.collectDebugLog();
      tgcalls.performDestroy();
      tgcalls = null;
    }
    if (callListener != null && tdlib != null && call != null) {
      tdlib.listeners().unsubscribeFromCallUpdates(call.id, callListener);
      callListener = null;
    }
    callInitialized = false; // FIXME?
  }

  private boolean isInitiated () {
    return tgcalls != null;
  }

  private void checkInitiated () {
    if (isInitiated() || TD.isFinished(call)) {
      if (TD.isFinished(call)) {
        releaseTgCalls(tdlib, call);
        cleanupChannels((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        U.stopForeground(this, true, TdlibNotificationManager.ID_FOREGROUND_ONGOING_CALL_NOTIFICATION, TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
        incomingNotification = ongoingCallNotification = null;
        stopSelf();
      }
      return;
    }

    if (call == null || call.state.getConstructor() != TdApi.CallStateReady.CONSTRUCTOR || !callInitialized || tdlib == null) {
      return;
    }

    updateOutputGainControlState();
    updateNetworkType(false);
    Socks5Proxy callProxy = null;
    int proxyId = Settings.instance().getEffectiveCallsProxyId();
    if (proxyId != Settings.PROXY_ID_NONE) {
      Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
      if (proxy != null && proxy.proxy != null && proxy.canUseForCalls()) {
        callProxy = new Socks5Proxy(proxy.proxy);
      }
    }
    boolean isMicDisabled = postponedCallSettings != null && postponedCallSettings.isMicMuted();
    boolean forceTcp = Settings.instance().forceTcpInCalls();

    Tdlib tdlib = this.tdlib;
    TdApi.Call call = this.call;
    TdApi.CallStateReady state = (TdApi.CallStateReady) call.state;

    ConnectionStateListener stateListener = new ConnectionStateListener() {
      private boolean vibratedOnConnect; // TGx101
      private int lastLoggedSignalBars = -1; // TGx101: diagnostics log only on change

      @Override
      public void onConnectionStateChanged (VoIPInstance context, @CallState int newState) {
        org.thunderdog.challegram.Tgx101Diag.mark("[call] connection state " + newState + " (" + org.thunderdog.challegram.Tgx101Diag.network() + ")");
        if (newState == CallState.ESTABLISHED) {
          if (!vibratedOnConnect) { // TGx101: a short vibration when the call connects, as in the official app
            vibratedOnConnect = true;
            vibrateOnConnect();
          }
          tdlib.dispatchCallStateChanged(call.id, newState);
          UI.post(TGCallService.this::refreshOngoingNotification); // TGx101: start the timer in the notification
        } else if (newState == CallState.FAILED) {
          long connectionId = context.getConnectionId();
          tdlib.context().calls().hangUp(tdlib, call.id, true, connectionId);
        }
      }

      @Override
      public void onSignalBarCountChanged (int newCount) {
        if (newCount != lastLoggedSignalBars) {
          lastLoggedSignalBars = newCount;
          org.thunderdog.challegram.Tgx101Diag.mark("[call] signal bars " + newCount + " (" + org.thunderdog.challegram.Tgx101Diag.network() + ")");
        }
        tdlib.dispatchCallBarsCount(call.id, newCount);
      }

      @Override
      public void onSignallingDataEmitted (byte[] data) {
        tdlib.client().send(new TdApi.SendCallSignalingData(call.id, data), tdlib.silentHandler());
      }
    };

    VoIPInstance tgcallsTemp;
    try {
      tgcallsTemp = VoIP.instantiateAndConnect(
        tdlib,
        call,
        state,
        stateListener,
        forceTcp,
        callProxy,
        lastNetworkType,
        audioGainControlEnabled,
        echoCancellationStrength,
        isMicDisabled
      );
    } catch (Throwable t) {
      tgcallsTemp = null;
    }
    final VoIPInstance tgcalls = tgcallsTemp;

    if (tgcalls != null) {
      this.callListener = new PrivateCallListener() {
        @Override
        public void onNewCallSignalingDataArrived (int callId, byte[] data) {
          tgcalls.handleIncomingSignalingData(data);
        }
      };
      tdlib.listeners().subscribeToCallUpdates(call.id, callListener);
      this.tgcalls = tgcalls;
    } else {
      hangUp();
    }
  }

  // Destroy

  public void processBroadcast (Context context, Intent intent) {
    receiver.onReceive(context, intent);
  }

  public static TGCallService currentInstance () {
    return reference != null ? reference.get() : null;
  }

  private boolean logViewed;

  public static void markLogViewed () {
    TGCallService service = currentInstance();
    if (service != null) {
      service.logViewed = true;
    }
  }

  @NonNull
  public CharSequence getLibraryNameAndVersion () {
    return tgcalls != null ?
      tgcalls.getLibraryName() + " " + tgcalls.getLibraryVersion() :
      "unknown";
  }

  @NonNull
  public CharSequence getDebugString () {
    if (tgcalls != null) {
      CharSequence log = tgcalls.collectDebugLog();
      if (log != null) {
        return log;
      }
    }
    return "";
  }

  // Without this permission Android shows an incoming call as a plain notification
  // instead of the full-screen call screen. On Android 14+ the user may have to allow
  // it manually; ask once. Returns true if the settings screen was opened.
  public static boolean requestFullScreenIntentOnce (android.app.Activity activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return false;
    }
    NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null || manager.canUseFullScreenIntent()) {
      return false;
    }
    android.content.SharedPreferences prefs = activity.getSharedPreferences("calls", Context.MODE_PRIVATE);
    if (prefs.getBoolean("full_screen_intent_asked", false)) {
      return false;
    }
    prefs.edit().putBoolean("full_screen_intent_asked", true).apply();
    try {
      Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
      intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
      activity.startActivity(intent);
      return true;
    } catch (Throwable t) {
      Log.e("Unable to open full-screen intent settings", t);
      return false;
    }
  }

  // TGx101: short vibration when a call connects; skipped in silent mode
  private void vibrateOnConnect () {
    try {
      android.media.AudioManager audio = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
      if (audio != null && audio.getRingerMode() == android.media.AudioManager.RINGER_MODE_SILENT) return;
      android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator == null || !vibrator.hasVibrator()) return;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
      } else {
        vibrator.vibrate(100);
      }
    } catch (Throwable ignored) { }
  }
}
