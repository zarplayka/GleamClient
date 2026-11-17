package ru.orion.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.EntityHitResult;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.render.GleamPose;

public final class OrionTargetHud {
    public static final String ID = "target_hud";
    private static final int WIDTH = 154, BODY = 41, EQUIPMENT_ROW = 22, FULL_LIGHT = 0xf000f0;
    private static final int TRACK_X = 39, TRACK_Y = 31, TRACK_WIDTH = WIDTH - TRACK_X - 8;
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
    private OrionHudView.Scene scene;

    private record Readout(String name, String health, String distance, String ping, int fill, int absorption,
                           boolean portrait, boolean equipment) {
        int height() { return equipment ? BODY + EQUIPMENT_ROW : BODY; }
    }

    public OrionTargetHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), this::renderScreen);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::renderWorld);
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

    private Readout read(Minecraft client, LivingEntity living, OrionConfig config) {
        float health = living.getHealth(), max = living.getMaxHealth(), absorption = living.getAbsorptionAmount();
        long now = System.nanoTime();
        double ratio = Math.clamp(health / Math.max(1F, max), 0F, 1F);
        shownHealth = barTarget == living
                ? ratio + (shownHealth - ratio) * Math.exp(-Math.max(0L, now - barTime) / 100_000_000.0)
                : ratio;
        barTime = now;
        barTarget = living;
        int fill = (int) Math.round(shownHealth * TRACK_WIDTH);
        int shield = (int) Math.round(Math.clamp(absorption / Math.max(1F, max), 0F, 1F) * TRACK_WIDTH);
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

    private void renderScreen(GuiGraphicsExtractor graphics, DeltaTracker ticks) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (!config.isTargetHudEnabled() || client.player == null || client.level == null) {
            clear();
            return;
        }
        if (config.isTargetHudWorld()) {
            drag.cancel(manager);
            view.dispose();
            return;
        }
        LivingEntity living = resolve(client, config);
        if (living == null && GleamMinecraft.screen(client) instanceof ChatScreen) living = client.player;
        if (living == null) {
            barTarget = null;
            drag.cancel(manager);
            view.dispose();
            return;
        }
        Readout data = read(client, living, config);
        if (!data.equals(previous)) {
            scene = scene(data);
            previous = data;
        }
        int height = data.height();
        float scale = OrionHudView.fit(config.getHudScale() * config.getHudElementScale(ID),
                WIDTH, height, graphics.guiWidth(), graphics.guiHeight());
        int w = Math.max(1, Math.round(WIDTH * scale)), h = Math.max(1, Math.round(height * scale));
        var pos = drag.placeWidget(graphics, w, h, (graphics.guiWidth() - w) / 2 + 48,
                graphics.guiHeight() / 2 + 25, manager);
        if (pos == null) return;
        view.draw(graphics, scene, pos.x(), pos.y(), w, h);
        graphics.nextStratum();
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(pos.x(), pos.y());
            pose.scale(w / (float) WIDTH, h / (float) height);
            if (living instanceof AbstractClientPlayer player) {
                Identifier skin = player.getSkin().body().texturePath();
                graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 8, 9, 8, 8, 24, 24, 8, 8, 64, 64);
                if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT))
                    graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 8, 9, 40, 8, 24, 24, 8, 8, 64, 64);
            }
            if (data.equipment) for (int i = 0; i < EQUIPMENT.length; i++) {
                var item = living.getItemBySlot(EQUIPMENT[i]);
                if (item.isEmpty()) continue;
                int x = 8 + i * 20;
                //? if >=26.1 {
                graphics.item(item, x, BODY + 2);
                graphics.itemDecorations(client.font, item, x, BODY + 2);
                //?} else {
                /*graphics.renderItem(item, x, BODY + 2);
                graphics.renderItemDecorations(client.font, item, x, BODY + 2);
                *///?}
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static OrionHudView.Scene scene(Readout data) {
        var scene = new OrionHudView.Scene(WIDTH, data.height());
        scene.panel(0, 0, WIDTH, data.height(), 7);
        scene.well(7, 8, 26, 26, 4);
        if (!data.portrait) scene.icon(TARGET_ICON, 12, 13, 16, "var(--accent)", false);
        scene.label(data.name, TRACK_X, 4, 76, 15, 9, "var(--text)", "left", true);
        scene.label(data.ping, 115, 4, 31, 15, 7, "var(--muted)", "right", false);
        scene.label(data.health, TRACK_X, 17, 75, 12, 8, OrionHudView.color(HEALTH), "left", true);
        scene.label(data.distance, 109, 17, 37, 12, 7.5F, "var(--muted)", "right", false);
        scene.well(TRACK_X, TRACK_Y, TRACK_WIDTH, 4, 2);
        if (data.fill > 0) scene.box(TRACK_X, TRACK_Y, data.fill, 4, 2, OrionHudView.color(HEALTH), null);
        if (data.absorption > 0) scene.box(TRACK_X, TRACK_Y + 5, data.absorption, 2, 1, OrionHudView.color(ABSORPTION), null);
        if (data.equipment) scene.divider(7, BODY, WIDTH - 14);
        return scene;
    }

    private void renderWorld(LevelRenderContext context) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (!config.isTargetHudEnabled() || !config.isTargetHudWorld() || client.player == null
                //? if >=26.2 {
                || client.level == null || client.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return;
                //?} else {
                /*|| client.level == null || client.options.hideGui) return;
                *///?}
        LivingEntity living = resolve(client, config);
        var camera = context.levelState().cameraRenderState;
        if (living == null || camera == null || !camera.initialized || camera.orientation == null) return;
        var position = living.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        var data = read(client, living, config);
        var stack = context.poseStack();
        stack.pushPose();
        try {
            stack.translate(position.x - camera.pos.x, position.y - camera.pos.y + living.getBbHeight() * .48, position.z - camera.pos.z);
            GleamPose.rotate(stack, camera.orientation);
            float size = .0058F * config.getHudElementScale(ID);
            // Keep the whole card to the left of the body in camera space.
            float shoulder = living.getBbWidth() * .64F + .07F;
            stack.translate(-shoulder, 0, .035F);
            stack.scale(size, -size, size);
            stack.translate(-WIDTH, -BODY / 2F, 0);
            boolean light = config.getClickGuiTheme() == 4;
            int text = light ? 0xff20232c : 0xfff1f3fc, muted = light ? 0xff464d60 : 0xffacb4cc;
            int background = light ? 0xf6edf0f9 : 0xf21c1e29;
            int track = light ? 0xffcdd2e0 : 0xff363a49;
            int hp = light ? 0xff20794d : HEALTH;
            var collector = context.submitNodeCollector();
            collector.submitCustomGeometry(stack, OrionEspRenderTypes.colorQuadsDepthTested(), (pose, vertices) -> {
                // Inset corners on the depth-tested billboard.
                quad(pose, vertices, 3, 0, WIDTH - 3, data.height(), 0, background);
                quad(pose, vertices, 0, 3, 3, data.height() - 3, 0, background);
                quad(pose, vertices, WIDTH - 3, 3, WIDTH, data.height() - 3, 0, background);
                quad(pose, vertices, TRACK_X, TRACK_Y, WIDTH - 8, TRACK_Y + 4, .2F, track);
                if (data.fill > 0) quad(pose, vertices, TRACK_X, TRACK_Y, TRACK_X + data.fill, TRACK_Y + 4, .35F, hp);
                if (data.absorption > 0) quad(pose, vertices, TRACK_X, TRACK_Y + 5, TRACK_X + data.absorption,
                        TRACK_Y + 7, .35F, ABSORPTION);
                if (data.equipment) quad(pose, vertices, 7, BODY, WIDTH - 7, BODY + 1, .2F, track);
            });
            if (living instanceof AbstractClientPlayer player) {
                Identifier skin = player.getSkin().body().texturePath();
                portrait(context, stack, skin, 8, .25F);
                if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT))
                    portrait(context, stack, skin, 40, .45F);
            } else {
                drawText(context, stack, "◎", 15, 16, muted);
            }
            stack.translate(0, 0, .4F);
            drawText(context, stack, client.font.plainSubstrByWidth(data.name, 74), TRACK_X, 9, text);
            drawText(context, stack, data.ping, WIDTH - 8 - client.font.width(data.ping), 9, muted);
            drawText(context, stack, data.health, TRACK_X, 20, hp);
            drawText(context, stack, data.distance, WIDTH - 8 - client.font.width(data.distance), 20, muted);
            if (data.equipment) {
                for (int i = 0; i < EQUIPMENT.length; i++) {
                    var item = living.getItemBySlot(EQUIPMENT[i]);
                    if (item.isEmpty()) continue;
                    var state = new ItemStackRenderState();
                    client.getItemModelResolver().updateForLiving(state, item, ItemDisplayContext.GUI, living);
                    stack.pushPose();
                    stack.translate(16 + i * 20, BODY + 11, 3);
                    stack.scale(13, -13, 13);
                    state.submit(stack, collector, FULL_LIGHT, OverlayTexture.NO_OVERLAY, 0);
                    stack.popPose();
                }
            }
        } finally {
            stack.popPose();
        }
    }

    private static void drawText(LevelRenderContext context, PoseStack pose, String text, float x, float y, int color) {
        if (text.isEmpty()) return;
        context.submitNodeCollector().submitText(pose, x, y, Component.literal(text).getVisualOrderText(),
                false, Font.DisplayMode.NORMAL, FULL_LIGHT, color, 0, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float x1, float y1, float z, int color) {
        out.addVertex(pose.pose(), x0, y1, z).setColor(color);
        out.addVertex(pose.pose(), x1, y1, z).setColor(color);
        out.addVertex(pose.pose(), x1, y0, z).setColor(color);
        out.addVertex(pose.pose(), x0, y0, z).setColor(color);
    }

    private static void portrait(LevelRenderContext context, PoseStack pose, Identifier skin, int left, float z) {
        context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.text(skin), (p, out) -> {
            float u0 = left / 64F, u1 = (left + 8) / 64F, v0 = 8 / 64F, v1 = 16 / 64F;
            out.addVertex(p.pose(), 8, 33, z).setUv(u0, v1).setLight(FULL_LIGHT).setColor(-1);
            out.addVertex(p.pose(), 32, 33, z).setUv(u1, v1).setLight(FULL_LIGHT).setColor(-1);
            out.addVertex(p.pose(), 32, 9, z).setUv(u1, v0).setLight(FULL_LIGHT).setColor(-1);
            out.addVertex(p.pose(), 8, 9, z).setUv(u0, v0).setLight(FULL_LIGHT).setColor(-1);
        });
    }
}
