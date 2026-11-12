package ru.orion.client.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import ru.orion.client.OrionClient;

/** Не отправляем серверу установку, которую остановил локальный питомец. */
public final class GleamPetPlacement {
    private static final ThreadLocal<Boolean> BLOCKED = new ThreadLocal<>();

    private GleamPetPlacement() { }

    public static void begin() { BLOCKED.set(false); }
    public static void end() { BLOCKED.remove(); }
    public static boolean blocked() { return Boolean.TRUE.equals(BLOCKED.get()); }

    public static boolean reject(BlockPlaceContext context, BlockState state) {
        var client = Minecraft.getInstance();
        if (BLOCKED.get() == null || context.getLevel() != client.level || context.getPlayer() != client.player) return false;
        var instance = OrionClient.getInstance();
        if (instance == null) return false;
        var shape = state.getCollisionShape(context.getLevel(), context.getClickedPos())
                .move(context.getClickedPos());
        if (!instance.getPets().obstructs(shape)) return false;
        BLOCKED.set(true);
        return true;
    }
}
