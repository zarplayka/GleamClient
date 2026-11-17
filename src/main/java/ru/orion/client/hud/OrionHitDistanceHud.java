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
import ru.orion.client.effect.OrionNameProtect;

import java.util.Locale;
import java.util.Objects;

public final class OrionHitDistanceHud {
    public static final String ID = "hit_distance";
    private static final int WIDTH = 127;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 17;
    private static final int HEIGHT = HEADER_HEIGHT + ROW_HEIGHT + 5;
    private static final int PAD_X = 7;

    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView(ID);
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private LocalPlayer victim;
    private Level level;
    private Hit lastHit;
    private Hit shownHit;
    private String shownName;
    private OrionHudView.Scene scene;

    private record Hit(String attacker, String distance) {}

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
        lastHit = new Hit(attacker.getName().getString(), String.format(Locale.ROOT, "%.2f", distance));
        victim = client.player;
        level = client.level;
    }

    private void clearHit() {
        victim = null;
        level = null;
        lastHit = null;
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
        Hit hit = lastHit;
        String name = hit == null ? null : OrionNameProtect.replace(hit.attacker());
        if (scene == null || !Objects.equals(hit, shownHit) || !Objects.equals(name, shownName)) {
            scene = new OrionHudView.Scene(WIDTH, HEIGHT);
            scene.panel(0, 0, WIDTH, HEIGHT, 7);
            scene.icon("M3 10h14M3 7v6m14-6v6M6 8v4m8-4v4", PAD_X, 5, 11, "var(--accent)", false);
            scene.label("HitDistance", PAD_X + 14, 2, 78, 17, 8.5F, "var(--text)", "left", true);
            scene.divider(PAD_X, HEADER_HEIGHT - 2, WIDTH - PAD_X * 2);
            int distanceX = WIDTH - PAD_X - 40;
            scene.label(name == null ? "—" : name, PAD_X, HEADER_HEIGHT,
                    distanceX - PAD_X - 3, ROW_HEIGHT - 2, 8F,
                    hit == null ? "var(--muted)" : "var(--text)", "left", false);
            if (hit != null) {
                scene.label(hit.distance(), distanceX, HEADER_HEIGHT, 28, ROW_HEIGHT - 2, 8F, "var(--text)", "right", true);
                scene.label("m", WIDTH - PAD_X - 10, HEADER_HEIGHT, 7, ROW_HEIGHT - 2, 6.5F, "var(--muted)", "left", false);
            }
            shownHit = hit;
            shownName = name;
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
