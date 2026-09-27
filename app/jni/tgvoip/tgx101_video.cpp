/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

// TGx101: video in one-to-one calls. Java side: org.thunderdog.challegram.voip.Tgx101Video,
// camera: org.telegram.messenger.voip.VideoCameraCapturer (the class name tgcalls expects).

#ifndef DISABLE_TGCALLS

#include <jni.h>
#include <map>
#include <memory>
#include <mutex>

#include <sdk/android/native_api/video/wrapper.h>
#include <tgcalls/Instance.h>
#include <tgcalls/StaticThreads.h>
#include <tgcalls/VideoCaptureInterface.h>
#include <platform/android/AndroidContext.h>
#include <platform/android/VideoCameraCapturer.h>

namespace tgcalls {
  bool initialize (JNIEnv *env); // tgvoip.cpp
}
tgcalls::Instance *tgx101_instance (jlong ptr); // tgvoip.cpp

namespace {

struct CaptureHolder {
  std::shared_ptr<tgcalls::VideoCaptureInterface> capture;
  std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> output;
};

std::mutex remoteSinksMutex;
std::map<jlong, std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> remoteSinks;

CaptureHolder *holder (jlong ptr) {
  return reinterpret_cast<CaptureHolder *>(ptr);
}

}

#define TGX101_VIDEO(RETURN_TYPE, NAME, ...) \
  extern "C" JNIEXPORT RETURN_TYPE JNICALL Java_org_thunderdog_challegram_voip_Tgx101Video_##NAME(JNIEnv *env, jclass clazz, ##__VA_ARGS__)

// tgcalls defines VideoCameraCapturer.nativeGetJavaVideoCapturerObserver inside its static library,
// and --exclude-libs,ALL hides it from the JNI lookup, so the camera class's native method is bound here.
static jobject getJavaVideoCapturerObserver (JNIEnv *env, jclass clazz, jlong ptr) {
  auto *capturer = reinterpret_cast<tgcalls::VideoCameraCapturer *>((intptr_t) ptr);
  return capturer != nullptr ? capturer->GetJavaVideoCapturerObserver(env).Release() : nullptr;
}

static bool registerCameraNatives (JNIEnv *env) {
  static bool registered = false;
  if (registered) {
    return true;
  }
  jclass cameraClass = env->FindClass("org/telegram/messenger/voip/VideoCameraCapturer");
  if (cameraClass == nullptr) {
    env->ExceptionClear();
    return false;
  }
  JNINativeMethod methods[] = {
    {"nativeGetJavaVideoCapturerObserver", "(J)Lorg/webrtc/CapturerObserver;", (void *) getJavaVideoCapturerObserver}
  };
  registered = env->RegisterNatives(cameraClass, methods, 1) == JNI_OK;
  env->DeleteLocalRef(cameraClass);
  if (!registered) {
    env->ExceptionClear();
  }
  return registered;
}

TGX101_VIDEO(jlong, nativeCreateCapture, jboolean front) {
  if (!tgcalls::initialize(env) || !registerCameraNatives(env)) {
    return 0;
  }
  auto platformContext = std::make_shared<tgcalls::AndroidContext>(env);
  std::shared_ptr<tgcalls::VideoCaptureInterface> capture = tgcalls::VideoCaptureInterface::Create(
    tgcalls::StaticThreads::getThreads(), front == JNI_TRUE ? "front" : "back", false, platformContext);
  if (capture == nullptr) {
    return 0;
  }
  auto *result = new CaptureHolder;
  result->capture = capture;
  return reinterpret_cast<jlong>(result);
}

TGX101_VIDEO(void, nativeSetCaptureOutput, jlong ptr, jobject sink) {
  CaptureHolder *h = holder(ptr);
  if (h == nullptr) return;
  h->output = sink != nullptr ? std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>(webrtc::JavaToNativeVideoSink(env, sink).release()) : nullptr;
  h->capture->setOutput(h->output);
}

TGX101_VIDEO(void, nativeSetCaptureActive, jlong ptr, jboolean active) {
  CaptureHolder *h = holder(ptr);
  if (h != nullptr) {
    h->capture->setState(active == JNI_TRUE ? tgcalls::VideoState::Active : tgcalls::VideoState::Inactive);
  }
}

TGX101_VIDEO(void, nativeSwitchCamera, jlong ptr, jboolean front) {
  CaptureHolder *h = holder(ptr);
  if (h != nullptr) {
    h->capture->switchToDevice(front == JNI_TRUE ? "front" : "back", false);
  }
}

TGX101_VIDEO(void, nativeDestroyCapture, jlong ptr) {
  CaptureHolder *h = holder(ptr);
  if (h == nullptr) return;
  h->capture->setOutput(nullptr);
  h->capture->setState(tgcalls::VideoState::Inactive);
  delete h;
}

TGX101_VIDEO(void, nativeAttachCapture, jlong instancePtr, jlong capturePtr) {
  tgcalls::Instance *instance = tgx101_instance(instancePtr);
  if (instance == nullptr) return;
  CaptureHolder *h = holder(capturePtr);
  instance->setVideoCapture(h != nullptr ? h->capture : nullptr);
}

TGX101_VIDEO(void, nativeSetRemoteSink, jlong instancePtr, jobject sink) {
  tgcalls::Instance *instance = tgx101_instance(instancePtr);
  if (instance == nullptr) return;
  std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> native;
  if (sink != nullptr) {
    native = std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>(webrtc::JavaToNativeVideoSink(env, sink).release());
  }
  {
    std::lock_guard<std::mutex> lock(remoteSinksMutex);
    if (native != nullptr) {
      remoteSinks[instancePtr] = native;
    } else {
      remoteSinks.erase(instancePtr);
    }
  }
  instance->setIncomingVideoOutput(native);
}

TGX101_VIDEO(void, nativeReleaseRemoteSink, jlong instancePtr) {
  std::lock_guard<std::mutex> lock(remoteSinksMutex);
  remoteSinks.erase(instancePtr);
}

#endif
