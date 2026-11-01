package ru.orion.client.basketball;

/** Геометрия препятствия в мировых координатах. */
public record OrionBasketballBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public OrionBasketballBox {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Некорректная граница");
    }
}
