package ru.orion.client.basketball;

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


/** Табло можно перемещать в чате. Кольцо заряда остаётся вокруг прицела. */
public final class OrionBasketballHud {
    private static final Identifier SCORE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "basketball_hud");
    private static final Identifier CHARGE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "basketball_charge");
    private static final int CHARGE_SIZE = 34;
    private final OrionBasketball game;
    private final OrionConfigManager manager;
    private final OrionHudDrag drag = new OrionHudDrag(SCORE.getPath());
    private final OrionHudView scoreView = new OrionHudView(SCORE.getPath());
    private final OrionHudView chargeView = new OrionHudView(CHARGE.getPath());
    private OrionHudView.Scene scoreScene;
    private Score lastScore;
    private int scoreWidth, scoreHeight;

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

        var match = game.match();
        long scoreAt = game.scoreAt();
        boolean scored = scoreAt != 0 && System.nanoTime() - scoreAt < 1_500_000_000L;
        String title = match.active()
                ? OrionGuiLanguage.translate("Красные") + " " + match.red() + " : " + match.blue() + " " + OrionGuiLanguage.translate("Синие")
                : scored ? "SCORE!  " + game.score() : OrionGuiLanguage.translate("Счёт") + ": " + game.score();
        String detail = match.active() ? OrionGuiLanguage.translate(match.status()) : "";
        Score score = new Score(title, detail, scored);
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
        scoreView.draw(context, scoreScene, x, y, width, height);

        int alpha = Math.round(game.indicatorAlpha() * 255);
        if (alpha > 0) {
            renderCharge(context, Math.round(game.indicatorCharge() * 100), alpha);
        } else {
            chargeView.dispose();
        }
    }

    private void updateScore(Score score) {
        var font = Minecraft.getInstance().font;
        scoreWidth = Math.max(font.width(score.title()), font.width(score.detail())) + 12;
        scoreHeight = score.detail().isEmpty() ? 15 : 28;
        scoreScene = new OrionHudView.Scene(scoreWidth, scoreHeight);
        scoreScene.panel(0, 0, scoreWidth, scoreHeight, 6);
        String tint = score.highlight() ? "#ffa63b" : "var(--text)";
        scoreScene.label(score.title(), 4, 0, scoreWidth - 8, 15, 8, tint, "center", true);
        if (!score.detail().isEmpty()) {
            scoreScene.label(score.detail(), 4, 13, scoreWidth - 8, 13, 8, "var(--muted)", "center", false);
        }
    }

    private void renderCharge(GuiGraphicsExtractor context, int percent, int alpha) {
        var scene = OrionHudView.charge(CHARGE_SIZE, percent, alpha);
        int x = (context.guiWidth() - CHARGE_SIZE) / 2, y = (context.guiHeight() - CHARGE_SIZE) / 2;
        chargeView.draw(context, scene, x, y, CHARGE_SIZE, CHARGE_SIZE);
    }

    private void release() {
        drag.cancel(manager);
        scoreView.dispose();
        chargeView.dispose();
    }

    private record Score(String title, String detail, boolean highlight) {}
}
