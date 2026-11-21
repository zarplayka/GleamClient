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
import java.util.List;

public final class OrionPotionsHud {
    public static final String ID = "potions_hud";
    private static final int WIDTH = 123, HEADER = 22, ROW = 15, INSET = 9;
    private static final String[] LEVELS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView(ID);
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private List<Effect> shown;
    private String emptyText;
    private OrionHudView.Scene scene;
    private LocalPlayer sampledPlayer;
    private int sampledTick;
    private List<Effect> sampled = List.of();

    private record Effect(Identifier sprite, String name, String duration) {}

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
            return;
        }
        if (sampledPlayer != client.player || sampledTick != client.player.tickCount) {
            sampledPlayer = client.player;
            sampledTick = client.player.tickCount;
            float tickRate = client.level.tickRateManager().tickrate();
            sampled = client.player.getActiveEffects().stream().filter(MobEffectInstance::showIcon)
                    .sorted(Comparator.comparing(effect -> effect.getEffect().value().getDescriptionId()))
                    .map(effect -> describe(effect, tickRate)).toList();
        }
        var effects = sampled;
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
            scene.panel(0, 0, WIDTH, height, 4);
            scene.icon("M10 3c2.7 3.5 5 6.1 5 9a5 5 0 1 1-10 0c0-2.9 2.3-5.5 5-9z", 8, 5, 10, "#8970e5", false);
            scene.label("Potions", 22, 2, 89, 15, 8, "var(--text)", "left", true);
            if (effects.isEmpty()) scene.label(empty, INSET, HEADER, WIDTH - 2 * INSET, ROW, 7, "var(--muted)", "left", false);
            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                int y = HEADER + i * ROW;
                scene.label(effect.name, 22, y, 65, ROW, 7.5F, "var(--muted)", "left", false);
                scene.label(effect.duration, 88, y, 26, ROW, 7.5F, "var(--text)", "right", true);
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
            for (int i = 0; i < effects.size(); i++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, effects.get(i).sprite, INSET, HEADER + i * ROW + 3, 8, 8,
                        OrionHudDrag.opacity());
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
    }
}
