package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import ru.orion.client.mixin.OrionFoodDataAccessor;

/** Совместимость с протоколом синхронизации AppleSkin 26.3. */
public final class OrionFoodSync {
    private static final CustomPacketPayload.Type<FoodValue> SATURATION = type("saturation");
    private static final CustomPacketPayload.Type<FoodValue> EXHAUSTION = type("exhaustion");
    private static final CustomPacketPayload.Type<FoodValue> REGENERATION = type("natural_regeneration");
    private static boolean naturalRegeneration = true;
    private static volatile LocalFood localFood;

    private OrionFoodSync() {}

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded("appleskin")) return;
        register(SATURATION);
        register(EXHAUSTION);
        register(REGENERATION);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            naturalRegeneration = true;
            localFood = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!(server instanceof IntegratedServer integrated)) return;
            var profile = integrated.getSingleplayerProfile();
            if (profile == null) return;
            var owner = server.getPlayerList().getPlayer(new NameAndId(profile).id());
            if (owner == null) return;
            var food = owner.getFoodData();
            localFood = new LocalFood(integrated, food.getSaturationLevel(),
                    ((OrionFoodDataAccessor) food).orion$getExhaustion(),
                    owner.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION));
        });
    }

    static boolean naturalRegeneration() {
        LocalFood local = currentLocalFood();
        return local != null ? local.regeneration() : naturalRegeneration;
    }

    static float saturation(Player player) {
        LocalFood local = currentLocalFood();
        return local != null ? local.saturation() : player.getFoodData().getSaturationLevel();
    }

    static float exhaustion(Player player) {
        LocalFood local = currentLocalFood();
        return local != null ? local.exhaustion() : ((OrionFoodDataAccessor) player.getFoodData()).orion$getExhaustion();
    }

    private static LocalFood currentLocalFood() {
        LocalFood local = localFood;
        return local != null && local.server() == Minecraft.getInstance().getSingleplayerServer() ? local : null;
    }

    private static CustomPacketPayload.Type<FoodValue> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("appleskin", path));
    }

    private static void register(CustomPacketPayload.Type<FoodValue> type) {
        PayloadTypeRegistry.clientboundPlay().register(type, StreamCodec.<FriendlyByteBuf, FoodValue>of(
                (buffer, payload) -> {
                    if (type == REGENERATION) buffer.writeBoolean(payload.value() != 0F);
                    else buffer.writeFloat(payload.value());
                },
                buffer -> new FoodValue(type, type == REGENERATION ? (buffer.readBoolean() ? 1F : 0F) : buffer.readFloat())
        ));
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            float value = payload.value();
            if (type == REGENERATION) {
                naturalRegeneration = value != 0F;
            } else if (Float.isFinite(value) && context.player() != null) {
                var food = context.player().getFoodData();
                if (type == SATURATION) food.setSaturation(Math.clamp(value, 0F, 20F));
                else ((OrionFoodDataAccessor) food).orion$setExhaustion(Math.clamp(value, 0F, 40F));
            }
        });
    }

    private record FoodValue(CustomPacketPayload.Type<FoodValue> type, float value) implements CustomPacketPayload {}
    private record LocalFood(IntegratedServer server, float saturation, float exhaustion, boolean regeneration) {}
}
