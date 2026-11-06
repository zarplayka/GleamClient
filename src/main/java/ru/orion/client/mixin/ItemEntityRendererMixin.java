package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.loader.api.FabricLoader;
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
    @Unique private static final boolean orion$externalPhysics = FabricLoader.getInstance().isModLoaded("itemphysiclite")
            || FabricLoader.getInstance().isModLoaded("itemphysic");
    @Shadow @Final private RandomSource random;
    @Unique private final Map<ItemEntity, OrionItemPhysicsState.Rotation> orion$rotations = new WeakHashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void orion$extract(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        var config = OrionClient.getConfigOrNull();
        if (orion$externalPhysics || config == null || !config.isItemPhysicsEnabled()) {
            orion$rotations.clear();
            return;
        }
        if (state.item.isEmpty()) return;
        var bounds = state.item.getModelBoundingBox();
        boolean flat = bounds.getZsize() < Math.min(bounds.getXsize(), bounds.getYsize()) * .55;
        double tick = entity.tickCount + (double) partial;
        OrionItemPhysicsState.Rotation rotation = orion$rotations.computeIfAbsent(entity, item -> new OrionItemPhysicsState.Rotation(
                item.onGround() ? (flat ? 90F : 0F) : Math.floorMod(item.getId() * 37, 360),
                Math.floorMod(item.getId() * 83, 360), (item.getId() & 1) == 0 ? 1F : -1F, tick));
        float dt = (float) Math.clamp((tick - rotation.lastTick) / 20, 0, .1);
        rotation.lastTick = Math.max(tick, rotation.lastTick);
        if (entity.onGround()) {
            if (!rotation.grounded) {
                rotation.groundPitch = flat ? 90F + 180F * Math.round((rotation.pitch - 90F) / 180F)
                        : 90F * Math.round(rotation.pitch / 90F);
                rotation.groundRoll = flat ? 90F * Math.round(rotation.roll / 90F) : 0F;
            }
            float settle = 1F - (float) Math.exp(-16F * dt);
            rotation.pitch += Mth.wrapDegrees(rotation.groundPitch - rotation.pitch) * settle;
            rotation.roll += Mth.wrapDegrees(rotation.groundRoll - rotation.roll) * settle;
        } else {
            float speed = (float) entity.getDeltaMovement().length();
            float tumble = 90F + Math.min(440F, speed * 740F);
            if (entity.isInWater()) tumble *= .28F;
            rotation.pitch += rotation.spin * tumble * dt;
            rotation.roll += tumble * .33F * dt;
            rotation.yaw += rotation.spin * tumble * .24F * dt;
        }
        rotation.grounded = entity.onGround();
        rotation.pitch = Mth.wrapDegrees(rotation.pitch);
        rotation.roll = Mth.wrapDegrees(rotation.roll);
        rotation.yaw = Mth.wrapDegrees(rotation.yaw);
        ((OrionItemPhysicsState) state).orion$setPhysicsRotation(rotation.pitch, rotation.yaw, rotation.roll);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void orion$submit(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                              CameraRenderState camera, CallbackInfo ci) {
        var config = OrionClient.getConfigOrNull();
        if (orion$externalPhysics || config == null || !config.isItemPhysicsEnabled()) return;
        ci.cancel();
        if (state.item.isEmpty()) return;
        var bounds = state.item.getModelBoundingBox();
        var rotation = (OrionItemPhysicsState) state;
        float pitch = rotation.orion$getPhysicsPitch();
        float roll = rotation.orion$getPhysicsRoll();
        double angle = Math.toRadians(pitch);
        double rollAngle = Math.toRadians(roll), cosine = Math.cos(angle);
        double x = cosine * Math.sin(rollAngle), y = cosine * Math.cos(rollAngle), z = -Math.sin(angle);
        // Высота зависит от поворота модели, чтобы она не проваливалась в землю.
        double minY = Math.min(bounds.minX * x, bounds.maxX * x) + Math.min(bounds.minY * y, bounds.maxY * y)
                + Math.min(bounds.minZ * z, bounds.maxZ * z);
        pose.pushPose();
        pose.translate(0, Math.max(0.02D, -minY + 0.02D), 0);
        pose.rotate(Axis.YP.rotationDegrees(rotation.orion$getPhysicsYaw()));
        pose.rotate(Axis.XP.rotationDegrees(pitch));
        pose.rotate(Axis.ZP.rotationDegrees(roll));
        ItemEntityRenderer.submitMultipleFromCount(pose, collector, state.lightCoords, state, random, bounds);
        pose.popPose();
    }

}
