package ru.orion.client.effect;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import ru.orion.client.OrionClient;
import java.util.ArrayDeque;

/** Число реальных нажатий кнопок мыши за последнюю секунду. */
public final class OrionClickCounter {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private OrionClickCounter() { }
    public static void onButton(MouseButtonInfo button, int action) {
        Minecraft client = Minecraft.getInstance();
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isKeystrokesEnabled() || action != InputConstants.PRESS || client == null || client.player == null || client.gui.screen() != null) return;
        count(false); count(true);
        if (button.button() == InputConstants.MOUSE_BUTTON_LEFT) LEFT.addLast(System.nanoTime());
        else if (button.button() == InputConstants.MOUSE_BUTTON_RIGHT) RIGHT.addLast(System.nanoTime());
    }
    public static int count(boolean right) {
        ArrayDeque<Long> queue = right ? RIGHT : LEFT;
        long now = System.nanoTime();
        while (!queue.isEmpty() && now - queue.peekFirst() >= 1_000_000_000L) queue.removeFirst();
        return queue.size();
    }
}
