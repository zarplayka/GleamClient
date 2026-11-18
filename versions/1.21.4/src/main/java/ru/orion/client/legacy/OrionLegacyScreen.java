package ru.orion.client.legacy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionLegacyTargetEsp;
import ru.orion.client.gui.OrionBrandMark;
import ru.orion.client.hud.OrionGlassStyle;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class OrionLegacyScreen extends Screen {
    private static final int PANEL_WIDTH = 266;
    private static final int PANEL_HEIGHT = 238;
    private static final int BUTTON_TOP = 32;
    private static final int SCALE_TOP = 60;
    private static final int TARGET_ESP_TOP = 86;
    private static final int TARGET_STYLE_TOP = 114;
    private static final int EFFECTS_TOP = 142;
    private static final int EFFECTS_GAP = 4;
    private static final int PAD = 10;
    private static final int EFFECT_COLUMNS = 3;
    private static final int EFFECT_WIDTH = (PANEL_WIDTH - 2 * PAD - (EFFECT_COLUMNS - 1) * EFFECTS_GAP) / EFFECT_COLUMNS;
    private static final int ROW_HEIGHT = 20;

    private static final int STEP_BUTTON_WIDTH = 22;
    private static final Color WHITE = new Color(0xF7F9FF);
    private static final Color ACCENT = new Color(0x7EA8FF);
    private static final Font FONT = new Font("Dialog", Font.PLAIN, 10);

    private final OrionConfig config;
    private final Runnable save;
    private final List<EffectToggle> effects;
    private final OrionLegacyTexture texture = new OrionLegacyTexture("legacy_menu");
    private List<Object> fingerprint = List.of();
    private boolean dirty;

    OrionLegacyScreen(OrionConfig config, Runnable save) {
        super(Component.literal("Orion Client"));
        this.config = config;
        this.save = save;
        effects = List.of(
                new EffectToggle("Круг прыжка", config::isJumpCircleEnabled, config::setJumpCircleEnabled),
                new EffectToggle("След игрока", config::isBreadcrumbsEnabled, config::setBreadcrumbsEnabled),
                new EffectToggle("Снаряды", config::isProjectileTrailEnabled, config::setProjectileTrailEnabled),
                new EffectToggle("Частицы", config::isWorldParticlesEnabled, config::setWorldParticlesEnabled),
                new EffectToggle("Контур блока", config::isBlockHighlightEnabled, config::setBlockHighlightEnabled),
                new EffectToggle("Зум (C)", config::isZoomEnabled, config::setZoomEnabled),
                new EffectToggle("Скрыть ник", config::isProtectNameEnabled, config::setProtectNameEnabled),
                new EffectToggle("Свой префикс", config::isFakePrefixEnabled, config::setFakePrefixEnabled),
                new EffectToggle("Фильтр чата", config::isChatFilterEnabled, config::setChatFilterEnabled),
                new EffectToggle("Подсветка чата", config::isChatHighlightEnabled, config::setChatHighlightEnabled),
                new EffectToggle("FullBright", config::isFullBrightEnabled, config::setFullBrightEnabled),
                new EffectToggle("Без погоды", config::isNoWeatherEnabled, config::setNoWeatherEnabled)
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x78000000);
        int scale = Math.max(1, Math.min(6, (int) minecraft.getWindow().getGuiScale()));
        var state = new ArrayList<Object>(List.of(scale, config.isHudEnabled(), config.getHudScale(),
                config.isTargetEspEnabled(), config.getTargetEspTexture()));
        for (EffectToggle effect : effects) {
            state.add(effect.enabled().getAsBoolean());
        }
        if (!fingerprint.equals(state)) {
            rebuildTexture(scale);
            fingerprint = state;
        }
        texture.draw(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Затемнение рисуется вместе с меню.
    }

    private void rebuildTexture(int scale) {
        BufferedImage image = new BufferedImage(PANEL_WIDTH * scale, PANEL_HEIGHT * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(scale, scale);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            OrionGlassStyle.drawPanel(graphics, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 12);
            OrionBrandMark.draw(graphics, 16, 17, 10, ACCENT);
            OrionGlassStyle.drawText(graphics, FONT.deriveFont(Font.BOLD, 11.0F), "Orion Client", 27, 21, WHITE);

            OrionGlassStyle.drawPanel(graphics, PAD, BUTTON_TOP, PANEL_WIDTH - 2 * PAD, ROW_HEIGHT, 8);
            OrionGlassStyle.drawText(graphics, FONT, config.isHudEnabled() ? "Водяной знак включён" : "Водяной знак выключен",
                    PAD + 8, BUTTON_TOP + 14, config.isHudEnabled() ? ACCENT : WHITE);

            OrionGlassStyle.drawText(graphics, FONT, "Масштаб  " + Math.round(config.getHudScale() * 100) + "%",
                    PAD, SCALE_TOP + 14, WHITE);
            drawStep(graphics, minusX(), "−");
            drawStep(graphics, plusX(), "+");
            drawOption(graphics, TARGET_ESP_TOP, config.isTargetEspEnabled() ? "TargetESP включён" : "TargetESP выключен",
                    config.isTargetEspEnabled() ? ACCENT : WHITE);
            drawOption(graphics, TARGET_STYLE_TOP, "Стиль: " + targetStyleName(), WHITE);
            for (int index = 0; index < effects.size(); index++) {
                EffectToggle effect = effects.get(index);
                int x = effectX(index);
                int top = effectY(index);
                OrionGlassStyle.drawPanel(graphics, x, top, EFFECT_WIDTH, ROW_HEIGHT, 8);
                OrionGlassStyle.drawText(graphics, FONT, effect.label(), x + 6, top + 14,
                        effect.enabled().getAsBoolean() ? ACCENT : WHITE);
            }
        } finally {
            graphics.dispose();
        }
        texture.update(image);
    }

    private static void drawOption(Graphics2D graphics, int top, String text, Color color) {
        OrionGlassStyle.drawPanel(graphics, PAD, top, PANEL_WIDTH - 2 * PAD, ROW_HEIGHT, 8);
        OrionGlassStyle.drawText(graphics, FONT, text, PAD + 8, top + 14, color);
    }

    private String targetStyleName() {
        return switch (OrionLegacyTargetEsp.textureName(config.getTargetEspTexture())) {
            case "rhomb" -> "Ромб";
            case "ghost" -> "Призрак";
            case "chain" -> "Цепь";
            case "crystals" -> "Кристаллы";
            case "marker" -> "Маркер";
            default -> throw new IllegalStateException("Unknown TargetESP style");
        };
    }

    private static void drawStep(Graphics2D graphics, int x, String label) {
        OrionGlassStyle.drawPanel(graphics, x, SCALE_TOP, STEP_BUTTON_WIDTH, ROW_HEIGHT, 8);
        OrionGlassStyle.drawText(graphics, FONT, label, x + 8, SCALE_TOP + 14, WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - panelX();
        double y = mouseY - panelY();
        if (button == 0 && y >= BUTTON_TOP && y < BUTTON_TOP + ROW_HEIGHT && x >= PAD && x < PANEL_WIDTH - PAD) {
            config.setHudEnabled(!config.isHudEnabled());
            dirty = true;
            return true;
        }
        if (button == 0 && y >= SCALE_TOP && y < SCALE_TOP + ROW_HEIGHT) {
            if (x >= minusX() && x < minusX() + STEP_BUTTON_WIDTH) {
                changeScale(-0.1F);
                return true;
            }
            if (x >= plusX() && x < plusX() + STEP_BUTTON_WIDTH) {
                changeScale(0.1F);
                return true;
            }
        }
        if (button == 0 && x >= PAD && x < PANEL_WIDTH - PAD) {
            if (y >= TARGET_ESP_TOP && y < TARGET_ESP_TOP + ROW_HEIGHT) {
                config.setTargetEspEnabled(!config.isTargetEspEnabled());
                dirty = true;
                return true;
            }
            if (y >= TARGET_STYLE_TOP && y < TARGET_STYLE_TOP + ROW_HEIGHT) {
                config.setTargetEspTexture(Math.floorMod(config.getTargetEspTexture() + 1, OrionLegacyTargetEsp.TEXTURES.length));
                dirty = true;
                return true;
            }
        }
        if (button == 0) {
            for (int index = 0; index < effects.size(); index++) {
                int left = effectX(index);
                int top = effectY(index);
                if (x >= left && x < left + EFFECT_WIDTH && y >= top && y < top + ROW_HEIGHT) {
                    EffectToggle effect = effects.get(index);
                    effect.setEnabled().accept(!effect.enabled().getAsBoolean());
                    dirty = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int effectX(int index) {
        return PAD + (index % EFFECT_COLUMNS) * (EFFECT_WIDTH + EFFECTS_GAP);
    }

    private static int effectY(int index) {
        return EFFECTS_TOP + (index / EFFECT_COLUMNS) * (ROW_HEIGHT + EFFECTS_GAP);
    }

    private record EffectToggle(String label, BooleanSupplier enabled, Consumer<Boolean> setEnabled) {
    }

    private void changeScale(float step) {
        config.setHudScale(Math.round((config.getHudScale() + step) * 100.0F) / 100.0F);
        config.sanitize();
        dirty = true;
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static int plusX() {
        return PANEL_WIDTH - PAD - STEP_BUTTON_WIDTH;
    }

    private static int minusX() {
        return plusX() - STEP_BUTTON_WIDTH - 4;
    }

    @Override
    public void removed() {
        texture.release();
        fingerprint = List.of();
        if (dirty) {
            save.run();
            dirty = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
