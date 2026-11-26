#pragma once

#include "video.h"
#include <condition_variable>
#include <deque>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <mutex>
#include <thread>
#include <vector>
#include <wincodec.h>

#pragma comment(lib, "windowscodecs.lib")

namespace replay {
inline std::mutex report_mutex;

inline void report(std::string_view event, std::string_view value = {}) {
    std::lock_guard lock(report_mutex);
    std::cout << event;
    if (!value.empty()) std::cout << '\t' << value;
    std::cout << '\n' << std::flush;
}

class RollingBuffer {
    std::deque<Packet> video_, audio_;
    size_t bytes_{};
    LONGLONG length_{};
    static constexpr size_t budget = 256ull * 1024 * 1024;
public:
    explicit RollingBuffer(LONGLONG length) : length_(length) {}
    void clear() { video_.clear(); audio_.clear(); bytes_ = 0; }
    LONGLONG duration() const {
        return video_.empty() ? 0 : video_.back().time + video_.back().duration - video_.front().time;
    }

    void push(const Packet& packet) {
        if (packet.audio) {
            if (video_.empty()) return;
            audio_.push_back(packet);
        } else {
            if (video_.empty() && !packet.key) return;
            video_.push_back(packet);
        }
        bytes_ += packet.size;
        if (duration() <= length_ && bytes_ <= budget) return;
        auto cutoff = video_.back().time - length_;
        auto discard = [&](auto& queue) { bytes_ -= queue.front().size; queue.pop_front(); };
        while (video_.size() > 1) {
            auto next = std::find_if(std::next(video_.begin()), video_.end(), [](const Packet& p) { return p.key; });
            if (next == video_.end()) {
                if (bytes_ > budget) clear();
                return;
            }
            if (next->time > cutoff && bytes_ <= budget) break;
            size_t count = std::distance(video_.begin(), next);
            while (count--) discard(video_);
            while (!audio_.empty() && audio_.front().time < video_.front().time) discard(audio_);
        }
        if (bytes_ > budget) clear();
    }

    std::vector<Packet> snapshot() const {
        if (video_.empty()) return {};
        const auto first = video_.front().time;
        const auto end = video_.back().time + video_.back().duration;
        std::vector<Packet> packets;
        packets.reserve(video_.size() + audio_.size());
        std::copy_if(audio_.begin(), audio_.end(), std::back_inserter(packets), [=](const Packet& audio) {
            return audio.time >= first && audio.time + audio.duration <= end;
        });
        const auto audio_count = packets.size();
        packets.insert(packets.end(), video_.begin(), video_.end());
        std::inplace_merge(packets.begin(), packets.begin() + audio_count, packets.end(),
            [](const Packet& left, const Packet& right) { return left.time < right.time; });
        return packets;
    }
};

inline com_ptr<IMFMediaType> pcm_type() {
    auto type = media_type();
    check_hresult(type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio));
    check_hresult(type->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, 2));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, 48000));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_BLOCK_ALIGNMENT, 4));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, 192000));
    return type;
}

inline com_ptr<IMFMediaType> aac_type() {
    auto type = media_type();
    check_hresult(type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio));
    check_hresult(type->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_AAC));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, 2));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, 48000));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16));
    check_hresult(type->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, 24000));
    return type;
}

inline void thumbnail(const std::filesystem::path& video, const std::filesystem::path& image) {
    auto options = attributes(2);
    check_hresult(options->SetUINT32(MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, TRUE));
    check_hresult(options->SetUINT32(MF_SOURCE_READER_ENABLE_ADVANCED_VIDEO_PROCESSING, TRUE));
    com_ptr<IMFSourceReader> reader;
    check_hresult(MFCreateSourceReaderFromURL(video.c_str(), options.get(), reader.put()));
    constexpr DWORD all = static_cast<DWORD>(MF_SOURCE_READER_ALL_STREAMS);
    constexpr DWORD stream = static_cast<DWORD>(MF_SOURCE_READER_FIRST_VIDEO_STREAM);
    check_hresult(reader->SetStreamSelection(all, FALSE));
    check_hresult(reader->SetStreamSelection(stream, TRUE));
    com_ptr<IMFMediaType> original;
    check_hresult(reader->GetNativeMediaType(stream, 0, original.put()));
    UINT32 source_width{}, source_height{};
    check_hresult(MFGetAttributeSize(original.get(), MF_MT_FRAME_SIZE, &source_width, &source_height));
    if (!source_width || !source_height) throw std::runtime_error("Invalid thumbnail dimensions");
    const double ratio = std::min({1.0, 320.0 / source_width, 180.0 / source_height});
    const auto width = std::max(2u, static_cast<UINT>(source_width * ratio) & ~1u);
    const auto height = std::max(2u, static_cast<UINT>(source_height * ratio) & ~1u);
    auto type = media_type();
    check_hresult(type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video));
    check_hresult(type->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32));
    check_hresult(MFSetAttributeSize(type.get(), MF_MT_FRAME_SIZE, width, height));
    check_hresult(reader->SetCurrentMediaType(stream, nullptr, type.get()));
    com_ptr<IMFSample> sample;
    for (int attempt = 0; attempt < 12 && !sample; ++attempt) {
        DWORD flags{};
        check_hresult(reader->ReadSample(stream, 0, nullptr, &flags, nullptr, sample.put()));
        if (flags & (MF_SOURCE_READERF_ENDOFSTREAM | MF_SOURCE_READERF_ERROR)) break;
    }
    if (!sample) return;
    com_ptr<IMFMediaBuffer> buffer;
    check_hresult(sample->ConvertToContiguousBuffer(buffer.put()));
    auto surface = buffer.try_as<IMF2DBuffer>();
    std::vector<BYTE> pixels(static_cast<size_t>(width) * height * 3);
    auto copy = [&](const BYTE* source, LONG stride) {
        for (UINT y = 0; y < height; ++y) {
            const BYTE* row = source + static_cast<ptrdiff_t>(y) * stride;
            BYTE* destination = pixels.data() + static_cast<size_t>(y) * width * 3;
            for (UINT x = 0; x < width; ++x) std::memcpy(destination + x * 3, row + x * 4, 3);
        }
    };
    if (surface) {
        BYTE* source{};
        LONG stride{};
        check_hresult(surface->Lock2D(&source, &stride));
        ScopeExit unlock([&] { surface->Unlock2D(); });
        copy(source, stride);
    } else {
        BYTE* source{};
        DWORD bytes{};
        check_hresult(buffer->Lock(&source, nullptr, &bytes));
        ScopeExit unlock([&] { buffer->Unlock(); });
        if (bytes != width * height * 4) return;
        copy(source + static_cast<size_t>(height - 1) * width * 4, -static_cast<LONG>(width * 4));
    }

    com_ptr<IWICImagingFactory> factory;
    check_hresult(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                                   __uuidof(IWICImagingFactory), factory.put_void()));
    com_ptr<IWICStream> output;
    check_hresult(factory->CreateStream(output.put()));
    check_hresult(output->InitializeFromFilename(image.c_str(), GENERIC_WRITE));
    com_ptr<IWICBitmapEncoder> encoder;
    check_hresult(factory->CreateEncoder(GUID_ContainerFormatJpeg, nullptr, encoder.put()));
    check_hresult(encoder->Initialize(output.get(), WICBitmapEncoderNoCache));
    com_ptr<IWICBitmapFrameEncode> frame;
    check_hresult(encoder->CreateNewFrame(frame.put(), nullptr));
    check_hresult(frame->Initialize(nullptr));
    check_hresult(frame->SetSize(width, height));
    auto format = GUID_WICPixelFormat24bppBGR;
    check_hresult(frame->SetPixelFormat(&format));
    if (format != GUID_WICPixelFormat24bppBGR) throw std::runtime_error("JPEG color format unavailable");
    check_hresult(frame->WritePixels(height, width * 3, static_cast<UINT>(pixels.size()), pixels.data()));
    check_hresult(frame->Commit());
    check_hresult(encoder->Commit());
}

class Mp4 {
    std::filesystem::path destination_, partial_;
    std::string id_;
    com_ptr<IMFSinkWriter> writer_;
    DWORD video_{}, audio_{};
    LONGLONG origin_{}, end_{};
    bool audio_enabled_, has_video_{};
public:
    Mp4(std::filesystem::path destination, std::string id, IMFMediaType* video_type,
        IMFMediaType* audio_input, IMFMediaType* audio_output, LONGLONG origin) :
        destination_(std::move(destination)), partial_(destination_), id_(std::move(id)),
        origin_(origin), audio_enabled_(audio_input != nullptr) {
        partial_.replace_extension(L".part.mp4");
        auto options = attributes(1);
        check_hresult(options->SetUINT32(MF_SINK_WRITER_DISABLE_THROTTLING, TRUE));
        try {
            check_hresult(MFCreateSinkWriterFromURL(partial_.c_str(), nullptr, options.get(), writer_.put()));
            check_hresult(writer_->AddStream(video_type, &video_));
            check_hresult(writer_->SetInputMediaType(video_, video_type, nullptr));
            if (audio_enabled_) {
                check_hresult(writer_->AddStream(audio_output, &audio_));
                check_hresult(writer_->SetInputMediaType(audio_, audio_input, nullptr));
            }
            check_hresult(writer_->BeginWriting());
        } catch (...) {
            cleanup();
            throw;
        }
    }

    ~Mp4() { cleanup(); }

    void write(const Packet& packet) {
        if (packet.time < origin_ || (packet.audio && !audio_enabled_)) return;
        auto time = packet.time - origin_;
        auto sample = make_sample(packet.bytes.get(), time, packet.duration);
        if (packet.key) check_hresult(sample->SetUINT32(MFSampleExtension_CleanPoint, TRUE));
        check_hresult(writer_->WriteSample(packet.audio ? audio_ : video_, sample.get()));
        has_video_ |= !packet.audio;
        end_ = std::max(end_, time + packet.duration);
    }

    void finish() {
        if (!has_video_) throw std::runtime_error("No video frames recorded");
        check_hresult(writer_->Finalize());
        writer_ = nullptr;
        if (!MoveFileExW(partial_.c_str(), destination_.c_str(), MOVEFILE_WRITE_THROUGH)) winrt::throw_last_error();
        auto metadata = destination_;
        metadata.replace_extension(L".duration");
        std::ofstream out(metadata, std::ios::trunc);
        out << end_ / 10'000;
        out.close();
        auto image = destination_;
        image.replace_extension(L".jpg");
        try { thumbnail(destination_, image); }
        catch (...) {
            std::error_code error;
            std::filesystem::remove(image, error);
        }
        report("SAVED", id_);
    }

private:
    void cleanup() {
        writer_ = nullptr;
        std::error_code error;
        std::filesystem::remove(partial_, error);
    }
};

class Export {
    std::mutex lock_;
    std::condition_variable wake_;
    std::deque<Packet> packets_;
    size_t bytes_{};
    bool closing_{}, overflow_{};
    std::atomic_bool done_{};
    std::thread thread_;
public:
    Export(std::filesystem::path path, std::string id, com_ptr<IMFMediaType> video, bool audio,
           LONGLONG first, std::vector<Packet> initial = {}, bool close = false)
        : packets_(initial.begin(), initial.end()), closing_(close) {
        for (const auto& packet : packets_) bytes_ += packet.size;
        thread_ = std::thread([this, path = std::move(path), id = std::move(id), video = std::move(video), audio, first] {
            try {
                winrt::init_apartment();
                auto input = audio ? pcm_type() : nullptr;
                auto output = audio ? aac_type() : nullptr;
                Mp4 file(path, id, video.get(), input.get(), output.get(), first);
                for (;;) {
                    Packet packet;
                    {
                        std::unique_lock lock(lock_);
                        wake_.wait(lock, [&] { return closing_ || !packets_.empty(); });
                        if (packets_.empty()) break;
                        packet = std::move(packets_.front());
                        bytes_ -= packet.size;
                        packets_.pop_front();
                    }
                    file.write(packet);
                }
                file.finish();
                if (overflow_) report("ERROR", "Recording stopped because the disk could not keep up");
            } catch (const winrt::hresult_error& e) {
                report("ERROR", "MP4 writer " + winrt::to_string(e.message()));
            } catch (const std::exception& e) { report("ERROR", e.what()); }
            done_.store(true);
        });
    }

    ~Export() { close(); if (thread_.joinable()) thread_.join(); }
    bool done() const { return done_.load(); }
    bool push(const Packet& packet) {
        std::lock_guard lock(lock_);
        if (closing_ || done()) return false;
        if (bytes_ + packet.size > 48ull * 1024 * 1024) {
            closing_ = overflow_ = true;
            wake_.notify_one();
            return false;
        }
        packets_.push_back(packet);
        bytes_ += packet.size;
        wake_.notify_one();
        return true;
    }
    void close() {
        std::lock_guard lock(lock_);
        closing_ = true;
        wake_.notify_one();
    }
};

inline void remux(const std::filesystem::path& input, const std::filesystem::path& output, LONGLONG from, LONGLONG to) {
    auto options = attributes(1);
    check_hresult(options->SetUINT32(MF_READWRITE_DISABLE_CONVERTERS, TRUE));
    com_ptr<IMFSourceReader> reader;
    check_hresult(MFCreateSourceReaderFromURL(input.c_str(), options.get(), reader.put()));
    constexpr DWORD all = static_cast<DWORD>(MF_SOURCE_READER_ALL_STREAMS);
    constexpr DWORD first_video = static_cast<DWORD>(MF_SOURCE_READER_FIRST_VIDEO_STREAM);
    constexpr DWORD first_audio = static_cast<DWORD>(MF_SOURCE_READER_FIRST_AUDIO_STREAM);
    constexpr DWORD any = static_cast<DWORD>(MF_SOURCE_READER_ANY_STREAM);
    check_hresult(reader->SetStreamSelection(all, FALSE));
    check_hresult(reader->SetStreamSelection(first_video, TRUE));
    com_ptr<IMFMediaType> video, audio;
    check_hresult(reader->GetNativeMediaType(first_video, 0, video.put()));
    check_hresult(reader->SetCurrentMediaType(first_video, nullptr, video.get()));
    if (SUCCEEDED(reader->GetNativeMediaType(first_audio, 0, audio.put()))) {
        check_hresult(reader->SetStreamSelection(first_audio, TRUE));
        check_hresult(reader->SetCurrentMediaType(first_audio, nullptr, audio.get()));
    }
    GUID subtype{};
    check_hresult(video->GetGUID(MF_MT_SUBTYPE, &subtype));
    if (subtype != MFVideoFormat_H264) throw std::runtime_error("Expected an H.264 recording");
    PROPVARIANT position{};
    position.vt = VT_I8;
    position.hVal.QuadPart = from;
    check_hresult(reader->SetCurrentPosition(GUID_NULL, position));
    std::unique_ptr<Mp4> file;
    DWORD video_index = UINT32_MAX;
    bool video_end = false, audio_end = !audio;
    while (!video_end || !audio_end) {
        DWORD stream{}, flags{};
        LONGLONG timestamp{};
        com_ptr<IMFSample> sample;
        check_hresult(reader->ReadSample(any, 0, &stream, &flags, &timestamp, sample.put()));
        if (flags & MF_SOURCE_READERF_ERROR) throw std::runtime_error("Could not read the recording");
        if (!sample && !file && (flags & MF_SOURCE_READERF_ENDOFSTREAM)) break;
        if (video_index == UINT32_MAX && sample) {
            com_ptr<IMFMediaType> current;
            check_hresult(reader->GetCurrentMediaType(stream, current.put()));
            GUID major{};
            check_hresult(current->GetGUID(MF_MT_MAJOR_TYPE, &major));
            if (major == MFMediaType_Video) video_index = stream;
        }
        bool is_video = stream == video_index;
        if (flags & MF_SOURCE_READERF_ENDOFSTREAM || timestamp >= to) {
            if (is_video) video_end = true; else audio_end = true;
            reader->SetStreamSelection(stream, FALSE);
            continue;
        }
        if (!sample) continue;
        auto packet = packet_from(sample.get(), !is_video);
        if (!file && is_video && packet.key && packet.time >= from)
            file = std::make_unique<Mp4>(output, output.stem().string(), video.get(), audio.get(), audio.get(), packet.time);
        if (file && packet.size) file->write(packet);
    }
    if (!file) throw std::runtime_error("No key frame in the selected video interval");
    file->finish();
}
}
