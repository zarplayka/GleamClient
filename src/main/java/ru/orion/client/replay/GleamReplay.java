package ru.orion.client.replay;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
//? if >=26.3 {
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
//?} else {
/*import org.lwjgl.glfw.GLFWNativeWin32;
*///?}
import org.lwjgl.system.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.GleamMinecraft;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class GleamReplay {
    public static final String LABEL = "gleam.replay.title";
    private static final Logger LOG = LoggerFactory.getLogger("GleamReplay");
    private static final Gson JSON = new Gson();
    private static final String RESOURCE = "/assets/orion-client/native/";
    static final Pattern ID = Pattern.compile("(?:Replay|Recording)-[a-f0-9-]{8,48}");
    private static final Map<String, String> DEFAULT_KEYS = Map.of("save", "2:F10", "toggle", "2:F9", "record", "2:F8");
    private static final Pattern KEY_CODE = Pattern.compile("(?:Key[A-Z]|Digit[0-9]|F(?:[1-9]|1[0-9]|2[0-4])|Insert|Delete|Home|End|PageUp|PageDown|Arrow(?:Up|Down|Left|Right)|Space|Enter|NumpadEnter)");
    public static final GleamReplay INSTANCE = new GleamReplay();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), Thread.ofPlatform().daemon().name("gleam-replay-control").factory());
    private final Path folder = FabricLoader.getInstance().getGameDir().resolve("gleam/clips");
    private final Path config = FabricLoader.getInstance().getConfigDir().resolve("gleam-replay.json");
    private volatile Preferences preferences = Preferences.defaults();
    private volatile World world;
    private volatile boolean buffer, recording, recordingPending, stoppingRecording;
    private volatile long recordedAt;
    private volatile List<Clip> clips = List.of();
    private volatile Path helper;
    private volatile String encoder = "", message = "";
    private volatile long messageVersion;
    private Process recorder, export;
    private String trimTarget;
    private BufferedWriter input;
    private boolean worldOpen, shuttingDown;
    private volatile boolean failed;
    private boolean captureLibraryLoaded, captureOpen;
    private volatile long captureGeneration;
    private long renderGeneration;
    private boolean renderAttached;

    record World(long hwnd, boolean multiplayer) {}
    record Clip(String id, String title, String kind, long at, double seconds, boolean favorite, long revision) {}
    record Preferences(String duration, int customSeconds, String resolution, int fps, String quality, String bitrateMode,
                       int bitrate, String audio, boolean mic, Map<String, String> keys) {
        static Preferences defaults() {
            return new Preferences("60", 60, "1080", 60, "balanced", "auto", 16, "game", false, DEFAULT_KEYS);
        }

        static Preferences read(JsonObject values, Map<String, String> keys) {
            return new Preferences(choice(values, "duration", "60", "30", "60", "120", "custom"),
                    bounded(values, "customSeconds", 60, 5, 300),
                    choice(values, "resolution", "1080", "720", "1080", "1440", "source"),
                    bounded(values, "fps", 60, 30, 60) == 30 ? 30 : 60,
                    choice(values, "quality", "balanced", "performance", "balanced", "quality"),
                    choice(values, "bitrateMode", "auto", "auto", "manual"), bounded(values, "bitrate", 16, 2, 60),
                    choice(values, "audio", "game", "game", "system", "none"), flag(values, "mic"), keys);
        }

        int height() { return "source".equals(resolution) ? 2160 : Integer.parseInt(resolution); }
        int seconds() { return "custom".equals(duration) ? customSeconds : Integer.parseInt(duration); }
        int rate() {
            if ("manual".equals(bitrateMode)) return bitrate;
            int base = switch (resolution) { case "720" -> 9; case "1440" -> 25; default -> 16; };
            double qualityFactor = switch (quality) { case "performance" -> .75; case "quality" -> 1.25; default -> 1; };
            return (int) Math.round(base * qualityFactor * (fps == 30 ? .65 : 1));
        }
        int speed() { return switch (quality) { case "performance" -> 25; case "quality" -> 75; default -> 50; }; }
        int audioMode() { return switch (audio) { case "system" -> 1; case "none" -> 2; default -> 0; }; }
    }

    private GleamReplay() { worker.allowCoreThreadTimeOut(true); }

    public static boolean available() {
        return Platform.get() == Platform.WINDOWS && GleamReplay.class.getResource(RESOURCE + "gleam-replay.exe") != null
                && GleamReplay.class.getResource(RESOURCE + "gleam-replay-capture.dll") != null;
    }

    public void register() {
        if (!available()) return;
        enqueue(this::loadPreferences);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean present = client.level != null;
            if (present == worldOpen) return;
            worldOpen = present;
            World next = present ? new World(hwnd(client), !client.isLocalServer()) : null;
            enqueue(() -> changeWorld(next));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            enqueue(this::shutdown);
            worker.shutdown();
        });
    }

    public boolean hotkey(Minecraft client, KeyEvent event) {
        if (!worldOpen || GleamMinecraft.overlay(client) != null) return false;
        var screen = GleamMinecraft.screen(client);
        if (screen instanceof ChatScreen || (screen != null && !(screen instanceof PauseScreen))) return false;
        String code = browserKey(event.key());
        if (code == null) return false;
        int modifiers = (event.hasControlDown() ? 1 : 0) | (event.hasAltDown() ? 2 : 0) |
                (event.hasShiftDown() ? 4 : 0) | ((event.modifiers() & InputConstants.MOD_SUPER) != 0 ? 8 : 0);
        String key = modifiers + ":" + code;
        for (var entry : preferences.keys().entrySet()) {
            if (key.equals(entry.getValue())) {
                enqueue(() -> action(entry.getKey(), new JsonObject()));
                return true;
            }
        }
        return false;
    }

    public void open(Minecraft client) {
        if (!available()) return;
        enqueue(this::scan);
        GleamMinecraft.setScreen(client, new GleamReplayScreen(GleamMinecraft.screen(client), this));
    }

    public void captureFrame(Minecraft client) {
        long generation = captureGeneration;
        if (!renderAttached && generation == renderGeneration) return;
        renderGeneration = generation;
        var window = client.getWindow();
        try {
            renderAttached = client.level == null ? ReplayFrameCapture.frame(0, 0)
                    : ReplayFrameCapture.frame(window.getWidth(), window.getHeight());
        } catch (IOException error) {
            renderAttached = false;
            enqueue(() -> {
                if (captureGeneration != generation || !captureOpen) return;
                failed = true;
                stopSession();
                LOG.warn("Direct game capture failed", error);
                notice("The game is not using OpenGL".equals(error.getMessage())
                        ? "Откаты с прямым захватом пока доступны только в OpenGL"
                        : "Не удалось захватить кадр игры · " + error.getMessage());
            });
        }
    }

    void submit(String command, JsonObject parameters) { enqueue(() -> action(command, parameters)); }
    List<Clip> clips() { return clips; }
    Preferences preferences() { return preferences; }
    boolean inWorld() { return world != null; }
    boolean buffered() { return buffer && !failed; }
    boolean recording() { return recording; }
    boolean pending() { return recordingPending || stoppingRecording; }
    long recordingSeconds() { return Math.max(0, (System.currentTimeMillis() - recordedAt) / 1000); }
    String message() { return message; }
    long messageVersion() { return messageVersion; }

    Path media(String id) { return owns(id) ? folder.resolve(id + ".mp4") : null; }
    Path thumbnail(String id) { return owns(id) ? folder.resolve(id + ".jpg") : null; }

    void readThumbnail(Clip clip, java.util.function.Consumer<byte[]> complete) {
        enqueue(() -> {
            byte[] image = null;
            Path file = thumbnail(clip.id());
            try {
                if (file != null && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 1_048_576) {
                    try (var input = Files.newInputStream(file)) {
                        var pixels = javax.imageio.ImageIO.read(input);
                        if (pixels != null) {
                            var output = new java.io.ByteArrayOutputStream();
                            if (javax.imageio.ImageIO.write(pixels, "png", output)) image = output.toByteArray();
                        }
                    }
                }
            } catch (IOException error) { LOG.debug("Could not read replay thumbnail", error); }
            complete.accept(image);
        });
    }

    private void enqueue(Runnable task) {
        try {
            worker.execute(() -> {
                try { task.run(); }
                catch (RuntimeException error) { LOG.warn("Replay action failed", error); notice("Не удалось выполнить действие"); }
            });
        } catch (RejectedExecutionException ignored) {}
    }

    private void loadPreferences() {
        try {
            if (!Files.isRegularFile(config)) return;
            if (Files.size(config) > 16_384) return;
            var stored = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            preferences = Preferences.read(stored, readKeys(stored.getAsJsonObject("keys"), false));
        } catch (IOException | RuntimeException error) { LOG.warn("Could not read replay settings", error); }
    }

    private void storePreferences() {
        try {
            Files.createDirectories(config.getParent());
            Path temp = Files.createTempFile(config.getParent(), "gleam-replay-", ".json");
            try {
                Files.writeString(temp, JSON.toJson(preferences));
                try { Files.move(temp, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, config, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException error) { LOG.warn("Could not save replay settings", error); notice("Не удалось сохранить настройки"); }
    }

    private void changeWorld(World next) {
        world = next;
        if (next != null) {
            failed = false;
            if (buffer && start()) send("BUFFER 1");
            return;
        }
        stopSession();
    }

    private void action(String command, JsonObject parameters) {
        switch (command) {
            case "toggle" -> setBuffer(!buffer || failed);
            case "buffer" -> setBuffer(flag(parameters, "enabled"));
            case "save" -> {
                if (world == null || !buffer || failed) notice("Буфер откатов выключен");
                else send("SAVE " + id(false));
            }
            case "record" -> record();
            case "settings" -> {
                preferences = Preferences.read(parameters, preferences.keys());
                storePreferences();
                if (!encoder.isEmpty()) notice("Новые параметры применятся при следующем запуске захвата");
            }
            case "bindings" -> {
                var previous = preferences;
                updateKeys(Preferences.read(JSON.toJsonTree(previous).getAsJsonObject(), readKeys(parameters, true)));
            }
            case "favorite" -> favorite(text(parameters, "id"), flag(parameters, "favorite"));
            case "remove" -> remove(text(parameters, "id"));
            case "trim" -> trim(parameters);
            case "folder" -> {
                try { Files.createDirectories(folder); Desktop.getDesktop().open(folder.toFile()); }
                catch (IOException | RuntimeException error) { notice("Не удалось открыть папку"); }
            }
            default -> { }
        }
    }

    private void updateKeys(Preferences next) {
        preferences = next;
        storePreferences();
    }

    private void setBuffer(boolean value) {
        buffer = value;
        failed = false;
        if (world != null && (value ? start() : recorder != null)) send(value ? "BUFFER 1" : "BUFFER 0");
    }

    private void record() {
        if (world == null) { notice("Открой мир, чтобы начать запись"); return; }
        if (stoppingRecording) return;
        if (recording || recordingPending) {
            stoppingRecording = true;
            send("STOP");
        } else if (start()) {
            recordingPending = true;
            send("RECORD " + id(true));
        }
    }

    private boolean start() {
        if (recorder != null && recorder.isAlive()) return true;
        var current = world;
        if (current == null || shuttingDown) return false;
        try {
            var options = preferences;
            var helper = binary("gleam-replay.exe");
            loadCaptureLibrary();
            Files.createDirectories(folder);
            String channel = UUID.randomUUID().toString();
            ReplayFrameCapture.open(current.hwnd(), options.height(), options.fps(), channel);
            captureOpen = true;
            var process = new ProcessBuilder(helper.toString(), "capture", Long.toUnsignedString(current.hwnd()),
                    Long.toString(ProcessHandle.current().pid()), folder.toString(), Integer.toString(options.height()),
                    Integer.toString(options.fps()), Integer.toString(options.rate() * 1_000_000),
                    Integer.toString(options.speed()), Integer.toString(options.seconds()), Integer.toString(options.audioMode()),
                    options.mic() ? "1" : "0", channel).redirectErrorStream(true).start();
            recorder = process;
            input = process.outputWriter(StandardCharsets.UTF_8);
            Thread.startVirtualThread(() -> readRecorder(process));
            return true;
        } catch (IOException | LinkageError error) {
            stopCapture();
            failed = true;
            LOG.warn("Could not start replay worker", error);
            notice("Не удалось запустить захват");
            return false;
        }
    }

    private void readRecorder(Process process) {
        try (var lines = process.inputReader(StandardCharsets.UTF_8)) {
            for (String line; (line = lines.readLine()) != null;) {
                String[] fields = line.split("\t", 2);
                String key = fields[0];
                String value = fields.length == 2 ? fields[1] : "";
                enqueue(() -> recorderEvent(process, key, value));
            }
        } catch (IOException error) { LOG.debug("Recorder pipe closed", error); }
        finally {
            enqueue(() -> {
                if (recorder != process) return;
                recorder = null;
                input = null;
                stopCapture();
                recording = recordingPending = stoppingRecording = false;
                encoder = "";
                failed = world != null && buffer;
                scan();
            });
        }
    }

    private void recorderEvent(Process process, String key, String value) {
        if ("SAVED".equals(key) && ID.matcher(value).matches()) {
            scan();
            notice(value.startsWith("Recording-") ? "Запись сохранена" : "Откат сохранён");
            return;
        }
        if (recorder != process) return;
        switch (key) {
            case "READY" -> {
                encoder = value;
                captureGeneration++;
            }
            case "IDLE" -> {
                encoder = "";
                stopIdle();
            }
            case "RECORDING" -> {
                recording = "1".equals(value);
                recordingPending = false;
                if (recording) recordedAt = System.currentTimeMillis();
                else {
                    stoppingRecording = false;
                    if (encoder.isEmpty()) stopIdle();
                }
            }
            case "ERROR" -> {
                LOG.warn("Recorder: {}", value);
                notice(switch (value) {
                    case "The replay buffer is empty" -> "Буфер пока пуст";
                    case "A replay is already saving" -> "Откат уже сохраняется";
                    case "No video frames recorded" -> "Игра ещё не передала кадры";
                    default -> value.contains("hardware encoder") ? "Аппаратный кодировщик H.264 недоступен" : "Ошибка захвата · " + value;
                });
            }
            default -> { }
        }
    }

    private void send(String command) {
        if (input == null) return;
        try { input.write(command); input.newLine(); input.flush(); }
        catch (IOException error) { LOG.debug("Recorder command failed", error); }
    }

    private void stopSession() {
        stopCapture();
        if (input != null) {
            send("QUIT");
            try { input.close(); } catch (IOException ignored) {}
        }
        recorder = null;
        input = null;
        encoder = "";
        recording = recordingPending = stoppingRecording = false;
    }

    private void stopCapture() {
        if (!captureOpen) return;
        ReplayFrameCapture.stop();
        captureOpen = false;
    }

    private void stopIdle() {
        if (!buffer && !recording && !recordingPending && !stoppingRecording) stopSession();
    }

    private boolean owns(String id) {
        return id != null && ID.matcher(id).matches() && clips.stream().anyMatch(clip -> clip.id().equals(id));
    }

    private void scan() {
        if (!Files.isDirectory(folder)) { clips = List.of(); return; }
        var next = new ArrayList<Clip>();
        try (var items = Files.newDirectoryStream(folder, "*.mp4")) {
            for (Path path : items) {
                String file = path.getFileName().toString();
                String id = file.substring(0, file.length() - 4);
                if (!ID.matcher(id).matches() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    Path duration = folder.resolve(id + ".duration");
                    if (!Files.isRegularFile(duration, LinkOption.NOFOLLOW_LINKS) || Files.size(duration) > 24) continue;
                    double seconds = Long.parseLong(Files.readString(duration).strip()) / 1000.0;
                    if (seconds <= 0) continue;
                    boolean recording = id.startsWith("Recording-");
                    var times = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    long at = times.creationTime().toMillis();
                    String title = (recording ? "Запись " : "Откат ") +
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(at));
                    next.add(new Clip(id, title, recording ? "recording" : "replay", at, seconds,
                            Files.isRegularFile(folder.resolve(id + ".favorite"), LinkOption.NOFOLLOW_LINKS),
                            times.lastModifiedTime().toMillis()));
                } catch (IOException | NumberFormatException ignored) { }
            }
        } catch (IOException error) { LOG.warn("Could not read replay library", error); return; }
        next.sort(Comparator.comparingLong(Clip::at).reversed());
        clips = List.copyOf(next);
    }

    private void favorite(String id, boolean enabled) {
        if (!owns(id)) return;
        var mark = folder.resolve(id + ".favorite");
        try {
            if (enabled && !Files.exists(mark, LinkOption.NOFOLLOW_LINKS)) Files.createFile(mark);
            if (!enabled) Files.deleteIfExists(mark);
            scan();
        } catch (IOException error) { notice("Не удалось обновить избранное"); }
    }

    private void remove(String id) {
        if (!owns(id)) return;
        if (id.equals(trimTarget)) { notice("Дождись завершения обрезки"); return; }
        try {
            Files.delete(folder.resolve(id + ".mp4"));
            for (String ext : List.of(".duration", ".jpg", ".favorite")) Files.deleteIfExists(folder.resolve(id + ext));
            scan();
            notice("Клип удалён");
        } catch (IOException error) { notice("Не удалось удалить клип"); }
    }

    private void trim(JsonObject parameters) {
        String id = text(parameters, "id");
        var clip = clips.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (clip == null) return;
        if (trimTarget != null) { notice("Дождись завершения обрезки"); return; }
        long end = Math.round(clip.seconds() * 1000);
        long from = longValue(parameters, "start", 0), to = longValue(parameters, "end", end);
        if (from < 0 || to > end || to <= from) return;
        boolean replace = flag(parameters, "replace");
        String out = id(id.startsWith("Recording-"));
        try {
            var process = new ProcessBuilder(binary("gleam-replay.exe").toString(), "trim",
                    folder.resolve(id + ".mp4").toString(), folder.resolve(out + ".mp4").toString(),
                    Long.toString(from), Long.toString(to)).redirectErrorStream(true).start();
            export = process;
            trimTarget = id;
            Thread.startVirtualThread(() -> {
                try {
                    if (!process.waitFor(120, TimeUnit.SECONDS)) process.destroyForcibly();
                    String log = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8);
                    if (process.exitValue() != 0) LOG.warn("Replay trim: {}", log);
                    enqueue(() -> finishTrim(process.exitValue(), id, out, replace));
                } catch (IOException | InterruptedException | IllegalThreadStateException error) {
                    enqueue(() -> finishTrim(1, id, out, replace));
                }
            });
        } catch (IOException error) { notice("Не удалось начать обрезку"); }
    }

    private void finishTrim(int result, String original, String output, boolean replace) {
        trimTarget = null;
        export = null;
        if (result != 0) { notice("Не удалось обрезать клип"); return; }
        try {
            if (replace && owns(original)) {
                Path source = folder.resolve(original + ".mp4");
                Path replacement = folder.resolve(output + ".mp4");
                Path backup = folder.resolve(original + ".trim-backup-" + UUID.randomUUID() + ".mp4");
                Files.move(source, backup, StandardCopyOption.ATOMIC_MOVE);
                try {
                    Files.move(replacement, source, StandardCopyOption.ATOMIC_MOVE);
                    Files.move(folder.resolve(output + ".duration"), folder.resolve(original + ".duration"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException error) {
                    if (Files.exists(source)) Files.move(source, replacement, StandardCopyOption.ATOMIC_MOVE);
                    Files.move(backup, source, StandardCopyOption.ATOMIC_MOVE);
                    throw error;
                }
                try { Files.deleteIfExists(backup); } catch (IOException error) { LOG.warn("Could not remove clip backup", error); }
                Path thumbnail = folder.resolve(output + ".jpg");
                try {
                    if (Files.isRegularFile(thumbnail)) Files.move(thumbnail, folder.resolve(original + ".jpg"), StandardCopyOption.REPLACE_EXISTING);
                    else Files.deleteIfExists(folder.resolve(original + ".jpg"));
                } catch (IOException error) { LOG.warn("Could not replace clip thumbnail", error); }
                notice("Клип обрезан");
            } else {
                notice("Копия сохранена");
            }
        } catch (IOException error) { LOG.warn("Replay replacement failed", error); notice("Обрезанный клип сохранён отдельной копией"); }
        finally { scan(); }
    }

    private void shutdown() {
        shuttingDown = true;
        stopSession();
        if (export != null && export.isAlive()) export.destroy();
    }

    synchronized void loadCaptureLibrary() throws IOException {
        if (captureLibraryLoaded) return;
        System.load(binary("gleam-replay-capture.dll").toAbsolutePath().toString());
        captureLibraryLoaded = true;
    }

    synchronized Path binary(String name) throws IOException {
        if (helper != null && "gleam-replay.exe".equals(name)) return helper;
        try (var source = GleamReplay.class.getResourceAsStream(RESOURCE + name)) {
            if (source == null) throw new IOException("Missing " + name);
            byte[] binary = source.readAllBytes();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary));
            var path = folder.getParent().resolve("native").resolve(digest).resolve(name);
            if (!Files.isRegularFile(path) || !MessageDigest.isEqual(binary, Files.readAllBytes(path))) {
                Files.createDirectories(path.getParent());
                var temp = Files.createTempFile(path.getParent(), "extract-", ".tmp");
                try {
                    Files.write(temp, binary);
                    try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally { Files.deleteIfExists(temp); }
            }
            if ("gleam-replay.exe".equals(name)) helper = path;
            return path;
        } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
    }

    private void notice(String value) {
        message = value;
        messageVersion++;
        if (world == null || shuttingDown) return;
        Minecraft client = Minecraft.getInstance();
        if (client != null) client.execute(() -> {
            if (client.player != null) GleamMinecraft.setOverlayMessage(client, Component.literal(value), false);
        });
    }
    private static long hwnd(Minecraft client) {
        long window = client.getWindow().handle();
        //? if >=26.3 {
        int properties = SDLVideo.SDL_GetWindowProperties(window);
        return SDLProperties.SDL_GetPointerProperty(properties, SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0);
        //?} else {
        /*return GLFWNativeWin32.glfwGetWin32Window(window);
        *///?}
    }
    private static String id(boolean recording) { return (recording ? "Recording-" : "Replay-") + UUID.randomUUID(); }

    static String browserKey(int key) {
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) return "Key" + (char) ('A' + key - InputConstants.KEY_A);
        if (key == InputConstants.KEY_0) return "Digit0";
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) return "Digit" + (key - InputConstants.KEY_1 + 1);
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) return "F" + (key - InputConstants.KEY_F1 + 1);
        if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) return "F" + (key - InputConstants.KEY_F13 + 13);
        return switch (key) {
            case InputConstants.KEY_INSERT -> "Insert";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_PAGEUP -> "PageUp";
            case InputConstants.KEY_PAGEDOWN -> "PageDown";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_LEFT -> "ArrowLeft";
            case InputConstants.KEY_RIGHT -> "ArrowRight";
            case InputConstants.KEY_SPACE -> "Space";
            case InputConstants.KEY_RETURN -> "Enter";
            case InputConstants.KEY_NUMPADENTER -> "NumpadEnter";
            default -> null;
        };
    }

    private static Map<String, String> readKeys(JsonObject json, boolean browser) {
        if (json == null) return DEFAULT_KEYS;
        var result = new java.util.LinkedHashMap<String, String>();
        for (String name : List.of("save", "toggle", "record")) {
            String value;
            if (browser && json.has(name) && json.get(name).isJsonObject()) {
                JsonObject item = json.getAsJsonObject(name);
                value = bounded(item, "modifiers", 0, 0, 15) + ":" + text(item, "code");
            } else value = text(json, name);
            String[] parts = value.split(":", 2);
            if (parts.length != 2 || !parts[0].matches("(?:[0-9]|1[0-5])") || !KEY_CODE.matcher(parts[1]).matches())
                value = DEFAULT_KEYS.get(name);
            if (result.containsValue(value)) return DEFAULT_KEYS;
            result.put(name, value);
        }
        return Map.copyOf(result);
    }

    private static String choice(JsonObject data, String name, String fallback, String... values) {
        String value = text(data, name);
        for (String allowed : values) if (allowed.equals(value)) return value;
        return fallback;
    }
    private static int bounded(JsonObject data, String name, int fallback, int min, int max) {
        return (int) Math.clamp(longValue(data, name, fallback), min, max);
    }
    private static long longValue(JsonObject data, String name, long fallback) {
        try { return data.get(name).getAsLong(); } catch (RuntimeException invalid) { return fallback; }
    }
    private static boolean flag(JsonObject data, String name) {
        JsonElement value = data.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }
    private static String text(JsonObject data, String name) {
        JsonElement value = data.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
}
