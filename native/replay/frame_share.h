#pragma once

#include <windows.h>
#include <winrt/base.h>
#include <stdexcept>
#include <string>
#include <string_view>

namespace replay {
inline constexpr size_t frame_slot_count = 3;
inline constexpr LONG shared_frames_version = 1;

enum FrameState : LONG { frame_free, frame_ready, frame_reading };

struct SharedFrame {
    volatile LONG state;
    LONGLONG time;
    UINT64 texture;
};

struct SharedFrames {
    volatile LONG version;
    UINT width, height;
    LUID adapter;
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

struct FrameChannel {
    winrt::handle mapping;
    winrt::handle wake;
    SharedFrames* frames{};

    FrameChannel(std::wstring_view token, bool create) {
        if (!valid_frame_token(token)) throw std::invalid_argument("Invalid frame channel");
        const std::wstring name = L"Local\\GleamReplay-" + std::wstring(token);
        const auto event_name = name + L"-ready";
        if (create) {
            mapping.attach(CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, 0,
                sizeof(SharedFrames), name.c_str()));
            if (!mapping) winrt::throw_last_error();
            if (GetLastError() == ERROR_ALREADY_EXISTS) throw std::runtime_error("Frame channel already exists");
            wake.attach(CreateEventW(nullptr, FALSE, FALSE, event_name.c_str()));
        } else {
            mapping.attach(OpenFileMappingW(FILE_MAP_WRITE, FALSE, name.c_str()));
            if (!mapping) winrt::throw_last_error();
            wake.attach(OpenEventW(SYNCHRONIZE, FALSE, event_name.c_str()));
        }
        if (!wake) winrt::throw_last_error();
        frames = static_cast<SharedFrames*>(MapViewOfFile(mapping.get(), FILE_MAP_WRITE, 0, 0, sizeof(SharedFrames)));
        if (!frames) winrt::throw_last_error();
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
