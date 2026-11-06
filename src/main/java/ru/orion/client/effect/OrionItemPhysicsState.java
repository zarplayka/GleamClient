package ru.orion.client.effect;

/** Данные для отложенной отрисовки отдельного предмета. */
public interface OrionItemPhysicsState {
    void orion$setPhysicsRotation(float pitch, float yaw, float roll);
    float orion$getPhysicsPitch();
    float orion$getPhysicsYaw();
    float orion$getPhysicsRoll();

    final class Rotation {
        public float pitch;
        public float yaw, roll, groundPitch, groundRoll;
        public final float spin;
        public double lastTick;
        public boolean grounded;

        public Rotation(float pitch, float yaw, float spin, double lastTick) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.spin = spin;
            this.lastTick = lastTick;
        }
    }
}
