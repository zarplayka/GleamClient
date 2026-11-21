package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import ru.orion.client.OrionClient;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.render.GleamCameraMatrices;

public final class OrionTargetHud {
    public static final String ID = "target_hud";
    private static final int WIDTH = 152, MARGIN = 6, BAR_INSET = 6;
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
    private Layout previousLayout;
    private OrionHudView.Scene scene;

    private record Layout(int height, int faceY, int faceSize, int textX, int barY) {}

    private record Readout(String name, int nameWidth, String health, String maxHealth, String extraHealth,
                           String distance, int ping, int fill, int absorption, boolean portrait, boolean equipment) {}

    private record Placement(int x, int y, int width, int height) {}

    public OrionTargetHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), (graphics, ticks) -> drag.draw(graphics, () -> render(graphics, ticks)));
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

    private void render(GuiGraphicsExtractor graphics, DeltaTracker ticks) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if ((!config.isTargetHudEnabled() && !drag.fadingOut()) || client.player == null || client.level == null) {
            clear();
            return;
        }
        boolean attached = config.isTargetHudWorld() && !OrionHudEditorScreen.editing(client);
        LivingEntity living = resolve(client, config);
        if (!attached && living == null && OrionHudEditorScreen.editing(client)) living = client.player;
        if (living == null) {
            barTarget = null;
            drag.cancel();
            view.dispose();
            return;
        }
        Layout layout = attached ? ATTACHED : SCREEN;
        Readout data = read(client, living, config);
        int height = layout.height;
        float scale = config.getHudScale() * config.getHudElementScale(ID);
        Placement placement;
        if (attached) {
            drag.cancel();
            placement = attachedPlacement(graphics, client, living, ticks, height, scale);
        } else {
            float fit = OrionHudView.fit(scale * OrionHudView.layoutScale(graphics.guiWidth(), graphics.guiHeight()),
                    WIDTH, height, graphics.guiWidth(), graphics.guiHeight());
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
        boolean attached = layout == ATTACHED;
        if (!attached) scene.panel(0, 0, WIDTH, layout.height, 8);
        scene.box(0, 0, WIDTH, layout.height, 8, "#0e1119e8", "#c1d4ad30");
        scene.well(5, layout.faceY - 1, layout.faceSize + 2, layout.faceSize + 2, 4);
        if (!data.portrait) {
            int iconSize = attached ? 21 : 16;
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
        int y = Math.clamp(Math.round(torsoY - cardHeight * width / (2F * WIDTH)),
                MARGIN, screenHeight - height - MARGIN);
        return new Placement(left ? leftEnd - width : rightStart, y, width, height);
    }
}
