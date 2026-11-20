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

import java.util.List;

public final class OrionStatusHud {
    private static final int KEY_SIZE = 20, GAP = 3, STEP = KEY_SIZE + GAP;
    private static final int SIZE = KEY_SIZE + STEP * 2, MOUSE_WIDTH = 16;
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
            drag.cancel();
            view.dispose();
            return;
        }
        var options = client.options;
        KeyMapping[] keys = {options.keyUp, options.keyLeft, options.keyDown, options.keyRight,
                options.keyJump, options.keyAttack, options.keyUse};
        int pressed = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].isDown()) pressed |= 1 << i;
        var labels = List.of(keyLabel(keys[0]), keyLabel(keys[1]), keyLabel(keys[2]), keyLabel(keys[3]),
                keys[4].isDefault() ? "" : keyLabel(keys[4]));
        State state = new State(pressed, OrionClickCounter.count(false), OrionClickCounter.count(true), labels);
        if (!state.equals(previous)) {
            scene = new OrionHudView.Scene(SIZE, SIZE);
            key(state, 0, labels.getFirst(), "", STEP, 0, KEY_SIZE);
            for (int i = 1; i <= 3; i++) key(state, i, labels.get(i), "", (i - 1) * STEP, STEP, KEY_SIZE);
            key(state, 4, labels.get(4), "", MOUSE_WIDTH + GAP, STEP * 2, SIZE - 2 * (MOUSE_WIDTH + GAP));
            key(state, 5, Integer.toString(state.leftCps), "L", 0, STEP * 2, MOUSE_WIDTH);
            key(state, 6, Integer.toString(state.rightCps), "R", SIZE - MOUSE_WIDTH, STEP * 2, MOUSE_WIDTH);
            previous = state;
        }
        float scale = config.getHudScale() * config.getHudElementScale("keystrokes");
        float fit = OrionHudView.fit(scale, SIZE, SIZE, context.guiWidth(), context.guiHeight());
        int size = Math.max(1, Math.round(SIZE * fit));
        int x = Math.clamp(config.getKeystrokesX() < 0 ? 8 : config.getKeystrokesX(), 0, context.guiWidth() - size);
        int y = Math.clamp(config.getKeystrokesY() < 0 ? context.guiHeight() - size - 45 : config.getKeystrokesY(), 0, context.guiHeight() - size);
        var position = drag.update(x, y, size, size, (nx, ny) -> {
            config.setKeystrokesX(nx);
            config.setKeystrokesY(ny);
        });
        view.draw(context, scene, position.x(), position.y(), size, size);
    }

    private static String keyLabel(KeyMapping key) {
        return ru.orion.client.keybind.OrionKeybinds.keyLabel(key);
    }

    private void key(State state, int index, String label, String mouse, int x, int y, int width) {
        scene.key(x, y, width, KEY_SIZE, label, mouse, (state.pressed & 1 << index) != 0);
    }

    private record State(int pressed, int leftCps, int rightCps, List<String> labels) {}
}
