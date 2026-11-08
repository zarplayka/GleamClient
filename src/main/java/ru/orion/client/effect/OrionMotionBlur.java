package ru.orion.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.mixin.RenderPipelinesAccessor;

import java.util.Optional;

/** Однопроходное направленное размытие копии мира до отрисовки HUD. */
public final class OrionMotionBlur {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);

    static RenderPipeline createPipeline() {
        return RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/motion_blur"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/motion_blur"))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withDepthStencilState(Optional.empty())
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0).build();
    }
    private final OrionConfigManager configManager;
    // Регистрируем до первой загрузки ресурсов: кеш не компилирует pipeline по запросу.
    private final RenderPipeline pipeline = RenderPipelinesAccessor.orion$registerOptional(createPipeline());
    private boolean shaderWarningLogged;
    private float blurX, blurY, radialBlur;
    private GpuTexture texture;
    private GpuTextureView view;
    private long lastNanos;
    private float lastYaw, lastPitch, smoothX, smoothY, smoothSpeed;
    private Vec3 lastPosition;

    public OrionMotionBlur(OrionConfigManager configManager) { this.configManager = configManager; }
    /** Вызывается после мира и его post effects, до перенаправления framebuffer в кеш HUD. */
    public void applyBeforeGui() {
        Minecraft client = Minecraft.getInstance();
        boolean active = configManager.getConfig().isMotionBlurEnabled() && client.player != null && client.level != null
                && client.gameRenderer.gameRenderState().shouldRenderLevel && client.gui.screen() == null;
        if (!active) {
            lastNanos = 0; smoothX = smoothY = smoothSpeed = 0; lastPosition = null;
            release();
            return;
        }
        long now = System.nanoTime();
        float yaw = client.gameRenderer.mainCamera().yRot(), pitch = client.gameRenderer.mainCamera().xRot();
        Vec3 position = client.gameRenderer.mainCamera().position();
        if (lastNanos == 0) { lastNanos = now; lastYaw = yaw; lastPitch = pitch; lastPosition = position; return; }
        float dt = Math.min(0.1F, Math.max(0.001F, (now-lastNanos)/1_000_000_000F));
        float speedX = Mth.clamp(Mth.wrapDegrees(yaw-lastYaw) / dt / 500F, -1, 1);
        float speedY = Mth.clamp((pitch-lastPitch) / dt / 500F, -1, 1);
        double distance = position.distanceTo(lastPosition);
        float movement = distance > 8.0 ? 0 : Mth.clamp((float) (distance / dt / 18.0), 0, 1);
        lastNanos = now; lastYaw = yaw; lastPitch = pitch; lastPosition = position;
        float smoothing = 1F-(float)Math.exp(-14F*dt);
        smoothX += (speedX-smoothX)*smoothing; smoothY += (speedY-smoothY)*smoothing;
        smoothSpeed += (movement-smoothSpeed)*smoothing;
        float strength = configManager.getConfig().getMotionBlurStrength();
        blurX = Mth.clamp(smoothX * strength, -1, 1);
        blurY = Mth.clamp(smoothY * strength, -1, 1);
        radialBlur = Mth.clamp(smoothSpeed * strength, 0, 1);
        if (Math.abs(blurX) + Math.abs(blurY) + radialBlur < 0.015F) return;
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture source = target.getColorTexture();
        if (source == null || source.isClosed() || target.getColorTextureView() == null
                || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) return;
        try {
            var compiled = RenderSystem.getCompiledPipelineNullable(pipeline);
            if (compiled == null) {
                warnShaderUnavailable(null);
                return;
            }
            if (texture == null || texture.isClosed() || view == null || view.isClosed()
                    || texture.getWidth(0) != source.getWidth(0) || texture.getHeight(0) != source.getHeight(0)
                    || texture.getFormat() != source.getFormat()) {
                release();
                texture = RenderSystem.getDevice().createTexture("orion_motion_blur_scene",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        source.getFormat(), source.getWidth(0), source.getHeight(0), 1, 1);
                view = RenderSystem.getDevice().createTextureView(texture);
            }
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, texture.getWidth(0), texture.getHeight(0));
            var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(),
                    new Vector4f(blurX, blurY, radialBlur, 1));
            try (var pass = encoder.createRenderPass(() -> "GleamClient motion blur", target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(compiled);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("Sampler0", view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.disableScissor();
                pass.draw(3, 1, 0, 0);
            }
            shaderWarningLogged = false;
            // submit() выполняет сам движок в конце кадра: здесь нельзя вращать transient-memory.
        } catch (RuntimeException exception) {
            warnShaderUnavailable(exception);
        }
    }

    private void warnShaderUnavailable(RuntimeException exception) {
        if (!shaderWarningLogged) {
            LOGGER.warn("Motion blur shader unavailable; skipping motion blur", exception);
            shaderWarningLogged = true;
        }
    }

    public void release() {
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
    }
}
