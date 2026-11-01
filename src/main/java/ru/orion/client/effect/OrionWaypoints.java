package ru.orion.client.effect;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommands.literal("orionwaypoint")
                    .executes(ctx -> message(ctx, "Метки: /orionwaypoint add <имя> [x y z], remove <имя>, color <имя> <RRGGBB>, list"))
                    .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.string())
                        .executes(ctx -> add(ctx, false))
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                            .then(ClientCommands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                    .executes(ctx -> add(ctx, true)))))))
                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.string())
                            .executes(this::remove)))
                    .then(ClientCommands.literal("color").then(ClientCommands.argument("name", StringArgumentType.string())
                            .then(ClientCommands.argument("color", StringArgumentType.word()).executes(this::color))))
                    .then(ClientCommands.literal("list").executes(this::list))));
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

    private int add(CommandContext<FabricClientCommandSource> ctx, boolean coordinates) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return error(ctx, "Сначала войдите в мир.");
        String name = StringArgumentType.getString(ctx, "name");
        if (name.length() > 32) return error(ctx, "Имя метки не длиннее 32 символов.");
        String key = worldKey(client);
        if (find(key, name) != null) return error(ctx, "Метка с таким именем уже есть в этом измерении.");
        if (manager.getConfig().getWaypoints().size() >= MAX_WAYPOINTS) return error(ctx, "Достигнут предел меток.");
        BlockPos position = coordinates ? new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")) : client.player.blockPosition();
        manager.getConfig().getWaypoints().add(new OrionConfig.Waypoint(name, key, position.getX(), position.getY(), position.getZ()));
        manager.save();
        return message(ctx, "Метка " + name + " добавлена: " + position.getX() + " " + position.getY() + " " + position.getZ() + ". Включите Waypoints в ClickGUI.");
    }

    private int remove(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return error(ctx, "Сначала войдите в мир.");
        OrionConfig.Waypoint point = find(worldKey(client), StringArgumentType.getString(ctx, "name"));
        if (point == null) return error(ctx, "Метка не найдена в этом измерении.");
        manager.getConfig().getWaypoints().remove(point);
        manager.save();
        return message(ctx, "Метка " + point.name + " удалена.");
    }

    private int color(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return error(ctx, "Сначала войдите в мир.");
        OrionConfig.Waypoint point = find(worldKey(client), StringArgumentType.getString(ctx, "name"));
        if (point == null) return error(ctx, "Метка не найдена в этом измерении.");
        String value = StringArgumentType.getString(ctx, "color");
        if (!value.matches("(?i)[0-9a-f]{6}")) return error(ctx, "Цвет задаётся шестью символами, например FF8800.");
        point.color = "#" + value.toUpperCase(Locale.ROOT);
        manager.save();
        return message(ctx, "Цвет метки " + point.name + " изменён.");
    }

    private int list(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return error(ctx, "Сначала войдите в мир.");
        String key = worldKey(client);
        var points = manager.getConfig().getWaypoints().stream().filter(point -> key.equals(point.world)).toList();
        if (points.isEmpty()) return message(ctx, "В этом измерении нет меток.");
        message(ctx, "Метки в этом измерении: " + points.size());
        for (OrionConfig.Waypoint point : points) message(ctx, point.name + ": " + point.x + " " + point.y + " " + point.z + " " + point.color);
        return points.size();
    }

    private OrionConfig.Waypoint find(String key, String name) {
        return manager.getConfig().getWaypoints().stream().filter(point -> key.equals(point.world) && point.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    private static int message(CommandContext<FabricClientCommandSource> ctx, String message) { ctx.getSource().sendFeedback(Component.literal(message)); return 1; }
    private static int error(CommandContext<FabricClientCommandSource> ctx, String message) { ctx.getSource().sendError(Component.literal(message)); return 0; }
    public static String worldKey(Minecraft client) {
        String world = client.getCurrentServer() != null ? "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT)
                : client.hasSingleplayerServer() ? "single:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : "local:unknown";
        return world + "|" + client.level.dimension().identifier();
    }
}
