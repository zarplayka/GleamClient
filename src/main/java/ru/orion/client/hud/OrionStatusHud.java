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
    private static final int WIDTH = 75, HEIGHT = 77;
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
            key(state, 0, keyLabel(keys[0]), "", 26, 0, 23, 21);
            for (int i = 1; i <= 3; i++) key(state, i, keyLabel(keys[i]), "", (i - 1) * 26, 24, 23, 21);
            key(state, 4, keyLabel(keys[4]), "", 0, 48, WIDTH, 10);
            key(state, 5, Integer.toString(state.leftCps), "L", 0, 61, 36, 16);
            key(state, 6, Integer.toString(state.rightCps), "R", 39, 61, 36, 16);
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

    private static String keyLabel(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private void key(State state, int index, String label, String mouse, float x, float y, float w, float h) {
        scene.key(x, y, w, h, label, mouse, (state.pressed & 1 << index) != 0);
    }

    private record State(int pressed, int leftCps, int rightCps) {}
}
