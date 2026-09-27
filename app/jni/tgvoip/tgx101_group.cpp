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

// TGx101: voice chats (group calls), audio only, RTC mode.
// Java side: org.thunderdog.challegram.voip.Tgx101GroupCall

#ifndef DISABLE_TGCALLS

#include <jni.h>
#include <jni_utils.h>
#include <memory>
#include <mutex>
#include <vector>

#include "bridge.h"

#include <sdk/android/native_api/jni/jvm.h>
#include <tgcalls/StaticThreads.h>
#include <tgcalls/group/GroupInstanceCustomImpl.h>

namespace tgcalls {
  bool initialize (JNIEnv *env); // tgvoip.cpp
}

namespace {

struct GroupJava {
  std::mutex mutex;
  jobject thiz = nullptr;
  jclass clazz = nullptr;

  GroupJava (JNIEnv *env, jobject obj) {
    thiz = env->NewGlobalRef(obj);
    clazz = (jclass) env->NewGlobalRef(env->GetObjectClass(obj));
  }

  void run (const std::function<void(JNIEnv *, jobject, jclass)> &act) {
    std::lock_guard<std::mutex> lock(mutex);
    if (thiz == nullptr) {
      return;
    }
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    act(env, thiz, clazz);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
    }
  }

  void release (JNIEnv *env) {
    std::lock_guard<std::mutex> lock(mutex);
    if (thiz != nullptr) {
      env->DeleteGlobalRef(thiz);
      env->DeleteGlobalRef(clazz);
      thiz = nullptr;
      clazz = nullptr;
    }
  }
};

// Audio-only: every requested SSRC is an audio channel of a participant.
class ImmediateDescriptionTask final : public tgcalls::RequestMediaChannelDescriptionTask {
public:
  void cancel () override { }
};

struct GroupContext {
  std::unique_ptr<tgcalls::GroupInstanceCustomImpl> instance;
  std::shared_ptr<GroupJava> java;
};

}

JNI_OBJECT_FUNC(jlong, voip_Tgx101GroupCall, nativeCreate, jstring jLogPath, jboolean jMuted) {
  if (!tgcalls::initialize(env)) {
    return 0;
  }
  auto java = std::make_shared<GroupJava>(env, thiz);

  tgcalls::GroupInstanceDescriptor descriptor;
  descriptor.threads = tgcalls::StaticThreads::getThreads();
  descriptor.config.need_log = false;
  descriptor.config.logPath = {jni::from_jstring(env, jLogPath)};
  descriptor.networkStateUpdated = [java](tgcalls::GroupNetworkState state) {
    java->run([state](JNIEnv *env, jobject obj, jclass clazz) {
      env->CallVoidMethod(obj, env->GetMethodID(clazz, "onNativeNetworkState", "(Z)V"), (jboolean) state.isConnected);
    });
  };
  descriptor.audioLevelsUpdated = [java](tgcalls::GroupLevelsUpdate const &update) {
    std::vector<jint> ssrcs;
    std::vector<jfloat> levels;
    std::vector<jboolean> voice;
    for (const auto &item : update.updates) {
      ssrcs.push_back((jint) item.ssrc);
      levels.push_back(item.value.isMuted ? 0.0f : item.value.level);
      voice.push_back((jboolean) (!item.value.isMuted && item.value.voice));
    }
    java->run([ssrcs, levels, voice](JNIEnv *env, jobject obj, jclass clazz) {
      auto size = (jsize) ssrcs.size();
      jintArray jSsrcs = env->NewIntArray(size);
      jfloatArray jLevels = env->NewFloatArray(size);
      jbooleanArray jVoice = env->NewBooleanArray(size);
      env->SetIntArrayRegion(jSsrcs, 0, size, ssrcs.data());
      env->SetFloatArrayRegion(jLevels, 0, size, levels.data());
      env->SetBooleanArrayRegion(jVoice, 0, size, voice.data());
      env->CallVoidMethod(obj, env->GetMethodID(clazz, "onNativeAudioLevels", "([I[F[Z)V"), jSsrcs, jLevels, jVoice);
      env->DeleteLocalRef(jSsrcs);
      env->DeleteLocalRef(jLevels);
      env->DeleteLocalRef(jVoice);
    });
  };
  descriptor.requestMediaChannelDescriptions = [](std::vector<uint32_t> const &ssrcs, std::function<void(std::vector<tgcalls::MediaChannelDescription> &&)> callback) -> std::shared_ptr<tgcalls::RequestMediaChannelDescriptionTask> {
    std::vector<tgcalls::MediaChannelDescription> descriptions;
    for (uint32_t ssrc : ssrcs) {
      tgcalls::MediaChannelDescription description;
      description.type = tgcalls::MediaChannelDescription::Type::Audio;
      description.audioSsrc = ssrc;
      descriptions.push_back(description);
    }
    callback(std::move(descriptions));
    return std::make_shared<ImmediateDescriptionTask>();
  };
  descriptor.initialEnableNoiseSuppression = true;

  auto *context = new GroupContext;
  context->java = java;
  context->instance = std::make_unique<tgcalls::GroupInstanceCustomImpl>(std::move(descriptor));
  context->instance->setIsMuted(jMuted == JNI_TRUE);
  return jni::ptr_to_jlong(context);
}

JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeEmitJoinPayload, jlong ptr) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context == nullptr || context->instance == nullptr) {
    return;
  }
  auto java = context->java;
  context->instance->setConnectionMode(tgcalls::GroupConnectionMode::GroupConnectionModeNone, false, false);
  context->instance->emitJoinPayload([java](tgcalls::GroupJoinPayload const &payload) {
    std::string json = payload.json;
    auto ssrc = (jint) payload.audioSsrc;
    java->run([json, ssrc](JNIEnv *env, jobject obj, jclass clazz) {
      jstring jJson = env->NewStringUTF(json.c_str());
      env->CallVoidMethod(obj, env->GetMethodID(clazz, "onNativeJoinPayload", "(Ljava/lang/String;I)V"), jJson, ssrc);
      env->DeleteLocalRef(jJson);
    });
  });
}

JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeSetJoinResponse, jlong ptr, jstring jPayload) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context == nullptr || context->instance == nullptr) {
    return;
  }
  context->instance->setConnectionMode(tgcalls::GroupConnectionMode::GroupConnectionModeRtc, true, false);
  context->instance->setJoinResponsePayload(jni::from_jstring(env, jPayload));
}

JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeSetMuted, jlong ptr, jboolean jMuted) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context != nullptr && context->instance != nullptr) {
    context->instance->setIsMuted(jMuted == JNI_TRUE);
  }
}

JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeSetVolume, jlong ptr, jint jSsrc, jdouble jVolume) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context != nullptr && context->instance != nullptr) {
    context->instance->setVolume((uint32_t) jSsrc, (double) jVolume);
  }
}

JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeDestroy, jlong ptr) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context == nullptr) {
    return;
  }
  context->java->release(env);
  if (context->instance == nullptr) {
    delete context;
    return;
  }
  // Same order as the official app: stop, then destroy right away (not from the stop callback)
  context->instance->stop(nullptr);
  context->instance.reset();
  delete context;
}

#endif
