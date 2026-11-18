package ru.orion.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.orion.client.effect.OrionFreeLook;

@Mixin(Camera.class)
public abstract class OrionFreeLookCameraMixin {
    @Shadow private Entity entity;

    @ModifyArgs(
            //? if >=26.1 {
            method = "alignWithEntity(F)V",
            //?} else {
            /*method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V",
            *///?}
            slice = @Slice(to = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void orion$freeLookRotation(Args rotation) {
        if (OrionFreeLook.isActive(entity)) {
            rotation.set(0, OrionFreeLook.yaw());
            rotation.set(1, OrionFreeLook.pitch());
        }
    }
}
