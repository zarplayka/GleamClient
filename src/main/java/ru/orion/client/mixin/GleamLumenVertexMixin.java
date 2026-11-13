package ru.orion.client.mixin;
//? if >=26.2 <26.3 {
/*import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import ru.orion.client.render.GleamLumenVertex;
// Дополнительная координата формы Lumen в формате вершин Minecraft 26.2.
@Mixin(BufferBuilder.class)
public abstract class GleamLumenVertexMixin implements GleamLumenVertex {
    @Shadow private long vertexPointer;
    @Shadow @Final private VertexFormat format;
    @Override public void gleam$uv3(float u, float v) {
        var attribute = format.getElement("UV3");
        if (attribute == null || vertexPointer == -1L) throw new IllegalStateException("Lumen vertex has no UV3");
        long offset = attribute.offset();
        MemoryUtil.memPutFloat(vertexPointer + offset, u);
        MemoryUtil.memPutFloat(vertexPointer + offset + Float.BYTES, v);
    }
}

*///?}
//? if <26.2 {
/*@org.spongepowered.asm.mixin.Mixin(com.mojang.blaze3d.vertex.BufferBuilder.class)
public abstract class GleamLumenVertexMixin implements ru.orion.client.render.GleamLumenVertex {
    @org.spongepowered.asm.mixin.Shadow
    private long beginElement(com.mojang.blaze3d.vertex.VertexFormatElement element) {
        throw new AssertionError();
    }

    @Override
    public void gleam$uv3(float u, float v) {
        long pointer = beginElement(UV3);
        if (pointer == -1L) throw new IllegalStateException("Lumen vertex has no UV3");
        org.lwjgl.system.MemoryUtil.memPutFloat(pointer, u);
        org.lwjgl.system.MemoryUtil.memPutFloat(pointer + Float.BYTES, v);
    }
}
*///?}
