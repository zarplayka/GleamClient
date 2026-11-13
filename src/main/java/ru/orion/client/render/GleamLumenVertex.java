package ru.orion.client.render;
import com.mojang.blaze3d.vertex.VertexConsumer;
public interface GleamLumenVertex {
    //? if <26.2 {
    /*com.mojang.blaze3d.vertex.VertexFormatElement UV3 = registerUv3();

    private static com.mojang.blaze3d.vertex.VertexFormatElement registerUv3() {
        for (int id = 0; id < com.mojang.blaze3d.vertex.VertexFormatElement.MAX_COUNT; id++) {
            if (com.mojang.blaze3d.vertex.VertexFormatElement.byId(id) == null) {
                return com.mojang.blaze3d.vertex.VertexFormatElement.register(
                        id, 3, com.mojang.blaze3d.vertex.VertexFormatElement.Type.FLOAT, false, 2);
            }
        }
        throw new IllegalStateException("No free vertex format element for Lumen UV3");
    }
    *///?}

    void gleam$uv3(float u, float v);
    static VertexConsumer uv3(VertexConsumer vertex, float u, float v) {
        //? if >=26.3 {
        return vertex.setUv3(u, v);
        //?} else {
                /*((GleamLumenVertex) vertex).gleam$uv3(u, v);
        return vertex;
        *///?}
    }
}
