#pragma once

#include "storage.h"
#include <audioclient.h>
#include <audioclientactivationparams.h>
#include <mmdeviceapi.h>
#include <array>
#include <map>
#include <optional>

#pragma comment(lib, "mmdevapi.lib")

namespace replay {
struct Activation : winrt::implements<Activation, IActivateAudioInterfaceCompletionHandler> {
    winrt::handle ready{CreateEventW(nullptr, TRUE, FALSE, nullptr)};
    com_ptr<IAudioClient> client;
    HRESULT result{E_PENDING};

    HRESULT __stdcall ActivateCompleted(IActivateAudioInterfaceAsyncOperation* operation) noexcept override {
        com_ptr<IUnknown> value;
        HRESULT actual{};
        result = operation->GetActivateResult(&actual, value.put());
        if (SUCCEEDED(result)) result = actual;
        if (SUCCEEDED(result)) result = value->QueryInterface(__uuidof(IAudioClient), client.put_void());
        SetEvent(ready.get());
        return S_OK;
    }
};

class AudioPort {
    com_ptr<IAudioClient> client_;
    com_ptr<IAudioCaptureClient> capture_;
    winrt::handle ready_{CreateEventW(nullptr, FALSE, FALSE, nullptr)};

    static com_ptr<IAudioClient> process_client(DWORD pid) {
        auto completion = winrt::make_self<Activation>();
        if (!completion->ready) winrt::throw_last_error();
        AUDIOCLIENT_ACTIVATION_PARAMS settings{};
        settings.ActivationType = AUDIOCLIENT_ACTIVATION_TYPE_PROCESS_LOOPBACK;
        settings.ProcessLoopbackParams.TargetProcessId = pid;
        settings.ProcessLoopbackParams.ProcessLoopbackMode = PROCESS_LOOPBACK_MODE_INCLUDE_TARGET_PROCESS_TREE;
        PROPVARIANT activation{};
        activation.vt = VT_BLOB;
        activation.blob.cbSize = sizeof(settings);
        activation.blob.pBlobData = reinterpret_cast<BYTE*>(&settings);
        com_ptr<IActivateAudioInterfaceAsyncOperation> operation;
        check_hresult(ActivateAudioInterfaceAsync(VIRTUAL_AUDIO_DEVICE_PROCESS_LOOPBACK, __uuidof(IAudioClient),
                                                   &activation, completion.get(), operation.put()));
        if (WaitForSingleObject(completion->ready.get(), 4000) != WAIT_OBJECT_0)
            throw std::runtime_error("Minecraft audio activation timed out");
        check_hresult(completion->result);
        return completion->client;
    }

    static com_ptr<IAudioClient> endpoint(EDataFlow flow) {
        com_ptr<IMMDeviceEnumerator> devices;
        check_hresult(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_INPROC_SERVER,
                                       __uuidof(IMMDeviceEnumerator), devices.put_void()));
        com_ptr<IMMDevice> device;
        check_hresult(devices->GetDefaultAudioEndpoint(flow, flow == eCapture ? eCommunications : eConsole, device.put()));
        com_ptr<IAudioClient> client;
        check_hresult(device->Activate(__uuidof(IAudioClient), CLSCTX_INPROC_SERVER, nullptr, client.put_void()));
        return client;
    }

public:
    AudioPort(DWORD pid, bool microphone) {
        if (!ready_) winrt::throw_last_error();
        client_ = pid ? process_client(pid) : endpoint(microphone ? eCapture : eRender);
        WAVEFORMATEX format{};
        format.wFormatTag = WAVE_FORMAT_PCM;
        format.nChannels = 2;
        format.nSamplesPerSec = 48000;
        format.wBitsPerSample = 16;
        format.nBlockAlign = 4;
        format.nAvgBytesPerSec = 192000;
        DWORD flags = AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY;
        if (!microphone) flags |= AUDCLNT_STREAMFLAGS_LOOPBACK;
        check_hresult(client_->Initialize(AUDCLNT_SHAREMODE_SHARED, flags, 0, 0, &format, nullptr));
        check_hresult(client_->SetEventHandle(ready_.get()));
        check_hresult(client_->GetService(__uuidof(IAudioCaptureClient), capture_.put_void()));
        check_hresult(client_->Start());
    }

    ~AudioPort() { if (client_) client_->Stop(); }
    HANDLE wake() const { return ready_.get(); }

    template <class Consumer>
    void read(Consumer&& on_buffer) {
        UINT32 frames{};
        check_hresult(capture_->GetNextPacketSize(&frames));
        while (frames) {
            BYTE* bytes{};
            DWORD flags{};
            UINT64 timestamp{};
            check_hresult(capture_->GetBuffer(&bytes, &frames, &flags, nullptr, &timestamp));
            {
                ScopeExit release([&] { capture_->ReleaseBuffer(frames); });
                if (!(flags & AUDCLNT_BUFFERFLAGS_SILENT)) {
                    if (flags & AUDCLNT_BUFFERFLAGS_TIMESTAMP_ERROR) timestamp = qpc_time() - frames * second / 48000;
                    on_buffer(reinterpret_cast<const int16_t*>(bytes), frames, static_cast<LONGLONG>(timestamp));
                }
            }
            check_hresult(capture_->GetNextPacketSize(&frames));
        }
    }
};

class Audio {
    static constexpr int stretch = 960;
    static constexpr LONGLONG period = second / 50;
    using Block = std::array<int32_t, stretch * 2>;
    std::optional<AudioPort> sound_, mic_;
    std::map<LONGLONG, Block> mixed_;
    LONGLONG next_{};
    bool track_{};

    template<class Operation>
    static void endpoint(std::optional<AudioPort>& port, std::string_view name, Operation&& operation) {
        try { operation(); }
        catch (const winrt::hresult_error&) { port.reset(); report("WARNING", name); }
        catch (const std::runtime_error&) { port.reset(); report("WARNING", name); }
    }

public:
    // 0 = game, 1 = default render endpoint, 2 = silent.
    Audio(DWORD game, unsigned mode, bool mic) {
        if (mode < 2) endpoint(sound_, "audio", [&] { sound_.emplace(mode == 0 ? game : 0, false); });
        if (mic) endpoint(mic_, "mic", [&] { mic_.emplace(0, true); });
        track_ = sound_.has_value() || mic_.has_value();
    }
    bool enabled() const { return track_; }
    void add_wake(HANDLE* events, DWORD& count) const {
        if (sound_) events[count++] = sound_->wake();
        if (mic_) events[count++] = mic_->wake();
    }

    template <class Consumer>
    void pump(LONGLONG origin, Consumer&& on_packet) {
        if (!enabled()) return;
        LONGLONG now = qpc_time() - origin;
        if (next_ < now / period - 100) {
            next_ = now / period - 100;
            mixed_.erase(mixed_.begin(), mixed_.lower_bound(next_));
        }
        auto mix = [&](const int16_t* source, UINT32 frames, LONGLONG timestamp) {
            LONGLONG first = (timestamp - origin) * 48000 / second;
            for (UINT32 i = 0; i < frames;) {
                LONGLONG at = first + i;
                if (at < next_ * stretch) {
                    i += static_cast<UINT32>(std::min<LONGLONG>(frames - i, next_ * stretch - at));
                    continue;
                }
                if (at > (now + period * 10) * 48000 / second) break;
                auto& block = mixed_[at / stretch];
                auto offset = static_cast<UINT32>(at % stretch);
                auto count = std::min(frames - i, stretch - offset);
                for (UINT32 sample = 0; sample < count * 2; ++sample)
                    block[offset * 2 + sample] += source[i * 2 + sample];
                i += count;
            }
        };
        if (sound_) endpoint(sound_, "audio", [&] { sound_->read(mix); });
        if (mic_) endpoint(mic_, "mic", [&] { mic_->read(mix); });
        // Audio endpoints can deliver packets at different times. Leave 100 ms for mixing.
        while (next_ < now / period - 5) {
            auto node = mixed_.extract(next_);
            Packet packet;
            packet.audio = true;
            packet.time = next_++ * period;
            packet.duration = period;
            packet.size = stretch * 2 * sizeof(int16_t);
            check_hresult(MFCreateMemoryBuffer(packet.size, packet.bytes.put()));
            BYTE* bytes{};
            check_hresult(packet.bytes->Lock(&bytes, nullptr, nullptr));
            auto* target = reinterpret_cast<int16_t*>(bytes);
            if (node.empty()) std::memset(target, 0, packet.size);
            else std::transform(node.mapped().begin(), node.mapped().end(), target, [](int32_t sample) {
                return static_cast<int16_t>(std::clamp(sample, -32768, 32767));
            });
            packet.bytes->Unlock();
            check_hresult(packet.bytes->SetCurrentLength(packet.size));
            on_packet(std::move(packet));
        }
    }
};
}
