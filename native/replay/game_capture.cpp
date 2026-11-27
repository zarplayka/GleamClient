#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <unknwn.h>
#include "frame_share.h"
#include <d3d11.h>
#include <jni.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "windowsapp.lib")
#pragma comment(lib, "oleaut32.lib")

namespace replay {
namespace {
struct Capture {
    FrameChannel channel;
    jlong generation;

    Capture(UINT width, UINT height, std::wstring_view token, jlong session)
        : channel(token, width, height), generation(session) {}
};

std::atomic<std::shared_ptr<Capture>> active;

void fail(JNIEnv* jni, const char* message) {
    if (auto type = jni->FindClass("java/io/IOException")) jni->ThrowNew(type, message);
}
}
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_open(
        JNIEnv* jni, jclass, jint width, jint height, jlong generation, jstring identifier) {
    using namespace replay;
    try {
        if (!identifier || jni->GetStringLength(identifier) != 36) throw std::invalid_argument("Invalid frame channel");
        std::array<jchar, 36> chars{};
        jni->GetStringRegion(identifier, 0, static_cast<jsize>(chars.size()), chars.data());
        if (jni->ExceptionCheck()) return;
        active.store(std::make_shared<Capture>(width, height, std::wstring(chars.begin(), chars.end()), generation));
    } catch (const winrt::hresult_error& error) {
        fail(jni, winrt::to_string(error.message()).c_str());
    } catch (const std::exception& error) {
        fail(jni, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_stop(JNIEnv*, jclass) {
    replay::active.store(nullptr);
}

extern "C" JNIEXPORT jlong JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_timestamp(JNIEnv*, jclass) {
    return replay::qpc_time();
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_submit(
        JNIEnv* jni, jclass, jlong generation, jobject data, jint width, jint height, jlong timestamp) {
    using namespace replay;
    auto capture = active.load();
    if (!capture || capture->generation != generation) return;
    auto& channel = capture->channel;
    auto* frames = channel.frames;
    if (!valid_frame_size(width, height) || static_cast<UINT>(width) > frames->width
            || static_cast<UINT>(height) > frames->height) return;
    const auto* source = jni->GetDirectBufferAddress(data);
    const auto bytes = static_cast<size_t>(width) * height * 4;
    if (!source || jni->GetDirectBufferCapacity(data) < static_cast<jlong>(bytes)) return;

    for (size_t i = 0; i < frame_slot_count; ++i) {
        auto& slot = frames->slots[i];
        if (InterlockedCompareExchange(&slot.state, frame_writing, frame_free) != frame_free) continue;
        std::memcpy(channel.pixels(i), source, bytes);
        slot.width = width;
        slot.height = height;
        slot.time = timestamp;
        InterlockedExchange(&slot.state, frame_ready);
        SetEvent(channel.wake.get());
        return;
    }
}

#include "playback.h"
