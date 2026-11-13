//? if <26.1 {
/*package ru.orion.client.mixin;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.entity.ClientAvatarState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;
@Mixin(GameRenderer.class)
public abstract class GleamLegacyCameraMixin {
    @Inject(method="getFov", at=@At("RETURN"), cancellable=true)
    private void gleam$zoom(Camera camera, float partialTick, boolean world, CallbackInfoReturnable<Float> callback) {
        var config = OrionClient.getConfigOrNull();
        if (((GameRenderer)(Object)this).getPanoramicScreenshotParameters() != null) return;
        float factor = OrionZoom.factor(Minecraft.getInstance(), config);
        if (factor != 1F && (world || config != null && config.isZoomHand()))
            callback.setReturnValue(callback.getReturnValueF()/factor);
    }
    @Inject(method="getProjectionMatrix", at=@At("RETURN"))
    private void gleam$aspect(float fov, CallbackInfoReturnable<Matrix4f> callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isAspectEnabled() && config.getAspectStretch() > 0F) {
            var matrix = callback.getReturnValue();
            matrix.m00(matrix.m00()/config.getAspectStretch());
        }
    }
    @Redirect(method="bobView", at=@At(value="INVOKE", target="Lnet/minecraft/client/entity/ClientAvatarState;getInterpolatedBob(F)F"))
    private float gleam$bob(ClientAvatarState avatar, float partialTick) {
        float bob = avatar.getInterpolatedBob(partialTick);
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isZoomRelativeBobbing() ? bob/OrionZoom.current() : bob;
    }
    @ModifyArgs(method="renderLevel", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void gleam$capture(Args args) {
        ru.orion.client.render.GleamCameraMatrices.capture(args.get(4), args.get(5));
    }
}
*///?}
