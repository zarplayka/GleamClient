package ru.orion.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import org.joml.Vector3f;
import ru.orion.client.render.GleamPose;

public final class OrionTargetHud {
    public static final String ID = "target_hud";
    private static final int WIDTH = 152, BAR_INSET = 6;
    private static final float WORLD_UNIT = 1.25F / WIDTH;
    private static final int FULL_LIGHT = 0xF000F0;
    private static final int BAR_WIDTH = WIDTH - BAR_INSET * 2;
    private static final Layout SCREEN = new Layout(45, 6, 28, 39, 39);
    private static final Layout ATTACHED = new Layout(48, 6, 31, 42, 42);
    private static final String HEALTH = "#b7df52", ABSORPTION = "#e8b967";
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

    private record Layout(int height, int faceY, int faceSize, int textX, int barY) {}

    private record Readout(String name, int nameWidth, String health, String maxHealth, String extraHealth,
                           String distance, int ping, int fill, int absorption, boolean portrait, boolean equipment) {}

    private record Placement(int x, int y, int width, int height) {}

    public OrionTargetHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), (graphics, ticks) -> drag.draw(graphics, () -> render(graphics)));
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
        drag.cancel();
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
        int fill = (int) Math.round(shownHealth * BAR_WIDTH);
        int shield = (int) Math.round(Math.clamp(absorption / Math.max(1F, max), 0F, 1F) * BAR_WIDTH);
        double range = Math.sqrt(living.getBoundingBox().distanceToSqr(client.player.getEyePosition()));
        int decimeters = (int) Math.round(range * 10.0);
        String distance = decimeters / 10 + "." + decimeters % 10 + " m";
        var connection = client.getConnection();
        var playerInfo = config.isTargetHudPing() && living instanceof Player && connection != null
                ? connection.getPlayerInfo(living.getUUID()) : null;
        int ping = playerInfo == null ? -1 : playerInfo.getLatency();
        boolean equipped = false;
        if (config.isTargetHudEquipment()) {
            for (var slot : EQUIPMENT) if (!living.getItemBySlot(slot).isEmpty()) {
                equipped = true;
                break;
            }
        }
        String name = OrionNameProtect.replace(living.getName().getString());
        return new Readout(name, client.font.width(name) + 4, healthText(health), healthText(max),
                absorption > 0 ? "+" + healthText(absorption) : "", distance, ping, fill, shield,
                living instanceof AbstractClientPlayer, equipped);
    }

    private static String healthText(float value) {
        int tenths = Math.round(value * 10);
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if ((!config.isTargetHudEnabled() && !drag.fadingOut()) || client.player == null || client.level == null) {
            clear();
            return;
        }
        boolean editing = OrionHudEditorScreen.editing(client);
        if (config.isTargetHudWorld() && !editing) {
            drag.cancel();
            view.dispose();
            return;
        }
        LivingEntity living = resolve(client, config);
        if (living == null && editing) living = client.player;
        if (living == null) {
            barTarget = null;
            drag.cancel();
            view.dispose();
            return;
        }
        Layout layout = SCREEN;
        Readout data = read(client, living, config);
        int height = layout.height;
        float scale = config.getHudScale() * config.getHudElementScale(ID);
        float fit = OrionHudView.fit(scale * OrionHudView.layoutScale(graphics.guiWidth(), graphics.guiHeight()),
                WIDTH, height, graphics.guiWidth(), graphics.guiHeight());
        int w = Math.max(1, Math.round(WIDTH * fit)), h = Math.max(1, Math.round(height * fit));
        var pos = drag.placeWidget(graphics, w, h, (graphics.guiWidth() - w) / 2 + 48,
                graphics.guiHeight() / 2 + 25, manager);
        Placement placement = pos == null ? null : new Placement(pos.x(), pos.y(), w, h);
        if (placement == null) return;
        if (!data.equals(previous)) {
            scene = scene(data, layout);
            previous = data;
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
                graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 6, layout.faceY, 8, 8,
                        layout.faceSize, layout.faceSize, 8, 8, 64, 64, OrionHudDrag.tint(-1));
                if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT))
                    graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 6, layout.faceY, 40, 8,
                            layout.faceSize, layout.faceSize, 8, 8, 64, 64, OrionHudDrag.tint(-1));
            }
            if (data.equipment) {
                pose.translate(layout.textX, layout.barY - 11);
                pose.scale(.58F, .58F);
                for (int i = 0; i < EQUIPMENT.length; i++) {
                    var item = living.getItemBySlot(EQUIPMENT[i]);
                    if (item.isEmpty()) continue;
                    int x = i * 17;
                    //? if >=26.1 {
                    graphics.item(item, x, 0);
                    graphics.itemDecorations(client.font, item, x, 0);
                    //?} else {
                    /*graphics.renderItem(item, x, 0);
                    graphics.renderItemDecorations(client.font, item, x, 0);
                    *///?}
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static OrionHudView.Scene scene(Readout data, Layout layout) {
        var scene = new OrionHudView.Scene(WIDTH, layout.height);
        scene.panel(0, 0, WIDTH, layout.height, 8);
        scene.box(0, 0, WIDTH, layout.height, 8, "#0e1119e8", "#c1d4ad30");
        scene.well(5, layout.faceY - 1, layout.faceSize + 2, layout.faceSize + 2, 4);
        if (!data.portrait) {
            int iconSize = 16;
            scene.icon(TARGET_ICON, 6 + (layout.faceSize - iconSize) / 2F,
                    layout.faceY + (layout.faceSize - iconSize) / 2F, iconSize, HEALTH, false);
        }
        int nameWidth = Math.min(data.nameWidth, WIDTH - layout.textX - (data.ping < 0 ? 7 : 42));
        scene.label(data.name, layout.textX, 5, nameWidth, 12, 9, "#f4f6f5", "left", true);
        if (data.ping >= 0) {
            int pingX = layout.textX + nameWidth + 2;
            String tint = data.ping < 150 ? "#67d99b" : data.ping < 300 ? "#f1c067" : "#ee8f8f";
            int bars = data.ping < 70 ? 4 : data.ping < 150 ? 3 : data.ping < 300 ? 2 : 1;
            for (int i = 0; i < 4; i++) scene.box(pingX + i * 2, 13 - i * 1.6F, 1.2F, 2 + i * 1.6F,
                    .55F, i < bars ? tint : "#ffffff30", null);
            scene.label(Integer.toString(data.ping), pingX + 10, 6, WIDTH - pingX - 15, 11,
                    7, tint, "left", true);
        }
        String healthMax = "/ " + data.maxHealth;
        int healthWidth = data.health.length() * 5 + 2;
        int maxWidth = healthMax.length() * 4 + 2;
        scene.label(data.health, layout.textX, 16, healthWidth, 12, 8, HEALTH, "left", true);
        scene.label(healthMax, layout.textX + healthWidth, 16, maxWidth, 12, 8, "#aab2bb", "left", false);
        scene.label(data.extraHealth, layout.textX + healthWidth + maxWidth + 2, 16, 19, 12,
                7, ABSORPTION, "left", true);
        scene.label(data.distance, 116, 16, 30, 12, 7.5F, "#aab2bb", "right", false);
        scene.box(BAR_INSET, layout.barY, BAR_WIDTH, 3, 1.5F, "#a3b39329", null);
        if (data.fill > 0) scene.box(BAR_INSET, layout.barY, data.fill, 3, 1.5F, HEALTH, null);
        int shield = Math.min(data.absorption, BAR_WIDTH - data.fill);
        if (shield > 0) scene.box(BAR_INSET + data.fill, layout.barY, shield, 3, 1.5F, ABSORPTION, null);
        return scene;
    }

    private void renderWorld(LevelRenderContext context) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (!config.isTargetHudWorld() || !config.isTargetHudEnabled() || client.player == null
                || client.level == null || OrionHudEditorScreen.editing(client)) return;
        //? if >=26.2 {
        if (client.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return;
        //?} else {
        /*if (client.options.hideGui) return;
        *///?}
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.orientation == null) return;
        var living = resolve(client, config);
        if (living == null) {
            barTarget = null;
            return;
        }

        var layout = ATTACHED;
        var data = read(client, living, config);
        float unit = WORLD_UNIT * config.getHudScale() * config.getHudElementScale(ID);
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 torso = living.getPosition(partial).add(0, living.getBbHeight() * .52, 0).subtract(camera.pos);
        Vec3 away = torso.normalize();
        // Put the whole board past the entity's bounding box, with one edge extending behind the model.
        var side = camera.orientation.transform(new Vector3f(1, 0, 0));
        double depth = living.getBbWidth() * .71 + living.getBbHeight() * .52 * Math.abs(away.y) + .13;
        double offset = WIDTH * unit * .5 + living.getBbWidth() * .27;
        Vec3 anchor = torso.add(away.scale(depth)).add(side.x * offset, side.y * offset, side.z * offset);

        PoseStack pose = context.poseStack();
        pose.pushPose();
        try {
            pose.translate(anchor.x, anchor.y, anchor.z);
            GleamPose.rotate(pose, camera.orientation);
            pose.scale(unit, -unit, unit);
            pose.translate(-WIDTH / 2F, -layout.height / 2F, 0);
            var collector = context.submitNodeCollector();
            collector.submitCustomGeometry(pose, OrionEspRenderTypes.worldLyricsSolid(), (matrix, vertices) -> {
                worldRect(matrix, vertices, 0, 0, WIDTH, layout.height, 0, 0xff34453a);
                worldRect(matrix, vertices, 1, 1, WIDTH - 2, layout.height - 2, .12F, 0xff0e1119);
                worldRect(matrix, vertices, 5, layout.faceY - 1, layout.faceSize + 2, layout.faceSize + 2, .25F, 0xff202a2d);
                worldRect(matrix, vertices, BAR_INSET, layout.barY, BAR_WIDTH, 3, .25F, 0xff2e3a32);
                if (data.fill > 0) worldRect(matrix, vertices, BAR_INSET, layout.barY, data.fill, 3, .4F, 0xffb7df52);
                int shield = Math.min(data.absorption, BAR_WIDTH - data.fill);
                if (shield > 0) worldRect(matrix, vertices, BAR_INSET + data.fill, layout.barY, shield, 3, .4F, 0xffe8b967);
            });
            if (living instanceof AbstractClientPlayer player) {
                Identifier skin = player.getSkin().body().texturePath();
                worldFace(context, pose, skin, layout, 8, .45F);
                if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT)) {
                    worldFace(context, pose, skin, layout, 40, .6F);
                }
            } else {
                worldLabel(context, pose, "◎", 13, layout.faceY + 9, 1.4F, 0xffb7df52);
            }
            int nameLimit = WIDTH - layout.textX - (data.ping < 0 ? 8 : 35);
            worldLabel(context, pose, client.font.plainSubstrByWidth(data.name, nameLimit), layout.textX, 5, 1F, 0xfff4f6f5);
            if (data.ping >= 0) {
                String ping = data.ping + " ms";
                worldLabel(context, pose, ping, WIDTH - 6 - client.font.width(ping) * .75F, 6, .75F,
                        data.ping < 150 ? 0xff67d99b : data.ping < 300 ? 0xfff1c067 : 0xffee8f8f);
            }
            worldLabel(context, pose, data.health, layout.textX, 17, .85F, 0xffb7df52);
            float next = layout.textX + client.font.width(data.health) * .85F + 3;
            String max = "/ " + data.maxHealth;
            worldLabel(context, pose, max, next, 17, .85F, 0xffaab2bb);
            float distanceX = WIDTH - 6 - client.font.width(data.distance) * .75F;
            next += client.font.width(max) * .85F + 3;
            if (!data.extraHealth.isEmpty() && next + client.font.width(data.extraHealth) * .75F < distanceX - 2) {
                worldLabel(context, pose, data.extraHealth, next, 17, .75F, 0xffe8b967);
            }
            worldLabel(context, pose, data.distance, distanceX, 17, .75F, 0xffaab2bb);
            if (data.equipment) {
                for (int i = 0; i < EQUIPMENT.length; i++) {
                    var item = living.getItemBySlot(EQUIPMENT[i]);
                    if (item.isEmpty()) continue;
                    var state = new ItemStackRenderState();
                    client.getItemModelResolver().updateForLiving(state, item, ItemDisplayContext.GUI, living);
                    pose.pushPose();
                    pose.translate(layout.textX + 6 + i * 14, 33, 1.5F);
                    pose.scale(11, -11, 3);
                    state.submit(pose, collector, FULL_LIGHT, 0, 0);
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static void worldRect(PoseStack.Pose pose, VertexConsumer vertices, float x, float y,
                                  float width, float height, float z, int color) {
        vertices.addVertex(pose.pose(), x, y, z).setColor(color);
        vertices.addVertex(pose.pose(), x, y + height, z).setColor(color);
        vertices.addVertex(pose.pose(), x + width, y + height, z).setColor(color);
        vertices.addVertex(pose.pose(), x + width, y, z).setColor(color);
    }

    private static void worldFace(LevelRenderContext context, PoseStack pose, Identifier skin,
                                  Layout layout, int faceU, float z) {
        float x = 6, y = layout.faceY, size = layout.faceSize;
        float u0 = faceU / 64F, u1 = (faceU + 8) / 64F;
        context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.text(skin), (matrix, vertices) -> {
            faceVertex(matrix, vertices, x, y, z, u0, .125F);
            faceVertex(matrix, vertices, x, y + size, z, u0, .25F);
            faceVertex(matrix, vertices, x + size, y + size, z, u1, .25F);
            faceVertex(matrix, vertices, x + size, y, z, u1, .125F);
        });
    }

    private static void faceVertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z, float u, float v) {
        vertices.addVertex(pose.pose(), x, y, z).setUv(u, v).setLight(FULL_LIGHT).setColor(-1);
    }

    private static void worldLabel(LevelRenderContext context, PoseStack pose, String text,
                                   float x, float y, float scale, int color) {
        pose.pushPose();
        pose.translate(x, y, .75F);
        pose.scale(scale, scale, 1);
        context.submitNodeCollector().submitText(pose, 0, 0, Component.literal(text).getVisualOrderText(), false,
                Font.DisplayMode.NORMAL, FULL_LIGHT, color, 0, 0);
        pose.popPose();
    }
}
