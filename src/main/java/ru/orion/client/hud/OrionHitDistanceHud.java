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
    private static final int WIDTH = 110;
    private static final int HEIGHT = 21;

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
        lastDistance = String.format(Locale.ROOT, "%.2f m", distance);
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
            scene.panel(0, 0, WIDTH, HEIGHT, 5);
            scene.label("HitDistance", 7, 0, 58, HEIGHT, 8.5F, "var(--muted)", "left", false);
            scene.label(value, 65, 0, 38, HEIGHT, 9F, "var(--text)", "right", true);
            shownDistance = value;
        }

        float scale = config.getHudScale() * config.getHudElementScale(ID);
        float fit = OrionHudView.fit(scale, WIDTH, HEIGHT, context.guiWidth(), context.guiHeight());
        int w = Math.max(1, Math.round(WIDTH * fit));
        int h = Math.max(1, Math.round(HEIGHT * fit));
        int x = Math.clamp(config.getHitDistanceX() < 0 ? context.guiWidth() - w - 8 : config.getHitDistanceX(),
                0, context.guiWidth() - w);
        int y = Math.clamp(config.getHitDistanceY() < 0 ? context.guiHeight() / 2 + 30 : config.getHitDistanceY(),
                0, context.guiHeight() - h);
        var position = drag.update(context, x, y, w, h, manager, (nx, ny) -> {
            config.setHitDistanceX(nx);
            config.setHitDistanceY(ny);
        });
        view.draw(context, scene, position.x(), position.y(), w, h);
    }
}
