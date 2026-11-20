package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionGuiLanguage;

import java.util.Comparator;
import java.util.List;

public final class OrionPotionsHud {
    public static final String ID = "potions_hud";
    private static final int WIDTH = 150, HEADER = 22, ROW = 18;
    private static final Identifier HEADER_ICON = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/gui/potions_hud.png");
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
        if (effects.isEmpty() && !(GleamMinecraft.screen(client) instanceof ChatScreen)) {
            release();
            return;
        }
        int height = HEADER + Math.max(1, effects.size()) * ROW + 4;
        String empty = OrionGuiLanguage.translate("Нет активных эффектов");
        if (!effects.equals(shown) || !empty.equals(emptyText)) {
            shown = effects;
            emptyText = empty;
            scene = new OrionHudView.Scene(WIDTH, height);
            scene.panel(0, 0, WIDTH, height, 7);
            scene.label("Potions", 22, 2, WIDTH - 30, 17, 8.5F, "var(--text)", "left", true);
            scene.divider(7, HEADER - 2, WIDTH - 14);
            if (effects.isEmpty()) scene.label(empty, 7, HEADER, WIDTH - 14, ROW, 8, "var(--muted)", "left", false);
            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                int y = HEADER + i * ROW;
                scene.label(effect.name, 25, y, WIDTH - 69, ROW, 8, "var(--text)", "left", false);
                scene.label(effect.duration, WIDTH - 42, y, 35, ROW, 8,
                        effect.expiring ? "#f28386" : "var(--accent)", "right", true);
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
            graphics.blit(RenderPipelines.GUI_TEXTURED, HEADER_ICON, 6, 3, 0, 0, 14, 14, 16, 16, 16, 16);
            for (int i = 0; i < effects.size(); i++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, effects.get(i).sprite, 7, HEADER + i * ROW + 2, 14, 14);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static Effect describe(MobEffectInstance effect, float tickRate) {
        var type = effect.getEffect().value();
        var sprite = BuiltInRegistries.MOB_EFFECT.getKey(type).withPrefix("mob_effect/");
        String name = type.getDisplayName().getString();
        if (effect.getAmplifier() > 0) {
            int level = effect.getAmplifier() + 1;
            name += " " + (level <= 10 ? Component.translatable("enchantment.level." + level).getString() : level);
        }
        int seconds = (int) Math.ceil(Math.max(0, effect.getDuration()) / (double) tickRate);
        int minutes = seconds / 60;
        String duration = effect.isInfiniteDuration() ? "∞" : minutes + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        return new Effect(sprite, name, duration, !effect.isInfiniteDuration() && seconds <= 10);
    }

    private void release() {
        drag.cancel(manager);
        view.dispose();
    }
}
