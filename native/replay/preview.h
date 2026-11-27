#pragma once

#include "storage.h"
#include <fcntl.h>
#include <io.h>
#include <span>

namespace replay {
// The pipe carries timestamps and PCM. One shared pixel buffer is released by an ACK on stdin.
inline void preview(const std::filesystem::path& path, LONGLONG from, bool still,
                    unsigned width_limit, unsigned height_limit, unsigned fps, std::wstring_view token) {
    if (_setmode(_fileno(stdout), _O_BINARY) == -1) throw std::runtime_error("Cannot open preview pipe");
    if (!token.empty() && (!valid_frame_token(token) || _setmode(_fileno(stdin), _O_BINARY) == -1))
        throw std::runtime_error("Invalid preview channel");
    auto attributes = replay::attributes(2);
    check_hresult(attributes->SetUINT32(MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, TRUE));
    check_hresult(attributes->SetUINT32(MF_SOURCE_READER_ENABLE_ADVANCED_VIDEO_PROCESSING, TRUE));
    com_ptr<IMFSourceReader> reader;
    check_hresult(MFCreateSourceReaderFromURL(path.c_str(), attributes.get(), reader.put()));
    constexpr DWORD all = static_cast<DWORD>(MF_SOURCE_READER_ALL_STREAMS);
    constexpr DWORD first_video = static_cast<DWORD>(MF_SOURCE_READER_FIRST_VIDEO_STREAM);
    constexpr DWORD first_audio = static_cast<DWORD>(MF_SOURCE_READER_FIRST_AUDIO_STREAM);
    constexpr DWORD any = static_cast<DWORD>(MF_SOURCE_READER_ANY_STREAM);
    check_hresult(reader->SetStreamSelection(all, FALSE));
    check_hresult(reader->SetStreamSelection(first_video, TRUE));
    com_ptr<IMFMediaType> original;
    check_hresult(reader->GetNativeMediaType(first_video, 0, original.put()));
    UINT32 w{}, h{};
    check_hresult(MFGetAttributeSize(original.get(), MF_MT_FRAME_SIZE, &w, &h));
    if (!w || !h) throw std::runtime_error("Invalid preview size");
    UINT32 fps_num{}, fps_den{};
    const bool limit_fps = !still &&
        (FAILED(MFGetAttributeRatio(original.get(), MF_MT_FRAME_RATE, &fps_num, &fps_den)) ||
         !fps_num || !fps_den || fps_num > UINT64(fps) * fps_den);
    double scale = std::min({1.0, double(width_limit) / w, double(height_limit) / h});
    w = std::max(2u, static_cast<UINT32>(w * scale) & ~1u);
    h = std::max(2u, static_cast<UINT32>(h * scale) & ~1u);
    auto decoded = media_type();
    check_hresult(decoded->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video));
    check_hresult(decoded->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32));
    check_hresult(MFSetAttributeSize(decoded.get(), MF_MT_FRAME_SIZE, w, h));
    check_hresult(reader->SetCurrentMediaType(first_video, nullptr, decoded.get()));
    com_ptr<IMFMediaType> audio;
    if (!still && SUCCEEDED(reader->GetNativeMediaType(first_audio, 0, audio.put()))) {
        auto pcm = pcm_type();
        if (SUCCEEDED(reader->SetCurrentMediaType(first_audio, nullptr, pcm.get())))
            check_hresult(reader->SetStreamSelection(first_audio, TRUE));
        else audio = nullptr;
    }
    PROPVARIANT position{};
    position.vt = VT_I8;
    position.hVal.QuadPart = from;
    check_hresult(reader->SetCurrentPosition(GUID_NULL, position));
    const auto frame_bytes = w * h * static_cast<UINT32>(sizeof(UINT32));
    std::vector<UINT32> inline_pixels;
    winrt::handle mapping;
    UINT32* shared_pixels{};
    ScopeExit unmap([&] { if (shared_pixels) UnmapViewOfFile(shared_pixels); });
    if (!token.empty()) {
        const auto name = L"Local\\GleamReplayPreview-" + std::wstring(token);
        mapping.attach(CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, 0, frame_bytes, name.c_str()));
        if (!mapping) winrt::throw_last_error();
        if (GetLastError() == ERROR_ALREADY_EXISTS) throw std::runtime_error("Preview channel already exists");
        shared_pixels = static_cast<UINT32*>(MapViewOfFile(mapping.get(), FILE_MAP_WRITE, 0, 0, frame_bytes));
        if (!shared_pixels) winrt::throw_last_error();
    } else inline_pixels.resize(static_cast<size_t>(w) * h);
    auto* pixels = shared_pixels ? shared_pixels : inline_pixels.data();
    const UINT32 header[]{0x47525031, w, h, audio ? 1u : 0u};
    if (fwrite(header, sizeof(header), 1, stdout) != 1) return;
    fflush(stdout);
    DWORD video_index = UINT32_MAX;
    bool video_done = false, audio_done = !audio;
    LONGLONG first_video_time = -1, last_video_slot = -1;
    while (!video_done || !audio_done) {
        DWORD stream{}, flags{};
        LONGLONG timestamp{};
        com_ptr<IMFSample> sample;
        check_hresult(reader->ReadSample(still ? first_video : any, 0, &stream, &flags, &timestamp, sample.put()));
        if (flags & MF_SOURCE_READERF_ERROR) throw std::runtime_error("Preview decode failed");
        if (video_index == UINT32_MAX) {
            com_ptr<IMFMediaType> type;
            check_hresult(reader->GetCurrentMediaType(stream, type.put()));
            GUID major{};
            check_hresult(type->GetGUID(MF_MT_MAJOR_TYPE, &major));
            if (major == MFMediaType_Video) video_index = stream;
        }
        bool video = stream == video_index;
        if (flags & MF_SOURCE_READERF_ENDOFSTREAM) {
            if (video) video_done = true; else audio_done = true;
            reader->SetStreamSelection(stream, FALSE);
            continue;
        }
        if (!sample || timestamp < from) continue;
        if (video && limit_fps) {
            if (first_video_time < 0) first_video_time = timestamp;
            const auto video_slot = ((timestamp - first_video_time) * fps + second / 2) / second;
            if (video_slot <= last_video_slot) continue;
            last_video_slot = video_slot;
        }
        auto emit = [&](UINT32 kind, const BYTE* data, UINT32 length) {
            return fwrite(&kind, sizeof(kind), 1, stdout) == 1 &&
                   fwrite(&timestamp, sizeof(timestamp), 1, stdout) == 1 &&
                   fwrite(&length, sizeof(length), 1, stdout) == 1 &&
                   (!length || fwrite(data, 1, length, stdout) == length) && fflush(stdout) == 0;
        };
        com_ptr<IMFMediaBuffer> buffer;
        check_hresult(sample->ConvertToContiguousBuffer(buffer.put()));
        if (video) {
            auto copy = [&](const BYTE* source, LONG stride) {
                for (UINT32 y = 0; y < h; ++y)
                    std::memcpy(pixels + static_cast<size_t>(y) * w,
                                source + static_cast<ptrdiff_t>(y) * stride, w * 4);
            };
            if (auto surface = buffer.try_as<IMF2DBuffer>()) {
                BYTE* source{};
                LONG stride{};
                check_hresult(surface->Lock2D(&source, &stride));
                ScopeExit unlock([&] { surface->Unlock2D(); });
                copy(source, stride);
            } else {
                BYTE* source{};
                DWORD length{};
                check_hresult(buffer->Lock(&source, nullptr, &length));
                ScopeExit unlock([&] { buffer->Unlock(); });
                if (length != frame_bytes) throw std::runtime_error("Unexpected video stride");
                copy(source + static_cast<size_t>(h - 1) * w * 4, -static_cast<LONG>(w * 4));
            }
            for (auto& pixel : std::span(pixels, static_cast<size_t>(w) * h))
                pixel = 0xff000000u | ((pixel & 0x000000ffu) << 16) | (pixel & 0x0000ff00u) | ((pixel & 0x00ff0000u) >> 16);
            if (shared_pixels) {
                if (!emit(1, nullptr, 0) || std::getc(stdin) != 1) return;
            } else if (!emit(1, reinterpret_cast<const BYTE*>(pixels), frame_bytes)) return;
            if (still) return;
        } else {
            BYTE* data{};
            DWORD length{};
            check_hresult(buffer->Lock(&data, nullptr, &length));
            ScopeExit unlock([&] { buffer->Unlock(); });
            if (length && !emit(2, data, length)) return;
        }
    }
}
}
