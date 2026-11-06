package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionSkinLayers;

@Mixin(ModelPart.class)
public abstract class OrionSkinPartMixin {
    @Inject(method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$voxelLayer(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo callback) {
        if (OrionSkinLayers.render((ModelPart) (Object) this, pose, vertices, light, overlay, color)) callback.cancel();
    }
}
