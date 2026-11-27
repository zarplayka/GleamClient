#pragma once

#include "storage.h"
#include <fcntl.h>
#include <io.h>

namespace replay {
// Fixed stream header, followed by bounded packets of pixels or stereo PCM.
inline void preview(const std::filesystem::path& path, LONGLONG from, bool still, unsigned width_limit, unsigned fps) {
    if (_setmode(_fileno(stdout), _O_BINARY) == -1) throw std::runtime_error("Cannot open preview pipe");
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
    double scale = std::min({1.0, double(width_limit) / w, (width_limit * .5625) / h});
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
    const UINT32 header[]{0x47525031, w, h, audio ? 1u : 0u};
    if (fwrite(header, sizeof(header), 1, stdout) != 1) return;
    fflush(stdout);
    std::vector<unsigned char> pixels(static_cast<size_t>(w) * h * 4);
    DWORD video_index = UINT32_MAX;
    bool video_done = false, audio_done = !audio;
    LONGLONG last_video_slot = -1;
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
        const auto video_slot = (timestamp - from) * fps / second;
        if (video && video_slot == last_video_slot) continue;
        auto emit = [&](UINT32 kind, const BYTE* data, UINT32 length) {
            return fwrite(&kind, sizeof(kind), 1, stdout) == 1 &&
                   fwrite(&timestamp, sizeof(timestamp), 1, stdout) == 1 &&
                   fwrite(&length, sizeof(length), 1, stdout) == 1 &&
                   fwrite(data, 1, length, stdout) == length && fflush(stdout) == 0;
        };
        com_ptr<IMFMediaBuffer> buffer;
        check_hresult(sample->ConvertToContiguousBuffer(buffer.put()));
        if (video) {
            auto copy = [&](const BYTE* source, LONG stride) {
                for (UINT32 y = 0; y < h; ++y)
                    std::memcpy(pixels.data() + static_cast<size_t>(y) * w * 4,
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
                if (length != pixels.size()) throw std::runtime_error("Unexpected video stride");
                copy(source + static_cast<size_t>(h - 1) * w * 4, -static_cast<LONG>(w * 4));
            }
            for (size_t i = 0; i < pixels.size(); i += 4) {
                std::swap(pixels[i], pixels[i + 2]);
                pixels[i + 3] = 255;
            }
            if (!emit(1, pixels.data(), static_cast<UINT32>(pixels.size())) || still) return;
            last_video_slot = video_slot;
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
