package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
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
import ru.orion.client.gui.OrionInterfaceFonts;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public final class OrionTargetHud {
    public static final String ID = "target_hud";
    private static final int WIDTH = 152, BAR_INSET = 6;
    private static final int PIXEL_SCALE = 3;
    private static final float WORLD_UNIT = 1.46F / WIDTH;
    private static final Identifier WORLD_CARD = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "target_hud_world");
    private static final int FULL_LIGHT = 0xF000F0;
    private static final int BAR_WIDTH = WIDTH - BAR_INSET * 2;
    private static final Layout SCREEN = new Layout(45, 6, 28, 39, 39);
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
    private DynamicTexture worldTexture;
    private BufferedImage worldCanvas;
    private long worldTextureAt;
    private WorldReadout previousWorld;

    private record WorldReadout(Readout data, int theme) {}

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
        releaseWorld();
    }

    private void releaseWorld() {
        if (worldTexture == null) return;
        Minecraft.getInstance().getTextureManager().release(WORLD_CARD);
        worldTexture = null;
        worldCanvas = null;
        previousWorld = null;
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
        if (!config.isTargetHudWorld() || !config.isTargetHudEnabled() || client.player == null || client.level == null) {
            releaseWorld();
            return;
        }
        if (OrionHudEditorScreen.editing(client)) return;
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

        Readout data = read(client, living, config);
        Layout layout = SCREEN;
        WorldReadout card = new WorldReadout(data, config.getClickGuiTheme());
        long now = System.nanoTime();
        if (previousWorld == null || !card.equals(previousWorld) && now - worldTextureAt >= 40_000_000L) {
            updateWorldTexture(client, card);
            worldTextureAt = now;
        }
        if (worldTexture == null) return;
        float unit = WORLD_UNIT * config.getHudScale() * config.getHudElementScale(ID);
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 torso = living.getPosition(partial).add(0, living.getBbHeight() * .60, 0).subtract(camera.pos);
        var side = camera.orientation.transform(new Vector3f(1, 0, 0));
        var back = camera.orientation.transform(new Vector3f(0, 0, -1));
        double silhouette = (Math.abs(side.x) + Math.abs(side.z)) * living.getBbWidth() * .5;
        double horizontal = WIDTH * unit * .5 + silhouette * .88 + .025;
        double depth = living.getBbWidth() * .45F + .13F;
        Vec3 anchor = torso.add(back.x * depth, back.y * depth, back.z * depth)
                .subtract(side.x * horizontal, side.y * horizontal, side.z * horizontal);
        PoseStack pose = context.poseStack();
        pose.pushPose();
        try {
            pose.translate(anchor.x, anchor.y, anchor.z);
            GleamPose.rotate(pose, camera.orientation);
            pose.scale(unit, -unit, unit);
            pose.translate(-WIDTH / 2F, -layout.height / 2F, 0);
            worldQuad(context, pose, WORLD_CARD, 0, 0, WIDTH, layout.height, 0, 0, 0, 1, 1);
            if (living instanceof AbstractClientPlayer player) {
                Identifier skin = player.getSkin().body().texturePath();
                worldQuad(context, pose, skin, 6, layout.faceY, layout.faceSize, layout.faceSize, 1.4F, .125F, .125F, .25F, .25F);
                if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT)) {
                    worldQuad(context, pose, skin, 6, layout.faceY, layout.faceSize, layout.faceSize, 2.2F, .625F, .125F, .75F, .25F);
                }
            }
            if (data.equipment) {
                for (int i = 0; i < EQUIPMENT.length; i++) {
                    var item = living.getItemBySlot(EQUIPMENT[i]);
                    if (item.isEmpty()) continue;
                    var state = new ItemStackRenderState();
                    client.getItemModelResolver().updateForLiving(state, item, ItemDisplayContext.GUI, living);
                    pose.pushPose();
                    pose.translate(layout.textX + (i * 17 + 8) * .58F, layout.barY - 11 + 8 * .58F, 3.2F);
                    pose.scale(16 * .58F, -16 * .58F, 3);
                    state.submit(pose, context.submitNodeCollector(), FULL_LIGHT, 0, 0);
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private void updateWorldTexture(Minecraft client, WorldReadout card) {
        int width = WIDTH * PIXEL_SCALE, height = SCREEN.height * PIXEL_SCALE;
        if (worldCanvas == null) worldCanvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = worldCanvas.createGraphics();
        try {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
            g.scale(PIXEL_SCALE, PIXEL_SCALE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintCard(g, scene(card.data(), SCREEN), card.theme());
        } finally {
            g.dispose();
        }
        if (worldTexture == null) {
            worldTexture = new DynamicTexture(() -> "Gleam TargetHUD", new NativeImage(width, height, true));
            client.getTextureManager().register(WORLD_CARD, worldTexture);
        }
        var image = worldTexture.getPixels();
        int[] colors = ((java.awt.image.DataBufferInt) worldCanvas.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setPixel(x, y, colors[y * width + x]);
        worldTexture.upload();
        previousWorld = card;
    }

    private static void paintCard(Graphics2D g, OrionHudView.Scene scene, int theme) {
        for (var entry : scene.themedParts(theme)) {
            var part = entry.getAsJsonObject();
            float x = part.get("x").getAsFloat(), y = part.get("y").getAsFloat();
            float w = part.get("w").getAsFloat(), h = part.get("h").getAsFloat();
            if (w <= 0 || h <= 0) continue;
            String kind = part.get("kind").getAsString();
            float radius = part.has("radius") ? 2 * part.get("radius").getAsFloat() : 0F;
            var area = new RoundRectangle2D.Float(x, y, w, h, radius, radius);
            switch (kind) {
                case "panel" -> {
                    if (theme == 4) g.setPaint(new GradientPaint(x, y, new Color(0xf3f6fc), x, y + h, new Color(0xe7ecf7)));
                    else if (theme == 5) g.setPaint(new GradientPaint(x, y, new Color(0xd352626f, true),
                            x, y + h, new Color(0xdc222930, true)));
                    else g.setColor(new Color(0xeb0f1216, true));
                    g.fill(area);
                    paintBorder(g, x, y, w, h, radius, theme == 4 ? 0x26414b69 : theme == 5 ? 0x53edf6ff : 0x26edf7f2);
                }
                case "well" -> {
                    g.setColor(new Color(theme == 4 ? 0x0a414b69 : theme == 5 ? 0x330b1118 : 0x08ffffff, true));
                    g.fill(area);
                    paintBorder(g, x, y, w, h, radius, theme == 4 ? 0x26414b69 : theme == 5 ? 0x24e8f4ff : 0x26edf7f2);
                }
                case "box" -> {
                    g.setColor(new Color(OrionHudView.Scene.argb(part.get("color").getAsString()), true));
                    g.fill(area);
                    if (part.has("border")) paintBorder(g, x, y, w, h, radius,
                            OrionHudView.Scene.argb(part.get("border").getAsString()));
                }
                case "label" -> {
                    Graphics2D label = (Graphics2D) g.create();
                    try {
                        label.clip(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
                        label.setFont(OrionInterfaceFonts.face(part.get("bold").getAsBoolean() ? 3 : 0, part.get("size").getAsFloat()));
                        label.setColor(new Color(OrionHudView.Scene.argb(part.get("color").getAsString()), true));
                        String text = part.get("text").getAsString();
                        var metrics = label.getFontMetrics();
                        float offset = "right".equals(part.get("align").getAsString()) ? Math.max(0, w - metrics.stringWidth(text)) : 0;
                        label.drawString(text, x + offset, y + (h + metrics.getAscent() - metrics.getDescent()) / 2F);
                    } finally {
                        label.dispose();
                    }
                }
                case "icon" -> {
                    // The non-player portrait uses the same reticle geometry as the HUD's SVG.
                    Graphics2D icon = (Graphics2D) g.create();
                    try {
                        icon.translate(x, y);
                        icon.scale(w / 20F, h / 20F);
                        icon.setColor(new Color(OrionHudView.Scene.argb(part.get("color").getAsString()), true));
                        icon.setStroke(new BasicStroke(1.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        icon.draw(new Ellipse2D.Float(4, 4, 12, 12));
                        icon.draw(new Line2D.Float(10, 2, 10, 6));
                        icon.draw(new Line2D.Float(10, 14, 10, 18));
                        icon.draw(new Line2D.Float(2, 10, 6, 10));
                        icon.draw(new Line2D.Float(14, 10, 18, 10));
                    } finally {
                        icon.dispose();
                    }
                }
            }
        }
    }

    private static void paintBorder(Graphics2D g, float x, float y, float w, float h, float radius, int color) {
        if (w < 1F || h < 1F) return;
        g.setColor(new Color(color, true));
        g.draw(new RoundRectangle2D.Float(x + .5F, y + .5F, w - 1, h - 1,
                Math.max(0, radius - 1), Math.max(0, radius - 1)));
    }

    private static void worldQuad(LevelRenderContext context, PoseStack pose, Identifier texture,
                                  float x, float y, float w, float h, float z, float u0, float v0, float u1, float v1) {
        context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.text(texture), (matrix, vertices) -> {
            faceVertex(matrix, vertices, x, y, z, u0, v0);
            faceVertex(matrix, vertices, x, y + h, z, u0, v1);
            faceVertex(matrix, vertices, x + w, y + h, z, u1, v1);
            faceVertex(matrix, vertices, x + w, y, z, u1, v0);
        });
    }

    private static void faceVertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z, float u, float v) {
        vertices.addVertex(pose.pose(), x, y, z).setUv(u, v).setLight(FULL_LIGHT).setColor(-1);
    }

}
