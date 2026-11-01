package ru.orion.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.mixin.GuiGraphicsExtractorAccessor;
import ru.orion.client.mixin.RenderPipelinesAccessor;

/** Однопроходное направленное размытие копии мира до отрисовки HUD. */
public final class OrionMotionBlur {
    private static RenderPipeline createPipeline() {
        return RenderPipelinesAccessor.orion$registerOptional(
            RenderPipeline.builder(RenderPipelinesAccessor.orion$getGuiSnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/motion_blur"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/motion_blur"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0).build());
    }
    private final OrionConfigManager configManager;
    private RenderPipeline pipeline;
    private boolean captureReady;
    private GpuTexture texture;
    private GpuTextureView view;
    private boolean requested;
    private long lastNanos;
    private float lastYaw, lastPitch, smoothX, smoothY, smoothSpeed;
    private Vec3 lastPosition;

    public OrionMotionBlur(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"motion_blur"), this::render); }
    private void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!configManager.getConfig().isMotionBlurEnabled() || client.player == null || client.level == null || client.gui.screen() != null) {
            lastNanos = 0; smoothX = smoothY = smoothSpeed = 0; lastPosition = null;
            release(); return;
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
        if ((Math.abs(smoothX)+Math.abs(smoothY)+smoothSpeed)*strength < 0.015F) { requested = false; return; }
        GpuTexture source = client.gameRenderer.mainRenderTarget().getColorTexture();
        if (source == null || source.isClosed() || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) return;
        try {
            if (pipeline == null) pipeline = createPipeline();
            if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) return;
        } catch (RuntimeException | LinkageError ignored) { return; }
        if (texture == null || texture.isClosed() || view == null || view.isClosed()
                || texture.getWidth(0) != source.getWidth(0) || texture.getHeight(0) != source.getHeight(0)
                || texture.getFormat() != source.getFormat()) {
            release();
            texture = RenderSystem.getDevice().createTexture("orion_motion_blur_scene",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    source.getFormat(), source.getWidth(0), source.getHeight(0), 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
        }
        int x = Math.round((Mth.clamp(smoothX * strength,-1,1)*0.5F+0.5F)*255);
        int y = Math.round((Mth.clamp(smoothY * strength,-1,1)*0.5F+0.5F)*255);
        int radial = Math.round(Mth.clamp(smoothSpeed * strength, 0, 1) * 255);
        int packed = ARGB.color(255,x,y,radial);
        requested = true;
        if (!captureReady) return;
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var scissor = new ScreenRectangle(0,0,context.guiWidth(),context.guiHeight());
        // Цветовая текстура OpenGL имеет начало внизу; Vulkan — вверху.
        boolean flip = RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(java.util.Locale.ROOT).contains("opengl");
        ((GuiGraphicsExtractorAccessor)(Object)context).orion$getGuiRenderState().addGuiElement(
                new BlitRenderState(pipeline, TextureSetup.singleTexture(view,sampler), new Matrix3x2f(context.pose()),
                        0,0,context.guiWidth(),context.guiHeight(),0,1,flip?1F:0F,flip?0F:1F,packed,scissor));
    }
    public void captureBeforeGui(GpuTexture source) {
        if (!requested) return;
        // GUI может сначала рисоваться во вспомогательную цель. Копировать её как мир нельзя.
        GpuTexture main = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
        if (source != main) return;
        requested = false;
        if (source == null || source.isClosed() || texture == null || texture.isClosed()
                || source.getWidth(0) != texture.getWidth(0) || source.getHeight(0) != texture.getHeight(0)
                || source.getFormat() != texture.getFormat() || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) return;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(source,texture,0,0,0,0,0,texture.getWidth(0),texture.getHeight(0));
        encoder.submit();
        captureReady = true;
    }
    public void release() {
        requested = false;
        captureReady = false;
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
    }
}
