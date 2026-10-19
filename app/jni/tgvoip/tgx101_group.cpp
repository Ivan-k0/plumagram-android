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

// TGx101: voice chats (group calls), audio only, RTC mode.
// Java side: org.thunderdog.challegram.voip.Tgx101GroupCall

#ifndef DISABLE_TGCALLS

#include <jni.h>
#include <jni_utils.h>
#include <memory>
#include <mutex>
#include <vector>
#include <map>
#include <string>
#include <sstream>

#include "bridge.h"

#include <sdk/android/native_api/jni/jvm.h>
#include <rtc_base/time_utils.h>
#include <tgcalls/StaticThreads.h>
#include <tgcalls/group/GroupInstanceCustomImpl.h>
#include <tgcalls/platform/PlatformInterface.h>
#include <sdk/android/native_api/audio_device_module/audio_device_android.h>
#include <sdk/android/native_api/video/wrapper.h>

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

  // TGx101 (review): the lock only guards taking local references — the Java call itself (a conference's encrypt /
  // decrypt waits for TDLib) runs unlocked, so frames, audio levels and the UI thread don't queue behind each other
  void run (const std::function<void(JNIEnv *, jobject, jclass)> &act) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    jobject obj;
    jclass cls;
    {
      std::lock_guard<std::mutex> lock(mutex);
      if (thiz == nullptr) {
        return;
      }
      obj = env->NewLocalRef(thiz);
      cls = (jclass) env->NewLocalRef(clazz);
    }
    if (obj == nullptr || cls == nullptr) {
      if (obj != nullptr) env->DeleteLocalRef(obj);
      if (cls != nullptr) env->DeleteLocalRef(cls);
      return;
    }
    act(env, obj, cls);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
    }
    env->DeleteLocalRef(obj);
    env->DeleteLocalRef(cls);
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

// The Android audio device aborts (RTC_CHECK_NOTREACHED) on questions the group module asks at
// start, such as MicrophoneMuteIsAvailable. Answer them safely and pass everything else through.
class SafeAndroidAudioDevice : public tgcalls::DefaultWrappedAudioDeviceModule {
public:
  explicit SafeAndroidAudioDevice (webrtc::scoped_refptr<webrtc::AudioDeviceModule> impl) : DefaultWrappedAudioDeviceModule(std::move(impl)) { }

  int32_t PlayoutDeviceName (uint16_t, char name[webrtc::kAdmMaxDeviceNameSize], char guid[webrtc::kAdmMaxGuidSize]) override { name[0] = 0; guid[0] = 0; return 0; }
  int32_t RecordingDeviceName (uint16_t, char name[webrtc::kAdmMaxDeviceNameSize], char guid[webrtc::kAdmMaxGuidSize]) override { name[0] = 0; guid[0] = 0; return 0; }
  int32_t SetPlayoutDevice (uint16_t) override { return 0; }
  int32_t SetPlayoutDevice (WindowsDeviceType) override { return 0; }
  int32_t SetRecordingDevice (uint16_t) override { return 0; }
  int32_t SetRecordingDevice (WindowsDeviceType) override { return 0; }
  int32_t SetMicrophoneVolume (uint32_t) override { return -1; }
  int32_t MicrophoneVolume (uint32_t *) const override { return -1; }
  int32_t MaxMicrophoneVolume (uint32_t *) const override { return -1; }
  int32_t MinMicrophoneVolume (uint32_t *) const override { return -1; }
  int32_t SpeakerMuteIsAvailable (bool *available) override { *available = false; return 0; }
  int32_t SetSpeakerMute (bool) override { return -1; }
  int32_t SpeakerMute (bool *enabled) const override { *enabled = false; return -1; }
  int32_t MicrophoneMuteIsAvailable (bool *available) override { *available = false; return 0; }
  int32_t SetMicrophoneMute (bool) override { return -1; }
  int32_t MicrophoneMute (bool *enabled) const override { *enabled = false; return -1; }
  int32_t EnableBuiltInAGC (bool) override { return -1; }
};

// TGx101 (conference calls, user 2026-10-14 «добавить участника из окна звонка»): a group call not bound to a chat is
// end-to-end encrypted. tgcalls asks for every audio frame to be encrypted / decrypted; TDLib does it on the device
// (encryptGroupCallData / decryptGroupCallData) — the Java side runs the request and waits for it.
// Decrypting needs the sender's user id: a participant's SSRC is matched to the user once the participants are known;
// until then tgcalls' request for that SSRC waits (≤ 5 s).
struct PendingDescriptions {
  std::vector<uint32_t> ssrcs;
  std::function<void(std::vector<tgcalls::MediaChannelDescription> &&)> callback;
  int64_t requestedAt;
};

struct GroupConference {
  std::mutex mutex;
  std::vector<PendingDescriptions> pending;
};

struct GroupContext {
  std::unique_ptr<tgcalls::GroupInstanceCustomImpl> instance;
  std::shared_ptr<GroupJava> java;
  std::shared_ptr<GroupConference> conference;
  // TGx101 (group call video, 2026-10-19): the participants' video sinks, by endpoint — kept alive here, tgcalls holds weak ones
  std::mutex sinksMutex;
  std::map<std::string, std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> sinks;
};

std::vector<std::string> splitString (const std::string &value, char delimiter) {
  std::vector<std::string> parts;
  std::stringstream stream(value);
  std::string part;
  while (std::getline(stream, part, delimiter)) {
    parts.push_back(part);
  }
  return parts;
}

int64_t userIdForSsrc (const std::shared_ptr<GroupJava> &java, uint32_t ssrc) {
  int64_t userId = 0;
  java->run([ssrc, &userId](JNIEnv *env, jobject obj, jclass clazz) {
    userId = (int64_t) env->CallLongMethod(obj, env->GetMethodID(clazz, "onNativeUserIdForSsrc", "(I)J"), (jint) ssrc);
  });
  return userId;
}

// Answers what can be answered: every SSRC whose user is known, or all of them after 5 s
void resolvePending (const std::shared_ptr<GroupJava> &java, const std::shared_ptr<GroupConference> &conference, bool force) {
  // (review) the user ids are asked from Java with no lock held: the pending list is taken out, checked, the rest put back
  std::vector<PendingDescriptions> taken;
  {
    std::lock_guard<std::mutex> lock(conference->mutex);
    taken.swap(conference->pending);
  }
  std::vector<PendingDescriptions> ready, waiting;
  int64_t now = rtc::TimeMillis();
  for (auto &request : taken) {
    bool known = true;
    for (uint32_t ssrc : request.ssrcs) {
      if (userIdForSsrc(java, ssrc) == 0) {
        known = false;
        break;
      }
    }
    if (known || force || now - request.requestedAt > 5000) {
      ready.push_back(std::move(request));
    } else {
      waiting.push_back(std::move(request));
    }
  }
  if (!waiting.empty()) {
    std::lock_guard<std::mutex> lock(conference->mutex);
    for (auto &request : waiting) conference->pending.push_back(std::move(request));
  }
  for (auto &request : ready) {
    std::vector<tgcalls::MediaChannelDescription> descriptions;
    for (uint32_t ssrc : request.ssrcs) {
      tgcalls::MediaChannelDescription description;
      description.type = tgcalls::MediaChannelDescription::Type::Audio;
      description.audioSsrc = ssrc;
      description.userId = userIdForSsrc(java, ssrc);
      descriptions.push_back(description);
    }
    request.callback(std::move(descriptions));
  }
}

}

JNI_OBJECT_FUNC(jlong, voip_Tgx101GroupCall, nativeCreate, jstring jLogPath, jboolean jMuted, jboolean jConference) {
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
  bool isConference = jConference == JNI_TRUE;
  auto conference = isConference ? std::make_shared<GroupConference>() : nullptr;
  if (isConference) {
    descriptor.isConference = true;
    descriptor.e2eEncryptDecrypt = [java](std::vector<uint8_t> const &data, int64_t userId, bool encrypt, int32_t unencryptedPrefixSize) -> std::vector<uint8_t> {
      std::vector<uint8_t> result;
      java->run([&](JNIEnv *env, jobject obj, jclass clazz) {
        jbyteArray jData = env->NewByteArray((jsize) data.size());
        env->SetByteArrayRegion(jData, 0, (jsize) data.size(), (const jbyte *) data.data());
        auto jResult = (jbyteArray) env->CallObjectMethod(obj, env->GetMethodID(clazz, "onNativeE2e", "([BJZI)[B"), jData, (jlong) userId, (jboolean) encrypt, (jint) unencryptedPrefixSize);
        env->DeleteLocalRef(jData);
        if (jResult != nullptr) {
          jsize size = env->GetArrayLength(jResult);
          result.resize((size_t) size);
          env->GetByteArrayRegion(jResult, 0, size, (jbyte *) result.data());
          env->DeleteLocalRef(jResult);
        }
      });
      return result;
    };
    descriptor.requestMediaChannelDescriptions = [java, conference](std::vector<uint32_t> const &ssrcs, std::function<void(std::vector<tgcalls::MediaChannelDescription> &&)> callback) -> std::shared_ptr<tgcalls::RequestMediaChannelDescriptionTask> {
      {
        std::lock_guard<std::mutex> lock(conference->mutex);
        conference->pending.push_back(PendingDescriptions {ssrcs, std::move(callback), rtc::TimeMillis()});
      }
      resolvePending(java, conference, false);
      return std::make_shared<ImmediateDescriptionTask>();
    };
  } else descriptor.requestMediaChannelDescriptions = [](std::vector<uint32_t> const &ssrcs, std::function<void(std::vector<tgcalls::MediaChannelDescription> &&)> callback) -> std::shared_ptr<tgcalls::RequestMediaChannelDescriptionTask> {
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
  descriptor.videoContentType = tgcalls::VideoContentType::Generic; // TGx101: the participants' cameras and screens can be received
  descriptor.createWrappedAudioDeviceModule = [](webrtc::TaskQueueFactory *) -> webrtc::scoped_refptr<tgcalls::WrappedAudioDeviceModule> {
    auto device = webrtc::CreateAndroidAudioDeviceModule(webrtc::AudioDeviceModule::kPlatformDefaultAudio);
    if (device == nullptr || device->Init() != 0) {
      return nullptr;
    }
    return rtc::make_ref_counted<SafeAndroidAudioDevice>(device);
  };

  auto *context = new GroupContext;
  context->java = java;
  context->conference = conference;
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

// The participants changed: SSRC requests that waited for their users are answered now (or once 5 s are over)
JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeParticipantsChanged, jlong ptr) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context != nullptr && context->conference != nullptr) {
    resolvePending(context->java, context->conference, false);
  }
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

// TGx101: which participants' videos to receive — one string per video: «audioSsrc|endpointId|quality|SEM:ssrc,ssrc;SEM:ssrc»,
// quality 0 thumbnail, 1 medium, 2 full
JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeRequestVideo, jlong ptr, jobjectArray jChannels) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context == nullptr || context->instance == nullptr) {
    return;
  }
  std::vector<tgcalls::VideoChannelDescription> channels;
  jsize count = jChannels != nullptr ? env->GetArrayLength(jChannels) : 0;
  for (jsize i = 0; i < count; i++) {
    auto jChannel = (jstring) env->GetObjectArrayElement(jChannels, i);
    std::string channel = jni::from_jstring(env, jChannel);
    env->DeleteLocalRef(jChannel);
    std::vector<std::string> fields = splitString(channel, '|');
    if (fields.size() < 4) continue;
    tgcalls::VideoChannelDescription description;
    description.audioSsrc = (uint32_t) std::stoul(fields[0]);
    description.endpointId = fields[1];
    int quality = std::stoi(fields[2]);
    description.minQuality = tgcalls::VideoChannelDescription::Quality::Thumbnail;
    description.maxQuality = quality >= 2 ? tgcalls::VideoChannelDescription::Quality::Full : quality == 1 ? tgcalls::VideoChannelDescription::Quality::Medium : tgcalls::VideoChannelDescription::Quality::Thumbnail;
    for (const std::string &group : splitString(fields[3], ';')) {
      std::vector<std::string> semanticsAndSsrcs = splitString(group, ':');
      if (semanticsAndSsrcs.size() != 2) continue;
      tgcalls::MediaSsrcGroup ssrcGroup;
      ssrcGroup.semantics = semanticsAndSsrcs[0];
      for (const std::string &ssrc : splitString(semanticsAndSsrcs[1], ',')) {
        if (!ssrc.empty()) ssrcGroup.ssrcs.push_back((uint32_t) std::stoul(ssrc));
      }
      description.ssrcGroups.push_back(ssrcGroup);
    }
    channels.push_back(description);
  }
  context->instance->setRequestedVideoChannels(std::move(channels));
}

// TGx101: where a participant's video goes (an org.webrtc.VideoSink), null — nowhere
JNI_OBJECT_FUNC(void, voip_Tgx101GroupCall, nativeSetVideoSink, jlong ptr, jstring jEndpointId, jobject jSink) {
  auto context = jni::jlong_to_ptr<GroupContext *>(ptr);
  if (context == nullptr || context->instance == nullptr) {
    return;
  }
  std::string endpointId = jni::from_jstring(env, jEndpointId);
  std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink;
  if (jSink != nullptr) {
    sink = std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>(webrtc::JavaToNativeVideoSink(env, jSink).release());
  }
  {
    std::lock_guard<std::mutex> lock(context->sinksMutex);
    if (sink != nullptr) {
      context->sinks[endpointId] = sink;
    } else {
      context->sinks.erase(endpointId);
    }
  }
  if (sink != nullptr) {
    context->instance->addIncomingVideoOutput(endpointId, sink);
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
