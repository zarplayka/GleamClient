package ru.orion.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

public final class OrionNoRender {
    public enum Option {
        RAIN("Дождь"), SNOW("Снег"), CLOUDS("Облака"), SKY("Небо, солнце, луна и звёзды"),
        FOG("Атмосферный туман"), WORLD_BORDER("Граница мира"), BLOCK_ENTITIES("Модели блок-сущностей"),
        PARTICLES("Все частицы"), PARTICLE_EXPLOSION("Частицы взрывов"),
        PLAYERS("Другие игроки"), HOSTILE("Враждебные мобы"), PASSIVE("Остальные мобы"),
        ARMOR_STANDS("Стойки для брони"), ITEM_FRAMES("Рамки и светящиеся рамки"), ITEMS("Предметы на земле"),
        EXPERIENCE("Сферы опыта"), PROJECTILES("Снаряды"), FALLING_BLOCKS("Падающие блоки"), TNT("Активированный TNT"),
        NAMETAGS("Ники над сущностями"), SHADOWS("Тени сущностей"), ENTITY_FIRE("Огонь на сущностях"),
        FIRE_OVERLAY("Огонь на экране"), WATER_OVERLAY("Подводная плёнка"), BLOCK_OVERLAY("Экран внутри блока"),
        PORTAL_OVERLAY("Эффект портала"), NAUSEA_OVERLAY("Эффект тошноты"), POWDER_SNOW("Рамка замерзания"),
        PUMPKIN("Обзор в тыкве"), SPYGLASS("Рамка подзорной трубы"), VIGNETTE("Виньетка"),
        HURT_CAMERA("Наклон камеры от урона"), HAND("Руки и предметы от первого лица"), TOTEM("Анимация тотема"),
        SCOREBOARD("Табло сбоку"), BOSS_BAR("Полосы боссов"), VANILLA_EFFECTS("Ванильные иконки эффектов"),
        TITLES("Заголовки на экране"), ACTION_BAR("Сообщения над хотбаром"), TITLED_ITEM("Название выбранного предмета");

        public final String label;

        Option(String label) { this.label = label; }
    }

    private OrionNoRender() {}

    public static boolean hides(Option option) {
        return hides(OrionClient.getConfigOrNull(), option);
    }

    public static boolean hides(OrionConfig config, Option option) {
        return config != null && config.isNoRenderEnabled() && config.hasNoRenderOption(option.name());
    }

    public static boolean hidesParticle(OrionConfig config, ParticleOptions particle) {
        return config != null && config.isNoRenderEnabled() && (hides(config, Option.PARTICLES)
                || config.hasHiddenRenderTypes(true) && config.hidesRenderType(true,
                BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType()).toString()));
    }

    public static boolean hidesEntity(Entity entity) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isNoRenderEnabled() || entity == Minecraft.getInstance().player) return false;
        if (config.hasHiddenRenderTypes(false) && config.hidesRenderType(false,
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) return true;
        return switch (entity) {
            case Player ignored -> hides(config, Option.PLAYERS);
            case ArmorStand ignored -> hides(config, Option.ARMOR_STANDS);
            case ItemFrame ignored -> hides(config, Option.ITEM_FRAMES);
            case ItemEntity ignored -> hides(config, Option.ITEMS);
            case ExperienceOrb ignored -> hides(config, Option.EXPERIENCE);
            case Projectile ignored -> hides(config, Option.PROJECTILES);
            case FallingBlockEntity ignored -> hides(config, Option.FALLING_BLOCKS);
            case PrimedTnt ignored -> hides(config, Option.TNT);
            case Enemy ignored -> hides(config, Option.HOSTILE);
            case LivingEntity ignored -> hides(config, Option.PASSIVE);
            default -> false;
        };
    }
}
