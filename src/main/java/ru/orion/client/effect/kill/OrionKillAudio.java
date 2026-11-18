package ru.orion.client.effect.kill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;

/** Пространственные звуковые сцены; декодирование и ресурсы обслуживает звуковой движок игры. */
public final class OrionKillAudio {
    private static final SoundEvent SWORD_RAIN = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "kill.sword_rain"));
    private static final SoundEvent BLACK_HOLE = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "kill.black_hole"));

    private OrionKillAudio() { }

    /** Вызывается один раз при создании сцены на клиентском потоке. Громкость: 0–1. */
    public static SoundInstance play(int type, Vec3 position, float speed, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || position == null
                || !Float.isFinite(volume) || volume <= 0 || !Float.isFinite(speed)) return null;
        SoundEvent event = switch (type) {
            case 0 -> SoundEvents.SOUL_ESCAPE.value();
            case 1 -> SoundEvents.PLAYER_ATTACK_CRIT;
            case 2 -> SWORD_RAIN;
            case 3 -> SoundEvents.ENCHANTMENT_TABLE_USE;
            case 4 -> BLACK_HOLE;
            case 5 -> SoundEvents.SCULK_CATALYST_BLOOM;
            case 6 -> SoundEvents.GLASS_BREAK;
            case 8 -> SoundEvents.CHERRY_LEAVES_BREAK;
            default -> null;
        };
        if (event == null) return null;
        var manager = client.getSoundManager();
        // Изменение pitch одновременно меняет длительность: удары совпадают с фазами анимации.
        SoundInstance sound = new SimpleSoundInstance(event, SoundSource.PLAYERS,
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
