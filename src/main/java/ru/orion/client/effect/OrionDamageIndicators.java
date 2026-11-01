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

/** Показывает только наблюдаемое изменение здоровья после локальной атаки. */
public final class OrionDamageIndicators {
    private static final long WAIT_NANOS = 900_000_000L;
    private static final long LIFE_NANOS = 1_250_000_000L;
    private static final int MAX_INDICATORS = 32;
    private final OrionConfigManager configManager;
    private final Map<LivingEntity, Pending> pending = new IdentityHashMap<>();
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
        if (!configManager.getConfig().isDamageIndicatorsEnabled() || client.level == null
                || !(entity instanceof LivingEntity target) || target == client.player) return;
        if (client.level != level) { clear(); level = client.level; }
        pending.put(target, new Pending(target.getHealth() + target.getAbsorptionAmount(), System.nanoTime()));
    }
    private void tick(Minecraft client) {
        if (!configManager.getConfig().isDamageIndicatorsEnabled() || client.level == null || client.player == null) {
            clear(); level = null; return;
        }
        if (level != client.level) { clear(); level = client.level; }
        long now = System.nanoTime();
        indicators.removeIf(indicator -> now - indicator.created() >= LIFE_NANOS);
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            LivingEntity target = entry.getKey();
            Pending attack = entry.getValue();
            float damage = attack.health() - (target.getHealth() + target.getAbsorptionAmount());
            if (damage > 0.05F && Float.isFinite(damage)) {
                addIndicator(target, damage, now);
                iterator.remove();
            } else if (now - attack.created() >= WAIT_NANOS || target.isRemoved()) {
                iterator.remove();
            }
        }
        releaseUnusedLabels();
    }
    private void addIndicator(LivingEntity target, float damage, long now) {
        if (indicators.size() >= MAX_INDICATORS) {
            indicators.remove(0);
            releaseUnusedLabels();
        }
        String text = damage >= 999.9F ? "999+" : formatDamage(damage);
        Label label = labels.computeIfAbsent(text, this::createLabel);
        Vec3 origin = target.position().add(0, target.getBbHeight() + 0.25D, 0);
        indicators.add(new Indicator(origin, damage, now, label));
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
            g.setFont(font);
            g.setColor(new Color(0, 0, 0, 150)); g.drawString(text, 9F, baseline + 2F);
            g.setColor(Color.WHITE); g.drawString(text, 7F, (float) baseline);
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
            float progress = Math.max(0F, Math.min(1F, (now - indicator.created()) / (float) LIFE_NANOS));
            float fade = Math.min(1F, (1F - progress) * 3F);
            float mix = Math.max(0F, Math.min(1F, indicator.damage() / config.getDamageIndicatorsThreshold()));
            int color = ARGB.color(Math.round(fade * 255),
                    Math.round(ARGB.red(low) + (ARGB.red(high) - ARGB.red(low)) * mix),
                    Math.round(ARGB.green(low) + (ARGB.green(high) - ARGB.green(low)) * mix),
                    Math.round(ARGB.blue(low) + (ARGB.blue(high) - ARGB.blue(low)) * mix));
            Vec3 pos = indicator.position().subtract(camera).add(0, 0.55D * (1D - Math.pow(1D - progress, 2D)), 0);
            Label label = indicator.label();
            float height = 0.26F;
            float width = height * label.width() / label.height();
            stack.pushPose(); stack.translate(pos.x, pos.y, pos.z); stack.rotate(cameraRotation);
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
        pending.clear(); indicators.clear();
        for (Label label : labels.values()) Minecraft.getInstance().getTextureManager().release(label.id());
        labels.clear();
        java.util.Arrays.fill(labelSlots, false);
    }
    private record Pending(float health, long created) { }
    private record Label(Identifier id, int width, int height, int slot) { }
    private record Indicator(Vec3 position, float damage, long created, Label label) { }
}
