package ru.orion.client.football;

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

public final class OrionFootballHud {
    private static final Identifier SCORE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "football_hud");
    private static final Identifier CHARGE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "football_charge");
    private static final int CHARGE_SIZE = 34;
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 8);
    private final OrionFootball football;
    private final OrionConfigManager manager;
    private final OrionHudDrag drag = new OrionHudDrag(SCORE.getPath());
    private DynamicTexture scoreTexture, chargeTexture;
    private Score lastScore;
    private int scoreWidth, scoreHeight, lastCharge;

    public OrionFootballHud(OrionFootball football, OrionConfigManager manager) {
        this.football = football;
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"football_hud_element"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (!config.isFootballEnabled() || client.player == null) {
            release();
            return;
        }
        int scale = Math.clamp(client.getWindow().getGuiScale(), 1, 6);
        long goalAt = football.goalAt();
        boolean goal = goalAt != 0 && System.nanoTime() - goalAt < 1_500_000_000L;
        var match = football.match();
        int percentage = football.shots() == 0 ? 0 : Math.round(football.goals() * 100F / football.shots());
        String title = match.active() ? tr("Красные") + " " + match.red() + " : " + match.blue() + " " + tr("Синие")
                : goal ? "GOAL!  " + football.goals()
                : config.isFootballPenaltyMode() ? tr("Удары") + " " + football.shots() + "  " + tr("Голы") + " " + football.goals()
                + "  " + tr("Сейвы") + " " + football.saves() + "  " + tr("Мимо") + " " + football.misses() + "  " + percentage + "%"
                : tr("Голы") + ": " + football.goals();
        Score score = new Score(scale, title, match.active() ? tr(match.status()) : "", goal);
        if (!score.equals(lastScore)) {
            updateScore(score);
            lastScore = score;
        }
        float size = config.getHudElementScale(SCORE.getPath());
        int width = Math.min(context.guiWidth(), Math.max(1, Math.round(scoreWidth * size)));
        int height = Math.min(context.guiHeight(), Math.max(1, Math.round(scoreHeight * size)));
        int maxX = context.guiWidth() - width, maxY = context.guiHeight() - height;
        int x = config.getFootballScoreX() < 0 ? maxX / 2 : Math.clamp(config.getFootballScoreX(), 0, maxX);
        int y = Math.clamp(config.getFootballScoreY() < 0 ? context.guiHeight() / 2 - 53 : config.getFootballScoreY(), 0, maxY);
        var position = drag.update(context, x, y, width, height, manager, config::setFootballScorePosition);
        x = position.x();
        y = position.y();
        if (!OrionGlassCompositor.INSTANCE.submit(SCORE, context, scoreTexture, x, y, width, height)) {
            context.blit(SCORE, x, y, x + width, y + height, 0, 1, 0, 1);
        }
        if (football.charging()) renderCharge(context, scale);
    }

    private void updateScore(Score score) {
        Graphics2D metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        float titleWidth, detailWidth;
        try {
            configure(metrics, 1);
            titleWidth = (float) FONT.getStringBounds(score.title(), metrics.getFontRenderContext()).getWidth();
            detailWidth = (float) FONT.getStringBounds(score.detail(), metrics.getFontRenderContext()).getWidth();
        } finally {
            metrics.dispose();
        }
        scoreWidth = (int) Math.ceil(Math.max(titleWidth, detailWidth)) + 10;
        scoreHeight = score.detail().isEmpty() ? 15 : 26;
        int w = scoreWidth * score.scale(), h = scoreHeight * score.scale();
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            configure(g, score.scale());
            OrionGlassStyle.drawPanel(g, 0, 0, scoreWidth, scoreHeight, 8);
            g.setFont(FONT);
            g.setColor(score.goal() ? new Color(255, 166, 59) : Color.WHITE);
            g.drawString(score.title(), (scoreWidth - titleWidth) / 2, 11);
            if (!score.detail().isEmpty()) g.drawString(score.detail(), (scoreWidth - detailWidth) / 2, 21);
        } finally {
            g.dispose();
        }
        scoreTexture = upload(SCORE, scoreTexture, image);
        OrionGlassCompositor.INSTANCE.updateMask(SCORE, new OrionGlassCompositor.MaskLayout(w, h, scoreWidth, scoreHeight,
                List.of(new OrionGlassCompositor.Panel(0, 0, scoreWidth, scoreHeight, 8))));
    }

    private void renderCharge(GuiGraphicsExtractor context, int scale) {
        int power = Math.round(football.charge() * 100);
        int fingerprint = scale * 101 + power;
        if (chargeTexture == null || fingerprint != lastCharge) {
            var image = new BufferedImage(CHARGE_SIZE * scale, CHARGE_SIZE * scale, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                configure(g, scale);
                g.setStroke(new BasicStroke(2.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(10, 10, 12, 140));
                g.draw(new Arc2D.Float(2, 2, 30, 30, 0, 360, Arc2D.OPEN));
                g.setColor(new Color(255, Math.round(185 - power * .8F), 35, 240));
                g.draw(new Arc2D.Float(2, 2, 30, 30, 90, -power * 3.6F, Arc2D.OPEN));
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

    private static String tr(String value) { return OrionGuiLanguage.translate(value); }
    private record Score(int scale, String title, String detail, boolean goal) { }
}
