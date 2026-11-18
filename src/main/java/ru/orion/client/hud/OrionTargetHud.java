package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.render.GleamCameraMatrices;

public final class OrionTargetHud {
    public static final String ID = "target_hud";
    private static final int WIDTH = 154, EQUIPMENT_ROW = 22, MARGIN = 6;
    private static final Layout SCREEN = new Layout(41, 9, 24, 39, 31);
    private static final Layout ATTACHED = new Layout(50, 8, 34, 49, 38);
    private static final int HEALTH = 0xff80d5a4, ABSORPTION = 0xffe8b967;
    private static final EquipmentSlot[] EQUIPMENT = { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };
    private static final String TARGET_ICON = "M10 2v4m0 8v4M2 10h4m8 0h4M10 4a6 6 0 1 1 0 12a6 6 0 1 1 0-12";
    private final OrionConfigManager manager;
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private final OrionHudView view = new OrionHudView(ID);
    private LivingEntity target;
    private LivingEntity attacked;
    private long lastSeen;
    private long attackedAt;
    private LivingEntity barTarget;
    private long barTime;
    private double shownHealth;
    private Readout previous;
    private Layout previousLayout;
    private OrionHudView.Scene scene;

    private record Layout(int body, int faceY, int faceSize, int textX, int barY) {
        int height(boolean equipment) { return body + (equipment ? EQUIPMENT_ROW : 0); }
        int barWidth() { return WIDTH - textX - 8; }
    }

    private record Readout(String name, String health, String distance, String ping, int fill, int absorption,
                           boolean portrait, boolean equipment) {}

    private record Placement(int x, int y, int width, int height) {}

    public OrionTargetHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), this::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::clear));
    }

    public void onAttack(Entity entity) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (config.isTargetHudEnabled() && entity instanceof LivingEntity living && valid(client, living, config)) {
            attacked = living;
            attackedAt = System.nanoTime();
        }
    }

    private void clear() {
        target = attacked = barTarget = null;
        drag.cancel(manager);
        view.dispose();
    }

    private static boolean valid(Minecraft client, LivingEntity candidate, OrionConfig config) {
        return candidate != null && candidate != client.player && candidate.level() == client.level && candidate.isAlive()
                && !candidate.isInvisibleTo(client.player) && client.player.distanceToSqr(candidate) <= 32 * 32
                && (!config.isTargetHudPlayersOnly() || candidate instanceof Player);
    }

    private LivingEntity resolve(Minecraft client, OrionConfig config) {
        long now = System.nanoTime();
        long hold = config.getTargetHudHoldSeconds() * 1_000_000_000L;
        if (config.isTargetHudAttackOnly()) {
            return now - attackedAt <= Math.max(180_000_000L, hold) && valid(client, attacked, config) ? attacked : null;
        }
        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && valid(client, living, config)) {
            target = living;
            lastSeen = now;
        } else if (attackedAt > lastSeen && valid(client, attacked, config)) {
            target = attacked;
            lastSeen = attackedAt;
        }
        return now - lastSeen <= hold && valid(client, target, config) ? target : null;
    }

    private Readout read(Minecraft client, LivingEntity living, OrionConfig config, Layout layout) {
        float health = living.getHealth(), max = living.getMaxHealth(), absorption = living.getAbsorptionAmount();
        long now = System.nanoTime();
        double ratio = Math.clamp(health / Math.max(1F, max), 0F, 1F);
        shownHealth = barTarget == living
                ? ratio + (shownHealth - ratio) * Math.exp(-Math.max(0L, now - barTime) / 100_000_000.0)
                : ratio;
        barTime = now;
        barTarget = living;
        int fill = (int) Math.round(shownHealth * layout.barWidth());
        int shield = (int) Math.round(Math.clamp(absorption / Math.max(1F, max), 0F, 1F) * layout.barWidth());
        String healthLabel = Math.round(health) + " / " + Math.round(max) + (absorption > 0 ? " +" + Math.round(absorption) : "");
        double range = Math.sqrt(living.getBoundingBox().distanceToSqr(client.player.getEyePosition()));
        int decimeters = (int) Math.round(range * 10.0);
        String distance = decimeters / 10 + "." + decimeters % 10 + " m";
        var connection = client.getConnection();
        var playerInfo = config.isTargetHudPing() && living instanceof Player && connection != null
                ? connection.getPlayerInfo(living.getUUID()) : null;
        String ping = playerInfo == null ? "" : playerInfo.getLatency() + " ms";
        boolean equipped = false;
        if (config.isTargetHudEquipment()) {
            for (var slot : EQUIPMENT) if (!living.getItemBySlot(slot).isEmpty()) {
                equipped = true;
                break;
            }
        }
        return new Readout(OrionNameProtect.replace(living.getName().getString()), healthLabel, distance, ping,
                fill, shield, living instanceof AbstractClientPlayer, equipped);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker ticks) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (!config.isTargetHudEnabled() || client.player == null || client.level == null) {
            clear();
            return;
        }
        boolean attached = config.isTargetHudWorld();
        LivingEntity living = resolve(client, config);
        if (!attached && living == null && GleamMinecraft.screen(client) instanceof ChatScreen) living = client.player;
        if (living == null) {
            barTarget = null;
            drag.cancel(manager);
            view.dispose();
            return;
        }
        Layout layout = attached ? ATTACHED : SCREEN;
        Readout data = read(client, living, config, layout);
        int height = layout.height(data.equipment);
        float scale = config.getHudScale() * config.getHudElementScale(ID);
        Placement placement;
        if (attached) {
            drag.cancel(manager);
            placement = attachedPlacement(graphics, client, living, ticks, height, scale);
        } else {
            float fit = OrionHudView.fit(scale, WIDTH, height, graphics.guiWidth(), graphics.guiHeight());
            int w = Math.max(1, Math.round(WIDTH * fit)), h = Math.max(1, Math.round(height * fit));
            var pos = drag.placeWidget(graphics, w, h, (graphics.guiWidth() - w) / 2 + 48,
                    graphics.guiHeight() / 2 + 25, manager);
            placement = pos == null ? null : new Placement(pos.x(), pos.y(), w, h);
        }
        if (placement == null) return;
        if (!data.equals(previous) || layout != previousLayout) {
            scene = scene(data, layout);
            previous = data;
            previousLayout = layout;
        }
        view.draw(graphics, scene, placement.x, placement.y, placement.width, placement.height);
        graphics.nextStratum();
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(placement.x, placement.y);
            pose.scale(placement.width / (float) WIDTH, placement.height / (float) height);
            if (living instanceof AbstractClientPlayer player) {
                Identifier skin = player.getSkin().body().texturePath();
                graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 8, layout.faceY, 8, 8,
                        layout.faceSize, layout.faceSize, 8, 8, 64, 64);
                if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT))
                    graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 8, layout.faceY, 40, 8,
                            layout.faceSize, layout.faceSize, 8, 8, 64, 64);
            }
            if (data.equipment) for (int i = 0; i < EQUIPMENT.length; i++) {
                var item = living.getItemBySlot(EQUIPMENT[i]);
                if (item.isEmpty()) continue;
                int x = 8 + i * 20;
                //? if >=26.1 {
                graphics.item(item, x, layout.body + 2);
                graphics.itemDecorations(client.font, item, x, layout.body + 2);
                //?} else {
                /*graphics.renderItem(item, x, layout.body + 2);
                graphics.renderItemDecorations(client.font, item, x, layout.body + 2);
                *///?}
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static OrionHudView.Scene scene(Readout data, Layout layout) {
        var scene = new OrionHudView.Scene(WIDTH, layout.height(data.equipment));
        boolean attached = layout == ATTACHED;
        if (attached) scene.surface(0, 0, WIDTH, layout.height(data.equipment), 8);
        else scene.panel(0, 0, WIDTH, layout.height(data.equipment), 7);
        scene.well(7, layout.faceY - 1, layout.faceSize + 2, layout.faceSize + 2, 4);
        if (!data.portrait) {
            int iconSize = attached ? 21 : 16;
            scene.icon(TARGET_ICON, 8 + (layout.faceSize - iconSize) / 2F,
                    layout.faceY + (layout.faceSize - iconSize) / 2F, iconSize, "var(--accent)", false);
        }
        int nameWidth = (data.ping.isEmpty() ? WIDTH - 8 : 115) - layout.textX;
        scene.label(data.name, layout.textX, attached ? 6 : 4, nameWidth, 15,
                attached ? 10 : 9, "var(--text)", "left", true);
        scene.label(data.ping, 115, attached ? 6 : 4, 31, 15, 7, "var(--muted)", "right", false);
        int detailsY = attached ? 23 : 17;
        scene.label(data.health, layout.textX, detailsY, 106 - layout.textX, 12,
                8, attached ? "var(--text)" : OrionHudView.color(HEALTH), "left", true);
        scene.label(data.distance, 109, detailsY, 37, 12, 7.5F, "var(--muted)", "right", false);
        int barHeight = attached ? 5 : 4;
        scene.well(layout.textX, layout.barY, layout.barWidth(), barHeight, 2);
        if (data.fill > 0) scene.box(layout.textX, layout.barY, data.fill, barHeight, 2, OrionHudView.color(HEALTH), null);
        if (data.absorption > 0) scene.box(layout.textX, layout.barY + barHeight + 1, data.absorption,
                2, 1, OrionHudView.color(ABSORPTION), null);
        if (data.equipment) scene.divider(7, layout.body, WIDTH - 14);
        return scene;
    }

    private static Placement attachedPlacement(GuiGraphicsExtractor graphics, Minecraft client, LivingEntity living,
                                                DeltaTracker ticks, int cardHeight, float hudScale) {
        //? if >=26.2 {
        if (client.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return null;
        //?} else {
        /*if (client.options.hideGui) return null;
        *///?}
        var camera = GleamMinecraft.levelState(client).cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null) return null;
        var projection = GleamCameraMatrices.projection(camera);
        var view = GleamCameraMatrices.view(camera);
        if (projection == null || view == null) return null;

        float partial = ticks.getGameTimeDeltaPartialTick(true);
        Vec3 position = living.getPosition(partial);
        Vec3 torso = position.add(0, living.getBbHeight() * .46, 0);
        var matrix = new Matrix4f(projection).mul(view);
        Vec3 offset = torso.subtract(camera.pos);
        var projected = new Vector4f((float) offset.x, (float) offset.y, (float) offset.z, 1F).mul(matrix);
        if (!Float.isFinite(projected.w) || projected.w <= .05F) return null;
        float centerX = projected.x / projected.w;
        float centerY = projected.y / projected.w;
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || Math.abs(centerX) > 1F || Math.abs(centerY) > 1F) return null;
        if (!(client.hitResult instanceof EntityHitResult aimed && aimed.getEntity() == living)
                && !clearPath(client, living, camera.pos, torso)
                && !clearPath(client, living, camera.pos, position.add(0, living.getEyeHeight(), 0))) return null;

        // Project the outline at torso height so the card sits outside the model even when the camera turns.
        float left = centerX, right = centerX;
        float radius = living.getBbWidth() / 2F;
        var edge = new Vector4f();
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                edge.set((float) offset.x + x * radius, (float) offset.y, (float) offset.z + z * radius, 1F).mul(matrix);
                if (edge.w <= .05F) continue;
                float ndc = edge.x / edge.w;
                left = Math.min(left, ndc);
                right = Math.max(right, ndc);
            }
        }

        int screenWidth = graphics.guiWidth(), screenHeight = graphics.guiHeight();
        float pixelsPerBlock = screenHeight * .5F * Math.abs(projection.m11()) / projected.w;
        float perspective = Math.clamp(pixelsPerBlock * 1.25F / WIDTH, .77F, 1.16F);
        float scale = OrionHudView.fit(hudScale * perspective, WIDTH, cardHeight, screenWidth, screenHeight);
        float edgeLeft = (left + 1F) * screenWidth / 2F;
        float edgeRight = (right + 1F) * screenWidth / 2F;
        float torsoY = (1F - centerY) * screenHeight / 2F;
        return placeBeside(screenWidth, screenHeight, edgeLeft, edgeRight, torsoY, cardHeight, scale);
    }

    private static boolean clearPath(Minecraft client, LivingEntity target, Vec3 from, Vec3 to) {
        return client.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target))
                .getType() == HitResult.Type.MISS;
    }

    private static Placement placeBeside(int screenWidth, int screenHeight, float edgeLeft, float edgeRight,
                                         float torsoY, int cardHeight, float scale) {
        int gap = Math.max(4, Math.round(scale * 6F));
        int leftEnd = (int) Math.floor(edgeLeft) - gap;
        int rightStart = (int) Math.ceil(edgeRight) + gap;
        int spaceLeft = leftEnd - MARGIN;
        int spaceRight = screenWidth - MARGIN - rightStart;
        int wantedWidth = Math.max(1, Math.round(WIDTH * scale / 4F) * 4);
        boolean left = spaceLeft >= wantedWidth || spaceRight < wantedWidth && spaceLeft >= spaceRight;
        int width = Math.min(wantedWidth, left ? spaceLeft : spaceRight);
        if (width < wantedWidth * .70F) return null;
        int height = Math.max(1, Math.round(cardHeight * width / (float) WIDTH));
        if (screenHeight - MARGIN * 2 < height) return null;
        int y = Math.clamp(Math.round(torsoY - ATTACHED.body * width / (2F * WIDTH)),
                MARGIN, screenHeight - height - MARGIN);
        return new Placement(left ? leftEnd - width : rightStart, y, width, height);
    }
}
