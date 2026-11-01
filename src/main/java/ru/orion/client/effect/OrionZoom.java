package ru.orion.client.effect;

import net.minecraft.client.Minecraft;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.keybind.OrionKeybinds;

/** Плавное приближение камеры по удержанию назначенной клавиши. */
public final class OrionZoom {
    private static float current = 1F;
    private static float temporaryFactor = 1F;
    private static boolean holding;
    private static long previous;
    private OrionZoom() { }

    public static void release() { holding = false; }

    public static float factor(OrionConfig config) {
        Minecraft client = Minecraft.getInstance();
        boolean active = updateHolding(client, config);
        float target = active ? temporaryFactor : 1F;
        long now = System.nanoTime();
        float dt = previous == 0 ? 0F : Math.min(1F, Math.max(0F, (now - previous) / 1_000_000_000F));
        previous = now;
        current += (target - current) * (1F - (float) Math.exp(-dt * 7F));
        if (Math.abs(current - target) < 0.001F) current = target;
        return current;
    }

    /** Колесо меняет силу только на время текущего удержания. */
    public static boolean scroll(Minecraft client, OrionConfig config, double vertical) {
        if (config == null || !Double.isFinite(vertical) || vertical == 0D || !updateHolding(client, config)) return false;
        double movement = client.options.discreteMouseScroll().get() ? Math.signum(vertical) : vertical;
        movement *= client.options.mouseWheelSensitivity().get();
        movement = Math.max(-4D, Math.min(4D, movement));
        temporaryFactor = (float) Math.max(1.5D, Math.min(12D, temporaryFactor * Math.pow(1.2D, movement)));
        return true;
    }

    private static boolean updateHolding(Minecraft client, OrionConfig config) {
        boolean active = config.isZoomEnabled() && client != null && client.player != null
                && client.gui.screen() == null && client.gui.overlay() == null && OrionKeybinds.isZoomDown();
        if (active && !holding) temporaryFactor = config.getZoomFactor();
        holding = active;
        return active;
    }
}
