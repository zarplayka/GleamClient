package ru.orion.client.hud;


import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.effect.OrionRainbow;
import ru.orion.client.gui.OrionGuiLanguage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BetterNear: панель в стиле клиента со списком игроков в радиусе — дистанция в блоках,
 * ник и стрелка направления относительно взгляда. Появляется только когда рядом кто-то есть.
 */
public final class OrionBetterNearHud {
    private static final int MAX_ROWS = 5;
    private static final int ARROW_SECTORS = 24; // квантование угла стрелки, чтобы не перерисовывать текстуру каждый кадр

    // Design units (1 unit = 1 gui px).
    private static final int PANEL_WIDTH = 127;
    private static final int HEADER_HEIGHT = 25;
    private static final int ROW_HEIGHT = 16;
    private static final int PAD_X = 7;
    private static final int BOTTOM_PAD = 4;
    private static final int SCREEN_MARGIN = 8;


    private final OrionConfigManager configManager;
    private final OrionHudView view = new OrionHudView("hud_better_near");
    private OrionHudView.Scene scene;
    private List<NearPlayer> previousPlayers;
    private int previousAccent;
    private int previousRainbowStep = Integer.MIN_VALUE;

    private int guiWidth;
    private int guiHeight;
    // Перетаскивание при открытом чате.
    private final OrionHudDrag drag = new OrionHudDrag("nearby");

    private record NearPlayer(String name, int distance, int sector) {
    }

    public OrionBetterNearHud(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        Identifier hudId = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "better_near");
        HudElementRegistry.addLast(hudId, this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        OrionConfig config = configManager.getConfig();
        if (!config.isBetterNearEnabled()) {
            release();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            release();
            return;
        }

        boolean editing = OrionHudEditorScreen.editing(client);
        List<NearPlayer> nearPlayers = collectNearPlayers(client, player, config.getBetterNearRadius());
        // В редакторе пустая панель остаётся доступной для перемещения.
        if (nearPlayers.isEmpty() && !editing) {
            release();
            return;
        }

        int accent = ru.orion.client.effect.OrionWorldChanger.parseOpaqueColor(config.getClickGuiAccent());
        int rainbowStep = OrionRainbow.fingerprintStep();
        if (!nearPlayers.equals(previousPlayers) || previousAccent != accent || previousRainbowStep != rainbowStep) {
            scene = buildScene(nearPlayers, OrionRainbow.orElse(accent));
            previousPlayers = nearPlayers;
            previousAccent = accent;
            previousRainbowStep = rainbowStep;
        }
        int height = HEADER_HEIGHT + Math.max(1, nearPlayers.size()) * ROW_HEIGHT + BOTTOM_PAD;
        float scale = config.getHudElementScale("nearby");
        float fit = OrionHudView.fit(scale, PANEL_WIDTH, height, context.guiWidth(), context.guiHeight());
        guiWidth = Math.max(1, Math.round(PANEL_WIDTH * fit));
        guiHeight = Math.max(1, Math.round(height * fit));

        int x = effectiveX(context, config);
        int y = effectiveY(context, config);
        var position=drag.update(x,y,guiWidth,guiHeight,(nx,ny)->{config.setBetterNearX(nx);config.setBetterNearY(ny);});
        view.draw(context, scene, position.x(), position.y(), guiWidth, guiHeight);
    }

    private int effectiveX(GuiGraphicsExtractor context, OrionConfig config) {
        int x = config.getBetterNearX() < 0
                ? context.guiWidth() - guiWidth - SCREEN_MARGIN
                : config.getBetterNearX();
        return Mth.clamp(x, 0, Math.max(0, context.guiWidth() - guiWidth));
    }

    private int effectiveY(GuiGraphicsExtractor context, OrionConfig config) {
        int y = config.getBetterNearY() < 0 ? SCREEN_MARGIN : config.getBetterNearY();
        return Mth.clamp(y, 0, Math.max(0, context.guiHeight() - guiHeight));
    }

    private List<NearPlayer> collectNearPlayers(Minecraft client, LocalPlayer player, int radius) {
        List<AbstractClientPlayer> candidates = new ArrayList<>();
        for (AbstractClientPlayer other : client.level.players()) {
            if (other == player) {
                continue;
            }
            if (player.distanceToSqr(other) <= (double) radius * radius) {
                candidates.add(other);
            }
        }
        candidates.sort(Comparator.comparingDouble(player::distanceToSqr));

        List<NearPlayer> result = new ArrayList<>(Math.min(candidates.size(), MAX_ROWS));
        for (int i = 0; i < candidates.size() && i < MAX_ROWS; i++) {
            AbstractClientPlayer other = candidates.get(i);
            int distance = Math.max(1, (int) Math.round(Math.sqrt(player.distanceToSqr(other))));

            // Куда указывает стрелка: азимут на цель минус направление взгляда (0 = прямо перед собой).
            double bearing = Math.toDegrees(Math.atan2(other.getZ() - player.getZ(), other.getX() - player.getX())) - 90.0;
            float relative = (float) Mth.wrapDegrees(bearing - player.getYRot());
            int sector = Math.floorMod(Math.round(relative / (360.0F / ARROW_SECTORS)), ARROW_SECTORS);

            result.add(new NearPlayer(OrionNameProtect.replace(other.getGameProfile().name()), distance, sector));
        }
        return result;
    }

    private OrionHudView.Scene buildScene(List<NearPlayer> players, int accent) {
        int height = HEADER_HEIGHT + Math.max(1, players.size()) * ROW_HEIGHT + BOTTOM_PAD;
        var result = new OrionHudView.Scene(PANEL_WIDTH, height);
        result.panel(0, 0, PANEL_WIDTH, height, 5);
        String tint = OrionRainbow.isActive() ? OrionHudView.color(accent) : "var(--accent)";
        result.icon("M12 6a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM4 16v-1a5.5 5.5 0 0 1 11 0v1m0-10a2 2 0 0 1 0 4m1 2a3 3 0 0 1 2 3",
                8, 6, 11, tint, false);
        result.label(OrionGuiLanguage.translate("Игроки рядом"), 23, 3, 81, 17, 8, "var(--text)", "left", true);
        result.divider(8, 22, 111);
        if (!players.isEmpty()) {
            result.label(Integer.toString(players.size()), 105, 4, 14, 15, 7, tint, "right", true);
        }
        if (players.isEmpty()) {
            result.label(OrionGuiLanguage.translate("Никого рядом"), PAD_X, HEADER_HEIGHT, PANEL_WIDTH - PAD_X * 2, ROW_HEIGHT,
                    7, "var(--muted)", "left", false);
        }
        for (int i = 0; i < players.size(); i++) {
            NearPlayer player = players.get(i);
            int row = HEADER_HEIGHT + i * ROW_HEIGHT;
            if (i > 0) result.divider(PAD_X, row - 1, PANEL_WIDTH - PAD_X * 2);
            result.icon(OrionHudView.arrow(player.sector * (360F / ARROW_SECTORS)),
                    PAD_X + 4, row + 3, 9, tint, true);
            result.label(player.name, PAD_X + 17, row, PANEL_WIDTH - PAD_X * 2 - 48, ROW_HEIGHT - 2,
                    8, "var(--text)", "left", false);
            result.label(Integer.toString(player.distance), PANEL_WIDTH - PAD_X - 40, row, 28, ROW_HEIGHT - 2,
                    8, "var(--text)", "right", true);
            result.label("m", PANEL_WIDTH - PAD_X - 10, row, 7, ROW_HEIGHT - 2, 6.5F, "var(--muted)", "left", false);
        }
        return result;
    }

    private void release() {
        drag.cancel();
        view.dispose();
    }
}
