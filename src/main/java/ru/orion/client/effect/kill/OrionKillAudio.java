package ru.orion.client.effect.kill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;

/** Пространственные звуковые сцены; декодирование и ресурсы обслуживает звуковой движок игры. */
public final class OrionKillAudio {
    private static final String[] NAMES = {"ghost", "gravestone", "sword_rain", "crystal_shatter", "black_hole",
            "angel_wings", "crystal_prison", "totem_explosion", "dragon_soul"};
    private static final SoundEvent[] SOUNDS = new SoundEvent[NAMES.length];

    static {
        for (int i = 0; i < NAMES.length; i++) {
            SOUNDS[i] = SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "kill." + NAMES[i]));
        }
    }

    private OrionKillAudio() { }

    /** Вызывается один раз при создании сцены на клиентском потоке. Громкость: 0–1. */
    public static SoundInstance play(int type, Vec3 position, float speed, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || position == null || type < 0 || type >= SOUNDS.length
                || !Float.isFinite(volume) || volume <= 0 || !Float.isFinite(speed)) return null;
        var manager = client.getSoundManager();
        // Изменение pitch одновременно меняет длительность: удары совпадают с фазами анимации.
        SoundInstance sound = new SimpleSoundInstance(SOUNDS[type], SoundSource.PLAYERS,
                Math.min(1F, volume), Math.max(.5F, Math.min(2F, speed)), SoundInstance.createUnseededRandom(),
                position.x, position.y + 1, position.z);
        manager.play(sound);
        return sound;
    }

    /** Останавливает только звуки Kill Effects при выключении модуля или смене мира. */
    public static void stop(SoundInstance sound) {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
    }
}
