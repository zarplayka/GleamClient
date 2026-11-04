package ru.orion.client.effect;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import java.util.stream.Stream;

/** Звук подтверждённого сервером попадания ближнего боя. */
public final class OrionHitSound {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);

    /** Порядок совпадает с индексом hitSoundIndex в конфиге. */
    public static final String[] SOUND_NAMES = {"Snap", "Knock", "Clang", "Glass", "Pulse", "Thud", "Свои (папка)"};
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
        executor.execute(() -> {
            try {
                byte[] data = resolveBytes(index);
                if (data != null) {
                    playClip(data, clipVolume, pitch);
                }
            } catch (Exception exception) {
                LOGGER.warn("HitSound: не удалось проиграть звук.", exception);
            }
        });
    }

    private byte[] resolveBytes(int index) throws IOException {
        if (index == CUSTOM_INDEX) {
            byte[] custom = resolveCustomBytes();
            if (custom != null) {
                return custom;
            }
            // Папка пуста — падаем на первый встроенный, чтобы модуль не молчал.
            return builtinBytes(0);
        }
        return builtinBytes(Math.max(0, Math.min(BUILTIN_FILES.length - 1, index)));
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

    private byte[] resolveCustomBytes() throws IOException {
        Path dir = customDir();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).filter(path -> {
                String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".au");
            }).forEach(files::add);
        }
        if (files.isEmpty()) {
            return null;
        }

        Path pick = files.get(random.nextInt(files.size()));
        long size = Files.size(pick);
        if (size > MAX_CUSTOM_BYTES) return null;
        String key = pick + "|" + size + "|" + Files.getLastModifiedTime(pick).toMillis();
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] data;
        try (InputStream stream = Files.newInputStream(pick)) {
            data = stream.readNBytes((int) MAX_CUSTOM_BYTES + 1);
        }
        if (data.length > MAX_CUSTOM_BYTES) return null;
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, data);
        return data;
    }

    private static void playClip(byte[] data, float volume, float pitch) throws Exception {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data)))) {
            AudioFormat source = stream.getFormat();
            AudioFormat pcm = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, stream)) {
                int frameSize = pcm.getFrameSize();
                int maxFrames = (int) Math.min(MAX_CUSTOM_BYTES / frameSize, pcm.getFrameRate() * 5);
                long declaredFrames = decoded.getFrameLength();
                int frames = declaredFrames >= 0 ? (int) Math.min(maxFrames, declaredFrames) : maxFrames;
                byte[] samples = new byte[frames * frameSize];
                int length = 0;
                while (length < samples.length) {
                    int read = decoded.read(samples, length, samples.length - length);
                    if (read <= 0) break;
                    length += read;
                }
                if (length == 0) return;
                for (int i = 0; i < length; i += 2) {
                    int sample = (short) ((samples[i] & 0xFF) | (samples[i + 1] << 8));
                    int scaled = Math.round(sample * volume);
                    samples[i] = (byte) scaled;
                    samples[i + 1] = (byte) (scaled >> 8);
                }
                AudioFormat playback = new AudioFormat(pcm.getSampleRate() * pitch, 16, pcm.getChannels(), true, false);
                Clip clip = AudioSystem.getClip();
                try {
                    clip.open(playback, samples, 0, length);
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
    }
}
