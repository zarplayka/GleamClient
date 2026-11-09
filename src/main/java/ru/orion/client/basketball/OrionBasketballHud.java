package ru.orion.client.basketball;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionGuiLanguage;
import ru.orion.client.hud.OrionGlassCompositor;
import ru.orion.client.hud.OrionGlassStyle;
import ru.orion.client.hud.OrionHudDrag;
import ru.orion.client.hud.OrionHudRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Табло можно перемещать в чате. Кольцо заряда остаётся вокруг прицела. */
public final class OrionBasketballHud {
    private static final Identifier SCORE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "basketball_hud");
    private static final Identifier CHARGE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "basketball_charge");
    private static final int CHARGE_SIZE = 34;
    private static final Font SCORE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 9);
    private final OrionBasketball game;
    private final OrionConfigManager manager;
    private final OrionHudDrag drag = new OrionHudDrag(SCORE.getPath());
    private DynamicTexture scoreTexture, chargeTexture;
    private Score lastScore;
    private int scoreWidth, scoreHeight, lastCharge;

    public OrionBasketballHud(OrionBasketball game, OrionConfigManager manager) {
        this.game = game;
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "basketball"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (!config.isBasketballEnabled() || !config.isBasketballHud() || client.player == null) {
            release();
            return;
        }

        int scale = Math.clamp(client.getWindow().getGuiScale(), 1, 6);
        var match = game.match();
        long scoreAt = game.scoreAt();
        boolean scored = scoreAt != 0 && System.nanoTime() - scoreAt < 1_500_000_000L;
        String title = match.active()
                ? OrionGuiLanguage.translate("Красные") + " " + match.red() + " : " + match.blue() + " " + OrionGuiLanguage.translate("Синие")
                : scored ? "SCORE!  " + game.score() : OrionGuiLanguage.translate("Счёт") + ": " + game.score();
        String detail = match.active() ? OrionGuiLanguage.translate(match.status()) : "";
        Score score = new Score(scale, title, detail, scored);
        if (!score.equals(lastScore)) {
            updateScore(score);
            lastScore = score;
        }

        float size = config.getHudElementScale(SCORE.getPath());
        int width = Math.min(context.guiWidth(), Math.max(1, Math.round(scoreWidth * size)));
        int height = Math.min(context.guiHeight(), Math.max(1, Math.round(scoreHeight * size)));
        int maxX = context.guiWidth() - width, maxY = context.guiHeight() - height;
        int x = config.getBasketballScoreX() < 0 ? maxX / 2 : Math.clamp(config.getBasketballScoreX(), 0, maxX);
        int y = Math.clamp(config.getBasketballScoreY() < 0 ? context.guiHeight() / 2 - 48 : config.getBasketballScoreY(), 0, maxY);
        var position = drag.update(context, x, y, width, height, manager, config::setBasketballScorePosition);
        x = position.x();
        y = position.y();
        if (!OrionGlassCompositor.INSTANCE.submit(SCORE, context, scoreTexture, x, y, width, height)) {
            context.blit(SCORE, x, y, x + width, y + height, 0, 1, 0, 1);
        }

        int alpha = Math.round(game.indicatorAlpha() * 255);
        if (alpha > 0) renderCharge(context, scale, alpha);
    }

    private void updateScore(Score score) {
        Graphics2D metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        float titleWidth, detailWidth;
        try {
            configure(metrics, 1);
            titleWidth = (float) SCORE_FONT.getStringBounds(score.title(), metrics.getFontRenderContext()).getWidth();
            detailWidth = (float) SCORE_FONT.getStringBounds(score.detail(), metrics.getFontRenderContext()).getWidth();
        } finally {
            metrics.dispose();
        }
        scoreWidth = (int) Math.ceil(Math.max(titleWidth, detailWidth)) + 10;
        scoreHeight = score.detail().isEmpty() ? 15 : 28;
        int w = scoreWidth * score.scale(), h = scoreHeight * score.scale();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            configure(g, score.scale());
            OrionGlassStyle.drawPanel(g, 0, 0, scoreWidth, scoreHeight, 8);
            g.setFont(SCORE_FONT);
            g.setColor(score.scored() ? new Color(255, 166, 59) : Color.WHITE);
            g.drawString(score.title(), (scoreWidth - titleWidth) / 2, 11);
            if (!score.detail().isEmpty()) g.drawString(score.detail(), (scoreWidth - detailWidth) / 2, 22);
        } finally {
            g.dispose();
        }
        scoreTexture = upload(SCORE, scoreTexture, image);
        OrionGlassCompositor.INSTANCE.updateMask(SCORE, new OrionGlassCompositor.MaskLayout(w, h, scoreWidth, scoreHeight,
                List.of(new OrionGlassCompositor.Panel(0, 0, scoreWidth, scoreHeight, 8))));
    }

    private void renderCharge(GuiGraphicsExtractor context, int scale, int alpha) {
        int phase = Math.round(game.indicatorCharge() * 100);
        int fingerprint = (scale * 101 + phase) * 256 + alpha;
        if (chargeTexture == null || fingerprint != lastCharge) {
            int size = CHARGE_SIZE * scale;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                configure(g, scale);
                g.setStroke(new BasicStroke(2.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(10, 10, 12, Math.round(alpha * .51F)));
                g.draw(new Arc2D.Float(2, 2, 30, 30, 0, 360, Arc2D.OPEN));
                g.setColor(new Color(255, Math.round(190 - phase * .95F), 35, Math.round(alpha * .94F)));
                g.draw(new Arc2D.Float(2, 2, 30, 30, 90, -phase * 3.6F, Arc2D.OPEN));
            } finally {
                g.dispose();
            }
            chargeTexture = upload(CHARGE, chargeTexture, image);
            lastCharge = fingerprint;
        }
        int x = (context.guiWidth() - CHARGE_SIZE) / 2, y = (context.guiHeight() - CHARGE_SIZE) / 2;
        context.blit(CHARGE, x, y, x + CHARGE_SIZE, y + CHARGE_SIZE, 0, 1, 0, 1);
    }

    private static void configure(Graphics2D g, int scale) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.scale(scale, scale);
    }

    private static DynamicTexture upload(Identifier id, DynamicTexture texture, BufferedImage image) {
        var textures = Minecraft.getInstance().getTextureManager();
        NativeImage pixels = texture == null ? null : texture.getPixels();
        if (pixels == null || pixels.isClosed() || pixels.getWidth() != image.getWidth() || pixels.getHeight() != image.getHeight()) {
            textures.release(id);
            pixels = new NativeImage(image.getWidth(), image.getHeight(), true);
            OrionHudRenderer.copyPixels(image, pixels);
            texture = new DynamicTexture(id::toString, pixels);
            textures.register(id, texture);
        } else {
            OrionHudRenderer.copyPixels(image, pixels);
            texture.upload();
        }
        return texture;
    }

    private void release() {
        drag.cancel(manager);
        if (scoreTexture == null) return;
        var textures = Minecraft.getInstance().getTextureManager();
        textures.release(SCORE);
        if (chargeTexture != null) textures.release(CHARGE);
        OrionGlassCompositor.INSTANCE.releaseMask(SCORE);
        scoreTexture = chargeTexture = null;
        lastScore = null;
    }

    private record Score(int scale, String title, String detail, boolean scored) { }
}
