package ru.orion.client.football;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionGuiLanguage;
import ru.orion.client.hud.OrionHudDrag;
import ru.orion.client.hud.OrionHudView;


public final class OrionFootballHud {
    private static final Identifier SCORE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "football_hud");
    private static final Identifier CHARGE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "football_charge");
    private static final int CHARGE_SIZE = 34;
    private final OrionFootball football;
    private final OrionConfigManager manager;
    private final OrionHudDrag drag = new OrionHudDrag(SCORE.getPath());
    private final OrionHudView scoreView = new OrionHudView(SCORE.getPath());
    private final OrionHudView chargeView = new OrionHudView(CHARGE.getPath());
    private OrionHudView.Scene scoreScene;
    private Score lastScore;
    private int scoreWidth, scoreHeight;

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
        long goalAt = football.goalAt();
        boolean goal = goalAt != 0 && System.nanoTime() - goalAt < 1_500_000_000L;
        var match = football.match();
        int percentage = football.shots() == 0 ? 0 : Math.round(football.goals() * 100F / football.shots());
        String title = match.active() ? tr("Красные") + " " + match.red() + " : " + match.blue() + " " + tr("Синие")
                : goal ? "GOAL!  " + football.goals()
                : config.isFootballPenaltyMode() ? tr("Удары") + " " + football.shots() + "  " + tr("Голы") + " " + football.goals()
                + "  " + tr("Сейвы") + " " + football.saves() + "  " + tr("Мимо") + " " + football.misses() + "  " + percentage + "%"
                : tr("Голы") + ": " + football.goals();
        Score score = new Score(title, match.active() ? tr(match.status()) : "", goal);
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
        var position = drag.update(x, y, width, height, config::setFootballScorePosition);
        x = position.x();
        y = position.y();
        scoreView.draw(context, scoreScene, x, y, width, height);
        if (football.charging()) {
            renderCharge(context, Math.round(football.charge() * 100), 255);
        } else {
            chargeView.dispose();
        }
    }

    private void updateScore(Score score) {
        var font = Minecraft.getInstance().font;
        scoreWidth = Math.max(67, Math.max(font.width(score.title()), font.width(score.detail())) + 26);
        scoreHeight = score.detail().isEmpty() ? 18 : 29;
        scoreScene = new OrionHudView.Scene(scoreWidth, scoreHeight);
        scoreScene.panel(0, 0, scoreWidth, scoreHeight, 4);
        scoreScene.icon("M3 16V5h14v11M3 11h14", 6, 4, 10, "var(--accent)", false);
        String tint = score.highlight() ? "#ffa63b" : "var(--text)";
        scoreScene.label(score.title(), 21, 0, scoreWidth - 26, 18, 8, tint, "left", true);
        if (!score.detail().isEmpty()) {
            scoreScene.label(score.detail(), 21, 15, scoreWidth - 26, 13, 8, "var(--muted)", "left", false);
        }
    }

    private void renderCharge(GuiGraphicsExtractor context, int percent, int alpha) {
        var scene = OrionHudView.charge(CHARGE_SIZE, percent, alpha);
        int x = (context.guiWidth() - CHARGE_SIZE) / 2, y = (context.guiHeight() - CHARGE_SIZE) / 2;
        chargeView.draw(context, scene, x, y, CHARGE_SIZE, CHARGE_SIZE);
    }

    private void release() {
        drag.cancel();
        scoreView.dispose();
        chargeView.dispose();
    }

    private static String tr(String value) { return OrionGuiLanguage.translate(value); }
    private record Score(String title, String detail, boolean highlight) {}
}
