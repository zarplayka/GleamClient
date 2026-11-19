package ru.orion.client.effect;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Звук попадания ближнего боя с учётом серверного подтверждения. */
public final class OrionHitSound {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);

    /** Порядок совпадает с индексом hitSoundIndex в конфиге. */
    public static final String[] SOUND_NAMES = {"PvP Tap", "Critical Hit", "Arrow Ping", "Soft Click", "Pling", "XP Pop", "Свои (случайно)"};
    public static final int CUSTOM_INDEX = 6;

    private static final SoundEvent[] BUILTIN_SOUNDS = {
            sound("pvp_tap"), sound("critical"), sound("arrow_ping"),
            sound("soft_click"), sound("pling"), sound("xp_pop")
    };
    private static final long MIN_INTERVAL_NANOS = 20_000_000L;
    private static final long ATTACK_WINDOW_NANOS = 750_000_000L;
    private static final long REPLACEMENT_WINDOW_NANOS = 180_000_000L;
    private static final long COMBAT_SOUND_WINDOW_NANOS = 350_000_000L;
    private static final Identifier ARES_CRIT = Identifier.withDefaultNamespace("aresmine.cosmetic.crit.steel_clash");
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
    private final Map<Integer, PendingAttack> pendingAttacks = new HashMap<>();
    private final Set<Integer> hurtWithoutSource = new HashSet<>();
    private ClientLevel attackLevel;
    private long lastPlay;
    private int lastEstimatedTarget = -1;
    private int lastHitTarget = -1;
    private boolean replacingHurtSound;
    private boolean handlingDamage;

    public OrionHitSound(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tickAttacks);
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

    public void onAttack(Entity target) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = configManager.getConfig();
        if (!config.isHitSoundEnabled() || client.player == null || client.player.isSpectator() || client.level == null
                || !(target instanceof LivingEntity living) || !living.isAlive()
                || config.isHitSoundPlayersOnly() && !(target instanceof Player)) return;
        resetAttackLevel(client.level);
        pendingAttacks.put(target.getId(), new PendingAttack(living, System.nanoTime(), living.hurtTime, effectiveHealth(living)));
    }

    public void onHurt(int targetId) {
        if (attackLevel == Minecraft.getInstance().level && pendingAttacks.containsKey(targetId)) {
            hurtWithoutSource.add(targetId);
        }
    }

    public void handleDamage(ClientboundDamageEventPacket packet, Runnable applyDamage) {
        boolean ownHit = confirmDamage(packet);
        boolean previous = replacingHurtSound;
        boolean wasHandlingDamage = handlingDamage;
        handlingDamage = true;
        replacingHurtSound = ownHit && configManager.getConfig().isHitSoundMuteVanilla();
        try {
            applyDamage.run();
        } finally {
            replacingHurtSound = previous;
            handlingDamage = wasHandlingDamage;
        }
    }

    private boolean confirmDamage(ClientboundDamageEventPacket packet) {
        OrionConfig config = configManager.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (!config.isHitSoundEnabled() || client.player == null || client.level == null) return false;
        resetAttackLevel(client.level);
        int attacker = client.player.getId();
        int targetId = packet.entityId();
        if (targetId == attacker || !(client.level.getEntity(targetId) instanceof LivingEntity target)
                || (config.isHitSoundPlayersOnly() && !(target instanceof Player))) return false;
        int cause = packet.sourceCauseId();
        int direct = packet.sourceDirectId();
        long now = System.nanoTime();
        PendingAttack pending = pendingAttacks.get(targetId);
        boolean recentAttack = pending != null && now - pending.started() <= ATTACK_WINDOW_NANOS;
        boolean melee = packet.sourceType().is(DamageTypes.PLAYER_ATTACK) || packet.sourceType().is(DamageTypes.SPEAR)
                || packet.sourceType().is(DamageTypes.MACE_SMASH);
        boolean possibleOwnHit = (cause == attacker && (direct < 0 || direct == attacker)) || cause < 0 && direct == attacker;
        boolean unassignedHit = cause < 0 && direct < 0 && (melee || packet.sourceType().is(DamageTypes.GENERIC));
        boolean ownHit = (melee || recentAttack) && possibleOwnHit || unassignedHit && recentAttack;
        hurtWithoutSource.remove(targetId);
        pendingAttacks.remove(targetId);
        if (!recentAttack && (possibleOwnHit || unassignedHit) && targetId == lastEstimatedTarget
                && now - lastPlay < REPLACEMENT_WINDOW_NANOS) return true;
        if (!ownHit) return false;
        lastEstimatedTarget = -1;
        playConfirmedHit(config, targetId);
        return true;
    }

    private void tickAttacks(Minecraft client) {
        OrionConfig config = configManager.getConfig();
        resetAttackLevel(config.isHitSoundEnabled() ? client.level : null);
        if (attackLevel == null || pendingAttacks.isEmpty()) return;
        long now = System.nanoTime();
        var iterator = pendingAttacks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingAttack attack = entry.getValue();
            if (now - attack.started() > ATTACK_WINDOW_NANOS || attack.target().isRemoved()) {
                iterator.remove();
            } else if (hurtWithoutSource.contains(entry.getKey()) || attack.target().hurtTime > attack.hurtTime()
                    || effectiveHealth(attack.target()) < attack.health() - .01F) {
                iterator.remove();
                if (playConfirmedHit(config, entry.getKey())) lastEstimatedTarget = entry.getKey();
            }
        }
        hurtWithoutSource.clear();
    }

    /** Called once when Minecraft starts a sound, on the client thread. */
    public boolean replaceCombatCue(SoundInstance sound) {
        Identifier id = sound.getIdentifier();
        if (!id.getNamespace().equals("minecraft")) return false;
        String path = id.getPath();
        boolean victimSound = path.startsWith("entity.") && path.endsWith(".hurt");
        boolean vanillaAttack = path.startsWith("entity.player.attack.");
        if (!handlingDamage && !victimSound && !vanillaAttack && !ARES_CRIT.equals(id)) return false;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return false;
        if (handlingDamage) return replacingHurtSound;
        var config = configManager.getConfig();
        if (!config.isHitSoundEnabled() || client.player == null || attackLevel == null || attackLevel != client.level) return false;
        // Relative hurt sounds are local feedback. Only positional victim sounds can identify our target.
        if (victimSound && sound.isRelative()) return false;

        long now = System.nanoTime();
        Vec3 origin = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        double playerDistance = client.player.distanceToSqr(origin);
        boolean nearPlayer = sound.isRelative() || playerDistance <= 4D;
        if (now - lastPlay < COMBAT_SOUND_WINDOW_NANOS && attackLevel.getEntity(lastHitTarget) instanceof LivingEntity target
                && matchesCombatSound(target, origin, victimSound, nearPlayer, playerDistance)) {
            return config.isHitSoundMuteVanilla();
        }

        int targetId = -1;
        long latest = 0L;
        for (var entry : pendingAttacks.entrySet()) {
            var attack = entry.getValue();
            if (now - attack.started() <= ATTACK_WINDOW_NANOS && (targetId < 0 || attack.started() - latest >= 0L)
                    && matchesCombatSound(attack.target(), origin, victimSound, nearPlayer, playerDistance)) {
                latest = attack.started();
                targetId = entry.getKey();
            }
        }
        if (targetId < 0) return false;
        if (!vanillaAttack) hurtWithoutSource.add(targetId);
        return config.isHitSoundMuteVanilla();
    }

    private static boolean matchesCombatSound(LivingEntity target, Vec3 origin, boolean victimSound,
                                              boolean nearPlayer, double playerDistance) {
        if (victimSound) {
            return target.getBoundingBox().inflate(.4D).contains(origin)
                    && target.distanceToSqr(origin) < playerDistance;
        }
        return nearPlayer || target.distanceToSqr(origin) <= 4D;
    }

    private static float effectiveHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private void resetAttackLevel(ClientLevel level) {
        if (level == attackLevel) return;
        attackLevel = level;
        pendingAttacks.clear();
        hurtWithoutSource.clear();
        lastPlay = 0;
        lastEstimatedTarget = -1;
        lastHitTarget = -1;
    }

    private boolean playConfirmedHit(OrionConfig config, int targetId) {
        long now = System.nanoTime();
        if (lastPlay != 0 && now - lastPlay < MIN_INTERVAL_NANOS) return false;
        lastPlay = now;
        lastHitTarget = targetId;
        float variation = config.isHitSoundVaryPitch() ? (random.nextFloat() - .5F) * .12F : 0F;
        play(config.getHitSoundIndex(), config.getHitSoundVolume(), Math.clamp(config.getHitSoundPitch() + variation, .5F, 2F));
        return true;
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
        if (index != CUSTOM_INDEX) {
            SoundEvent event = BUILTIN_SOUNDS[Math.clamp(index, 0, BUILTIN_SOUNDS.length - 1)];
            client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
            return;
        }
        float clipVolume = volume * client.options.getSoundSourceVolume(SoundSource.MASTER)
                * client.options.getSoundSourceVolume(SoundSource.UI);
        if (clipVolume <= 0F) return;
        String selected = configManager.getConfig().getHitSoundFile();
        executor.execute(() -> {
            try {
                SoundData sound = resolveCustomBytes(selected);
                if (sound != null) {
                    playClip(decode(sound.bytes(), sound.ogg()), clipVolume, pitch);
                } else {
                    client.execute(() -> play(0, volumePercent, pitch));
                }
            } catch (Exception exception) {
                LOGGER.warn("HitSound: не удалось проиграть звук.", exception);
                client.execute(() -> play(0, volumePercent, pitch));
            }
        });
    }

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hit." + path));
    }

    private record SoundData(byte[] bytes, boolean ogg) { }
    private record Samples(AudioFormat format, byte[] bytes) { }
    private record PendingAttack(LivingEntity target, long started, int hurtTime, float health) { }

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
