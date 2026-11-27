#pragma once

#include <windows.h>
#include <d3d11_4.h>
#include <dxgi1_6.h>
#include "frame_share.h"
#include <array>
#include <mfapi.h>
#include <mferror.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <codecapi.h>
#include <icodecapi.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#pragma comment(lib, "windowsapp.lib")
#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfuuid.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "strmiids.lib")
#pragma comment(lib, "user32.lib")

namespace replay {
using winrt::check_hresult;
using winrt::com_ptr;
template <typename Cleanup>
struct ScopeExit {
    Cleanup cleanup;
    explicit ScopeExit(Cleanup action) : cleanup(std::move(action)) {}
    ScopeExit(const ScopeExit&) = delete;
    ~ScopeExit() { cleanup(); }
};

inline auto media_type() {
    com_ptr<IMFMediaType> type;
    check_hresult(MFCreateMediaType(type.put()));
    return type;
}

inline auto attributes(UINT32 count) {
    com_ptr<IMFAttributes> result;
    check_hresult(MFCreateAttributes(result.put(), count));
    return result;
}

inline auto make_sample(IMFMediaBuffer* buffer, LONGLONG time, LONGLONG duration) {
    com_ptr<IMFSample> sample;
    check_hresult(MFCreateSample(sample.put()));
    check_hresult(sample->AddBuffer(buffer));
    check_hresult(sample->SetSampleTime(time));
    check_hresult(sample->SetSampleDuration(duration));
    return sample;
}

struct Packet {
    com_ptr<IMFMediaBuffer> bytes;
    LONGLONG time{};
    LONGLONG duration{};
    DWORD size{};
    bool key{};
    bool audio{};
};

inline Packet packet_from(IMFSample* sample, bool audio = false) {
    Packet packet;
    packet.audio = audio;
    check_hresult(sample->GetSampleTime(&packet.time));
    check_hresult(sample->GetSampleDuration(&packet.duration));
    UINT32 key{};
    packet.key = SUCCEEDED(sample->GetUINT32(MFSampleExtension_CleanPoint, &key)) && key != 0;
    com_ptr<IMFMediaBuffer> original;
    check_hresult(sample->ConvertToContiguousBuffer(original.put()));
    check_hresult(original->GetCurrentLength(&packet.size));
    if (!packet.size) return packet;
    check_hresult(MFCreateMemoryBuffer(packet.size, packet.bytes.put()));
    BYTE* output{};
    BYTE* input{};
    check_hresult(packet.bytes->Lock(&output, nullptr, nullptr));
    HRESULT locked = original->Lock(&input, nullptr, nullptr);
    if (SUCCEEDED(locked)) {
        std::memcpy(output, input, packet.size);
        original->Unlock();
    }
    packet.bytes->Unlock();
    check_hresult(locked);
    check_hresult(packet.bytes->SetCurrentLength(packet.size));
    return packet;
}

struct VideoSettings {
    unsigned height;
    unsigned fps;
    unsigned bitrate;
    unsigned quality;
};

class Video {
    FrameChannel& channel_;
    com_ptr<ID3D11Device> device_;
    com_ptr<ID3D11DeviceContext> context_;
    com_ptr<ID3D11VideoDevice> video_device_;
    com_ptr<ID3D11VideoContext> video_context_;
    com_ptr<ID3D11VideoProcessorEnumerator> processor_format_;
    com_ptr<ID3D11VideoProcessor> processor_;
    com_ptr<ID3D11Texture2D> texture_;
    com_ptr<ID3D11VideoProcessorInputView> texture_view_;
    com_ptr<IMFDXGIDeviceManager> device_manager_;
    com_ptr<IMFVideoSampleAllocatorEx> allocator_;
    com_ptr<IMFTransform> encoder_;
    com_ptr<IMFMediaEventGenerator> events_;
    com_ptr<IMFMediaType> output_type_;
    com_ptr<ICodecAPI> codec_;
    std::vector<UINT32> rgba_;
    VideoSettings settings_;
    unsigned width_, height_;
    DWORD in_{}, out_{};
    size_t requests_{};
    LONGLONG last_input_{-1};
    bool force_key_{}, draining_{}, bgra_{}, software_output_pending_{};
    std::wstring name_;

    auto video_type(GUID subtype) const {
        auto type = media_type();
        check_hresult(type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video));
        check_hresult(type->SetGUID(MF_MT_SUBTYPE, subtype));
        check_hresult(MFSetAttributeSize(type.get(), MF_MT_FRAME_SIZE, width_, height_));
        check_hresult(MFSetAttributeRatio(type.get(), MF_MT_FRAME_RATE, settings_.fps, 1));
        check_hresult(MFSetAttributeRatio(type.get(), MF_MT_PIXEL_ASPECT_RATIO, 1, 1));
        check_hresult(type->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive));
        check_hresult(type->SetUINT32(MF_MT_YUV_MATRIX, MFVideoTransferMatrix_BT709));
        check_hresult(type->SetUINT32(MF_MT_VIDEO_NOMINAL_RANGE, MFNominalRange_16_235));
        check_hresult(type->SetUINT32(MF_MT_VIDEO_PRIMARIES, MFVideoPrimaries_BT709));
        check_hresult(type->SetUINT32(MF_MT_TRANSFER_FUNCTION, MFVideoTransFunc_709));
        return type;
    }

    void set_codec(const GUID& id, ULONG value) {
        if (!codec_) return;
        VARIANT option{};
        option.vt = VT_UI4;
        option.ulVal = value;
        codec_->SetValue(&id, &option);
    }

    void open_video_processor() {
        video_device_ = device_.as<ID3D11VideoDevice>();
        video_context_ = context_.as<ID3D11VideoContext>();
        D3D11_VIDEO_PROCESSOR_CONTENT_DESC description{};
        description.InputFrameFormat = D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE;
        description.InputWidth = description.OutputWidth = width_;
        description.InputHeight = description.OutputHeight = height_;
        description.InputFrameRate = description.OutputFrameRate = {settings_.fps, 1};
        description.Usage = D3D11_VIDEO_USAGE_OPTIMAL_SPEED;
        check_hresult(video_device_->CreateVideoProcessorEnumerator(&description, processor_format_.put()));
        check_hresult(video_device_->CreateVideoProcessor(processor_format_.get(), 0, processor_.put()));
        video_context_->VideoProcessorSetStreamAutoProcessingMode(processor_.get(), 0, FALSE);
        D3D11_VIDEO_PROCESSOR_COLOR_SPACE input{};
        input.Nominal_Range = D3D11_VIDEO_PROCESSOR_NOMINAL_RANGE_0_255;
        input.YCbCr_Matrix = 1;
        video_context_->VideoProcessorSetStreamColorSpace(processor_.get(), 0, &input);
        D3D11_VIDEO_PROCESSOR_COLOR_SPACE output{};
        output.Nominal_Range = D3D11_VIDEO_PROCESSOR_NOMINAL_RANGE_16_235;
        output.YCbCr_Matrix = 1;
        video_context_->VideoProcessorSetOutputColorSpace(processor_.get(), &output);

        UINT support{};
        check_hresult(processor_format_->CheckVideoProcessorFormat(DXGI_FORMAT_R8G8B8A8_UNORM, &support));
        bgra_ = !(support & D3D11_VIDEO_PROCESSOR_FORMAT_SUPPORT_INPUT);
        D3D11_TEXTURE2D_DESC desc{};
        desc.Width = width_;
        desc.Height = height_;
        desc.MipLevels = desc.ArraySize = 1;
        desc.Format = bgra_ ? DXGI_FORMAT_B8G8R8A8_UNORM : DXGI_FORMAT_R8G8B8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.BindFlags = D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;
        check_hresult(device_->CreateTexture2D(&desc, nullptr, texture_.put()));
        D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC view{};
        view.ViewDimension = D3D11_VPIV_DIMENSION_TEXTURE2D;
        check_hresult(video_device_->CreateVideoProcessorInputView(texture_.get(), processor_format_.get(), &view, texture_view_.put()));
    }

    void configure_encoder(IMFActivate* candidate, bool gpu) {
        check_hresult(candidate->ActivateObject(__uuidof(IMFTransform), encoder_.put_void()));
        if (gpu) {
            com_ptr<IMFAttributes> attrs;
            check_hresult(encoder_->GetAttributes(attrs.put()));
            check_hresult(attrs->SetUINT32(MF_TRANSFORM_ASYNC_UNLOCK, TRUE));
            check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_SET_D3D_MANAGER,
                    reinterpret_cast<ULONG_PTR>(device_manager_.get())));
        }
        in_ = out_ = 0;
        HRESULT streams = encoder_->GetStreamIDs(1, &in_, 1, &out_);
        if (streams != E_NOTIMPL) check_hresult(streams);
        codec_ = encoder_.try_as<ICodecAPI>();
        set_codec(CODECAPI_AVEncMPVDefaultBPictureCount, 0);
        set_codec(CODECAPI_AVEncMPVGOPSize, settings_.fps);
        set_codec(CODECAPI_AVEncCommonMeanBitRate, settings_.bitrate);
        set_codec(CODECAPI_AVEncCommonQualityVsSpeed, gpu ? settings_.quality : 100);
        if (codec_) {
            VARIANT low_latency{};
            low_latency.vt = VT_BOOL;
            low_latency.boolVal = VARIANT_TRUE;
            codec_->SetValue(&CODECAPI_AVLowLatencyMode, &low_latency);
        }
        auto compressed = video_type(MFVideoFormat_H264);
        check_hresult(compressed->SetUINT32(MF_MT_AVG_BITRATE, settings_.bitrate));
        check_hresult(compressed->SetUINT32(MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_Main));
        auto uncompressed = video_type(MFVideoFormat_NV12);
        check_hresult(encoder_->SetOutputType(out_, compressed.get(), 0));
        check_hresult(encoder_->SetInputType(in_, uncompressed.get(), 0));
        if (gpu) events_ = encoder_.as<IMFMediaEventGenerator>();
        check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0));
        check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM, 0));

        name_ = gpu ? L"Hardware H.264" : L"Software H.264";
        LPWSTR name{};
        UINT32 length{};
        if (SUCCEEDED(candidate->GetAllocatedString(MFT_FRIENDLY_NAME_Attribute, &name, &length))) {
            name_.assign(name, length);
            CoTaskMemFree(name);
        }
    }

    bool open_encoder(const LUID* luid) {
        MFT_REGISTER_TYPE_INFO input{MFMediaType_Video, MFVideoFormat_NV12};
        MFT_REGISTER_TYPE_INFO output{MFMediaType_Video, MFVideoFormat_H264};
        IMFActivate** candidates{};
        UINT32 count{};
        if (luid) {
            auto filter = attributes(1);
            check_hresult(filter->SetBlob(MFT_ENUM_ADAPTER_LUID, reinterpret_cast<const UINT8*>(luid), sizeof(*luid)));
            check_hresult(MFTEnum2(MFT_CATEGORY_VIDEO_ENCODER, MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER,
                    &input, &output, filter.get(), &candidates, &count));
        } else {
            check_hresult(MFTEnumEx(MFT_CATEGORY_VIDEO_ENCODER, MFT_ENUM_FLAG_SYNCMFT | MFT_ENUM_FLAG_SORTANDFILTER,
                    &input, &output, &candidates, &count));
        }
        ScopeExit release([&] {
            for (UINT32 i = 0; i < count; ++i) candidates[i]->Release();
            CoTaskMemFree(candidates);
        });
        for (UINT32 i = 0; i < count; ++i) {
            try {
                configure_encoder(candidates[i], luid != nullptr);
                return true;
            } catch (const winrt::hresult_error&) {
                events_ = nullptr;
                codec_ = nullptr;
                encoder_ = nullptr;
                candidates[i]->ShutdownObject();
            }
        }
        return false;
    }

    bool try_hardware(IDXGIAdapter1* adapter, const LUID& luid) {
        check_hresult(D3D11CreateDevice(adapter, D3D_DRIVER_TYPE_UNKNOWN, nullptr,
            D3D11_CREATE_DEVICE_BGRA_SUPPORT | D3D11_CREATE_DEVICE_VIDEO_SUPPORT, nullptr, 0, D3D11_SDK_VERSION,
            device_.put(), nullptr, context_.put()));
        context_.as<ID3D11Multithread>()->SetMultithreadProtected(TRUE);
        open_video_processor();

        UINT token{};
        check_hresult(MFCreateDXGIDeviceManager(&token, device_manager_.put()));
        check_hresult(device_manager_->ResetDevice(device_.get(), token));
        check_hresult(MFCreateVideoSampleAllocatorEx(__uuidof(IMFVideoSampleAllocatorEx), allocator_.put_void()));
        check_hresult(allocator_->SetDirectXManager(device_manager_.get()));
        auto allocation = attributes(1);
        check_hresult(allocation->SetUINT32(MF_SA_D3D11_BINDFLAGS, D3D11_BIND_RENDER_TARGET));
        auto uncompressed = video_type(MFVideoFormat_NV12);
        check_hresult(allocator_->InitializeSampleAllocatorEx(4, 8, allocation.get(), uncompressed.get()));
        return open_encoder(&luid);
    }

    void release_hardware() {
        allocator_ = nullptr;
        device_manager_ = nullptr;
        texture_view_ = nullptr;
        texture_ = nullptr;
        processor_ = nullptr;
        processor_format_ = nullptr;
        video_context_ = nullptr;
        video_device_ = nullptr;
        context_ = nullptr;
        device_ = nullptr;
    }

    bool open_hardware() {
        com_ptr<IDXGIFactory1> factory;
        if (FAILED(CreateDXGIFactory1(__uuidof(IDXGIFactory1), factory.put_void()))) return false;
        auto preferred = factory.try_as<IDXGIFactory6>();
        for (UINT i = 0;; ++i) {
            com_ptr<IDXGIAdapter1> adapter;
            const auto result = preferred ? preferred->EnumAdapterByGpuPreference(i, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
                    __uuidof(IDXGIAdapter1), adapter.put_void()) : factory->EnumAdapters1(i, adapter.put());
            if (FAILED(result)) return false;
            DXGI_ADAPTER_DESC1 desc{};
            if (FAILED(adapter->GetDesc1(&desc)) || (desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE)) continue;
            try {
                if (try_hardware(adapter.get(), desc.AdapterLuid)) return true;
            } catch (const winrt::hresult_error&) {}
            release_hardware();
        }
    }

    void fill_rgba(const BYTE* pixels, UINT width, UINT height) {
        const auto x_offset = (width_ - width) / 2;
        const auto y_offset = (height_ - height) / 2;
        if (width != width_ || height != height_) std::fill(rgba_.begin(), rgba_.end(), 0xff000000u);
        for (UINT y = 0; y < height; ++y) {
            const auto* row = reinterpret_cast<const UINT32*>(pixels) + static_cast<size_t>(height - 1 - y) * width;
            auto* destination = rgba_.data() + static_cast<size_t>(y + y_offset) * width_ + x_offset;
            if (!bgra_) std::memcpy(destination, row, width * sizeof(UINT32));
            else for (UINT x = 0; x < width; ++x) {
                const auto color = row[x];
                destination[x] = (color & 0xff00ff00u) | ((color & 0xffu) << 16) | ((color >> 16) & 0xffu);
            }
        }
    }

    com_ptr<IMFSample> hardware_sample() {
        com_ptr<IMFSample> sample;
        HRESULT result = allocator_->AllocateSample(sample.put());
        if (result == MF_E_SAMPLEALLOCATOR_EMPTY) return {};
        check_hresult(result);
        context_->UpdateSubresource(texture_.get(), 0, nullptr, rgba_.data(), width_ * 4, 0);

        com_ptr<IMFMediaBuffer> buffer;
        check_hresult(sample->GetBufferByIndex(0, buffer.put()));
        auto target_buffer = buffer.as<IMFDXGIBuffer>();
        com_ptr<ID3D11Texture2D> target;
        check_hresult(target_buffer->GetResource(__uuidof(ID3D11Texture2D), target.put_void()));
        UINT subresource{};
        check_hresult(target_buffer->GetSubresourceIndex(&subresource));
        D3D11_TEXTURE2D_DESC texture_desc{};
        target->GetDesc(&texture_desc);
        D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC output_desc{};
        if (texture_desc.ArraySize > 1) {
            output_desc.ViewDimension = D3D11_VPOV_DIMENSION_TEXTURE2DARRAY;
            output_desc.Texture2DArray.FirstArraySlice = subresource / texture_desc.MipLevels;
            output_desc.Texture2DArray.ArraySize = 1;
        } else output_desc.ViewDimension = D3D11_VPOV_DIMENSION_TEXTURE2D;
        com_ptr<ID3D11VideoProcessorOutputView> output;
        check_hresult(video_device_->CreateVideoProcessorOutputView(target.get(), processor_format_.get(), &output_desc, output.put()));
        D3D11_VIDEO_PROCESSOR_STREAM stream{};
        stream.Enable = TRUE;
        stream.pInputSurface = texture_view_.get();
        check_hresult(video_context_->VideoProcessorBlt(processor_.get(), output.get(), 0, 1, &stream));
        context_->Flush();
        return sample;
    }

    com_ptr<IMFSample> software_sample(LONGLONG timestamp, LONGLONG interval) {
        com_ptr<IMFMediaBuffer> buffer;
        const DWORD pixels = width_ * height_, bytes = pixels + pixels / 2;
        check_hresult(MFCreateMemoryBuffer(bytes, buffer.put()));
        {
            BYTE* y_plane{};
            check_hresult(buffer->Lock(&y_plane, nullptr, nullptr));
            ScopeExit unlock([&] { buffer->Unlock(); });
            auto* chroma = y_plane + pixels;
            for (UINT y = 0; y < height_; y += 2) {
                for (UINT x = 0; x < width_; x += 2) {
                    const UINT p = y * width_ + x;
                    const std::array<UINT, 4> offsets{p, p + 1, p + width_, p + width_ + 1};
                    int red = 0, green = 0, blue = 0;
                    for (auto index : offsets) {
                        auto color = rgba_[index];
                        int r = color & 255, g = (color >> 8) & 255, b = (color >> 16) & 255;
                        y_plane[index] = static_cast<BYTE>(16 + ((47 * r + 157 * g + 16 * b + 128) >> 8));
                        red += r; green += g; blue += b;
                    }
                    *chroma++ = static_cast<BYTE>(128 + ((-26 * red - 87 * green + 112 * blue + 512) >> 10));
                    *chroma++ = static_cast<BYTE>(128 + ((112 * red - 102 * green - 10 * blue + 512) >> 10));
                }
            }
        }
        check_hresult(buffer->SetCurrentLength(bytes));
        return make_sample(buffer.get(), timestamp, interval);
    }

    template<class Consumer>
    bool output_one(Consumer& consume) {
        MFT_OUTPUT_STREAM_INFO info{};
        check_hresult(encoder_->GetOutputStreamInfo(out_, &info));
        com_ptr<IMFSample> allocated;
        if (!(info.dwFlags & (MFT_OUTPUT_STREAM_PROVIDES_SAMPLES | MFT_OUTPUT_STREAM_CAN_PROVIDE_SAMPLES))) {
            check_hresult(MFCreateSample(allocated.put()));
            com_ptr<IMFMediaBuffer> buffer;
            if (info.cbAlignment) check_hresult(MFCreateAlignedMemoryBuffer(info.cbSize, info.cbAlignment - 1, buffer.put()));
            else check_hresult(MFCreateMemoryBuffer(info.cbSize, buffer.put()));
            check_hresult(allocated->AddBuffer(buffer.get()));
        }
        MFT_OUTPUT_DATA_BUFFER data{out_, allocated.get(), 0, nullptr};
        DWORD status{};
        const auto result = encoder_->ProcessOutput(0, 1, &data, &status);
        if (data.pEvents) data.pEvents->Release();
        com_ptr<IMFSample> output;
        if (data.pSample == allocated.get()) output = allocated;
        else output.attach(data.pSample);
        if (result == MF_E_TRANSFORM_NEED_MORE_INPUT) return false;
        if (result == MF_E_TRANSFORM_STREAM_CHANGE) {
            com_ptr<IMFMediaType> replacement;
            check_hresult(encoder_->GetOutputAvailableType(out_, 0, replacement.put()));
            check_hresult(encoder_->SetOutputType(out_, replacement.get(), 0));
            output_type_ = nullptr;
            return true;
        }
        check_hresult(result);
        if (output) {
            if (!output_type_) check_hresult(encoder_->GetOutputCurrentType(out_, output_type_.put()));
            auto packet = packet_from(output.get());
            if (packet.size) consume(std::move(packet));
        }
        return true;
    }

public:
    Video(FrameChannel& channel, VideoSettings settings, bool prefer_hardware)
        : channel_(channel), settings_(settings), width_(channel.frames->width), height_(channel.frames->height) {
        if (height_ > settings.height) throw std::runtime_error("Invalid capture height");
        if (!prefer_hardware || !open_hardware()) {
            const auto scale = std::min({1.0, 1280.0 / width_, 720.0 / height_});
            width_ = std::max(2u, static_cast<unsigned>(width_ * scale) & ~1u);
            height_ = std::max(2u, static_cast<unsigned>(height_ * scale) & ~1u);
            settings_.fps = std::min(30u, settings_.fps);
            settings_.bitrate = std::min(8'000'000u, settings_.bitrate);
            bgra_ = false;
            if (!open_encoder(nullptr)) throw std::runtime_error("H.264 encoding is unavailable");
        }
        rgba_.resize(static_cast<size_t>(width_) * height_);
    }

    ~Video() {
        encoder_->ProcessMessage(MFT_MESSAGE_COMMAND_FLUSH, 0);
        encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_END_STREAMING, 0);
    }

    bool hardware() const noexcept { return bool(events_); }
    unsigned width() const noexcept { return width_; }
    unsigned height() const noexcept { return height_; }
    unsigned fps() const noexcept { return settings_.fps; }
    const std::wstring& name() const { return name_; }
    void request_key() { force_key_ = true; }
    IMFMediaType* type() const { return output_type_.get(); }

    template <class Consumer>
    bool pump(Consumer&& on_packet) {
        if (!events_) {
            if (software_output_pending_ || draining_) {
                while (output_one(on_packet)) {}
                software_output_pending_ = false;
            }
            return draining_;
        }
        bool drained = false;
        for (;;) {
            com_ptr<IMFMediaEvent> event;
            HRESULT result = events_->GetEvent(MF_EVENT_FLAG_NO_WAIT, event.put());
            if (result == MF_E_NO_EVENTS_AVAILABLE) break;
            check_hresult(result);
            HRESULT status{};
            check_hresult(event->GetStatus(&status));
            check_hresult(status);
            MediaEventType type{};
            check_hresult(event->GetType(&type));
            if (type == METransformNeedInput) ++requests_;
            else if (type == METransformHaveOutput) output_one(on_packet);
            else if (type == METransformDrainComplete) drained = true;
        }
        return drained;
    }

    void submit(LONGLONG origin) {
        if (events_ && !requests_) return;
        auto& frames = *channel_.frames;
        size_t latest = frame_slot_count;
        LONGLONG time = -1;
        for (size_t i = 0; i < frame_slot_count; ++i) {
            auto& slot = frames.slots[i];
            if (InterlockedCompareExchange(&slot.state, frame_ready, frame_ready) == frame_ready && slot.time > time) {
                time = slot.time;
                latest = i;
            }
        }
        if (latest == frame_slot_count) return;
        auto& slot = frames.slots[latest];
        InterlockedExchange(&slot.state, frame_reading);
        ScopeExit release([&] { InterlockedExchange(&slot.state, frame_free); });
        for (auto& older : frames.slots) {
            if (InterlockedCompareExchange(&older.state, frame_ready, frame_ready) == frame_ready && older.time <= time)
                InterlockedExchange(&older.state, frame_free);
        }
        const LONGLONG timestamp = std::max<LONGLONG>(0, time - origin);
        if (timestamp <= last_input_ || !valid_frame_size(slot.width, slot.height) ||
                slot.width > width_ || slot.height > height_) return;
        fill_rgba(channel_.pixels(latest), slot.width, slot.height);
        const LONGLONG interval = second / settings_.fps;
        auto sample = hardware() ? hardware_sample() : software_sample(timestamp, interval);
        if (!sample) return;
        if (hardware()) {
            check_hresult(sample->SetSampleTime(timestamp));
            check_hresult(sample->SetSampleDuration(interval));
        }
        if (force_key_ || last_input_ < 0 || timestamp / second != last_input_ / second) {
            set_codec(CODECAPI_AVEncVideoForceKeyFrame, 1);
            force_key_ = false;
        }
        check_hresult(encoder_->ProcessInput(in_, sample.get(), 0));
        if (events_) --requests_;
        else software_output_pending_ = true;
        last_input_ = timestamp;
    }

    void begin_drain() {
        draining_ = true;
        check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_END_OF_STREAM, 0));
        check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_COMMAND_DRAIN, 0));
    }
};
}
