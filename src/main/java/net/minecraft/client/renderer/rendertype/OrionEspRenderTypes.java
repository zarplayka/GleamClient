package net.minecraft.client.renderer.rendertype;

//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;

//?} else {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;

*///?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//? if >=26.2 <26.3 {
/*import com.mojang.blaze3d.PrimitiveTopology;
*///?}
//? if >=26.2 {
import net.minecraft.client.renderer.BindGroupLayouts;
//?} else {
/*import com.mojang.blaze3d.shaders.UniformType;
*///?}
import net.minecraft.client.renderer.RenderPipelines;
//? if >=26.3 {
import net.minecraft.client.renderer.oit.OitPipelineSet;
//?}

import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.RenderPipelinesAccessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OrionEspRenderTypes {
    // ESP_DEPTH — без теста глубины (rhomb рисуется поверх модели цели).
    // ESP_DEPTH_TESTED — с тестом глубины: ghost/chain/crystals/marker прячутся
    // за моделькой и блоками; глубину не пишем, чтобы не резать чужую прозрачность.
    //? if >=26.2 {
    private static final CompareOp WORLD_DEPTH_COMPARISON = CompareOp.GREATER_THAN_OR_EQUAL;
    //?} else {
    /*private static final CompareOp WORLD_DEPTH_COMPARISON = CompareOp.LESS_THAN_OR_EQUAL;
    *///?}
    private static final DepthStencilState ESP_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    private static final DepthStencilState ESP_DEPTH_TESTED = new DepthStencilState(WORLD_DEPTH_COMPARISON, false);
    private static final RenderPipeline KILL_EFFECTS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/kill_effects"))
            .withVertexShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());
    private static final RenderType KILL_EFFECTS = RenderType.create("orion_kill_effects",
            RenderSetup.builder(KILL_EFFECTS_PIPELINE)
                    //? if >=26.3 {
                    .setOitPipelines(oit("kill_effects", KILL_EFFECTS_PIPELINE, false, false))
                    //?}

                    .withTexture("Sampler0", Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/kill/materials.png"))
                    .sortOnUpload().createRenderSetup());
    private static final RenderPipeline KILL_MODELS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/kill_models"))
            .withVertexShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(WORLD_DEPTH_COMPARISON, true))
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());
    private static final RenderType KILL_MODELS = RenderType.create("orion_kill_models",
            RenderSetup.builder(KILL_MODELS_PIPELINE)
                    //? if >=26.3 {
                    .setOitPipelines(oit("kill_models", KILL_MODELS_PIPELINE, false, false))
                    //?}

                    .withTexture("Sampler0", Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/kill/materials.png"))
                    .sortOnUpload().createRenderSetup());
    private static final RenderPipeline WINGS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/cosmetic_wings"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(WORLD_DEPTH_COMPARISON, true))
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());
    private static final RenderType WINGS = RenderType.create("orion_cosmetic_wings",
            RenderSetup.builder(WINGS_PIPELINE)
                    //? if >=26.3 {
                    .setOitPipelines(oit("cosmetic_wings", WINGS_PIPELINE, false, false))
                    //?}

                    .sortOnUpload()
                    .createRenderSetup());

    private static final RenderPipeline LYRICS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/world_lyrics"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());
    private static final RenderType LYRICS = RenderType.create("gleam_world_lyrics",
            RenderSetup.builder(LYRICS_PIPELINE)
                    //? if >=26.3 {
                    .setOitPipelines(oit("world_lyrics", LYRICS_PIPELINE, false, false))
                    //?}

                    .sortOnUpload().createRenderSetup());
    private static final RenderPipeline LYRICS_SOLID_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/world_lyrics_solid"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            *///?}
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(WORLD_DEPTH_COMPARISON, true))
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());
    private static final RenderType LYRICS_SOLID = RenderType.create("gleam_world_lyrics_solid",
            RenderSetup.builder(LYRICS_SOLID_PIPELINE)
                    //? if >=26.3 {
                    .withForcedSolidModelPhase()
                    //?}
                    .createRenderSetup());

    private static final RenderPipeline ESP_COLOR_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_color"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH)
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());

    private static final RenderPipeline ESP_COLOR_DEPTH_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_color_depth"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());

    private static final RenderPipeline ESP_TEXTURE_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_texture"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/esp_texture"))
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH)
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());

    private static final RenderPipeline ESP_TEXTURE_DEPTH_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_texture_depth"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/esp_texture"))
            //? if >=26.2 {
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            //?} else {
            /*.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            *///?}
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            //? if >=26.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?} else {
            /*.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            *///?}
            .build());

    private static final RenderType COLOR_QUADS = RenderType.create(
            "orion_targetesp_color",
            RenderSetup.builder(ESP_COLOR_PIPELINE)
                    //? if >=26.3 {
                    .setOitPipelines(oit("esp_color", ESP_COLOR_PIPELINE, true, true))
                    //?}

                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                    .createRenderSetup()
    );

    private static final RenderType COLOR_QUADS_DEPTH_TESTED = RenderType.create(
            "orion_targetesp_color_depth",
            RenderSetup.builder(ESP_COLOR_DEPTH_PIPELINE)
                    //? if >=26.3 {
                    .setOitPipelines(oit("esp_color_depth", ESP_COLOR_DEPTH_PIPELINE, true, false))
                    //?}

                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                    .createRenderSetup()
    );

    //? if >=26.3 {
    private static final OitPipelineSet TEXTURE_OIT = oit("esp_texture", ESP_TEXTURE_PIPELINE, true, true);
    private static final OitPipelineSet TEXTURE_DEPTH_OIT = oit("esp_texture_depth", ESP_TEXTURE_DEPTH_PIPELINE, true, false);
    //?}
    private static final Map<Identifier, RenderType> TEXTURED = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> TEXTURED_DEPTH_TESTED = new ConcurrentHashMap<>();

    private OrionEspRenderTypes() {
    }

    //? if >=26.3 {
    private static OitPipelineSet oit(String name, RenderPipeline pipeline, boolean additive, boolean throughWalls) {
        RenderPipeline.Builder base = RenderPipeline.builder()
                .withVertexShader(pipeline.getShaders().get(ShaderType.VERTEX))
                .withFragmentShader(pipeline.getShaders().get(ShaderType.FRAGMENT))
                .withCull(false)
                .withVertexBinding(0, pipeline.getVertexFormatBinding(0))
                .withPrimitiveTopology(pipeline.getPrimitiveTopology());
        pipeline.getBindGroupLayouts().forEach(base::withBindGroupLayout);
        if (additive) base.withShaderDefine("OIT_ADDITIVE");
        var builder = OitPipelineSet.builder("orion_" + name, base);
        if (throughWalls) builder.withoutDepthTest();
        return RenderPipelines.register(builder.build());
    }
    //?}
    public static RenderType colorQuads() {
        return COLOR_QUADS;
    }

    public static RenderType cosmeticWings() { return WINGS; }

    public static RenderType worldLyrics() { return LYRICS; }
    public static RenderType worldLyricsSolid() { return LYRICS_SOLID; }

    public static RenderType killEffects() { return KILL_EFFECTS; }

    public static RenderType killModels() { return KILL_MODELS; }

    public static RenderType colorQuadsDepthTested() {
        return COLOR_QUADS_DEPTH_TESTED;
    }

    public static RenderType textured(Identifier texture) {
        return TEXTURED.computeIfAbsent(texture, OrionEspRenderTypes::createTextured);
    }

    public static RenderType texturedDepthTested(Identifier texture) {
        return TEXTURED_DEPTH_TESTED.computeIfAbsent(texture, OrionEspRenderTypes::createTexturedDepthTested);
    }

    private static RenderType createTextured(Identifier texture) {
        return RenderType.create(
                "orion_targetesp_textured",
                RenderSetup.builder(ESP_TEXTURE_PIPELINE)
                        //? if >=26.3 {
                        .setOitPipelines(TEXTURE_OIT)
                        //?}

                        .withTexture("Sampler0", texture)
                        .sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                        .createRenderSetup()
        );
    }

    private static RenderType createTexturedDepthTested(Identifier texture) {
        return RenderType.create(
                "orion_targetesp_textured_depth",
                RenderSetup.builder(ESP_TEXTURE_DEPTH_PIPELINE)
                        //? if >=26.3 {
                        .setOitPipelines(TEXTURE_DEPTH_OIT)
                        //?}

                        .withTexture("Sampler0", texture)
                        .sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                        .createRenderSetup()
        );
    }
}
