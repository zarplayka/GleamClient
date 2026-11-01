package ru.orion.client.effect;

/** Данные для отложенной отрисовки отдельного предмета. */
public interface OrionItemPhysicsState {
    void orion$setPhysicsRotation(float pitch, float yaw);
    float orion$getPhysicsPitch();
    float orion$getPhysicsYaw();

    final class Rotation {
        public float pitch;
        public final float yaw;
        public long lastNanos;

        public Rotation(float pitch, float yaw, long lastNanos) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.lastNanos = lastNanos;
        }
    }
}
