package ru.orion.client.basketball;

/** Положение щита и кольца. Направление задаётся углом вокруг вертикали. */
public record OrionBasketballHoop(OrionBasketballVector base, double yaw) {
    public static final double HORIZONTAL_SCALE = 1.75;
    public static final double VERTICAL_SCALE = 1.45;
    public static final double RIM_HEIGHT = 3.05 * VERTICAL_SCALE;
    public static final double RIM_RADIUS = 0.30 * HORIZONTAL_SCALE;
    public static final double RIM_THICKNESS = 0.035 * HORIZONTAL_SCALE;
    public static final double RIM_FORWARD = 0.48 * HORIZONTAL_SCALE;
    public static final double BOARD_WIDTH = 1.80 * HORIZONTAL_SCALE;
    public static final double BOARD_HEIGHT = 1.05 * VERTICAL_SCALE;
    public static final double BOARD_CENTER_Y = 3.38 * VERTICAL_SCALE;
    public OrionBasketballHoop {
        if (base == null || !Double.isFinite(base.x()) || !Double.isFinite(base.y()) || !Double.isFinite(base.z()) || !Double.isFinite(yaw))
            throw new IllegalArgumentException("Некорректное положение кольца");
    }
    public OrionBasketballVector toLocal(OrionBasketballVector point) {
        var p = point.subtract(base); double c = Math.cos(yaw), s = Math.sin(yaw);
        return new OrionBasketballVector(p.x() * c - p.z() * s, p.y(), p.x() * s + p.z() * c);
    }
    public OrionBasketballVector toWorldVector(OrionBasketballVector vector) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return new OrionBasketballVector(vector.x() * c + vector.z() * s, vector.y(), -vector.x() * s + vector.z() * c);
    }
    public OrionBasketballVector toWorld(OrionBasketballVector point) { return base.add(toWorldVector(point)); }
}
