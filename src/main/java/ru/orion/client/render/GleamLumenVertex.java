package ru.orion.client.render;
import com.mojang.blaze3d.vertex.VertexConsumer;
public interface GleamLumenVertex {
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
