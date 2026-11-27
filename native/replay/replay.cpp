#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include "audio.h"
#include "storage.h"
#include "preview.h"
#include <sstream>

namespace replay {
struct CommandQueue {
    winrt::handle wake{CreateEventW(nullptr, FALSE, FALSE, nullptr)};
    std::mutex lock;
    std::deque<std::string> lines;
    std::atomic_bool disconnected{};
};

std::shared_ptr<CommandQueue> commands() {
    auto queue = std::make_shared<CommandQueue>();
    if (!queue->wake) winrt::throw_last_error();
    std::thread([queue] {
        std::string line;
        while (std::getline(std::cin, line)) {
            std::lock_guard lock(queue->lock);
            if (queue->lines.size() < 32 && line.size() <= 100) queue->lines.push_back(std::move(line));
            SetEvent(queue->wake.get());
        }
        queue->disconnected.store(true);
        SetEvent(queue->wake.get());
    }).detach();
    return queue;
}

bool valid_id(std::string_view id, std::string_view prefix) {
    return id.starts_with(prefix) && id.size() > prefix.size() && id.size() <= 64 &&
        std::all_of(id.begin() + prefix.size(), id.end(), [](char c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == '-';
        });
}

void capture(HWND window, DWORD pid, const std::filesystem::path& directory, VideoSettings settings,
             unsigned duration, unsigned audio_mode, bool mic, std::wstring_view token) {
    DWORD owner{};
    if (!GetWindowThreadProcessId(window, &owner) || owner != pid) throw std::runtime_error("Invalid capture window");
    FrameChannel frames(token, false);
    std::filesystem::create_directories(directory);
    auto queue = commands();
    RollingBuffer buffer(duration * second);
    std::unique_ptr<Video> video;
    std::unique_ptr<Audio> audio;
    std::unique_ptr<Export> saved_replay, recording;
    bool buffering = false;
    std::string requested_recording;
    LONGLONG origin{}, stop_at{-1};
    auto output_path = [&](const std::string& id) { return directory / (id + ".mp4"); };
    auto open = [&](const std::string& id, const Packet& first) {
        com_ptr<IMFMediaType> type;
        type.copy_from(video->type());
        return std::make_unique<Export>(output_path(id), id, std::move(type), audio->enabled(), first.time);
    };
    auto consume = [&](Packet packet) {
        if (buffering) buffer.push(packet);
        if (recording && !recording->done()) {
            if (stop_at < 0 || origin + packet.time < stop_at) recording->push(packet);
        } else if (!recording && !requested_recording.empty() && !packet.audio && packet.key) {
            recording = open(requested_recording, packet);
            requested_recording.clear();
            recording->push(packet);
            report("RECORDING", "1");
        }
    };
    bool quitting = false;
    while (!quitting) {
        std::deque<std::string> commands;
        {
            std::lock_guard lock(queue->lock);
            commands.swap(queue->lines);
        }
        if (queue->disconnected.load()) quitting = true;
        if (recording && recording->done()) {
            recording.reset();
            stop_at = -1;
            report("RECORDING", "0");
        }
        if (saved_replay && saved_replay->done()) saved_replay.reset();
        for (const auto& command : commands) {
            if (command == "QUIT") { quitting = true; break; }
            if (command == "BUFFER 1") buffering = true;
            else if (command == "BUFFER 0") { buffering = false; buffer.clear(); }
            else if (command.starts_with("RECORD ") && valid_id(std::string_view(command).substr(7), "Recording-")) {
                if (recording || !requested_recording.empty()) report("ERROR", "The previous recording is still saving");
                else {
                    requested_recording = command.substr(7);
                    if (video) video->request_key();
                    stop_at = -1;
                }
            } else if (command == "STOP") {
                if (recording && stop_at < 0) stop_at = qpc_time();
                if (!requested_recording.empty()) {
                    requested_recording.clear();
                    report("ERROR", "No video frames recorded");
                    report("RECORDING", "0");
                }
            } else if (command.starts_with("SAVE ") && valid_id(std::string_view(command).substr(5), "Replay-")) {
                if (saved_replay) { report("ERROR", "A replay is already saving"); continue; }
                auto packets = buffer.snapshot();
                if (packets.empty() || !video || !video->type()) { report("ERROR", "The replay buffer is empty"); continue; }
                const auto first = std::find_if(packets.begin(), packets.end(), [](const Packet& p) { return !p.audio; })->time;
                auto id = command.substr(5);
                com_ptr<IMFMediaType> type;
                type.copy_from(video->type());
                saved_replay = std::make_unique<Export>(output_path(id), id, std::move(type), audio->enabled(), first, std::move(packets), true);
            }
        }
        if (quitting || !IsWindow(window)) break;
        const auto now = qpc_time();
        if (recording && stop_at >= 0 && now - stop_at >= second / 5) recording->close();
        const bool streaming = buffering || !requested_recording.empty() ||
            (recording && (stop_at < 0 || now - stop_at < second / 5));
        if (streaming && !video) {
            auto opened_video = std::make_unique<Video>(frames, settings);
            auto opened_audio = std::make_unique<Audio>(pid, audio_mode, mic);
            origin = qpc_time();
            video = std::move(opened_video);
            audio = std::move(opened_audio);
            report("READY", winrt::to_string(video->name()));
        } else if (!streaming && video) {
            audio.reset();
            video.reset();
            report("IDLE");
        }
        if (video) {
            video->pump(consume);
            video->submit(origin);
            audio->pump(origin, consume);
            HANDLE handles[4]{queue->wake.get(), frames.wake.get()};
            DWORD count = 2;
            audio->add_wake(handles, count);
            WaitForMultipleObjects(count, handles, FALSE, 10);
        } else WaitForSingleObject(queue->wake.get(), recording || saved_replay ? 50 : INFINITE);
    }

    if (video && recording && !recording->done()) {
        try {
            stop_at = qpc_time();
            video->begin_drain();
            auto until = qpc_time() + second / 2;
            while (!video->pump(consume) && qpc_time() < until) Sleep(4);
            audio->pump(origin, consume);
        } catch (...) {}
    }
}

template <typename T>
T parse(const wchar_t* text, T minimum, T maximum) {
    size_t read{};
    const std::wstring value(text);
    if (value.empty() || value.front() < L'0' || value.front() > L'9') throw std::invalid_argument("Invalid argument");
    auto number = std::stoull(value, &read);
    if (read != value.size() || number < minimum || number > maximum) throw std::invalid_argument("Argument out of range");
    return static_cast<T>(number);
}
}

int wmain(int argc, wchar_t** argv) {
    using namespace replay;
    try {
        SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        winrt::init_apartment();
        check_hresult(MFStartup(MF_VERSION, MFSTARTUP_FULL));
        ScopeExit shutdown([] { MFShutdown(); });
        if (argc == 13 && std::wstring_view(argv[1]) == L"capture") {
            auto window = reinterpret_cast<HWND>(parse<uintptr_t>(argv[2], 1, UINTPTR_MAX));
            auto pid = parse<DWORD>(argv[3], 1, UINT32_MAX);
            VideoSettings settings{parse<unsigned>(argv[5], 144, 2160), parse<unsigned>(argv[6], 15, 60),
                parse<unsigned>(argv[7], 2'000'000, 60'000'000), parse<unsigned>(argv[8], 0, 100)};
            capture(window, pid, argv[4], settings, parse<unsigned>(argv[9], 5, 300),
                    parse<unsigned>(argv[10], 0, 2), parse<unsigned>(argv[11], 0, 1) != 0, argv[12]);
            return 0;
        }
        if (argc == 6 && std::wstring_view(argv[1]) == L"trim") {
            auto from = parse<ULONGLONG>(argv[4], 0, INT64_MAX / 10'000);
            auto to = parse<ULONGLONG>(argv[5], from + 1, INT64_MAX / 10'000);
            remux(argv[2], argv[3], from * 10'000, to * 10'000);
            return 0;
        }
        if ((argc == 5 || argc == 7 || argc == 8 || argc == 9) && std::wstring_view(argv[1]) == L"preview") {
            auto from = parse<ULONGLONG>(argv[3], 0, INT64_MAX / 10'000);
            const auto width = argc >= 7 ? parse<unsigned>(argv[5], 2, 3840) : 640;
            const auto height = argc >= 8 ? parse<unsigned>(argv[6], 2, 2160) : width * 9 / 16;
            const auto fps = argc >= 7 ? parse<unsigned>(argv[argc == 7 ? 6 : 7], 12, 60) : 24;
            preview(argv[2], static_cast<LONGLONG>(from * 10'000), parse<unsigned>(argv[4], 0, 1) != 0,
                    width, height, fps, argc == 9 ? std::wstring_view(argv[8]) : std::wstring_view{});
            return 0;
        }
        throw std::invalid_argument("Unknown recorder command");
    } catch (const winrt::hresult_error& e) {
        std::ostringstream error;
        error << "0x" << std::hex << static_cast<uint32_t>(e.code().value) << " " << winrt::to_string(e.message());
        report("ERROR", error.str());
    } catch (const std::exception& e) { report("ERROR", e.what()); }
    return 1;
}
