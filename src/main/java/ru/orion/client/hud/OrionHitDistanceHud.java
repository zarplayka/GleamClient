package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.util.Locale;

public final class OrionHitDistanceHud {
    public static final String ID = "hit_distance";
    private static final int WIDTH = 116;
    private static final int HEIGHT = 35;

    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView(ID);
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private LocalPlayer victim;
    private Level level;
    private String lastDistance;
    private String shownDistance;
    private OrionHudView.Scene scene;

    public OrionHitDistanceHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), this::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            clearHit();
            drag.cancel(manager);
            view.dispose();
        }));
    }

    public void onDamage(ClientboundDamageEventPacket packet) {
        var client = Minecraft.getInstance();
        if (!manager.getConfig().isHitDistanceEnabled() || client.level == null
                || client.player == null || client.player.getId() != packet.entityId()) return;
        var source = packet.getSource(client.level);
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || attacker == client.player || source.is(DamageTypes.THORNS)) return;

        // The packet identifies the attacker but does not carry the server's hit position.
        double distance = Math.sqrt(client.player.getBoundingBox().distanceToSqr(attacker.getEyePosition()));
        lastDistance = String.format(Locale.ROOT, "%.2f", distance);
        victim = client.player;
        level = client.level;
    }

    private void clearHit() {
        victim = null;
        level = null;
        lastDistance = null;
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker ticks) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (client.player != victim || client.level != level) clearHit();
        if (client.player == null || !config.isHitDistanceEnabled()) {
            clearHit();
            drag.cancel(manager);
            view.dispose();
            return;
        }
        String value = lastDistance == null ? "—" : lastDistance;
        if (!value.equals(shownDistance)) {
            scene = new OrionHudView.Scene(WIDTH, HEIGHT);
            scene.panel(0, 0, WIDTH, HEIGHT, 7);
            scene.icon("M3 10h14M3 7v6m14-6v6M6 8v4m8-4v4", 7, 5, 11, "var(--accent)", false);
            scene.label("HitDistance", 21, 2, 88, 17, 8.5F, "var(--text)", "left", true);
            scene.divider(7, 19, WIDTH - 14);
            scene.label(value, 8, 21, WIDTH - 27, 12, 8F, "var(--text)", "right", true);
            if (lastDistance != null) scene.label("m", WIDTH - 16, 21, 8, 12, 6.5F, "var(--muted)", "left", false);
            shownDistance = value;
        }

        float scale = config.getHudScale() * config.getHudElementScale(ID);
        float fit = OrionHudView.fit(scale, WIDTH, HEIGHT, context.guiWidth(), context.guiHeight());
        int w = Math.max(1, Math.round(WIDTH * fit));
        int h = Math.max(1, Math.round(HEIGHT * fit));
        int x = Math.clamp(config.getHitDistanceX() < 0 ? context.guiWidth() - w - 8 : config.getHitDistanceX(),
                0, context.guiWidth() - w);
        int y = Math.clamp(config.getHitDistanceY() < 0 ? 22 : config.getHitDistanceY(),
                0, context.guiHeight() - h);
        var available = drag.available(context, x, y, w, h);
        if (available == null) {
            drag.cancel(manager);
            return;
        }
        var requested = drag.update(context, available.x(), available.y(), w, h, manager, (nx, ny) -> {
            var safe = drag.available(context, nx, ny, w, h);
            if (safe == null) return;
            config.setHitDistanceX(safe.x());
            config.setHitDistanceY(safe.y());
        });
        var position = drag.available(context, requested.x(), requested.y(), w, h);
        if (position == null) position = available;
        drag.trackPosition(position, w, h);
        view.draw(context, scene, position.x(), position.y(), w, h);
    }
}
