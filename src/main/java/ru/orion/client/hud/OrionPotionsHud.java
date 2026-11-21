package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionGuiLanguage;

import java.util.Comparator;
import java.util.List;

public final class OrionPotionsHud {
    public static final String ID = "potions_hud";
    private static final int WIDTH = 110, HEADER = 21, ROW = 14, INSET = 8, TIMER = 23;
    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView(ID);
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private List<Effect> shown;
    private String emptyText;
    private OrionHudView.Scene scene;

    private record Effect(Identifier sprite, String name, String duration, boolean expiring) {}

    public OrionPotionsHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), this::render);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker ticks) {
        Minecraft client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (client.player == null || client.level == null || !config.isPotionsEnabled()) {
            release();
            return;
        }
        float tickRate = client.level.tickRateManager().tickrate();
        var effects = client.player.getActiveEffects().stream().filter(MobEffectInstance::showIcon)
                .sorted(Comparator.comparing(effect -> effect.getEffect().value().getDescriptionId()))
                .map(effect -> describe(effect, tickRate)).toList();
        if (effects.isEmpty() && !OrionHudEditorScreen.editing(client)) {
            release();
            return;
        }
        int height = HEADER + Math.max(1, effects.size()) * ROW + 4;
        String empty = OrionGuiLanguage.translate("Нет активных эффектов");
        if (!effects.equals(shown) || !empty.equals(emptyText)) {
            shown = effects;
            emptyText = empty;
            scene = new OrionHudView.Scene(WIDTH, height);
            scene.panel(0, 0, WIDTH, height, 5);
            scene.icon("M10 3c2.7 3.5 5 6.1 5 9a5 5 0 1 1-10 0c0-2.9 2.3-5.5 5-9z", 8, 5, 11, "var(--accent)", false);
            scene.label(OrionGuiLanguage.translate("Эффекты"), 23, 2, 79, 17, 8, "var(--text)", "left", true);
            scene.divider(7, 19, WIDTH - 14);
            if (effects.isEmpty()) scene.label(empty, INSET, HEADER, WIDTH - 2 * INSET, ROW, 6F, "var(--muted)", "left", false);
            int timerX = WIDTH - INSET - TIMER;
            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                int y = HEADER + i * ROW;
                scene.label(effect.name, 19, y, timerX - 20, 13, 7, "var(--text)", "left", false);
                scene.timer(effect.duration, timerX, y + 1, TIMER, 11, effect.expiring);
            }
        }
        float fit = OrionHudView.fit(config.getHudScale() * config.getHudElementScale(ID),
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
            for (int i = 0; i < effects.size(); i++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, effects.get(i).sprite, INSET, HEADER + i * ROW + 3, 8, 8);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static Effect describe(MobEffectInstance effect, float tickRate) {
        var type = effect.getEffect().value();
        var sprite = BuiltInRegistries.MOB_EFFECT.getKey(type).withPrefix("mob_effect/");
        String name = type.getDisplayName().getString() + " " + (effect.getAmplifier() + 1);
        int seconds = (int) Math.ceil(Math.max(0, effect.getDuration()) / (double) tickRate);
        int minutes = seconds / 60;
        String duration = effect.isInfiniteDuration() ? "∞" : minutes + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        return new Effect(sprite, name, duration, !effect.isInfiniteDuration() && seconds <= 10);
    }

    private void release() {
        drag.cancel();
        view.dispose();
    }
}
