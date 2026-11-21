package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionGuiLanguage;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class OrionPotionsHud {
    public static final String ID = "potions_hud";
    private static final int WIDTH = 123, HEADER = 22, ROW = 15, INSET = 9;
    private static final String[] LEVELS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView(ID);
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private List<VisibleEffect> shown;
    private String emptyText;
    private OrionHudView.Scene scene;
    private LocalPlayer sampledPlayer;
    private int sampledTick;
    private List<Effect> sampled = List.of();
    private final TreeMap<Identifier, EffectRow> rows = new TreeMap<>(Comparator.comparing(Identifier::toString));
    private boolean effectsChanged;
    private long animationAt;

    private record Effect(Identifier sprite, String name, String duration) {}
    private record VisibleEffect(Effect effect, float y, float visibility) {}
    private static final class EffectRow {
        private Effect effect;
        private float visibility;
        private boolean present;

        private EffectRow(Effect effect) { this.effect = effect; }
    }

    public OrionPotionsHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), (graphics, ticks) -> drag.draw(graphics, () -> render(graphics, ticks)));
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker ticks) {
        Minecraft client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (client.player == null || client.level == null || (!config.isPotionsEnabled() && !drag.fadingOut())) {
            release();
            sampledPlayer = null;
            rows.clear();
            animationAt = 0L;
            return;
        }
        if (sampledPlayer != client.player || sampledTick != client.player.tickCount) {
            if (sampledPlayer != client.player) {
                rows.clear();
                animationAt = 0L;
            }
            sampledPlayer = client.player;
            sampledTick = client.player.tickCount;
            float tickRate = client.level.tickRateManager().tickrate();
            var current = client.player.getActiveEffects().stream().filter(MobEffectInstance::showIcon)
                    .sorted(Comparator.comparing(effect -> effect.getEffect().value().getDescriptionId()))
                    .map(effect -> describe(effect, tickRate)).toList();
            effectsChanged = !current.equals(sampled) || rows.isEmpty();
            sampled = current;
        }
        if (effectsChanged) {
            rows.values().forEach(row -> row.present = false);
            for (var effect : sampled) {
                var row = rows.computeIfAbsent(effect.sprite(), unused -> new EffectRow(effect));
                row.present = true;
                row.effect = effect;
            }
            effectsChanged = false;
        }
        var clientEditing = OrionHudEditorScreen.editing(client);
        long now = System.nanoTime();
        float delta = animationAt == 0L || !config.isAnimationsEnabled() ? 1F
                : Math.clamp((now - animationAt) / 190_000_000F, 0F, 1F);
        animationAt = now;
        var effects = new ArrayList<VisibleEffect>(rows.size());
        float total = 0F;
        float panelOpacity = clientEditing ? 1F : 0F;
        var iterator = rows.values().iterator();
        while (iterator.hasNext()) {
            var row = iterator.next();
            row.visibility = Math.clamp(row.visibility + (row.present ? delta : -delta), 0F, 1F);
            if (row.visibility == 0F && !row.present) {
                iterator.remove();
                continue;
            }
            float opacity = row.visibility * row.visibility * (3 - 2 * row.visibility);
            effects.add(new VisibleEffect(row.effect, HEADER + total + (1 - opacity) * 4, opacity));
            total += ROW * opacity;
            panelOpacity = Math.max(panelOpacity, opacity);
        }
        if (effects.isEmpty() && !clientEditing) {
            release();
            return;
        }
        int height = HEADER + Math.max(ROW, Math.round(total)) + 4;
        String empty = OrionGuiLanguage.translate("Нет активных эффектов");
        if (!effects.equals(shown) || !empty.equals(emptyText)) {
            shown = effects;
            emptyText = empty;
            scene = new OrionHudView.Scene(WIDTH, height);
            scene.panel(0, 0, WIDTH, height, 4);
            scene.icon("M10 3c2.7 3.5 5 6.1 5 9a5 5 0 1 1-10 0c0-2.9 2.3-5.5 5-9z", 8, 5, 10, "#8970e5", false);
            scene.label("Potions", 22, 2, 89, 15, 8, "var(--text)", "left", true);
            scene.animateParts(0, panelOpacity, 0);
            if (effects.isEmpty()) scene.label(empty, INSET, HEADER, WIDTH - 2 * INSET, ROW, 7, "var(--muted)", "left", false);
            for (var row : effects) {
                int first = scene.mark();
                scene.label(row.effect().name(), 22, row.y(), 65, ROW, 7.5F, "var(--muted)", "left", false);
                scene.label(row.effect().duration(), 88, row.y(), 26, ROW, 7.5F, "var(--text)", "right", true);
                scene.animateParts(first, row.visibility(), 0);
            }
        }
        float fit = OrionHudView.fit(config.getHudScale() * config.getHudElementScale(ID)
                        * OrionHudView.layoutScale(graphics.guiWidth(), graphics.guiHeight()),
                WIDTH, height, graphics.guiWidth(), graphics.guiHeight());
        int w = Math.max(1, Math.round(WIDTH * fit)), h = Math.max(1, Math.round(height * fit));
        var pos = drag.placeWidget(graphics, w, h, graphics.guiWidth() - w - 8, 68, manager);
        if (pos == null) return;
        view.draw(graphics, scene, pos.x(), pos.y(), w, h);
        graphics.nextStratum();
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(pos.x(), pos.y());
            pose.scale(w / (float) WIDTH, h / (float) height);
            for (var row : effects) {
                if (row.visibility() <= 0F) continue;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.effect().sprite(), INSET, Math.round(row.y()) + 3, 8, 8,
                        OrionHudDrag.opacity() * row.visibility());
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static Effect describe(MobEffectInstance effect, float tickRate) {
        var type = effect.getEffect().value();
        var sprite = BuiltInRegistries.MOB_EFFECT.getKey(type).withPrefix("mob_effect/");
        int level = effect.getAmplifier() + 1;
        String name = type.getDisplayName().getString() + " " + (level > 0 && level <= LEVELS.length ? LEVELS[level - 1] : level);
        int seconds = (int) Math.ceil(Math.max(0, effect.getDuration()) / (double) tickRate);
        int minutes = seconds / 60;
        String duration = effect.isInfiniteDuration() ? "∞" : (minutes > 0 ? minutes + "m" : "") + seconds % 60 + "s";
        return new Effect(sprite, name, duration);
    }

    private void release() {
        drag.cancel();
        view.dispose();
        shown = null;
    }
}
