package ru.orion.client.effect;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Звук подтверждённого сервером попадания ближнего боя. */
public final class OrionHitSound {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);

    /** Порядок совпадает с индексом hitSoundIndex в конфиге. */
    public static final String[] SOUND_NAMES = {"Snap", "Knock", "Clang", "Glass", "Pulse", "Thud", "Свои (случайно)"};
    public static final int CUSTOM_INDEX = 6;

    private static final String[] BUILTIN_FILES = {"snap.wav", "knock.wav", "clang.wav", "glass.wav", "pulse.wav", "thud.wav"};
    private static final String RESOURCE_DIR = "/assets/orion-client/sounds/hitsounds/";
    private static final long MIN_INTERVAL_NANOS = 40_000_000L;
    private static final long MAX_CUSTOM_BYTES = 4 * 1024 * 1024L;
    private static final int CACHE_LIMIT = 16;

    private final OrionConfigManager configManager;
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "orion-hitsound");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private long lastPlay;

    public OrionHitSound(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        try {
            Files.createDirectories(customDir());
        } catch (IOException exception) {
            LOGGER.warn("HitSound: не удалось создать папку своих звуков.", exception);
        }
    }

    /** Папка пользовательских звуков: config/orion-client/hitsounds. */
    public Path customDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("orion-client").resolve("hitsounds");
    }

    public record ImportResult(String file, String status) { }

    public void chooseCustomSound(Consumer<ImportResult> finished) {
        if (GraphicsEnvironment.isHeadless()) {
            finished.accept(new ImportResult("", "Выбор файла недоступен"));
            return;
        }
        EventQueue.invokeLater(() -> {
            var dialog = new FileDialog((Frame) null, "HitSound — WAV, OGG, AIFF, AU", FileDialog.LOAD);
            try {
                dialog.setFilenameFilter((directory, name) -> supported(name));
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                var selected = dialog.getFiles();
                if (selected.length == 0) finished.accept(new ImportResult("", ""));
                else importSound(selected[0].toPath(), finished);
            } catch (RuntimeException failure) {
                finished.accept(new ImportResult("", "Не удалось открыть выбор файла"));
            } finally {
                dialog.dispose();
            }
        });
    }

    public void importSound(Path source, Consumer<ImportResult> finished) {
        Thread.startVirtualThread(() -> {
            String name = source.getFileName() == null ? "" : source.getFileName().toString();
            if (!supported(name)) {
                finished.accept(new ImportResult("", "Выбери файл WAV, OGG, AIFF или AU"));
                return;
            }
            try {
                byte[] data = readCustom(source);
                if (data == null) {
                    finished.accept(new ImportResult("", "Файл должен быть меньше 4 МБ"));
                    return;
                }
                decode(data, ogg(name));
                Path directory = customDir().toAbsolutePath().normalize();
                Files.createDirectories(directory);
                if (source.toAbsolutePath().normalize().getParent().equals(directory) && name.length() <= 160
                        && Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    finished.accept(new ImportResult(name, "Звук выбран"));
                    return;
                }
                int dot = name.lastIndexOf('.');
                String stem = name.substring(0, Math.min(120, dot)).replaceAll("[\\p{Cntrl}:]", "_");
                String extension = name.substring(dot).toLowerCase(Locale.ROOT);
                Path temp = Files.createTempFile(directory, ".sound-", ".tmp");
                try {
                    Files.write(temp, data);
                    for (int i = 1; i <= 100; i++) {
                        Path target = directory.resolve(stem + (i == 1 ? "" : " (" + i + ")") + extension);
                        try {
                            Files.move(temp, target);
                        } catch (FileAlreadyExistsException exists) {
                            continue;
                        }
                        finished.accept(new ImportResult(target.getFileName().toString(), "Звук загружен"));
                        return;
                    }
                    throw new IOException("Нет свободного имени звука");
                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (Exception failure) {
                LOGGER.debug("HitSound import failed", failure);
                finished.accept(new ImportResult("", "Не удалось загрузить или прочитать звук"));
            }
        });
    }

    public void listCustomSounds(Consumer<List<String>> finished) {
        Thread.startVirtualThread(() -> {
            try {
                finished.accept(customSounds());
            } catch (IOException failure) {
                finished.accept(List.of());
            }
        });
    }

    private List<String> customSounds() throws IOException {
        Path directory = customDir();
        if (!Files.isDirectory(directory)) return List.of();
        try (var entries = Files.list(directory)) {
            return entries.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString()).filter(OrionHitSound::supported)
                    .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
        }
    }

    private static boolean supported(String name) {
        return !name.startsWith(".") && !name.contains(":") && !name.chars().anyMatch(Character::isISOControl)
                && name.toLowerCase(Locale.ROOT).matches(".+\\.(wav|ogg|aiff|aif|au)");
    }

    private static boolean ogg(String name) { return name.toLowerCase(Locale.ROOT).endsWith(".ogg"); }

    public void onDamage(ClientboundDamageEventPacket packet) {
        OrionConfig config = configManager.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (!config.isHitSoundEnabled() || client.player == null || client.level == null) return;
        int attacker = client.player.getId();
        if (packet.sourceCauseId() != attacker || packet.sourceDirectId() != attacker || packet.entityId() == attacker) return;
        if (!packet.sourceType().is(DamageTypes.PLAYER_ATTACK) && !packet.sourceType().is(DamageTypes.SPEAR)) return;
        if (!(client.level.getEntity(packet.entityId()) instanceof LivingEntity target)
                || (config.isHitSoundPlayersOnly() && !(target instanceof Player))) return;

        long now = System.nanoTime();
        if (lastPlay != 0 && now - lastPlay < MIN_INTERVAL_NANOS) return;
        lastPlay = now;
        float variation = config.isHitSoundVaryPitch() ? (random.nextFloat() - .5F) * .12F : 0F;
        play(config.getHitSoundIndex(), config.getHitSoundVolume(), Math.clamp(config.getHitSoundPitch() + variation, .5F, 2F));
    }

    /** Кнопка «Тест» в ClickGUI — без дебаунса. */
    public void preview() {
        OrionConfig config = configManager.getConfig();
        play(config.getHitSoundIndex(), config.getHitSoundVolume(), config.getHitSoundPitch());
    }

    /** Открывает папку своих звуков в проводнике. */
    public void openCustomFolder(Consumer<Boolean> finished) {
        Thread worker = new Thread(() -> {
            try {
                Path directory = customDir().toAbsolutePath().normalize();
                Files.createDirectories(directory);
                if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                    new ProcessBuilder("explorer.exe", directory.toString()).start();
                } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(directory.toFile());
                } else {
                    throw new IOException("Открытие папки не поддерживается на этой системе");
                }
                finished.accept(true);
            } catch (Exception exception) {
                LOGGER.warn("HitSound: не удалось открыть папку {}.", customDir(), exception);
                finished.accept(false);
            }
        }, "orion-open-hitsounds");
        worker.setDaemon(true);
        worker.start();
    }

    private void play(int index, int volumePercent, float pitch) {
        if (volumePercent <= 0) return;
        Minecraft client = Minecraft.getInstance();
        float volume = volumePercent / 100F;
        volume *= volume;
        float clipVolume = volume * client.options.getSoundSourceVolume(SoundSource.MASTER)
                * client.options.getSoundSourceVolume(SoundSource.UI);
        if (clipVolume <= 0F) return;
        String selected = configManager.getConfig().getHitSoundFile();
        executor.execute(() -> {
            try {
                SoundData sound = resolveSound(index, selected);
                if (sound != null) {
                    playClip(decode(sound.bytes(), sound.ogg()), clipVolume, pitch);
                }
            } catch (Exception exception) {
                LOGGER.warn("HitSound: не удалось проиграть звук.", exception);
            }
        });
    }

    private record SoundData(byte[] bytes, boolean ogg) { }
    private record Samples(AudioFormat format, byte[] bytes) { }

    private SoundData resolveSound(int index, String selected) throws IOException {
        if (index == CUSTOM_INDEX) {
            SoundData custom = resolveCustomBytes(selected);
            if (custom != null) return custom;
            index = 0;
        }
        byte[] builtin = builtinBytes(Math.clamp(index, 0, BUILTIN_FILES.length - 1));
        return builtin == null ? null : new SoundData(builtin, false);
    }

    private byte[] builtinBytes(int index) throws IOException {
        String name = BUILTIN_FILES[index];
        byte[] cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = OrionHitSound.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (stream == null) {
                LOGGER.warn("HitSound: встроенный звук {} не найден в ресурсах.", name);
                return null;
            }
            byte[] data = stream.readAllBytes();
            cache.put(name, data);
            return data;
        }
    }

    private SoundData resolveCustomBytes(String selected) throws IOException {
        String name = selected;
        if (name.isEmpty()) {
            var files = customSounds();
            if (files.isEmpty()) return null;
            name = files.get(random.nextInt(files.size()));
        }
        if (!supported(name) || name.contains("/") || name.contains("\\")) return null;
        Path pick = customDir().resolve(name);
        if (!Files.isRegularFile(pick, LinkOption.NOFOLLOW_LINKS)) return null;
        long size = Files.size(pick);
        if (size > MAX_CUSTOM_BYTES) return null;
        String key = pick + "|" + size + "|" + Files.getLastModifiedTime(pick).toMillis();
        byte[] cached = cache.get(key);
        if (cached != null) {
            return new SoundData(cached, ogg(name));
        }
        byte[] data = readCustom(pick);
        if (data == null) return null;
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, data);
        return new SoundData(data, ogg(name));
    }

    private static byte[] readCustom(Path file) throws IOException {
        if (Files.size(file) > MAX_CUSTOM_BYTES) return null;
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes((int) MAX_CUSTOM_BYTES + 1);
            return bytes.length <= MAX_CUSTOM_BYTES ? bytes : null;
        }
    }

    private static Samples decode(byte[] data, boolean ogg) throws Exception {
        if (ogg) {
            try (var stream = new JOrbisAudioStream(new ByteArrayInputStream(data))) {
                AudioFormat pcm = pcmFormat(stream.getFormat());
                int limit = maxBytes(pcm);
                var decoded = stream.read(limit);
                byte[] samples = new byte[Math.min(limit, decoded.remaining()) / pcm.getFrameSize() * pcm.getFrameSize()];
                decoded.get(samples);
                if (samples.length == 0) throw new IOException("Пустой звук");
                return new Samples(pcm, samples);
            }
        }
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data)))) {
            AudioFormat pcm = pcmFormat(stream.getFormat());
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, stream)) {
                int frameSize = pcm.getFrameSize();
                byte[] samples = decoded.readNBytes(maxBytes(pcm));
                int length = samples.length / frameSize * frameSize;
                if (length == 0) throw new IOException("Пустой звук");
                return new Samples(pcm, length == samples.length ? samples : java.util.Arrays.copyOf(samples, length));
            }
        }
    }

    private static AudioFormat pcmFormat(AudioFormat source) throws IOException {
        float rate = source.getSampleRate();
        int channels = source.getChannels();
        if (!(rate >= 8000 && rate <= 96000) || channels < 1 || channels > 2) throw new IOException("Формат звука не поддерживается");
        return new AudioFormat(rate, 16, channels, true, false);
    }

    private static int maxBytes(AudioFormat format) { return (int) (format.getFrameRate() * 5) * format.getFrameSize(); }

    private static void playClip(Samples decoded, float volume, float pitch) throws Exception {
        byte[] samples = decoded.bytes();
        AudioFormat pcm = decoded.format();
        for (int i = 0; i < samples.length; i += 2) {
            int sample = (short) ((samples[i] & 0xFF) | (samples[i + 1] << 8));
            int scaled = Math.round(sample * volume);
            samples[i] = (byte) scaled;
            samples[i + 1] = (byte) (scaled >> 8);
        }
        AudioFormat playback = new AudioFormat(pcm.getSampleRate() * pitch, 16, pcm.getChannels(), true, false);
        Clip clip = AudioSystem.getClip();
        try {
            clip.open(playback, samples, 0, samples.length);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) clip.close();
            });
            clip.start();
        } catch (Exception exception) {
            clip.close();
            throw exception;
        }
    }
}
