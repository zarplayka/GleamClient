#pragma once

#include <d3d10.h>
#include <mfapi.h>
#include <mfmediaengine.h>
#include <wincodec.h>
#include <cmath>

#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfuuid.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "windowscodecs.lib")

namespace replay {
namespace {
struct MediaApartment {
    MediaApartment() {
        winrt::init_apartment();
        try { winrt::check_hresult(MFStartup(MF_VERSION)); }
        catch (...) { winrt::uninit_apartment(); throw; }
    }

    ~MediaApartment() { MFShutdown(); winrt::uninit_apartment(); }
};

struct PlaybackNotify : winrt::implements<PlaybackNotify, IMFMediaEngineNotify> {
    std::atomic<HRESULT> error{S_OK};
    std::atomic<unsigned> completed_steps{};

    HRESULT __stdcall EventNotify(DWORD event, DWORD_PTR parameter, DWORD detail) noexcept final {
        if (event == MF_MEDIA_ENGINE_EVENT_NOTIFYSTABLESTATE) {
            SetEvent(reinterpret_cast<HANDLE>(parameter));
        } else if (event == MF_MEDIA_ENGINE_EVENT_FRAMESTEPCOMPLETED) {
            ++completed_steps;
        } else if (event == MF_MEDIA_ENGINE_EVENT_ERROR) {
            const auto code = static_cast<HRESULT>(detail);
            error.store(FAILED(code) ? code : E_FAIL);
        }
        return S_OK;
    }
};

class MediaPlayback {
    MediaApartment apartment_;
    winrt::com_ptr<PlaybackNotify> notify_{winrt::make_self<PlaybackNotify>()};
    winrt::com_ptr<ID3D11Device> device_;
    winrt::com_ptr<ID3D11DeviceContext> context_;
    winrt::com_ptr<IMFDXGIDeviceManager> manager_;
    winrt::com_ptr<ID3D11Texture2D> output_, staging_;
    winrt::com_ptr<IWICBitmap> bitmap_;
    winrt::com_ptr<IMFMediaEngine> engine_;
    const UINT width_limit_, height_limit_;
    UINT width_{}, height_{};
    bool frame_available_{}, play_requested_{}, preview_after_seek_{};
    unsigned step_generation_{};
    LONGLONG step_deadline_{};
    double pending_seek_{-1}, speed_{1}, volume_{1};

    void gpu() {
        if (FAILED(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
                D3D11_CREATE_DEVICE_BGRA_SUPPORT | D3D11_CREATE_DEVICE_VIDEO_SUPPORT, nullptr, 0,
                D3D11_SDK_VERSION, device_.put(), nullptr, context_.put()))) return;
        auto threading = device_.try_as<ID3D10Multithread>();
        if (threading) threading->SetMultithreadProtected(TRUE);
        UINT token{};
        winrt::check_hresult(MFCreateDXGIDeviceManager(&token, manager_.put()));
        winrt::check_hresult(manager_->ResetDevice(device_.get(), token));
    }

    void surfaces() {
        if (manager_) {
            D3D11_TEXTURE2D_DESC desc{};
            desc.Width = width_;
            desc.Height = height_;
            desc.MipLevels = desc.ArraySize = 1;
            desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
            desc.SampleDesc.Count = 1;
            desc.BindFlags = D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;
            winrt::check_hresult(device_->CreateTexture2D(&desc, nullptr, output_.put()));
            desc.Usage = D3D11_USAGE_STAGING;
            desc.BindFlags = 0;
            desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
            winrt::check_hresult(device_->CreateTexture2D(&desc, nullptr, staging_.put()));
        } else {
            winrt::com_ptr<IWICImagingFactory> factory;
            winrt::check_hresult(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                    __uuidof(IWICImagingFactory), factory.put_void()));
            winrt::check_hresult(factory->CreateBitmap(width_, height_, GUID_WICPixelFormat32bppBGR,
                    WICBitmapCacheOnLoad, bitmap_.put()));
        }
    }

    void copy_pixels(UINT32* pixels, const BYTE* input, UINT stride) const noexcept {
        for (UINT y = 0; y < height_; ++y) {
            const auto* row = reinterpret_cast<const UINT32*>(input + static_cast<size_t>(y) * stride);
            for (UINT x = 0; x < width_; ++x) {
                const auto color = row[x];
                *pixels++ = 0xff000000u | ((color & 0x000000ffu) << 16)
                        | (color & 0x0000ff00u) | ((color & 0x00ff0000u) >> 16);
            }
        }
    }

    void advance() {
        if (step_deadline_) {
            if (notify_->completed_steps.load() == step_generation_ && qpc_time() < step_deadline_) return;
            step_deadline_ = 0;
        }
        if (engine_->IsSeeking()) return;

        if (!play_requested_ && !engine_->IsPaused()) winrt::check_hresult(engine_->Pause());
        if (pending_seek_ >= 0) {
            winrt::check_hresult(engine_->SetCurrentTime(pending_seek_));
            pending_seek_ = -1;
            preview_after_seek_ = !play_requested_;
            return;
        }
        if (play_requested_) {
            preview_after_seek_ = false;
            if (engine_->IsPaused() && !engine_->IsEnded()) winrt::check_hresult(engine_->Play());
        } else if (preview_after_seek_) {
            preview_after_seek_ = false;
            step_generation_ = notify_->completed_steps.load();
            winrt::check_hresult(engine_.as<IMFMediaEngineEx>()->FrameStep(TRUE));
            step_deadline_ = qpc_time() + second;
        }
    }

public:
    MediaPlayback(const std::wstring& path, UINT width, UINT height, bool hardware)
        : width_limit_(width), height_limit_(height) {
        if (hardware) gpu();
        winrt::com_ptr<IMFAttributes> attrs;
        winrt::check_hresult(MFCreateAttributes(attrs.put(), 3));
        winrt::check_hresult(attrs->SetUnknown(MF_MEDIA_ENGINE_CALLBACK, notify_.get()));
        winrt::check_hresult(attrs->SetUINT32(MF_MEDIA_ENGINE_VIDEO_OUTPUT_FORMAT,
                manager_ ? DXGI_FORMAT_B8G8R8A8_UNORM : DXGI_FORMAT_B8G8R8X8_UNORM));
        if (manager_) winrt::check_hresult(attrs->SetUnknown(MF_MEDIA_ENGINE_DXGI_MANAGER, manager_.get()));
        winrt::com_ptr<IMFMediaEngineClassFactory> factory;
        winrt::check_hresult(CoCreateInstance(CLSID_MFMediaEngineClassFactory, nullptr, CLSCTX_INPROC_SERVER,
                __uuidof(IMFMediaEngineClassFactory), factory.put_void()));
        winrt::check_hresult(factory->CreateInstance(0, attrs.get(), engine_.put()));
        try {
            winrt::com_ptr<IMFByteStream> stream;
            winrt::check_hresult(MFCreateFile(MF_ACCESSMODE_READ, MF_OPENMODE_FAIL_IF_NOT_EXIST,
                    MF_FILEFLAGS_NONE, path.c_str(), stream.put()));
            const auto ex = engine_.as<IMFMediaEngineEx>();
            engine_->SetPreload(MF_MEDIA_ENGINE_PRELOAD_AUTOMATIC);
            BSTR url = SysAllocString(L"clip.mp4");
            if (!url) throw std::bad_alloc();
            const auto result = ex->SetSourceFromByteStream(stream.get(), url);
            SysFreeString(url);
            winrt::check_hresult(result);
        } catch (...) {
            engine_->Shutdown();
            throw;
        }
    }

    ~MediaPlayback() { engine_->Shutdown(); }

    jlong size() {
        winrt::check_hresult(notify_->error.load());
        if (!width_ && engine_->GetReadyState() >= MF_MEDIA_ENGINE_READY_HAVE_METADATA) {
            DWORD width{}, height{};
            winrt::check_hresult(engine_->GetNativeVideoSize(&width, &height));
            if (!width || !height) throw std::runtime_error("Replay has no video track");
            const auto scale = std::min({1.0, double(width_limit_) / width, double(height_limit_) / height});
            width_ = std::max(2u, static_cast<UINT>(width * scale));
            height_ = std::max(2u, static_cast<UINT>(height * scale));
            surfaces();
        }
        return (static_cast<jlong>(width_) << 32) | height_;
    }

    void control(bool play, double seek, double rate, double volume) {
        if (volume_ != volume) {
            winrt::check_hresult(engine_->SetVolume(volume));
            volume_ = volume;
        }
        if (speed_ != rate) {
            winrt::check_hresult(engine_->SetPlaybackRate(rate));
            speed_ = rate;
        }
        play_requested_ = play;
        if (seek >= 0) pending_seek_ = seek;
    }

    jlong poll() {
        winrt::check_hresult(notify_->error.load());
        advance();
        LONGLONG pts{};
        frame_available_ = engine_->OnVideoStreamTick(&pts) == S_OK;
        const bool seeking = engine_->IsSeeking() || pending_seek_ >= 0 || preview_after_seek_
                || (step_deadline_ && !frame_available_);
        const bool ended = engine_->IsEnded();
        const double seconds = engine_->GetCurrentTime();
        const auto millis = std::isfinite(seconds) && seconds > 0 ? static_cast<jlong>(seconds * 1000) : 0;
        return (millis << 4) | (frame_available_ ? 1 : 0)
                | (play_requested_ && !ended ? 2 : 0) | (ended && !seeking ? 4 : 0) | (seeking ? 8 : 0);
    }

    void frame(UINT32* pixels) {
        if (!frame_available_) return;
        frame_available_ = false;
        RECT rect{0, 0, static_cast<LONG>(width_), static_cast<LONG>(height_)};
        MFARGB background{};
        if (output_) {
            winrt::check_hresult(engine_->TransferVideoFrame(output_.get(), nullptr, &rect, &background));
            context_->CopyResource(staging_.get(), output_.get());
            D3D11_MAPPED_SUBRESOURCE mapped{};
            winrt::check_hresult(context_->Map(staging_.get(), 0, D3D11_MAP_READ, 0, &mapped));
            copy_pixels(pixels, static_cast<const BYTE*>(mapped.pData), mapped.RowPitch);
            context_->Unmap(staging_.get(), 0);
        } else {
            winrt::check_hresult(engine_->TransferVideoFrame(bitmap_.get(), nullptr, &rect, &background));
            winrt::check_hresult(bitmap_->CopyPixels(nullptr, width_ * 4, width_ * height_ * 4, reinterpret_cast<BYTE*>(pixels)));
            copy_pixels(pixels, reinterpret_cast<const BYTE*>(pixels), width_ * 4);
        }
    }

    jlong byte_size() const noexcept { return static_cast<jlong>(width_) * height_ * 4; }
};

template<typename Result, typename Function>
Result playback_call(JNIEnv* jni, Result error_value, Function&& function) {
    try { return function(); }
    catch (const winrt::hresult_error& error) { fail(jni, winrt::to_string(error.message()).c_str()); }
    catch (const std::exception& error) { fail(jni, error.what()); }
    return error_value;
}
}
}

extern "C" JNIEXPORT jlong JNICALL Java_ru_orion_client_replay_ReplayPlayback_openPlayer(
        JNIEnv* jni, jclass, jstring path, jint width, jint height, jboolean hardware) {
    using namespace replay;
    return playback_call(jni, jlong{}, [&] {
        if (!path || width < 2 || width > 3840 || height < 2 || height > 2160)
            throw std::invalid_argument("Invalid replay player format");
        const auto length = jni->GetStringLength(path);
        std::wstring file(length, L'\0');
        static_assert(sizeof(wchar_t) == sizeof(jchar));
        jni->GetStringRegion(path, 0, length, reinterpret_cast<jchar*>(file.data()));
        if (jni->ExceptionCheck()) return jlong{};
        return reinterpret_cast<jlong>(new MediaPlayback(file, width, height, hardware == JNI_TRUE));
    });
}

extern "C" JNIEXPORT jlong JNICALL Java_ru_orion_client_replay_ReplayPlayback_playerSize(
        JNIEnv* jni, jclass, jlong player) {
    return replay::playback_call(jni, jlong{}, [&] { return reinterpret_cast<replay::MediaPlayback*>(player)->size(); });
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayPlayback_controlPlayer(
        JNIEnv* jni, jclass, jlong player, jboolean play, jdouble seek, jdouble rate, jdouble volume) {
    replay::playback_call(jni, false, [&] {
        reinterpret_cast<replay::MediaPlayback*>(player)->control(play == JNI_TRUE, seek, rate, volume);
        return true;
    });
}

extern "C" JNIEXPORT jlong JNICALL Java_ru_orion_client_replay_ReplayPlayback_pollPlayer(
        JNIEnv* jni, jclass, jlong player) {
    return replay::playback_call(jni, jlong{}, [&] { return reinterpret_cast<replay::MediaPlayback*>(player)->poll(); });
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayPlayback_copyPlayerFrame(
        JNIEnv* jni, jclass, jlong player, jobject output) {
    replay::playback_call(jni, false, [&] {
        auto playback = reinterpret_cast<replay::MediaPlayback*>(player);
        auto pixels = static_cast<UINT32*>(jni->GetDirectBufferAddress(output));
        if (!pixels || jni->GetDirectBufferCapacity(output) < playback->byte_size())
            throw std::invalid_argument("Replay player frame buffer is too small");
        playback->frame(pixels);
        return true;
    });
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayPlayback_closePlayer(
        JNIEnv*, jclass, jlong player) {
    delete reinterpret_cast<replay::MediaPlayback*>(player);
}
