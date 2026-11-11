package ru.orion.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Медиасессии Windows и громкость выбранного приложения. Весь I/O выполняется вне игрового потока. */
public final class OrionMediaController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final String RESOURCE = "/assets/orion-client/media/";
    private final OrionConfigManager manager;
    private final GleamLyricsService lyrics = new GleamLyricsService(FabricLoader.getInstance().getConfigDir().resolve("gleam-lyrics"));
    private final GleamAudioBeatMonitor beat = new GleamAudioBeatMonitor(() -> helper(true));
    private final ScheduledExecutorService polling = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("gleam-media-poll").factory());
    private final ThreadPoolExecutor controls = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(12), Thread.ofPlatform().daemon().name("gleam-media-control").factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private final AtomicLong revision = new AtomicLong();
    private final AtomicReference<VolumeRequest> pendingVolume = new AtomicReference<>();
    private final AtomicReference<GleamLyricsTimeline.Seek> pendingLyricsSeek = new AtomicReference<>();
    private final AtomicBoolean volumeQueued = new AtomicBoolean();
    private volatile JsonObject state = new JsonObject();
    private volatile boolean running;
    private volatile float volume = .7F;
    private volatile boolean volumeAvailable;
    private volatile long lastVolumeSend, lastAction;
    private volatile String error = "";
    private volatile byte[] coverPng;
    private volatile int coverGeneration;
    private volatile String coverAccent = "#A7B8F4";
    private long nextVolumePoll;
    private String fetchedTrack = "";
    private Path directory, mediaHelper, volumeHelper;

    private record Reply(int status, String output) { }
    private record VolumeRequest(String source, float value) { }

    public OrionMediaController(OrionConfigManager manager) { this.manager = manager; }

    public void register() {
        running = true;
        polling.scheduleWithFixedDelay(this::poll, 0, 900, TimeUnit.MILLISECONDS);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            running = false;
            pendingVolume.set(null);
            polling.shutdownNow();
            controls.shutdownNow();
            lyrics.close();
            beat.close();
        });
    }

    public boolean isActive() { return state.has("source"); }
    public GleamLyricsService.Snapshot lyricsSnapshot() { return lyrics.snapshot(); }
    boolean matchesLyrics(GleamLyricsService.Snapshot snapshot) {
        return !snapshot.track().isEmpty() && snapshot.track().equals(string(state, "_lyricsTrack"));
    }
    double lyricsMillis(GleamLyricsService.Snapshot snapshot, long now) {
        JsonObject current = state;
        if (!snapshot.track().equals(string(current, "_lyricsTrack"))) return -1;
        var seek = pendingLyricsSeek.get();
        if (seek != null && seek.blocks(snapshot.track(), now)) return -1;
        double position = currentSeconds(current, now);
        double duration = number(current, "duration");
        return duration > 0 && position >= duration ? -1 : position * 1000;
    }
    GleamAudioBeatMonitor.Sample beatSample(long now) { return beat.sample(now); }
    public String getSource() { return string(state, "source"); }
    public String getTitle() { return string(state, "title"); }
    public String getArtist() { return string(state, "artist"); }
    public boolean isPlaying() { return flag(state, "playing"); }
    public boolean isShuffleActive() { return flag(state, "shuffle"); }
    public int getRepeatMode() { return state.has("repeat") ? state.get("repeat").getAsInt() : 0; }
    public float getVolume() { return volume; }
    public boolean isVolumeAvailable() { return volumeAvailable; }
    public int getCoverGeneration() { return coverGeneration; }
    public byte[] getCoverPng() { return coverPng; }

    public double getDurationSeconds() { return number(state, "duration"); }
    public double getCurrentSeconds() {
        return currentSeconds(state, System.nanoTime());
    }
    private static double currentSeconds(JsonObject current, long now) {
        double position = number(current, "position");
        if (flag(current, "playing") && current.has("_capturedAt"))
            position += Math.max(0, (now - current.get("_capturedAt").getAsLong()) / 1e9);
        double duration = number(current, "duration");
        return duration > 0 ? Math.min(position, duration) : position;
    }

    public JsonObject viewState() {
        var view = state.deepCopy();
        view.remove("_capturedAt");
        view.remove("_lyricsTrack");
        view.addProperty("active", isActive());
        view.addProperty("position", Math.floor(getCurrentSeconds() * 5) / 5);
        view.addProperty("volume", volume);
        view.addProperty("canVolume", volumeAvailable);
        view.addProperty("coverColor", coverAccent);
        view.addProperty("error", System.currentTimeMillis() - lastAction < 6000 ? error : "");
        return view;
    }

    public void playPause() { command("toggle", "canPlay", null); }
    public void next() { command("next", "canNext", null); }
    public void previous() { command("previous", "canPrevious", null); }
    public void toggleShuffle() { command("shuffle", "canShuffle", null); }
    public void cycleRepeat() { command("repeat", "canRepeat", null); }
    public void seek(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return;
        command("seek", "canSeek", String.format(Locale.ROOT, "%.3f", Math.min(seconds, getDurationSeconds())));
    }

    private void command(String action, String capability, String argument) {
        String source = getSource();
        if (!running || !flag(state, capability) || source.isEmpty()) return;
        var seek = "seek".equals(action) ? new GleamLyricsTimeline.Seek(string(state, "_lyricsTrack"),
                Double.parseDouble(argument), System.nanoTime()) : null;
        if (seek != null) pendingLyricsSeek.set(seek);
        revision.incrementAndGet();
        lastAction = System.currentTimeMillis();
        error = "";
        boolean queued = enqueue(() -> {
            if (!source.equals(getSource())) {
                if (seek != null) pendingLyricsSeek.compareAndSet(seek, null);
                return;
            }
            try {
                List<String> args = new ArrayList<>(List.of(action, source));
                if (argument != null) args.add(argument);
                Reply result = run(helper(false), args);
                if (!source.equals(getSource())) return;
                if (result.status() != 0) {
                    if (seek != null) pendingLyricsSeek.compareAndSet(seek, null);
                    error = "Плеер не выполнил команду";
                }
                else polling.execute(this::poll);
            } catch (Exception failure) {
                if (seek != null) pendingLyricsSeek.compareAndSet(seek, null);
                if (running) error = "Управление плеером недоступно";
            }
        });
        if (!queued && seek != null) pendingLyricsSeek.compareAndSet(seek, null);
    }

    public void setVolume(float requested, boolean force) {
        if (!Float.isFinite(requested) || !running || !volumeAvailable) return;
        String source = getSource();
        if (source.isEmpty()) return;
        volume = Math.clamp(requested, 0, 1);
        long now = System.currentTimeMillis();
        if (!force && now - lastVolumeSend < 120) return;
        lastVolumeSend = lastAction = now;
        error = "";
        revision.incrementAndGet();
        pendingVolume.set(new VolumeRequest(source, volume));
        queueVolume();
    }

    private void queueVolume() {
        if (!volumeQueued.compareAndSet(false, true)) return;
        if (!enqueue(() -> {
            try {
                VolumeRequest request;
                while (running && (request = pendingVolume.getAndSet(null)) != null) {
                    if (!request.source().equals(getSource())) continue;
                    try {
                        Reply result = run(helper(true), List.of(request.source(), String.format(Locale.ROOT, "%.4f", request.value())));
                        if (result.status() != 0 && request.source().equals(getSource())) {
                            volumeAvailable = false;
                            error = "Громкость этого приложения недоступна";
                        }
                    } catch (Exception failure) {
                        if (running) error = "Громкость этого приложения недоступна";
                    }
                }
            } finally {
                volumeQueued.set(false);
                if (running && pendingVolume.get() != null) queueVolume();
            }
        })) volumeQueued.set(false);
    }

    private boolean enqueue(Runnable work) {
        try {
            controls.execute(work);
            return true;
        } catch (RejectedExecutionException full) {
            if (running) error = "Подожди выполнения предыдущей команды";
            return false;
        }
    }

    private void poll() {
        if (!running) return;
        if (!WINDOWS || !manager.getConfig().isMediaPlayerEnabled()) {
            clear();
            return;
        }
        try {
            long startedRevision = revision.get();
            Reply result = run(helper(false), List.of("snapshot"));
            if (!running) return;
            if (result.status() != 0) { clear(); return; }
            JsonObject next = JsonParser.parseString(result.output()).getAsJsonObject();
            if (!next.has("source") || string(next, "source").isBlank()) { clear(); return; }
            String source = string(next, "source");
            if (source.length() > 512 || !validNumbers(next)) throw new IOException("Invalid media snapshot");
            if (startedRevision != revision.get() && source.equals(getSource())) return;
            if (!source.equals(getSource())) {
                volumeAvailable = false;
                pendingVolume.set(null);
                nextVolumePoll = 0;
            }
            next.addProperty("_capturedAt", System.nanoTime());
            String track = source + '\0' + string(next, "title") + '\0' + string(next, "artist") + '\0' + string(next, "album");
            boolean coverChanged = !track.equals(fetchedTrack);
            if (coverChanged) {
                fetchedTrack = track;
                coverPng = null;
                coverAccent = "#A7B8F4";
                coverGeneration++;
            }
            var settings = manager.getConfig().getAnimatedLyrics();
            var trackIdentity = new GleamLyricsService.Track(source, string(next, "title"), string(next, "artist"),
                    string(next, "album"), number(next, "duration"));
            next.addProperty("_lyricsTrack", trackIdentity.identity());
            state = next;
            var seek = pendingLyricsSeek.get();
            if (seek != null && seek.settled(trackIdentity.identity(), number(next, "position"), flag(next, "playing"),
                    next.get("_capturedAt").getAsLong())) pendingLyricsSeek.compareAndSet(seek, null);
            lyrics.select(trackIdentity, settings.isEnabled());
            var lyricState = lyrics.snapshot();
            beat.setEnabled(settings.isEnabled() && settings.getIntensity() > 0 && settings.getBeatSensitivity() > 0
                    && flag(next, "playing") && lyricState.track().equals(trackIdentity.identity())
                    && "ready".equals(lyricState.lyrics().status()));
            if (coverChanged) {
                try {
                    fetchCover(source);
                } catch (IOException failure) {
                    LOGGER.debug("Could not read media cover", failure);
                }
            }
            pollVolume(source);
        } catch (Exception failure) {
            if (running) {
                LOGGER.debug("Could not read media session", failure);
                clear();
            }
        }
    }

    private void pollVolume(String source) throws InterruptedException {
        long now = System.currentTimeMillis();
        if (now < nextVolumePoll || now - lastVolumeSend < 1500) return;
        nextVolumePoll = now + 3000;
        Reply result;
        try {
            result = run(helper(true), List.of(source));
        } catch (IOException failure) {
            volumeAvailable = false;
            LOGGER.debug("Could not read app volume", failure);
            return;
        }
        if (!source.equals(getSource()) || System.currentTimeMillis() - lastVolumeSend < 1500) return;
        float value;
        try {
            value = result.status() == 0 ? Float.parseFloat(result.output().strip()) : -1;
        } catch (NumberFormatException invalid) {
            value = -1;
        }
        volumeAvailable = Float.isFinite(value) && value >= 0 && value <= 1;
        if (volumeAvailable) volume = value;
    }

    private void fetchCover(String source) throws IOException, InterruptedException {
        Path path = Files.createTempFile(helper(false).getParent(), "cover-", ".png");
        try {
            Reply result = run(helper(false), List.of("cover", source, path.toString()));
            if (result.status() != 0 || !running || !source.equals(getSource()) || Files.size(path) > 2_000_000) return;
            byte[] data = Files.readAllBytes(path);
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) return;
            long red = 0, green = 0, blue = 0;
            for (int y = 1; y <= 8; y++) for (int x = 1; x <= 8; x++) {
                int rgb = image.getRGB(x * (image.getWidth() - 1) / 9, y * (image.getHeight() - 1) / 9);
                red += rgb >> 16 & 255; green += rgb >> 8 & 255; blue += rgb & 255;
            }
            coverAccent = String.format(Locale.ROOT, "#%02X%02X%02X", red / 128 + 96, green / 128 + 96, blue / 128 + 96);
            coverPng = data;
            coverGeneration++;
        } finally { Files.deleteIfExists(path); }
    }

    private synchronized Path helper(boolean volume) throws IOException {
        Path existing = volume ? volumeHelper : mediaHelper;
        if (existing != null) return existing;
        String name = volume ? "OrionVolume.exe" : "OrionThumbnail.exe";
        try (InputStream stream = OrionMediaController.class.getResourceAsStream(RESOURCE + name)) {
            if (stream == null) throw new IOException("Missing media helper " + name);
            if (directory == null) {
                directory = Files.createTempDirectory("gleam-media-");
                directory.toFile().deleteOnExit();
            }
            Path destination = directory.resolve(name);
            Files.copy(stream, destination);
            destination.toFile().deleteOnExit();
            if (volume) volumeHelper = destination;
            else mediaHelper = destination;
            return destination;
        }
    }

    private static Reply run(Path executable, List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(args);
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(6, TimeUnit.SECONDS)) throw new IOException("Media helper timed out");
            try (InputStream stream = process.getInputStream()) {
                byte[] output = stream.readNBytes(32_769);
                if (output.length > 32_768) throw new IOException("Media response too large");
                return new Reply(process.exitValue(), new String(output, StandardCharsets.UTF_8).strip().replace("\uFEFF", ""));
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private void clear() {
        lyrics.select(null, false);
        pendingLyricsSeek.set(null);
        beat.setEnabled(false);
        if (!state.isEmpty() || coverPng != null) coverGeneration++;
        state = new JsonObject();
        coverPng = null;
        volumeAvailable = false;
        fetchedTrack = "";
    }

    private static boolean validNumbers(JsonObject value) {
        double position = number(value, "position"), duration = number(value, "duration");
        return Double.isFinite(position) && Double.isFinite(duration) && position >= 0 && duration >= 0;
    }
    private static String string(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : "";
    }
    private static boolean flag(JsonObject value, String key) { return value.has(key) && value.get(key).getAsBoolean(); }
    private static double number(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsDouble() : 0; }
}
