#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <unknwn.h>
#include "frame_share.h"
#include <d3d11.h>
#include <dxgi.h>
#include <gl/GL.h>
#include <jni.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "opengl32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "windowsapp.lib")
#pragma comment(lib, "oleaut32.lib")

namespace replay {
namespace {
constexpr GLenum draw_framebuffer = 0x8CA9, read_framebuffer = 0x8CA8;
constexpr GLenum draw_framebuffer_binding = 0x8CA6, read_framebuffer_binding = 0x8CAA;
constexpr GLenum color_attachment = 0x8CE0, framebuffer_complete = 0x8CD5;
constexpr GLenum framebuffer_srgb = 0x8DB9, sync_gpu_commands_complete = 0x9117;
constexpr GLenum already_signaled = 0x911A, condition_satisfied = 0x911C, wait_failed = 0x911D;
constexpr GLenum color_buffer = 0x1800, access_write_discard = 0x0002;
using Sync = struct __GLsync*;
using CreateContext = HGLRC (WINAPI*)(HDC, HGLRC, const int*);

template <typename Function>
Function proc(const char* name) {
    auto address = wglGetProcAddress(name);
    const auto value = reinterpret_cast<std::intptr_t>(address);
    if (!value || value == 1 || value == 2 || value == 3 || value == -1)
        throw std::runtime_error("OpenGL GPU sharing is unavailable");
    return reinterpret_cast<Function>(address);
}

struct Gl {
    decltype(&glGenTextures) gen = proc<decltype(gen)>("glGenFramebuffers");
    decltype(&glDeleteTextures) remove = proc<decltype(remove)>("glDeleteFramebuffers");
    decltype(&glBindTexture) bind = proc<decltype(bind)>("glBindFramebuffer");
    void (APIENTRY* attach)(GLenum, GLenum, GLenum, GLuint, GLint) = proc<decltype(attach)>("glFramebufferTexture2D");
    GLenum (APIENTRY* status)(GLenum) = proc<decltype(status)>("glCheckFramebufferStatus");
    void (APIENTRY* blit)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum) = proc<decltype(blit)>("glBlitFramebuffer");
    void (APIENTRY* clear)(GLenum, GLint, const GLfloat*) = proc<decltype(clear)>("glClearBufferfv");
    Sync (APIENTRY* fence)(GLenum, GLbitfield) = proc<decltype(fence)>("glFenceSync");
    GLenum (APIENTRY* poll)(Sync, GLbitfield, UINT64) = proc<decltype(poll)>("glClientWaitSync");
    void (APIENTRY* remove_fence)(Sync) = proc<decltype(remove_fence)>("glDeleteSync");

    void framebuffer(GLenum target, GLuint& id, GLuint texture) const {
        gen(1, &id);
        bind(target, id);
        attach(target, color_attachment, GL_TEXTURE_2D, texture, 0);
        if (target == draw_framebuffer) glDrawBuffer(color_attachment);
        else glReadBuffer(color_attachment);
        if (status(target) != framebuffer_complete) throw std::runtime_error("The recording framebuffer is unavailable");
    }
};

struct WindowContext {
    HWND window{};
    HDC dc{};
    HGLRC context{};

    void open(CreateContext create, HGLRC share, int pixel_format, POINT position) {
        const auto instance = GetModuleHandleW(nullptr);
        constexpr wchar_t window_class[] = L"Gleam Replay GPU Transfer";
        WNDCLASSW info{};
        info.style = CS_OWNDC;
        info.lpfnWndProc = DefWindowProcW;
        info.hInstance = instance;
        info.lpszClassName = window_class;
        if (!RegisterClassW(&info) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) winrt::throw_last_error();
        window = CreateWindowExW(WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE, window_class, L"", WS_POPUP,
            position.x, position.y, 2, 2, nullptr, nullptr, instance, nullptr);
        if (!window) winrt::throw_last_error();
        dc = GetDC(window);
        PIXELFORMATDESCRIPTOR format{};
        if (!dc || !DescribePixelFormat(dc, pixel_format, sizeof(format), &format) || !SetPixelFormat(dc, pixel_format, &format))
            winrt::throw_last_error();
        // WGL_ARB_create_context_profile, OpenGL 3.3 core.
        const int attributes[]{0x2091, 3, 0x2092, 3, 0x9126, 1, 0};
        context = create(dc, share, attributes);
        if (!context || !wglMakeCurrent(dc, context))
            throw std::runtime_error("Could not open the shared OpenGL transfer context");
    }

    ~WindowContext() {
        if (context) {
            wglMakeCurrent(nullptr, nullptr);
            wglDeleteContext(context);
        }
        if (dc) ReleaseDC(window, dc);
        if (window) DestroyWindow(window);
    }
};

class GameFrames : public std::enable_shared_from_this<GameFrames> {
    struct Stage {
        GLuint texture{};
        Sync fence{};
        LONGLONG time{};
        std::atomic_bool queued{};
    };

    struct Transfer {
        const Gl& gl;
        HANDLE (WINAPI* open_device)(void*) = proc<decltype(open_device)>("wglDXOpenDeviceNV");
        BOOL (WINAPI* close_device)(HANDLE) = proc<decltype(close_device)>("wglDXCloseDeviceNV");
        HANDLE (WINAPI* register_texture)(HANDLE, void*, GLuint, GLenum, GLenum) = proc<decltype(register_texture)>("wglDXRegisterObjectNV");
        BOOL (WINAPI* unregister_texture)(HANDLE, HANDLE) = proc<decltype(unregister_texture)>("wglDXUnregisterObjectNV");
        BOOL (WINAPI* lock)(HANDLE, GLint, HANDLE*) = proc<decltype(lock)>("wglDXLockObjectsNV");
        BOOL (WINAPI* unlock)(HANDLE, GLint, HANDLE*) = proc<decltype(unlock)>("wglDXUnlockObjectsNV");
        HANDLE interop{};
        struct Output { HANDLE registration{}; GLuint texture{}, framebuffer{}; bool locked{}; };
        std::array<Output, frame_slot_count> outputs{};
        std::array<GLuint, frame_slot_count> sources{};
        std::array<Stage, frame_slot_count>& stages;

        void open(ID3D11Device* device, const std::array<winrt::com_ptr<ID3D11Texture2D>, frame_slot_count>& textures,
                  int width, int height) {
            interop = open_device(device);
            if (!interop) throw std::runtime_error("The OpenGL renderer and recorder cannot share a GPU device");
            for (size_t i = 0; i < stages.size(); ++i) {
                auto& stage = stages[i];
                glGenTextures(1, &stage.texture);
                glBindTexture(GL_TEXTURE_2D, stage.texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
                gl.framebuffer(read_framebuffer, sources[i], stage.texture);
                auto& output = outputs[i];
                glGenTextures(1, &output.texture);
                output.registration = register_texture(interop, textures[i].get(), output.texture, GL_TEXTURE_2D, access_write_discard);
                acquire(output);
                gl.framebuffer(draw_framebuffer, output.framebuffer, output.texture);
                release(output);
            }
            glFlush();
        }

        void acquire(Output& output) const {
            if (!output.registration || !lock(interop, 1, &output.registration))
                throw std::runtime_error("Could not acquire the shared recording texture");
            output.locked = true;
        }

        void release(Output& output) const {
            if (!unlock(interop, 1, &output.registration)) throw std::runtime_error("Could not publish the game frame");
            output.locked = false;
        }

        ~Transfer() {
            for (size_t i = 0; i < stages.size(); ++i) {
                const auto& stage = stages[i];
                const auto& output = outputs[i];
                if (stage.queued && stage.fence) gl.remove_fence(stage.fence);
                if (sources[i]) gl.remove(1, &sources[i]);
                if (stage.texture) glDeleteTextures(1, &stage.texture);
                if (output.framebuffer) gl.remove(1, &output.framebuffer);
                HANDLE registration = output.registration;
                if (output.locked) unlock(interop, 1, &registration);
                if (registration) unregister_texture(interop, registration);
                if (output.texture) glDeleteTextures(1, &output.texture);
            }
            if (interop) close_device(interop);
        }
    };

    enum class State { starting, running, failed };
    FrameChannel channel_;
    winrt::handle transfer_wake_{CreateEventW(nullptr, FALSE, FALSE, nullptr)};
    winrt::com_ptr<ID3D11Device> device_;
    std::array<winrt::com_ptr<ID3D11Texture2D>, frame_slot_count> textures_;
    std::array<Stage, frame_slot_count> stages_{};
    std::array<GLuint, frame_slot_count> draw_buffers_{};
    std::unique_ptr<Gl> game_gl_;
    HGLRC game_context_{};
    POINT position_{};
    std::atomic<State> state_{State::starting};
    std::atomic_bool stopping_{};
    std::string error_;
    int width_, height_;
    LONGLONG next_{}, interval_;

    void run_transfer(CreateContext create, HGLRC share, int pixel_format) noexcept {
        // WGL interop can block inside the driver. Keep both its calls and the
        // GPU completion of the staging texture off Minecraft's render thread.
        try {
            WindowContext context;
            context.open(create, share, pixel_format, position_);
            Gl gl;
            Transfer transfer{.gl = gl, .stages = stages_};
            transfer.open(device_.get(), textures_, width_, height_);
            state_.store(State::running, std::memory_order_release);
            while (!stopping_.load(std::memory_order_acquire)) {
                auto& shared = *channel_.frames;
                size_t target = frame_slot_count;
                for (size_t i = 0; i < frame_slot_count; ++i) {
                    if (InterlockedCompareExchange(&shared.slots[i].state, frame_free, frame_free) == frame_free) {
                        target = i;
                        break;
                    }
                }
                size_t source = frame_slot_count;
                bool waiting = false;
                for (size_t i = 0; i < frame_slot_count; ++i) {
                    auto& stage = stages_[i];
                    if (!stage.queued.load(std::memory_order_acquire)) continue;
                    waiting = true;
                    if (target == frame_slot_count) break;
                    auto completion = gl.poll(stage.fence, 0, 0);
                    if (completion == wait_failed) throw std::runtime_error("The game frame fence failed");
                    if (completion != already_signaled && completion != condition_satisfied) continue;
                    if (source == frame_slot_count || stage.time < stages_[source].time) source = i;
                }
                if (source == frame_slot_count) {
                    WaitForSingleObject(transfer_wake_.get(), waiting ? 3 : INFINITE);
                    continue;
                }
                auto& stage = stages_[source];
                auto& output = transfer.outputs[target];
                transfer.acquire(output);
                gl.bind(read_framebuffer, transfer.sources[source]);
                gl.bind(draw_framebuffer, output.framebuffer);
                gl.blit(0, 0, width_, height_, 0, 0, width_, height_, GL_COLOR_BUFFER_BIT, GL_NEAREST);
                transfer.release(output);
                gl.remove_fence(stage.fence);
                shared.slots[target].time = stage.time;
                InterlockedExchange(&shared.slots[target].state, frame_ready);
                stage.queued.store(false, std::memory_order_release);
                SetEvent(channel_.wake.get());
            }
        } catch (const winrt::hresult_error& error) {
            error_ = winrt::to_string(error.message());
            state_.store(State::failed, std::memory_order_release);
        } catch (const std::exception& error) {
            error_ = error.what();
            state_.store(State::failed, std::memory_order_release);
        }
    }

public:
    GameFrames(HWND window, int limit, int fps, std::wstring_view token)
        : channel_(token, true), interval_(second / fps) {
        if (!transfer_wake_) winrt::throw_last_error();
        RECT rect{};
        if (!GetClientRect(window, &rect)) winrt::throw_last_error();
        const auto w = rect.right - rect.left, h = rect.bottom - rect.top;
        if (w < 64 || h < 64) throw std::runtime_error("The game framebuffer is too small");
        const auto factor = std::min({1.0, double(limit) / h, 3840.0 / w});
        width_ = static_cast<int>(w * factor) & ~1;
        height_ = static_cast<int>(h * factor) & ~1;
        if (width_ < 64 || height_ < 64) throw std::runtime_error("The game framebuffer is too small");
        MONITORINFO monitor{sizeof(MONITORINFO)};
        if (GetMonitorInfoW(MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST), &monitor))
            position_ = {monitor.rcMonitor.left, monitor.rcMonitor.top};

        winrt::check_hresult(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT,
            nullptr, 0, D3D11_SDK_VERSION, device_.put(), nullptr, nullptr));
        winrt::com_ptr<IDXGIAdapter> adapter;
        winrt::check_hresult(device_.as<IDXGIDevice>()->GetAdapter(adapter.put()));
        DXGI_ADAPTER_DESC adapter_info{};
        winrt::check_hresult(adapter->GetDesc(&adapter_info));
        auto& metadata = *channel_.frames;
        metadata.adapter = adapter_info.AdapterLuid;
        metadata.width = static_cast<UINT>(width_);
        metadata.height = static_cast<UINT>(height_);
        D3D11_TEXTURE2D_DESC desc{};
        desc.Width = metadata.width;
        desc.Height = metadata.height;
        desc.MipLevels = desc.ArraySize = desc.SampleDesc.Count = 1;
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.BindFlags = D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET;
        desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED;
        for (size_t i = 0; i < textures_.size(); ++i) {
            auto& texture = textures_[i];
            winrt::check_hresult(device_->CreateTexture2D(&desc, nullptr, texture.put()));
            HANDLE handle{};
            winrt::check_hresult(texture.as<IDXGIResource>()->GetSharedHandle(&handle));
            metadata.slots[i].texture = reinterpret_cast<UINT64>(handle);
        }
        InterlockedExchange(&metadata.version, shared_frames_version);
    }

    void detach() noexcept {
        if (game_gl_ && wglGetCurrentContext() == game_context_)
            game_gl_->remove(static_cast<GLsizei>(draw_buffers_.size()), draw_buffers_.data());
        stopping_.store(true, std::memory_order_release);
        SetEvent(transfer_wake_.get());
    }

    void capture(int width, int height) {
        if (width < 2 || height < 2) return;
        if (!game_gl_) {
            game_context_ = wglGetCurrentContext();
            if (!game_context_) throw std::runtime_error("The game is not using OpenGL");
            const int pixel_format = GetPixelFormat(wglGetCurrentDC());
            if (!pixel_format) throw std::runtime_error("OpenGL pixel format is unavailable");
            auto create = proc<CreateContext>("wglCreateContextAttribsARB");
            game_gl_ = std::make_unique<Gl>();
            std::thread([self = shared_from_this(), create, context = game_context_, pixel_format] {
                self->run_transfer(create, context, pixel_format);
            }).detach();
            return;
        }
        if (wglGetCurrentContext() != game_context_) throw std::runtime_error("The OpenGL context changed during recording");
        const auto state = state_.load(std::memory_order_acquire);
        if (state == State::failed) throw std::runtime_error(error_);
        if (state != State::running) return;
        const auto now = qpc_time();
        if (now < next_) return;
        for (size_t i = 0; i < stages_.size(); ++i) {
            auto& stage = stages_[i];
            if (stage.queued.load(std::memory_order_acquire)) continue;
            const auto& gl = *game_gl_;
            GLint read{}, draw{}, read_buffer{};
            glGetIntegerv(read_framebuffer_binding, &read);
            glGetIntegerv(draw_framebuffer_binding, &draw);
            if (!draw_buffers_[i]) {
                try { gl.framebuffer(draw_framebuffer, draw_buffers_[i], stage.texture); }
                catch (...) { gl.bind(draw_framebuffer, draw); throw; }
            } else gl.bind(draw_framebuffer, draw_buffers_[i]);
            gl.bind(read_framebuffer, 0);
            glGetIntegerv(GL_READ_BUFFER, &read_buffer);
            glReadBuffer(GL_BACK);
            const bool scissor = glIsEnabled(GL_SCISSOR_TEST) != GL_FALSE;
            const bool srgb = glIsEnabled(framebuffer_srgb) != GL_FALSE;
            if (scissor) glDisable(GL_SCISSOR_TEST);
            if (srgb) glDisable(framebuffer_srgb);
            const auto scale = std::min(double(width_) / width, double(height_) / height);
            const int w = static_cast<int>(width * scale), h = static_cast<int>(height * scale);
            const int x = (width_ - w) / 2, y = (height_ - h) / 2;
            if (w != width_ || h != height_) {
                const GLfloat black[]{0, 0, 0, 1};
                gl.clear(color_buffer, 0, black);
            }
            // Flip the image before D3D consumes it with row zero at the top.
            gl.blit(0, 0, width, height, x, y + h, x + w, y, GL_COLOR_BUFFER_BIT, GL_LINEAR);
            stage.fence = gl.fence(sync_gpu_commands_complete, 0);
            if (srgb) glEnable(framebuffer_srgb);
            if (scissor) glEnable(GL_SCISSOR_TEST);
            glReadBuffer(static_cast<GLenum>(read_buffer));
            gl.bind(read_framebuffer, read);
            gl.bind(draw_framebuffer, draw);
            if (!stage.fence) throw std::runtime_error("Could not queue the game frame");
            stage.time = now;
            stage.queued.store(true, std::memory_order_release);
            SetEvent(transfer_wake_.get());
            next_ = std::max(next_ + interval_, now + interval_ / 2);
            break;
        }
    }
};

std::mutex source_mutex;
std::shared_ptr<GameFrames> requested, attached;
std::atomic_bool requested_capture{};

void fail(JNIEnv* jni, const char* message) {
    if (auto type = jni->FindClass("java/io/IOException")) jni->ThrowNew(type, message);
}
}
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_open(
        JNIEnv* jni, jclass, jlong window, jint height, jint fps, jstring identifier) {
    using namespace replay;
    try {
        if (!identifier || jni->GetStringLength(identifier) != 36) throw std::invalid_argument("Invalid frame channel");
        std::array<jchar, 36> chars{};
        jni->GetStringRegion(identifier, 0, static_cast<jsize>(chars.size()), chars.data());
        if (jni->ExceptionCheck()) return;
        const std::wstring token(chars.begin(), chars.end());
        if (fps < 15 || fps > 60 || height < 144 || height > 2160) throw std::invalid_argument("Invalid capture format");
        auto source = std::make_shared<GameFrames>(reinterpret_cast<HWND>(window), height, fps, token);
        std::lock_guard lock(source_mutex);
        requested = std::move(source);
        requested_capture = true;
    } catch (const winrt::hresult_error& error) {
        fail(jni, winrt::to_string(error.message()).c_str());
    } catch (const std::exception& error) {
        fail(jni, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_stop(JNIEnv*, jclass) {
    using namespace replay;
    std::lock_guard lock(source_mutex);
    requested.reset();
    requested_capture = false;
}

extern "C" JNIEXPORT jboolean JNICALL Java_ru_orion_client_replay_ReplayFrameCapture_frame(
        JNIEnv* jni, jclass, jint width, jint height) {
    using namespace replay;
    if (!attached && !requested_capture.load()) return JNI_FALSE;
    std::shared_ptr<GameFrames> source;
    {
        std::unique_lock lock(source_mutex, std::try_to_lock);
        if (!lock) return JNI_TRUE;
        source = requested;
    }
    if (source != attached) {
        if (attached) attached->detach();
        attached = std::move(source);
    }
    if (!attached) return JNI_FALSE;
    try {
        attached->capture(width, height);
        return JNI_TRUE;
    } catch (const std::exception& error) {
        attached->detach();
        {
            std::lock_guard lock(source_mutex);
            if (requested == attached) {
                requested.reset();
                requested_capture = false;
            }
        }
        attached.reset();
        fail(jni, error.what());
        return JNI_FALSE;
    }
}

#include "playback.h"
