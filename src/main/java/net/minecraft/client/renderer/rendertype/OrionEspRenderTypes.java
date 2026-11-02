package net.minecraft.client.renderer.rendertype;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.RenderPipelinesAccessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OrionEspRenderTypes {
    // ESP_DEPTH — без теста глубины (rhomb рисуется поверх модели цели).
    // ESP_DEPTH_TESTED — с тестом глубины: ghost/chain/crystals/marker прячутся
    // за моделькой и блоками; глубину не пишем, чтобы не резать чужую прозрачность.
    private static final DepthStencilState ESP_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    private static final DepthStencilState ESP_DEPTH_TESTED = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    private static final RenderPipeline KILL_EFFECTS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/kill_effects"))
            .withVertexShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());
    private static final RenderType KILL_EFFECTS = RenderType.create("orion_kill_effects",
            RenderSetup.builder(KILL_EFFECTS_PIPELINE)
                    .withTexture("Sampler0", Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/kill/materials.png"))
                    .sortOnUpload().createRenderSetup());
    private static final RenderPipeline KILL_MODELS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/kill_models"))
            .withVertexShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/kill_models"))
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());
    private static final RenderType KILL_MODELS = RenderType.create("orion_kill_models",
            RenderSetup.builder(KILL_MODELS_PIPELINE)
                    .withTexture("Sampler0", Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/kill/materials.png"))
                    .sortOnUpload().createRenderSetup());
    private static final RenderPipeline WINGS_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/cosmetic_wings"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());
    private static final RenderType WINGS = RenderType.create("orion_cosmetic_wings", RenderSetup.builder(WINGS_PIPELINE).createRenderSetup());

    private static final RenderPipeline ESP_COLOR_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_color"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());

    private static final RenderPipeline ESP_COLOR_DEPTH_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_color_depth"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());

    private static final RenderPipeline ESP_TEXTURE_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_texture"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());

    private static final RenderPipeline ESP_TEXTURE_DEPTH_PIPELINE = RenderPipelinesAccessor.orion$register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/targetesp_texture_depth"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(ESP_DEPTH_TESTED)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build());

    private static final RenderType COLOR_QUADS = RenderType.create(
            "orion_targetesp_color",
            RenderSetup.builder(ESP_COLOR_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                    .createRenderSetup()
    );

    private static final RenderType COLOR_QUADS_DEPTH_TESTED = RenderType.create(
            "orion_targetesp_color_depth",
            RenderSetup.builder(ESP_COLOR_DEPTH_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                    .createRenderSetup()
    );

    private static final Map<Identifier, RenderType> TEXTURED = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> TEXTURED_DEPTH_TESTED = new ConcurrentHashMap<>();

    private OrionEspRenderTypes() {
    }

    public static RenderType colorQuads() {
        return COLOR_QUADS;
    }

    public static RenderType cosmeticWings() { return WINGS; }

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
                        .withTexture("Sampler0", texture)
                        .sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                        .createRenderSetup()
        );
    }
}
