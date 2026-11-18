package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.TriState;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

final class OrionEspRenderTypes {
    private static final Map<ResourceLocation, RenderType> TEXTURED = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TEXTURED_DEPTH_TESTED = new HashMap<>();
    private static final RenderType COLOR = createLayer("orion_esp_color", null, false);
    private static final RenderType COLOR_DEPTH_TESTED = createLayer("orion_esp_color_depth", null, true);

    private OrionEspRenderTypes() {
    }

    static RenderType colorQuads() {
        return COLOR;
    }

    static RenderType colorQuadsDepthTested() {
        return COLOR_DEPTH_TESTED;
    }

    static RenderType textured(ResourceLocation texture) {
        return TEXTURED.computeIfAbsent(texture, id -> createLayer("orion_esp_texture", id, false));
    }

    static RenderType texturedDepthTested(ResourceLocation texture) {
        return TEXTURED_DEPTH_TESTED.computeIfAbsent(texture, id -> createLayer("orion_esp_texture_depth", id, true));
    }

    static RenderType outline(float width) {
        var state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)));
        return RenderType.create("orion_block_outline", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 1536, false, false, state.createCompositeState(false));
    }

    private static RenderType createLayer(String name, ResourceLocation texture, boolean depthTest) {
        var state = RenderType.CompositeState.builder()
                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                .setDepthTestState(depthTest ? RenderStateShard.LEQUAL_DEPTH_TEST : RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE);
        VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
        if (texture == null) {
            state.setShaderState(RenderStateShard.POSITION_COLOR_SHADER);
        } else {
            state.setShaderState(RenderStateShard.POSITION_TEXTURE_COLOR_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, TriState.DEFAULT, false));
            format = DefaultVertexFormat.POSITION_TEX_COLOR;
        }
        return RenderType.create(name, format, VertexFormat.Mode.QUADS, 1536, false, true,
                state.createCompositeState(false));
    }
}
