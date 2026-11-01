package ru.orion.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.orion.client.OrionClient;

/** Клиентский таймер боевого взаимодействия между игроками. */
public final class OrionPvpSafe {
    private static long lastHitNanos;
    private static Object lastLevel;
    private OrionPvpSafe() { }
    public static void onDamage(ClientboundDamageEventPacket packet) {
        Minecraft client = Minecraft.getInstance();
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isPvpSafeEnabled() || client.level == null || client.player == null) return;
        Entity victim = client.level.getEntity(packet.entityId());
        Entity attacker = packet.getSource(client.level).getEntity();
        if (victim instanceof Player && attacker instanceof Player && victim != attacker
                && (victim == client.player || attacker == client.player)) {
            lastLevel = client.level;
            lastHitNanos = System.nanoTime();
        }
    }
    public static int remainingSeconds() {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isPvpSafeEnabled() || Minecraft.getInstance().level != lastLevel || lastHitNanos == 0) return 0;
        double left = config.getPvpSafeSeconds() - (System.nanoTime() - lastHitNanos) / 1_000_000_000D;
        return Math.max(0, (int) Math.ceil(left));
    }
}
