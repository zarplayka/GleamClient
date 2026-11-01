package ru.orion.client.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.orion.client.effect.OrionItemPhysicsState;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements OrionItemPhysicsState {
    @Unique private float orion$physicsPitch;
    @Unique private float orion$physicsYaw;

    @Override public void orion$setPhysicsRotation(float pitch, float yaw) {
        orion$physicsPitch = pitch;
        orion$physicsYaw = yaw;
    }
    @Override public float orion$getPhysicsPitch() { return orion$physicsPitch; }
    @Override public float orion$getPhysicsYaw() { return orion$physicsYaw; }
}
