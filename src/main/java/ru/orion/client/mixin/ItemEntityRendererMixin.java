package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionItemPhysicsState;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Shadow @Final private RandomSource random;
    @Unique private final Map<ItemEntity, OrionItemPhysicsState.Rotation> orion$rotations = new WeakHashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void orion$extract(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isItemPhysicsEnabled()) {
            orion$rotations.clear();
            return;
        }
        long now = System.nanoTime();
        OrionItemPhysicsState.Rotation rotation = orion$rotations.computeIfAbsent(entity, item -> new OrionItemPhysicsState.Rotation(
                item.onGround() ? 90F : Math.floorMod(item.getId() * 37, 360),
                Math.floorMod(item.getId() * 83, 360), now));
        float dt = Math.min(0.1F, Math.max(0F, (now - rotation.lastNanos) / 1_000_000_000F));
        rotation.lastNanos = now;
        if (entity.onGround()) {
            float difference = Mth.wrapDegrees(90F - rotation.pitch);
            rotation.pitch += difference * (1F - (float) Math.exp(-10F * dt));
        } else {
            float speed = (float) entity.getDeltaMovement().length();
            rotation.pitch += (140F + Math.min(260F, speed * 250F)) * dt;
        }
        rotation.pitch = Mth.wrapDegrees(rotation.pitch);
        ((OrionItemPhysicsState) state).orion$setPhysicsRotation(rotation.pitch, rotation.yaw);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void orion$submit(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                              CameraRenderState camera, CallbackInfo ci) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isItemPhysicsEnabled()) return;
        ci.cancel();
        if (state.item.isEmpty()) return;
        var bounds = state.item.getModelBoundingBox();
        var rotation = (OrionItemPhysicsState) state;
        float pitch = rotation.orion$getPhysicsPitch();
        double angle = Math.toRadians(pitch);
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        // Высота зависит от поворота модели, чтобы она не проваливалась в землю.
        double minY = Math.min(bounds.minY * cosine, bounds.maxY * cosine)
                + Math.min(-bounds.minZ * sine, -bounds.maxZ * sine);
        pose.pushPose();
        pose.translate(0, Math.max(0.02D, -minY + 0.02D), 0);
        pose.rotate(Axis.YP.rotationDegrees(rotation.orion$getPhysicsYaw()));
        pose.rotate(Axis.XP.rotationDegrees(pitch));
        ItemEntityRenderer.submitMultipleFromCount(pose, collector, state.lightCoords, state, random, bounds);
        pose.popPose();
    }

}
