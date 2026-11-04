package ru.orion.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.keybind.OrionKeybinds;

public final class OrionZoom {
    public static final String[] CURVES = {
            "Мгновенно", "Линейно", "Sine In", "Sine Out", "Sine In/Out",
            "Quad In", "Quad Out", "Quad In/Out", "Cubic In", "Cubic Out", "Cubic In/Out",
            "Expo In", "Expo Out", "Expo In/Out"
    };

    public static final String[] SPYGLASS_MODES = {"Не требуется", "В руке", "В инвентаре"};

    private static boolean keyDown;
    private static boolean toggled;
    private static boolean active;
    private static boolean secondary;
    private static int scrollTier;
    private static double scrollRemainder;
    private static float current = 1F;
    private static float from = 1F;
    private static float target = 1F;
    private static long started;
    private static float duration;
    private static int curve;

    private OrionZoom() {}

    public static void keyEvent(boolean pressed) {
        if (!pressed) keyDown = false;
    }

    public static float current() { return current; }
    public static boolean active() { return active || secondary; }
    public static boolean secondary() { return secondary; }

    public static float factor(Minecraft client, OrionConfig config) {
        if (config == null || !config.isZoomEnabled() || client.player == null) {
            keyDown = toggled = active = secondary = false;
            scrollTier = 0;
            scrollRemainder = 0D;
            current = from = target = 1F;
            return current;
        }

        boolean wasActive = active;
        boolean wasSecondary = secondary;
        updateActivation(client, config);
        secondary = canZoom(client, config) && OrionKeybinds.isSecondaryZoomDown();
        float next = secondary ? config.getSecondaryZoomFactor() : active ? (float) Math.clamp(config.getZoomFactor()
                * Math.pow(config.getZoomScrollStep(), config.isZoomScroll() ? scrollTier : 0), 1D, 500D) : 1F;
        long now = System.nanoTime();
        if (next != target) {
            from = current;
            target = next;
            started = now;
            if (secondary || wasSecondary) {
                duration = secondary ? config.getSecondaryZoomInTime() : config.getSecondaryZoomOutTime();
                curve = secondary ? config.getZoomInCurve() : config.getZoomOutCurve();
            } else if (wasActive && active) {
                duration = config.getZoomScrollSmoothing() * .35F;
                curve = 12;
            } else {
                duration = active ? config.getZoomInTime() : config.getZoomOutTime();
                curve = active ? config.getZoomInCurve() : config.getZoomOutCurve();
            }
        }
        double progress = duration == 0 ? 1D : Math.clamp((now - started) / (duration * 1e9D), 0D, 1D);
        double blend = ease(progress, curve);
        current = (float) (1D / ((1D - blend) / from + blend / target));
        return current;
    }

    public static boolean scroll(Minecraft client, OrionConfig config, double vertical) {
        if (config == null || !config.isZoomEnabled() || !config.isZoomScroll()
                || !Double.isFinite(vertical) || vertical == 0D) return false;
        if (!canZoom(client, config) || OrionKeybinds.isSecondaryZoomDown()
                || !(config.isZoomToggle() ? toggled : OrionKeybinds.isZoomDown())) return false;
        double movement = client.options.discreteMouseScroll().get() ? Math.signum(vertical) : vertical;
        movement *= client.options.mouseWheelSensitivity().get();
        if (Math.signum(movement) != Math.signum(scrollRemainder)) scrollRemainder = 0D;
        scrollRemainder += Math.clamp(movement, -20D, 20D);
        int steps = (int) scrollRemainder;
        if (steps == 0) return true;
        scrollRemainder -= steps;
        int maxTier = Math.round(config.getZoomScrollTiers());
        int minTier = -Math.min(maxTier, (int) Math.ceil(Math.log(config.getZoomFactor()) / Math.log(config.getZoomScrollStep())));
        scrollTier = Math.clamp(scrollTier + steps, minTier, maxTier);
        return true;
    }

    private static boolean canZoom(Minecraft client, OrionConfig config) {
        if (client.player == null || client.gui.screen() != null || client.gui.overlay() != null) return false;
        if (config.getZoomSpyglassMode() == 1) return client.player.isHolding(Items.SPYGLASS);
        return config.getZoomSpyglassMode() != 2 || client.player.isHolding(Items.SPYGLASS)
                || client.player.getInventory().contains(stack -> stack.is(Items.SPYGLASS));
    }

    private static void updateActivation(Minecraft client, OrionConfig config) {
        boolean down = OrionKeybinds.isZoomDown();
        boolean allowed = canZoom(client, config);
        if (!allowed || !config.isZoomToggle()) toggled = false;
        else if (down && !keyDown) toggled = !toggled;
        keyDown = down;
        active = allowed && (config.isZoomToggle() ? toggled : down);
        if (!active) {
            scrollRemainder = 0D;
            if (!config.isZoomRetainScroll()) scrollTier = 0;
        }
    }

    static double ease(double t, int curve) {
        if (curve == 0 || t >= 1D) return 1D;
        if (curve == 1 || t <= 0D) return t;
        int family = (curve - 2) / 3;
        return switch ((curve - 2) % 3) {
            case 0 -> easeIn(t, family);
            case 1 -> 1D - easeIn(1D - t, family);
            default -> t < .5D ? easeIn(2D * t, family) / 2D : 1D - easeIn(2D - 2D * t, family) / 2D;
        };
    }

    private static double easeIn(double t, int family) {
        return switch (family) {
            case 0 -> 1D - Math.cos(t * Math.PI / 2D);
            case 1 -> t * t;
            case 2 -> t * t * t;
            default -> Math.pow(2D, 10D * (t - 1D));
        };
    }
}
