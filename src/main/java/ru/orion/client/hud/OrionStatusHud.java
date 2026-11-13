package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionClickCounter;

public final class OrionStatusHud {
    private static final int WIDTH = 66, HEIGHT = 72;
    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView("hud_status_panels");
    private final OrionHudDrag drag = new OrionHudDrag("keystrokes");
    private OrionHudView.Scene scene;
    private State previous;

    public OrionStatusHud(OrionConfigManager manager) { this.manager = manager; }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "status_panels"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (client.player == null || !config.isKeystrokesEnabled()) {
            drag.cancel(manager);
            view.dispose();
            return;
        }
        var options = client.options;
        KeyMapping[] keys = {options.keyUp, options.keyLeft, options.keyDown, options.keyRight,
                options.keyJump, options.keyAttack, options.keyUse};
        int pressed = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].isDown()) pressed |= 1 << i;
        State state = new State(pressed, OrionClickCounter.count(false), OrionClickCounter.count(true));
        if (!state.equals(previous)) {
            scene = new OrionHudView.Scene(WIDTH, HEIGHT);
            key(state, 0, "W", 23, 0, 20, 20, 7.5F);
            key(state, 1, "A", 0, 23, 20, 20, 7.5F);
            key(state, 2, "S", 23, 23, 20, 20, 7.5F);
            key(state, 3, "D", 46, 23, 20, 20, 7.5F);
            key(state, 4, "SPACE", 0, 46, 66, 10, 7.5F);
            key(state, 5, "L " + state.leftCps + " CPS", 0, 59, 31.5F, 13, 6.2F);
            key(state, 6, "R " + state.rightCps + " CPS", 34.5F, 59, 31.5F, 13, 6.2F);
            previous = state;
        }
        float scale = config.getHudScale() * config.getHudElementScale("keystrokes");
        float fit = OrionHudView.fit(scale, WIDTH, HEIGHT, context.guiWidth(), context.guiHeight());
        int w = Math.max(1, Math.round(WIDTH * fit));
        int h = Math.max(1, Math.round(HEIGHT * fit));
        int x = Math.clamp(config.getKeystrokesX() < 0 ? 8 : config.getKeystrokesX(), 0, context.guiWidth() - w);
        int y = Math.clamp(config.getKeystrokesY() < 0 ? context.guiHeight() - h - 45 : config.getKeystrokesY(), 0, context.guiHeight() - h);
        var position = drag.update(context, x, y, w, h, manager, (nx, ny) -> {
            config.setKeystrokesX(nx);
            config.setKeystrokesY(ny);
        });
        view.draw(context, scene, position.x(), position.y(), w, h);
    }

    private void key(State state, int index, String label, float x, float y, float w, float h, float fontSize) {
        scene.panel(x, y, w, h, 3.5F);
        if ((state.pressed & 1 << index) != 0) {
            scene.activeKey(x, y, w, h, 3.5F);
        }
        scene.label(label, x + 1, y, w - 2, h, fontSize, "var(--text)", "center", true);
    }

    private record State(int pressed, int leftCps, int rightCps) {}
}
