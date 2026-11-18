//? if <26.1 {
/*package ru.orion.client.mixin;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.OrionGlintRenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.SequencedMap;
import java.util.LinkedHashMap;
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class GleamLegacyGlintBufferMixin {
    @Shadow @Final @Mutable protected SequencedMap<RenderType,ByteBufferBuilder> fixedBuffers;
    @Unique private boolean gleam$ownsFixedMap;
    @Inject(method="getBuffer",at=@At("HEAD"))
    private void gleam$deferGlint(RenderType type, CallbackInfoReturnable<VertexConsumer> callback) {
        if(type.pipeline()!=RenderPipelines.GLINT || fixedBuffers.containsKey(type)
                || !OrionGlintRenderTypes.isLegacyGlint(type)) return;
        // Shared-буфер сбрасывается раньше основы предмета: EQUAL-depth тогда отбрасывает glint.
        // Отдельный fixed-буфер в конце очереди повторяет порядок vanilla glint.
        if(!gleam$ownsFixedMap) {
            fixedBuffers = new LinkedHashMap<>(fixedBuffers);
            gleam$ownsFixedMap = true;
        }
        fixedBuffers.put(type, ru.orion.client.render.GleamLegacyGlintBuffers.create(type.bufferSize()));
    }
}
*///?}
