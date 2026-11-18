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
        RAIN("Дождь", "item/water_bucket"),
        SNOW("Снег", "item/snowball"),
        CLOUDS("Облака", "block/white_wool"),
        SKY("Небо, солнце, луна и звёзды", "item/nether_star"),
        FOG("Атмосферный туман", "block/gray_stained_glass"),
        WORLD_BORDER("Граница мира", "item/barrier"),
        BLOCK_ENTITIES("Модели блок-сущностей", "block/furnace_front"),
        PARTICLES("Все частицы", "item/glowstone_dust"),
        PARTICLE_EXPLOSION("Частицы взрывов", "item/fire_charge"),
        PLAYERS("Другие игроки", "entity/player/wide/steve"),
        HOSTILE("Враждебные мобы", "item/zombie_spawn_egg"),
        PASSIVE("Остальные мобы", "item/cow_spawn_egg"),
        ARMOR_STANDS("Стойки для брони", "item/armor_stand"),
        ITEM_FRAMES("Рамки и светящиеся рамки", "item/item_frame"),
        ITEMS("Предметы на земле", "item/iron_ingot"),
        EXPERIENCE("Сферы опыта", "item/experience_bottle"),
        PROJECTILES("Снаряды", "item/arrow"),
        FALLING_BLOCKS("Падающие блоки", "block/sand"),
        TNT("Активированный TNT", "block/tnt_side"),
        NAMETAGS("Ники над сущностями", "item/name_tag"),
        SHADOWS("Тени сущностей", "item/ink_sac"),
        ENTITY_FIRE("Огонь на сущностях", "item/blaze_powder"),
        FIRE_OVERLAY("Огонь на экране", "item/flint_and_steel"),
        WATER_OVERLAY("Подводная плёнка", "item/cod"),
        BLOCK_OVERLAY("Экран внутри блока", "block/stone"),
        PORTAL_OVERLAY("Эффект портала", "block/obsidian"),
        NAUSEA_OVERLAY("Эффект тошноты", "item/pufferfish"),
        POWDER_SNOW("Рамка замерзания", "item/powder_snow_bucket"),
        PUMPKIN("Обзор в тыкве", "block/carved_pumpkin"),
        SPYGLASS("Рамка подзорной трубы", "item/spyglass"),
        VIGNETTE("Виньетка", "item/ender_eye"),
        HURT_CAMERA("Наклон камеры от урона", "item/iron_sword"),
        HAND("Руки и предметы от первого лица", "item/wooden_pickaxe"),
        TOTEM("Анимация тотема", "item/totem_of_undying"),
        SCOREBOARD("Табло сбоку", "item/oak_sign"),
        BOSS_BAR("Полосы боссов", "item/dragon_breath"),
        VANILLA_EFFECTS("Ванильные иконки эффектов", "item/potion"),
        TITLES("Заголовки на экране", "item/writable_book"),
        ACTION_BAR("Сообщения над хотбаром", "item/paper"),
        TITLED_ITEM("Название выбранного предмета", "item/book");

        public final String label;
        public final String icon;

        Option(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
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
