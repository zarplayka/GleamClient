#pragma once

#include <windows.h>
#include <d3d11_4.h>
#include <dxgi1_2.h>
#include <dwmapi.h>
#include <mfapi.h>
#include <mferror.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <codecapi.h>
#include <icodecapi.h>
#include <windows.graphics.capture.interop.h>
#include <windows.graphics.directx.direct3d11.interop.h>
#include <winrt/Windows.Graphics.Capture.h>
#include <winrt/Windows.Graphics.DirectX.Direct3D11.h>
#include <winrt/Windows.Foundation.Metadata.h>
#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>

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
#pragma comment(lib, "dwmapi.lib")

namespace replay {
using winrt::check_hresult;
using winrt::com_ptr;
using namespace winrt::Windows::Graphics;
using namespace winrt::Windows::Graphics::Capture;
using namespace winrt::Windows::Graphics::DirectX;
using namespace winrt::Windows::Graphics::DirectX::Direct3D11;

constexpr LONGLONG second = 10'000'000;

template <typename Cleanup>
struct ScopeExit {
    Cleanup cleanup;
    explicit ScopeExit(Cleanup action) : cleanup(std::move(action)) {}
    ScopeExit(const ScopeExit&) = delete;
    ~ScopeExit() { cleanup(); }
};

inline LONGLONG qpc_time() {
    static const LONGLONG frequency = [] { LARGE_INTEGER value; QueryPerformanceFrequency(&value); return value.QuadPart; }();
    LARGE_INTEGER current;
    QueryPerformanceCounter(&current);
    return current.QuadPart / frequency * second + current.QuadPart % frequency * second / frequency;
}

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
    HWND window_;
    winrt::handle wake_;
    GraphicsCaptureItem item_{nullptr};
    Direct3D11CaptureFramePool frame_pool_{nullptr};
    GraphicsCaptureSession capture_{nullptr};
    Direct3D11CaptureFramePool::FrameArrived_revoker arrived_;
    IDirect3DDevice projected_{nullptr};
    SizeInt32 capture_size_{};
    com_ptr<ID3D11Device> device_;
    com_ptr<ID3D11DeviceContext> context_;
    com_ptr<ID3D11VideoDevice> video_device_;
    com_ptr<ID3D11VideoContext> video_context_;
    com_ptr<ID3D11VideoProcessorEnumerator> processor_format_;
    com_ptr<ID3D11VideoProcessor> processor_;
    com_ptr<IMFDXGIDeviceManager> device_manager_;
    com_ptr<IMFVideoSampleAllocatorEx> allocator_;
    com_ptr<IMFTransform> encoder_;
    com_ptr<IMFMediaEventGenerator> events_;
    com_ptr<IMFMediaType> output_type_;
    com_ptr<ICodecAPI> codec_;
    VideoSettings settings_;
    unsigned width_{}, height_{};
    DWORD in_{}, out_{};
    size_t requests_{};
    LONGLONG next_frame_{}, last_input_{-1};
    bool force_key_ = false;
    std::wstring name_;

    RECT content_rect(SizeInt32 size) const {
        RECT full{0, 0, size.Width, size.Height}, client{}, window{}, extended{};
        if (!GetClientRect(window_, &client)) return full;
        POINT corner{client.left, client.top}, other{client.right, client.bottom};
        if (!ClientToScreen(window_, &corner) || !ClientToScreen(window_, &other)) return full;
        GetWindowRect(window_, &window);
        DwmGetWindowAttribute(window_, DWMWA_EXTENDED_FRAME_BOUNDS, &extended, sizeof(extended));
        for (const RECT& bounds : {extended, window}) {
            if (bounds.right - bounds.left != size.Width || bounds.bottom - bounds.top != size.Height) continue;
            if (corner.x < bounds.left || corner.y < bounds.top || other.x > bounds.right || other.y > bounds.bottom) continue;
            if (other.x - corner.x < 2 || other.y - corner.y < 2) continue;
            return {corner.x - bounds.left, corner.y - bounds.top, other.x - bounds.left, other.y - bounds.top};
        }
        return full;
    }

    void geometry(SizeInt32 size) {
        processor_ = nullptr;
        processor_format_ = nullptr;
        D3D11_VIDEO_PROCESSOR_CONTENT_DESC description{};
        description.InputFrameFormat = D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE;
        description.InputWidth = static_cast<UINT>(size.Width);
        description.InputHeight = static_cast<UINT>(size.Height);
        description.OutputWidth = width_;
        description.OutputHeight = height_;
        description.InputFrameRate = description.OutputFrameRate = {settings_.fps, 1};
        description.Usage = D3D11_VIDEO_USAGE_OPTIMAL_SPEED;
        check_hresult(video_device_->CreateVideoProcessorEnumerator(&description, processor_format_.put()));
        check_hresult(video_device_->CreateVideoProcessor(processor_format_.get(), 0, processor_.put()));

        RECT src = content_rect(size);
        LONG content_width = src.right - src.left, content_height = src.bottom - src.top;
        double scale = std::min(double(width_) / content_width, double(height_) / content_height);
        LONG w = static_cast<LONG>(content_width * scale) & ~1L;
        LONG h = static_cast<LONG>(content_height * scale) & ~1L;
        LONG x = (static_cast<LONG>(width_) - w) / 2;
        LONG y = (static_cast<LONG>(height_) - h) / 2;
        RECT dst{x, y, x + w, y + h};
        RECT full{0, 0, static_cast<LONG>(width_), static_cast<LONG>(height_)};
        video_context_->VideoProcessorSetStreamSourceRect(processor_.get(), 0, TRUE, &src);
        video_context_->VideoProcessorSetStreamDestRect(processor_.get(), 0, TRUE, &dst);
        video_context_->VideoProcessorSetOutputTargetRect(processor_.get(), TRUE, &full);
        video_context_->VideoProcessorSetStreamAutoProcessingMode(processor_.get(), 0, FALSE);
    }

    auto video_type(GUID subtype) const {
        auto type = media_type();
        check_hresult(type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video));
        check_hresult(type->SetGUID(MF_MT_SUBTYPE, subtype));
        check_hresult(MFSetAttributeSize(type.get(), MF_MT_FRAME_SIZE, width_, height_));
        check_hresult(MFSetAttributeRatio(type.get(), MF_MT_FRAME_RATE, settings_.fps, 1));
        check_hresult(MFSetAttributeRatio(type.get(), MF_MT_PIXEL_ASPECT_RATIO, 1, 1));
        check_hresult(type->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive));
        return type;
    }

    void set_codec(const GUID& id, ULONG value) {
        if (!codec_) return;
        VARIANT option{};
        option.vt = VT_UI4;
        option.ulVal = value;
        codec_->SetValue(&id, &option);
    }

    void open_encoder() {
        auto dxgi_device = device_.as<IDXGIDevice>();
        com_ptr<IDXGIAdapter> adapter;
        check_hresult(dxgi_device->GetAdapter(adapter.put()));
        DXGI_ADAPTER_DESC adapter_info{};
        check_hresult(adapter->GetDesc(&adapter_info));
        auto filter = attributes(1);
        check_hresult(filter->SetBlob(MFT_ENUM_ADAPTER_LUID, reinterpret_cast<UINT8*>(&adapter_info.AdapterLuid), sizeof(LUID)));
        MFT_REGISTER_TYPE_INFO input{MFMediaType_Video, MFVideoFormat_NV12};
        MFT_REGISTER_TYPE_INFO output{MFMediaType_Video, MFVideoFormat_H264};
        IMFActivate** activations{};
        UINT32 count{};
        check_hresult(MFTEnum2(MFT_CATEGORY_VIDEO_ENCODER, MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER,
                               &input, &output, filter.get(), &activations, &count));
        ScopeExit release([&] {
            for (UINT32 i = 0; i < count; ++i) activations[i]->Release();
            CoTaskMemFree(activations);
        });
        UINT token{};
        check_hresult(MFCreateDXGIDeviceManager(&token, device_manager_.put()));
        check_hresult(device_manager_->ResetDevice(device_.get(), token));
        check_hresult(MFCreateVideoSampleAllocatorEx(__uuidof(IMFVideoSampleAllocatorEx), allocator_.put_void()));
        check_hresult(allocator_->SetDirectXManager(device_manager_.get()));
        auto allocation = attributes(1);
        check_hresult(allocation->SetUINT32(MF_SA_D3D11_BINDFLAGS, D3D11_BIND_RENDER_TARGET));
        auto uncompressed = video_type(MFVideoFormat_NV12);
        check_hresult(allocator_->InitializeSampleAllocatorEx(4, 8, allocation.get(), uncompressed.get()));

        for (UINT32 i = 0; i < count; ++i) {
            try {
                check_hresult(activations[i]->ActivateObject(__uuidof(IMFTransform), encoder_.put_void()));
                com_ptr<IMFAttributes> encoder_attributes;
                check_hresult(encoder_->GetAttributes(encoder_attributes.put()));
                check_hresult(encoder_attributes->SetUINT32(MF_TRANSFORM_ASYNC_UNLOCK, TRUE));
                check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_SET_D3D_MANAGER,
                                                        reinterpret_cast<ULONG_PTR>(device_manager_.get())));
                in_ = out_ = 0;
                HRESULT streams = encoder_->GetStreamIDs(1, &in_, 1, &out_);
                if (streams != E_NOTIMPL) check_hresult(streams);
                codec_ = encoder_.try_as<ICodecAPI>();
                set_codec(CODECAPI_AVEncMPVDefaultBPictureCount, 0);
                set_codec(CODECAPI_AVEncMPVGOPSize, settings_.fps);
                set_codec(CODECAPI_AVEncCommonMeanBitRate, settings_.bitrate);
                set_codec(CODECAPI_AVEncCommonQualityVsSpeed, settings_.quality);
                if (codec_) {
                    VARIANT low_latency{};
                    low_latency.vt = VT_BOOL;
                    low_latency.boolVal = VARIANT_TRUE;
                    codec_->SetValue(&CODECAPI_AVLowLatencyMode, &low_latency);
                }
                auto compressed = video_type(MFVideoFormat_H264);
                check_hresult(compressed->SetUINT32(MF_MT_AVG_BITRATE, settings_.bitrate));
                check_hresult(compressed->SetUINT32(MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_Main));
                check_hresult(encoder_->SetOutputType(out_, compressed.get(), 0));
                check_hresult(encoder_->SetInputType(in_, uncompressed.get(), 0));
                events_ = encoder_.as<IMFMediaEventGenerator>();
                check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0));
                check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM, 0));
                LPWSTR friendly{};
                UINT32 length{};
                if (SUCCEEDED(activations[i]->GetAllocatedString(MFT_FRIENDLY_NAME_Attribute, &friendly, &length))) {
                    name_.assign(friendly, length);
                    CoTaskMemFree(friendly);
                }
                return;
            } catch (const winrt::hresult_error&) {
                events_ = nullptr;
                codec_ = nullptr;
                encoder_ = nullptr;
                activations[i]->ShutdownObject();
            }
        }
        throw std::runtime_error("No DXGI H.264 hardware encoder for the capture GPU");
    }

public:
    Video(HWND window, VideoSettings settings)
        : window_(window), wake_(CreateEventW(nullptr, FALSE, FALSE, nullptr)), settings_(settings) {
        if (!wake_) winrt::throw_last_error();
        if (!GraphicsCaptureSession::IsSupported()) throw std::runtime_error("Windows Graphics Capture unavailable");
        auto factory = winrt::get_activation_factory<GraphicsCaptureItem, IGraphicsCaptureItemInterop>();
        check_hresult(factory->CreateForWindow(window, winrt::guid_of<GraphicsCaptureItem>(), winrt::put_abi(item_)));
        capture_size_ = item_.Size();
        if (capture_size_.Width < 2 || capture_size_.Height < 2) throw std::runtime_error("Window has no capturable content");
        auto source = content_rect(capture_size_);
        auto content_width = source.right - source.left, content_height = source.bottom - source.top;
        double factor = std::min({1.0, double(settings.height) / content_height, 3840.0 / content_width});
        width_ = std::max(2u, static_cast<unsigned>(content_width * factor) & ~1u);
        height_ = std::max(2u, static_cast<unsigned>(content_height * factor) & ~1u);
        if (width_ < 64 || height_ < 64) throw std::runtime_error("The capture window is too small");

        check_hresult(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
            D3D11_CREATE_DEVICE_BGRA_SUPPORT | D3D11_CREATE_DEVICE_VIDEO_SUPPORT, nullptr, 0, D3D11_SDK_VERSION,
            device_.put(), nullptr, context_.put()));
        context_.as<ID3D11Multithread>()->SetMultithreadProtected(TRUE);
        video_device_ = device_.as<ID3D11VideoDevice>();
        video_context_ = context_.as<ID3D11VideoContext>();
        auto dxgi = device_.as<IDXGIDevice>();
        winrt::com_ptr<IInspectable> inspectable;
        check_hresult(CreateDirect3D11DeviceFromDXGIDevice(dxgi.get(), inspectable.put()));
        projected_ = inspectable.as<IDirect3DDevice>();
        geometry(capture_size_);
        open_encoder();
        frame_pool_ = Direct3D11CaptureFramePool::CreateFreeThreaded(projected_, DirectXPixelFormat::B8G8R8A8UIntNormalized, 2, capture_size_);
        winrt::handle event;
        if (!DuplicateHandle(GetCurrentProcess(), wake_.get(), GetCurrentProcess(), event.put(), 0, FALSE, DUPLICATE_SAME_ACCESS))
            winrt::throw_last_error();
        auto shared_wake = std::make_shared<winrt::handle>(std::move(event));
        arrived_ = frame_pool_.FrameArrived(winrt::auto_revoke, [shared_wake](auto const&, auto const&) { SetEvent(shared_wake->get()); });
        capture_ = frame_pool_.CreateCaptureSession(item_);
        auto supports = [](const wchar_t* name) {
            return winrt::Windows::Foundation::Metadata::ApiInformation::IsPropertyPresent(
                L"Windows.Graphics.Capture.GraphicsCaptureSession", name);
        };
        if (supports(L"IsCursorCaptureEnabled")) capture_.IsCursorCaptureEnabled(false);
        if (supports(L"IncludeSecondaryWindows")) capture_.IncludeSecondaryWindows(false);
        if (supports(L"MinUpdateInterval")) capture_.MinUpdateInterval(winrt::Windows::Foundation::TimeSpan(second / settings_.fps));
    }

    ~Video() {
        arrived_.revoke();
        try {
            if (capture_) capture_.Close();
            if (frame_pool_) frame_pool_.Close();
        } catch (...) {}
        if (encoder_) {
            encoder_->ProcessMessage(MFT_MESSAGE_COMMAND_FLUSH, 0);
            encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_END_STREAMING, 0);
        }
    }

    void start() { capture_.StartCapture(); }
    HANDLE wake() const { return wake_.get(); }
    const std::wstring& name() const { return name_; }
    void request_key() { force_key_ = true; }
    IMFMediaType* type() const { return output_type_.get(); }

    template <class Consumer>
    bool pump(Consumer&& on_packet) {
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
            if (type == METransformDrainComplete) drained = true;
            if (type != METransformHaveOutput) continue;

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
            DWORD output_status{};
            result = encoder_->ProcessOutput(0, 1, &data, &output_status);
            if (data.pEvents) data.pEvents->Release();
            com_ptr<IMFSample> output;
            if (data.pSample == allocated.get()) output = allocated;
            else output.attach(data.pSample);
            if (result == MF_E_TRANSFORM_STREAM_CHANGE) {
                com_ptr<IMFMediaType> type_change;
                check_hresult(encoder_->GetOutputAvailableType(out_, 0, type_change.put()));
                check_hresult(encoder_->SetOutputType(out_, type_change.get(), 0));
                output_type_ = nullptr;
                continue;
            }
            check_hresult(result);
            if (!output) continue;
            if (!output_type_) check_hresult(encoder_->GetOutputCurrentType(out_, output_type_.put()));
            auto packet = packet_from(output.get());
            if (packet.size) on_packet(std::move(packet));
        }
        return drained;
    }

    void submit(LONGLONG origin) {
        auto frame = frame_pool_.TryGetNextFrame();
        if (!frame) return;
        if (auto latest = frame_pool_.TryGetNextFrame()) frame = std::move(latest);
        SizeInt32 size = frame.ContentSize();
        if (size.Width < 2 || size.Height < 2) return;
        if (size.Width != capture_size_.Width || size.Height != capture_size_.Height) {
            frame.Close();
            capture_size_ = size;
            frame_pool_.Recreate(projected_, DirectXPixelFormat::B8G8R8A8UIntNormalized, 2, size);
            geometry(size);
            return;
        }
        LONGLONG interval = second / settings_.fps;
        LONGLONG timestamp = std::max<LONGLONG>(0, frame.SystemRelativeTime().count() - origin);
        if (!requests_ || timestamp + interval / 6 < next_frame_ || timestamp <= last_input_) return;
        com_ptr<IMFSample> sample;
        HRESULT allocated = allocator_->AllocateSample(sample.put());
        if (allocated == MF_E_SAMPLEALLOCATOR_EMPTY) return;
        check_hresult(allocated);
        com_ptr<IMFMediaBuffer> buffer;
        check_hresult(sample->GetBufferByIndex(0, buffer.put()));
        auto target_buffer = buffer.as<IMFDXGIBuffer>();
        com_ptr<ID3D11Texture2D> target, source;
        check_hresult(target_buffer->GetResource(__uuidof(ID3D11Texture2D), target.put_void()));
        check_hresult(frame.Surface().as<::Windows::Graphics::DirectX::Direct3D11::IDirect3DDxgiInterfaceAccess>()
            ->GetInterface(__uuidof(ID3D11Texture2D), source.put_void()));
        UINT subresource{};
        check_hresult(target_buffer->GetSubresourceIndex(&subresource));

        com_ptr<ID3D11VideoProcessorInputView> input;
        D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC input_desc{};
        input_desc.ViewDimension = D3D11_VPIV_DIMENSION_TEXTURE2D;
        check_hresult(video_device_->CreateVideoProcessorInputView(source.get(), processor_format_.get(), &input_desc, input.put()));
        com_ptr<ID3D11VideoProcessorOutputView> output;
        D3D11_TEXTURE2D_DESC texture_desc{};
        target->GetDesc(&texture_desc);
        D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC output_desc{};
        if (texture_desc.ArraySize > 1) {
            output_desc.ViewDimension = D3D11_VPOV_DIMENSION_TEXTURE2DARRAY;
            output_desc.Texture2DArray.FirstArraySlice = subresource / texture_desc.MipLevels;
            output_desc.Texture2DArray.ArraySize = 1;
        } else output_desc.ViewDimension = D3D11_VPOV_DIMENSION_TEXTURE2D;
        check_hresult(video_device_->CreateVideoProcessorOutputView(target.get(), processor_format_.get(), &output_desc, output.put()));
        D3D11_VIDEO_PROCESSOR_STREAM stream{};
        stream.Enable = TRUE;
        stream.pInputSurface = input.get();
        check_hresult(video_context_->VideoProcessorBlt(processor_.get(), output.get(), 0, 1, &stream));
        check_hresult(sample->SetSampleTime(timestamp));
        check_hresult(sample->SetSampleDuration(interval));
        if (force_key_ || last_input_ < 0 || timestamp / second != last_input_ / second) {
            set_codec(CODECAPI_AVEncVideoForceKeyFrame, 1);
            force_key_ = false;
        }
        check_hresult(encoder_->ProcessInput(in_, sample.get(), 0));
        --requests_;
        last_input_ = timestamp;
        next_frame_ = std::max(next_frame_ + interval, timestamp + interval / 2);
    }

    void begin_drain() {
        capture_.Close();
        check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_NOTIFY_END_OF_STREAM, 0));
        check_hresult(encoder_->ProcessMessage(MFT_MESSAGE_COMMAND_DRAIN, 0));
    }
};
}
