#pragma once

#include <windows.h>
#include <winrt/base.h>
#include <stdexcept>
#include <string>
#include <string_view>

namespace replay {
inline constexpr size_t frame_slot_count = 3;
inline constexpr LONG shared_frames_version = 2;

enum FrameState : LONG { frame_free, frame_writing, frame_ready, frame_reading };

struct SharedFrame {
    volatile LONG state;
    UINT width, height;
    LONGLONG time;
};

struct SharedFrames {
    volatile LONG version;
    UINT width, height;
    SharedFrame slots[frame_slot_count];
};

inline bool valid_frame_token(std::wstring_view token) {
    if (token.size() != 36) return false;
    for (wchar_t character : token) {
        if (character != L'-' && (character < L'0' || character > L'9') &&
            (character < L'a' || character > L'f')) return false;
    }
    return true;
}

inline bool valid_frame_size(UINT width, UINT height) {
    return width >= 2 && width <= 3840 && height >= 2 && height <= 2160 && !(width & 1) && !(height & 1);
}

struct FrameChannel {
    winrt::handle mapping;
    winrt::handle wake;
    SharedFrames* frames{};

    explicit FrameChannel(std::wstring_view token, UINT width = 0, UINT height = 0) {
        if (!valid_frame_token(token)) throw std::invalid_argument("Invalid frame channel");
        const std::wstring name = L"Local\\GleamReplay-" + std::wstring(token);
        const auto event_name = name + L"-ready";
        if (width || height) {
            if (!valid_frame_size(width, height)) throw std::invalid_argument("Invalid frame dimensions");
            const auto bytes = static_cast<DWORD>(sizeof(SharedFrames) + frame_slot_count * width * height * 4);
            mapping.attach(CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, 0, bytes, name.c_str()));
            if (!mapping) winrt::throw_last_error();
            if (GetLastError() == ERROR_ALREADY_EXISTS) throw std::runtime_error("Frame channel already exists");
            wake.attach(CreateEventW(nullptr, FALSE, FALSE, event_name.c_str()));
        } else {
            mapping.attach(OpenFileMappingW(FILE_MAP_WRITE, FALSE, name.c_str()));
            if (!mapping) winrt::throw_last_error();
            wake.attach(OpenEventW(SYNCHRONIZE, FALSE, event_name.c_str()));
        }
        if (!wake) winrt::throw_last_error();
        frames = static_cast<SharedFrames*>(MapViewOfFile(mapping.get(), FILE_MAP_WRITE, 0, 0, 0));
        if (!frames) winrt::throw_last_error();
        if (width) {
            frames->width = width;
            frames->height = height;
            InterlockedExchange(&frames->version, shared_frames_version);
        } else {
            MEMORY_BASIC_INFORMATION view{};
            const bool valid = InterlockedCompareExchange(&frames->version, 0, 0) == shared_frames_version
                && valid_frame_size(frames->width, frames->height)
                && VirtualQuery(frames, &view, sizeof(view))
                && view.RegionSize >= sizeof(SharedFrames) + frame_slot_count * frame_bytes();
            if (!valid) {
                UnmapViewOfFile(frames);
                throw std::runtime_error("Invalid capture frame mapping");
            }
        }
    }

    size_t frame_bytes() const noexcept { return static_cast<size_t>(frames->width) * frames->height * 4; }
    BYTE* pixels(size_t slot) const noexcept {
        return reinterpret_cast<BYTE*>(frames + 1) + slot * frame_bytes();
    }

    FrameChannel(const FrameChannel&) = delete;
    FrameChannel& operator=(const FrameChannel&) = delete;
    ~FrameChannel() { UnmapViewOfFile(frames); }
};

inline constexpr LONGLONG second = 10'000'000;
inline LONGLONG qpc_time() {
    static const LONGLONG frequency = [] { LARGE_INTEGER value; QueryPerformanceFrequency(&value); return value.QuadPart; }();
    LARGE_INTEGER current;
    QueryPerformanceCounter(&current);
    return current.QuadPart / frequency * second + current.QuadPart % frequency * second / frequency;
}
}
