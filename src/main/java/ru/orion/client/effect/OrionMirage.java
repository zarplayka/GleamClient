package ru.orion.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.mixin.RenderPipelinesAccessor;
import java.util.Optional;

/** Мягкие отражения видимых поверхностей по глубине мира, до рук и кеша HUD. */
public final class OrionMirage {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private final OrionConfigManager manager;
    private final RenderPipeline reflections = RenderPipelinesAccessor.orion$registerOptional(createPipeline(false));
    private final RenderPipeline composite = RenderPipelinesAccessor.orion$registerOptional(createPipeline(true));
    private GpuTexture scene, reflected;
    private GpuTextureView sceneView, reflectedView;
    private boolean warned;
    private static java.lang.reflect.Method irisInUse;
    private static Object irisApi;
    private static boolean irisChecked;

    public OrionMirage(OrionConfigManager manager) { this.manager = manager; }

    static RenderPipeline createPipeline(boolean composite) {
        String name = composite ? "mirage_composite" : "mirage_reflections";
        return RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/" + name))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1).build();
    }

    public static boolean externalShadersActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError ignored) { irisInUse = null; }
        }
        try { return irisInUse != null && Boolean.TRUE.equals(irisInUse.invoke(irisApi)); }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    public void applyToWorld() {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = manager.getConfig();
        if (!config.isMirageEnabled() || client.level == null || client.player == null || externalShadersActive()) { release(); return; }
        var camera = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.projectionMatrix == null) return;
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTexture source = target.getColorTexture(), depth = target.getDepthTexture();
        if (source == null || depth == null || source.isClosed() || depth.isClosed()
                || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0 || (depth.usage() & GpuTexture.USAGE_TEXTURE_BINDING) == 0) return;
        try {
            var reflectionPipeline = RenderSystem.getCompiledPipelineNullable(reflections);
            var compositePipeline = RenderSystem.getCompiledPipelineNullable(composite);
            if (reflectionPipeline == null || compositePipeline == null) { warn(null); return; }
            float ratio = config.getMirageQuality() == 0 ? .5F : config.getMirageQuality() == 1 ? .65F : .75F;
            int rw = Math.max(1, Math.round(source.getWidth(0) * ratio)), rh = Math.max(1, Math.round(source.getHeight(0) * ratio));
            if (scene == null || scene.isClosed() || sceneView == null || sceneView.isClosed()
                    || reflected == null || reflectedView == null || reflectedView.isClosed()
                    || scene.getWidth(0) != source.getWidth(0) || scene.getHeight(0) != source.getHeight(0)
                    || scene.getFormat() != source.getFormat() || reflected.getWidth(0) != rw || reflected.getHeight(0) != rh) {
                release();
                var device = RenderSystem.getDevice();
                scene = device.createTexture("orion_mirage_world", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        source.getFormat(), source.getWidth(0), source.getHeight(0), 1, 1);
                sceneView = device.createTextureView(scene);
                reflected = device.createTexture("orion_mirage_reflections", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                        source.getFormat(), rw, rh, 1, 1);
                reflectedView = device.createTextureView(reflected);
            }
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(source, scene, 0, 0, 0, 0, 0, scene.getWidth(0), scene.getHeight(0));
            var info = RenderSystem.getDevice().getDeviceInfo();
            var parameters = parameters(camera.projectionMatrix, config, info.backendName().toLowerCase(java.util.Locale.ROOT).contains("opengl") ? 1F : -1F, info.isZZeroToOne());
            drawPasses(encoder, reflectionPipeline, compositePipeline, sceneView, target.getDepthTextureView(), reflectedView, target.getColorTextureView(), parameters);
            warned = false;
        } catch (RuntimeException exception) { warn(exception); }
    }

    static GpuBufferSlice parameters(Matrix4f projection, OrionConfig config, float ySign, boolean zeroToOne) {
        int steps = config.getMirageQuality() == 0 ? 24 : config.getMirageQuality() == 1 ? 40 : 64;
        return RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(projection),
                new Vector4f(config.getMirageStrength(), config.getMirageRoughness(), config.getMirageBloom(), config.getMirageColorGrade()),
                new Vector3f(steps, ySign, zeroToOne ? 1 : 0), new Matrix4f(projection).invert());
    }

    static void drawPasses(CommandEncoder encoder, CompiledRenderPipeline reflection, CompiledRenderPipeline composite,
                           GpuTextureView scene, GpuTextureView depth, GpuTextureView reflected, GpuTextureView target, GpuBufferSlice parameters) {
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (var pass = encoder.createRenderPass(() -> "GleamClient Mirage reflections", reflected, Optional.empty())) {
            pass.setPipeline(reflection); pass.setUniform("DynamicTransforms", parameters);
            pass.setUniform("Sampler0", scene, linear); pass.setUniform("Sampler1", depth, nearest);
            pass.disableScissor(); pass.draw(3, 1, 0, 0);
        }
        try (var pass = encoder.createRenderPass(() -> "GleamClient Mirage composite", target, Optional.empty())) {
            pass.setPipeline(composite); pass.setUniform("DynamicTransforms", parameters);
            pass.setUniform("Sampler0", scene, linear); pass.setUniform("Sampler1", reflected, linear);
            pass.disableScissor(); pass.draw(3, 1, 0, 0);
        }
    }

    private void warn(RuntimeException exception) {
        if (!warned) { LOGGER.warn("Mirage render pass unavailable; preserving the original frame", exception); warned = true; }
    }
    public void release() {
        if (sceneView != null) sceneView.close(); if (reflectedView != null) reflectedView.close();
        if (scene != null) scene.close(); if (reflected != null) reflected.close();
        sceneView = reflectedView = null; scene = reflected = null;
    }
}
