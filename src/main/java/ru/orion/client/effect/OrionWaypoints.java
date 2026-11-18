package ru.orion.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import java.util.Locale;

/** Метки и последняя смерть сохраняются отдельно для каждого мира и измерения. */
public final class OrionWaypoints {
    private static final int MAX_WAYPOINTS = 512;
    private final OrionConfigManager manager;
    private boolean wasDead;

    public OrionWaypoints(OrionConfigManager manager) { this.manager = manager; }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        new ru.orion.client.hud.OrionWaypointHud(manager).register();
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.level == null) { wasDead = false; return; }
        boolean dead = client.player.isDeadOrDying();
        if (dead && !wasDead && manager.getConfig().isDeathPointEnabled()) {
            BlockPos pos = client.player.blockPosition();
            manager.getConfig().setDeathPoint(new OrionConfig.Waypoint("Смерть", worldKey(client), pos.getX(), pos.getY(), pos.getZ()));
            manager.save();
        }
        wasDead = dead;
    }

    /** Быстрая метка получает свободное имя в текущем измерении. */
    public static OrionConfig.Waypoint addAtPlayer(OrionConfigManager manager, Minecraft client) {
        var config = manager.getConfig();
        if (client.player == null || client.level == null || config.getWaypoints().size() >= MAX_WAYPOINTS) return null;
        String world = worldKey(client);
        String name = nextName(config, world);
        BlockPos position = client.player.blockPosition();
        var point = new OrionConfig.Waypoint(name, world, position.getX(), position.getY(), position.getZ());
        point.color = config.getWaypointQuickColor();
        config.getWaypoints().add(point);
        config.setWaypointsEnabled(true);
        manager.save();
        return point;
    }

    public static String nextName(OrionConfig config, String world) {
        String prefix = config.getWaypointQuickPrefix();
        if (prefix == null || prefix.isBlank()) prefix = "Метка";
        if (prefix.length() > 24) prefix = prefix.substring(0, 24);
        int number = 1;
        String name;
        while (true) {
            name = prefix + " " + number++;
            String candidate = name;
            if (config.getWaypoints().stream().noneMatch(point -> world.equals(point.world) && point.name.equalsIgnoreCase(candidate))) break;
        }
        return name;
    }

    public static String worldKey(Minecraft client) {
        String world = client.getCurrentServer() != null ? "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT)
                : client.hasSingleplayerServer() ? "single:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : "local:unknown";
        return world + "|" + client.level.dimension().identifier();
    }
}
