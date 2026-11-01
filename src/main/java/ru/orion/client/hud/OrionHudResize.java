package ru.orion.client.hud;

/** Масштабирование за угол: фиксированный левый верхний угол и исходные пропорции. */
public final class OrionHudResize {
    private OrionHudResize() { }
    public static float scale(float initial, double width, double height, double dx, double dy,
                              double availableWidth, double availableHeight) {
        if (width <= 0 || height <= 0 || !Float.isFinite(initial) || initial <= 0) return 1F;
        double ratio = 1 + (dx * width + dy * height) / (width * width + height * height);
        double maximum = Math.min(4, initial * Math.min(availableWidth / width, availableHeight / height));
        double minimum = Math.min(.35, maximum);
        return (float)Math.max(minimum, Math.min(maximum, initial * ratio));
    }
}
