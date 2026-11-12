package ru.orion.client.mixin;
//? if <26.3 {
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
        MemoryUtil.memPutFloat(vertexPointer + attribute.offset(), u);
        MemoryUtil.memPutFloat(vertexPointer + attribute.offset() + 4L, v);
    }
}

*///?}
