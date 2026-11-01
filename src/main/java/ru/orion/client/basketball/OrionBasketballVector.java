package ru.orion.client.basketball;

/** Чистая математика физической симуляции в блоках и секундах. */
public record OrionBasketballVector(double x, double y, double z) {
    public static final OrionBasketballVector ZERO = new OrionBasketballVector(0, 0, 0);
    public OrionBasketballVector add(OrionBasketballVector other) { return new OrionBasketballVector(x + other.x, y + other.y, z + other.z); }
    public OrionBasketballVector subtract(OrionBasketballVector other) { return new OrionBasketballVector(x - other.x, y - other.y, z - other.z); }
    public OrionBasketballVector scale(double factor) { return new OrionBasketballVector(x * factor, y * factor, z * factor); }
    public double dot(OrionBasketballVector other) { return x * other.x + y * other.y + z * other.z; }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
    public OrionBasketballVector normalize() { double n = length(); return n < 1.0e-12 ? ZERO : scale(1.0 / n); }
}
