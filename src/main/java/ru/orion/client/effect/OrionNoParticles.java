package ru.orion.client.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import ru.orion.client.config.OrionConfig;

/** Фильтруем только известные виды, не затрагивая неизвестные или полезные частицы. */
public final class OrionNoParticles {
    private OrionNoParticles() { }

    public static boolean shouldHide(OrionConfig config, ParticleOptions options) {
        if (config == null || !config.isNoParticlesEnabled() || options == null) return false;
        ParticleType<?> type = options.getType();
        if (config.isNoParticlesExplosions() && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER)) return true;
        if (config.isNoParticlesCombat() && (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT
                || type == ParticleTypes.SWEEP_ATTACK || type == ParticleTypes.DAMAGE_INDICATOR)) return true;
        return config.isNoParticlesAmbient() && (type == ParticleTypes.ASH || type == ParticleTypes.WHITE_ASH
                || type == ParticleTypes.CRIMSON_SPORE || type == ParticleTypes.WARPED_SPORE
                || type == ParticleTypes.SPORE_BLOSSOM_AIR || type == ParticleTypes.FALLING_SPORE_BLOSSOM
                || type == ParticleTypes.CHERRY_LEAVES || type == ParticleTypes.PALE_OAK_LEAVES
                || type == ParticleTypes.RED_POPLAR_LEAVES || type == ParticleTypes.ORANGE_POPLAR_LEAVES
                || type == ParticleTypes.YELLOW_POPLAR_LEAVES || type == ParticleTypes.TINTED_LEAVES
                || type == ParticleTypes.MYCELIUM || type == ParticleTypes.UNDERWATER
                || type == ParticleTypes.FIREFLY || type == ParticleTypes.SNOWFLAKE
                || type == ParticleTypes.ENCHANT);
    }
}
