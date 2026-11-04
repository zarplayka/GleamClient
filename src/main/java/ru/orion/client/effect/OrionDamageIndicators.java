package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Числа наблюдаемых изменений здоровья и поглощения. */
public final class OrionDamageIndicators {
    private static final long WAIT_NANOS = 900_000_000L;
    private static final double TRACK_RANGE_SQUARED = 32D * 32D;
    private static final int HEAL_COLOR = 0x66EF9E;
    private static final int MAX_INDICATORS = 32;
    private final OrionConfigManager configManager;
    private final Map<LivingEntity, Pending> pending = new IdentityHashMap<>();
    private final Map<LivingEntity, Sample> health = new IdentityHashMap<>();
    private int scan;
    private int lane;
    private final List<Indicator> indicators = new ArrayList<>();
    private final Map<String, Label> labels = new HashMap<>();
    private ClientLevel level;
    private final boolean[] labelSlots = new boolean[MAX_INDICATORS];

    public OrionDamageIndicators(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }
    public void onAttack(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (!config.isDamageIndicatorsEnabled() || !config.isDamageIndicatorsOwnHits() || client.level == null
                || !(entity instanceof LivingEntity target) || target == client.player) return;
        if (client.level != level) { clear(); level = client.level; }
        float health = effectiveHealth(target);
        Pending previous = pending.get(target);
        long now = System.nanoTime();
        if (previous != null && now - previous.created() < WAIT_NANOS && previous.health() - health > .05F) {
            addIndicator(target, previous.health() - health, false, now);
        }
        pending.put(target, new Pending(health, now));
    }
    private void tick(Minecraft client) {
        OrionConfig config = configManager.getConfig();
        if (!config.isDamageIndicatorsEnabled() || client.level == null || client.player == null) {
            clear(); level = null; return;
        }
        if (level != client.level) { clear(); level = client.level; }
        long now = System.nanoTime();
        if (indicators.removeIf(indicator -> now - indicator.created() >= indicator.lifetime())) {
            releaseUnusedLabels();
        }
        if (!config.isDamageIndicatorsOwnHits()) pending.clear();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            LivingEntity target = entry.getKey();
            Pending attack = entry.getValue();
            float damage = attack.health() - effectiveHealth(target);
            if (damage > 0.05F && Float.isFinite(damage)) {
                addIndicator(target, damage, false, now);
                iterator.remove();
            } else if (now - attack.created() >= WAIT_NANOS || target.isRemoved()) {
                iterator.remove();
            }
        }
        if (!config.isDamageIndicatorsOwnHits() || config.isDamageIndicatorsHealing()) {
            trackNearby(client, config, now);
        } else {
            health.clear();
        }
        releaseUnusedLabels();
    }

    private void trackNearby(Minecraft client, OrionConfig config, long now) {
        scan++;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == client.player || living.isRemoved()
                    || living.distanceToSqr(client.player) > TRACK_RANGE_SQUARED) continue;
            float current = effectiveHealth(living);
            if (!Float.isFinite(current)) continue;
            Sample previous = health.put(living, new Sample(current, scan));
            if (previous == null) continue;
            float delta = current - previous.health();
            if (delta < -.05F && !config.isDamageIndicatorsOwnHits()) {
                addIndicator(living, -delta, false, now);
            } else if (delta > .05F && config.isDamageIndicatorsHealing()) {
                addIndicator(living, delta, true, now);
            }
        }
        health.values().removeIf(sample -> sample.scan() != scan);
    }

    private static float effectiveHealth(LivingEntity target) {
        return target.getHealth() + target.getAbsorptionAmount();
    }

    private void addIndicator(LivingEntity target, float damage, boolean healing, long now) {
        if (!Float.isFinite(damage) || damage <= .05F) return;
        if (indicators.size() >= MAX_INDICATORS) {
            indicators.remove(0);
            releaseUnusedLabels();
        }
        String text = (healing ? "+" : "") + (damage >= 999.9F ? "999+" : formatDamage(damage));
        Label label = labels.computeIfAbsent(text, this::createLabel);
        Vec3 origin = target.position().add(0, target.getBbHeight() * .78D, 0);
        float spread = ((lane++ % 5 + 5) % 5 - 2) * .095F;
        long lifetime = (long) (configManager.getConfig().getDamageIndicatorsLifetime() * 1_000_000_000D);
        indicators.add(new Indicator(origin, damage, healing, !healing && target.getHealth() <= 0F,
                spread, now, lifetime, label));
    }
    private static String formatDamage(float damage) {
        float rounded = Math.round(damage * 10F) / 10F;
        return rounded == Math.rint(rounded) ? String.format(Locale.ROOT, "%.0f", rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }
    private Label createLabel(String text) {
        Font font = new Font("Segoe UI", Font.BOLD, 48);
        if (!font.canDisplay('Я') || !font.canDisplay('ё')) font = new Font(Font.SANS_SERIF, Font.BOLD, 48);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        var metrics = pg.getFontMetrics(font);
        int width = Math.max(1, (int) Math.ceil(metrics.getStringBounds(text, pg).getWidth()) + 16);
        int height = metrics.getHeight() + 12;
        int baseline = 5 + metrics.getAscent();
        pg.dispose();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            var outline = font.createGlyphVector(g.getFontRenderContext(), text).getOutline(8F, baseline);
            g.setColor(new Color(0, 0, 0, 235));
            g.setStroke(new BasicStroke(4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(outline);
            g.setColor(Color.WHITE);
            g.fill(outline);
        } finally { g.dispose(); }
        NativeImage pixels = new NativeImage(width, height, true);
        int[] colors = image.getRGB(0, 0, width, height, null, 0, width);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels.setPixel(x, y, colors[y * width + x]);
        int slot = 0;
        while (slot < labelSlots.length && labelSlots[slot]) slot++;
        if (slot == labelSlots.length) throw new IllegalStateException("Слишком много текстур индикаторов");
        labelSlots[slot] = true;
        Identifier id = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "damage_indicator_" + slot);
        DynamicTexture texture = new DynamicTexture(() -> id.toString(), pixels);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Label(id, width, height, slot);
    }
    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = configManager.getConfig();
        if (!config.isDamageIndicatorsEnabled() || client.level == null || client.level != level || indicators.isEmpty()) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        var cameraRotation = client.gameRenderer.mainCamera().rotation();
        int low = OrionWorldChanger.parseOpaqueColor(config.getDamageIndicatorsLowColor());
        int high = OrionWorldChanger.parseOpaqueColor(config.getDamageIndicatorsHighColor());
        long now = System.nanoTime();
        PoseStack stack = context.poseStack();
        for (Indicator indicator : indicators) {
            float progress = Math.max(0F, Math.min(1F, (now - indicator.created()) / (float) indicator.lifetime()));
            if (progress >= 1F) continue;
            float fade = Math.min(1F, (1F - progress) / .34F);
            float mix = indicator.lethal() ? 1F : Math.clamp(indicator.damage() / config.getDamageIndicatorsThreshold(), 0F, 1F);
            int alpha = Math.round(fade * 255F);
            int color = indicator.healing() ? (alpha << 24) | HEAL_COLOR : ARGB.color(alpha,
                    Math.round(ARGB.red(low) + (ARGB.red(high) - ARGB.red(low)) * mix),
                    Math.round(ARGB.green(low) + (ARGB.green(high) - ARGB.green(low)) * mix),
                    Math.round(ARGB.blue(low) + (ARGB.blue(high) - ARGB.blue(low)) * mix));
            double rise = .85D * progress * (2D - progress);
            Vec3 pos = indicator.position().subtract(camera).add(0, rise, 0);
            Label label = indicator.label();
            float appear = Math.min(1F, (now - indicator.created()) / 150_000_000F);
            float size = .62F + .38F * appear * (2F - appear);
            float height = .32F * size * config.getDamageIndicatorsScale() * (indicator.lethal() ? 1.14F : 1F);
            float width = height * label.width() / label.height();
            stack.pushPose();
            stack.translate(pos.x, pos.y, pos.z);
            stack.rotate(cameraRotation);
            stack.translate(indicator.spread() * (1F + progress), 0, 0);
            context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.texturedDepthTested(label.id()),
                    (pose, vertices) -> quad(pose, vertices, width, height, color));
            stack.popPose();
        }
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float width, float height, int color) {
        vc.addVertex(pose, -width / 2, -height / 2, 0).setUv(0, 1).setColor(color);
        vc.addVertex(pose, width / 2, -height / 2, 0).setUv(1, 1).setColor(color);
        vc.addVertex(pose, width / 2, height / 2, 0).setUv(1, 0).setColor(color);
        vc.addVertex(pose, -width / 2, height / 2, 0).setUv(0, 0).setColor(color);
    }
    private void releaseUnusedLabels() {
        var iterator = labels.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (indicators.stream().noneMatch(indicator -> indicator.label() == entry.getValue())) {
                Minecraft.getInstance().getTextureManager().release(entry.getValue().id());
                labelSlots[entry.getValue().slot()] = false;
                iterator.remove();
            }
        }
    }
    private void clear() {
        pending.clear(); health.clear(); indicators.clear();
        lane = scan = 0;
        for (Label label : labels.values()) Minecraft.getInstance().getTextureManager().release(label.id());
        labels.clear();
        java.util.Arrays.fill(labelSlots, false);
    }
    private record Pending(float health, long created) { }
    private record Sample(float health, int scan) { }
    private record Label(Identifier id, int width, int height, int slot) { }
    private record Indicator(Vec3 position, float damage, boolean healing, boolean lethal,
                             float spread, long created, long lifetime, Label label) { }
}
