package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.GuiGraphicsExtractorAccessor;
import ru.orion.client.mixin.RenderPipelinesAccessor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Копирует фон до отрисовки GUI и объединяет его с маской и чётким Java2D-слоем. */
public final class OrionGlassCompositor {
    public static final OrionGlassCompositor INSTANCE = new OrionGlassCompositor();
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final long CAPTURE_INTERVAL_NANOS = 1_000_000_000L / 30L;
    private static final RenderPipeline PIPELINE = RenderPipelinesAccessor.orion$registerOptional(
            RenderPipeline.builder(RenderPipelinesAccessor.orion$getGuiSnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/hud_glass"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/hud_glass"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                    .build());

    private GpuTexture scene;
    private GpuTextureView sceneView;
    private boolean captureRequested;
    private boolean sceneCaptured;
    private long lastCaptureNanos;
    private final List<CaptureArea> captureAreas = new ArrayList<>();
    private final List<CaptureArea> lastCaptureAreas = new ArrayList<>();
    private boolean captureWholeScene;
    private boolean bottomOrigin;
    private final Map<Identifier, MaskTexture> masks = new HashMap<>();
    private boolean shaderWarningLogged;

    private OrionGlassCompositor() {
    }

    public void updateMask(Identifier id, MaskLayout layout) {
        MaskTexture existing = masks.get(id);
        DynamicTexture mask = existing == null ? null : existing.texture();
        MaskLayout maskLayout = existing == null ? null : existing.layout();
        if (layout.equals(maskLayout) && mask != null && mask.getPixels() != null
                && !mask.getPixels().isClosed()) {
            return;
        }
        BufferedImage image = new BufferedImage(layout.pixelWidth(), layout.pixelHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(layout.pixelWidth() / layout.designWidth(), layout.pixelHeight() / layout.designHeight());
            g.setColor(Color.WHITE);
            for (Panel panel : layout.panels()) {
                g.fill(new RoundRectangle2D.Float(panel.x(), panel.y(), panel.width(), panel.height(),
                        panel.arc(), panel.arc()));
            }
        } finally {
            g.dispose();
        }
        if (mask == null || mask.getPixels() == null || mask.getPixels().isClosed()
                || maskLayout == null || maskLayout.pixelWidth() != layout.pixelWidth()
                || maskLayout.pixelHeight() != layout.pixelHeight()) {
            if (mask != null) {
                mask.close();
            }
            NativeImage pixels = new NativeImage(layout.pixelWidth(), layout.pixelHeight(), true);
            OrionHudRenderer.copyPixels(image, pixels);
            mask = new DynamicTexture(() -> id + "_glass_mask", pixels);
        } else {
            OrionHudRenderer.copyPixels(image, mask.getPixels());
            mask.upload();
        }
        masks.put(id, new MaskTexture(layout, mask));
    }

    public boolean submit(Identifier id, GuiGraphicsExtractor context, DynamicTexture foreground,
                          int x, int y, int width, int height) {
        return submit(id,context,foreground,x,y,width,height,false);
    }

    public boolean submit(Identifier id, GuiGraphicsExtractor context, DynamicTexture foreground,
                          int x, int y, int width, int height, boolean screenGlass) {
        Minecraft client = Minecraft.getInstance();
        MaskTexture entry = masks.get(id);
        // Экран меню может размывать весь GUI отдельным проходом: оставляем ему управление фоном.
        if (entry == null || (!screenGlass && client.gui.screen() != null && !(client.gui.screen() instanceof ChatScreen))
                || (!screenGlass && ((GuiGraphicsExtractorAccessor) (Object) context).orion$getGuiRenderState().isHudHidden)) {
            return false;
        }
        if (!screenGlass && !OrionClient.getInstance().getConfigManager().getConfig().isHudElementGlass(id.getPath())) {
            try {
                if (RenderSystem.getCompiledPipelineNullable(PIPELINE) == null) return false;
            } catch (RuntimeException exception) {
                warnShaderUnavailable(exception);
                return false;
            }
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var mask = entry.texture().getTextureView();
            TextureSetup textures = new TextureSetup(foreground.getTextureView(), mask, mask, sampler, sampler, sampler);
            ((GuiGraphicsExtractorAccessor) (Object) context).orion$getGuiRenderState().addGuiElement(
                    new BlitRenderState(PIPELINE, textures, new Matrix3x2f(context.pose()), x, y, x + width, y + height,
                            0F, 1F, 0F, 1F, 0x00FFFFFF, new ScreenRectangle(0, 0, context.guiWidth(), context.guiHeight())));
            return true;
        }
        GpuTexture source = client.gameRenderer.mainRenderTarget().getColorTexture();
        if (source == null || source.isClosed() || source.getWidth(0) <= 0 || source.getHeight(0) <= 0
                || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) {
            return false;
        }
        try {
            if (RenderSystem.getCompiledPipelineNullable(PIPELINE) == null) {
                warnShaderUnavailable(null);
                return false;
            }
        } catch (RuntimeException exception) {
            warnShaderUnavailable(exception);
            return false;
        }
        if (scene == null || scene.isClosed() || sceneView == null || sceneView.isClosed()
                || scene.getWidth(0) != source.getWidth(0)
                || scene.getHeight(0) != source.getHeight(0) || scene.getFormat() != source.getFormat()) {
            // Уже собранные GUI-состояния продолжают использовать прежний sceneView до отрисовки.
            if (captureRequested) {
                return false;
            }
            releaseScene();
            scene = RenderSystem.getDevice().createTexture("orion_glass_scene",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    source.getFormat(), source.getWidth(0), source.getHeight(0), 1, 1);
            sceneView = RenderSystem.getDevice().createTextureView(scene);
            bottomOrigin = RenderSystem.getDevice().getDeviceInfo().backendName()
                    .toLowerCase(java.util.Locale.ROOT).contains("opengl");
        }
        CaptureArea area = CaptureArea.from(context.pose(), x, y, width, height,
                context.guiWidth(), context.guiHeight(), source.getWidth(0), source.getHeight(0), bottomOrigin);
        if (area == null) return false;
        if (captureAreas.size() < 64) captureAreas.add(area);
        else captureWholeScene = true;
        captureRequested = true;
        if (!sceneCaptured) {
            return false;
        }
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        TextureSetup textures = new TextureSetup(foreground.getTextureView(), sceneView, entry.texture().getTextureView(),
                sampler, sampler, sampler);
        // Все вызовы submit идут из HUD с базовой областью отсечения по размеру экрана.
        ScreenRectangle scissorArea = new ScreenRectangle(0, 0, context.guiWidth(), context.guiHeight());
        ((GuiGraphicsExtractorAccessor) (Object) context).orion$getGuiRenderState()
                .addGuiElement(new BlitRenderState(PIPELINE, textures,
                new Matrix3x2f(context.pose()), x, y, x + width, y + height,
                0.0F, 1.0F, 0.0F, 1.0F, -1, scissorArea));
        return true;
    }

    private void warnShaderUnavailable(RuntimeException exception) {
        if (!shaderWarningLogged) {
            LOGGER.warn("HUD glass shader unavailable; using the transparent HUD fallback", exception);
            shaderWarningLogged = true;
        }
    }

    /** Получает актуальную текстуру render target непосредственно перед отрисовкой GUI. */
    public void captureBeforeGui(GpuTexture source) {
        if (!captureRequested) {
            if (masks.isEmpty()) {
                releaseScene();
            }
            return;
        }
        if(source != Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture())return;
        captureRequested = false;
        // Между extraction и render framebuffer может быть пересоздан при resize или смене мира.
        // В этом кадре сохраняем прежний фон, не закрывая текстуры уже собранных GUI-состояний.
        if (source == null || source.isClosed() || scene == null || scene.isClosed()
                || sceneView == null || sceneView.isClosed()
                || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0
                || source.getWidth(0) != scene.getWidth(0)
                || source.getHeight(0) != scene.getHeight(0)
                || source.getFormat() != scene.getFormat()) {
            sceneCaptured = false;
            captureAreas.clear();
            captureWholeScene = false;
            return;
        }
        if (captureWholeScene) {
            captureAreas.clear();
            captureAreas.add(new CaptureArea(0, 0, source.getWidth(0), source.getHeight(0)));
            captureWholeScene = false;
        }
        long now = System.nanoTime();
        if (sceneCaptured && now - lastCaptureNanos < CAPTURE_INTERVAL_NANOS
                && captureAreas.equals(lastCaptureAreas)) {
            captureAreas.clear();
            return;
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        // Размер текстуры прежний: shader продолжает читать по gl_FragCoord.
        // Копируем только видимые панели с запасом под выборки размытия.
        for (CaptureArea area : captureAreas) {
            encoder.copyTextureToTexture(source, scene, 0, area.x(), area.y(), area.x(), area.y(),
                    area.width(), area.height());
        }
        encoder.submit();
        lastCaptureAreas.clear();
        lastCaptureAreas.addAll(captureAreas);
        captureAreas.clear();
        lastCaptureNanos = now;
        sceneCaptured = true;
    }

    public void close() {
        captureRequested = false;
        captureAreas.clear();
        captureWholeScene = false;
        releaseScene();
        for (MaskTexture entry : masks.values()) {
            entry.texture().close();
        }
        masks.clear();
    }

    public void releaseMask(Identifier id) {
        MaskTexture entry = masks.remove(id);
        if (entry != null) {
            entry.texture().close();
        }
    }

    private void releaseScene() {
        sceneCaptured = false;
        lastCaptureAreas.clear();
        lastCaptureNanos = 0L;
        if (sceneView != null) {
            sceneView.close();
            sceneView = null;
        }
        if (scene != null) {
            scene.close();
            scene = null;
        }
    }

    public record Panel(float x, float y, float width, float height, float arc) {
    }

    /** Координаты копирования в framebuffer, включая поворот/масштаб GUI и края blur. */
    record CaptureArea(int x, int y, int width, int height) {
        static CaptureArea from(Matrix3x2fc pose, int x, int y, int width, int height,
                                int guiWidth, int guiHeight, int pixelWidth, int pixelHeight, boolean bottomOrigin) {
            if (width <= 0 || height <= 0 || guiWidth <= 0 || guiHeight <= 0) return null;
            float x0 = pose.m00() * x + pose.m10() * y + pose.m20();
            float y0 = pose.m01() * x + pose.m11() * y + pose.m21();
            float dx = pose.m00() * width, dy = pose.m01() * width;
            float ex = pose.m10() * height, ey = pose.m11() * height;
            float scaleX = pixelWidth / (float) guiWidth, scaleY = pixelHeight / (float) guiHeight;
            int left = Math.max(0, (int) Math.floor((x0 + Math.min(0, dx) + Math.min(0, ex)) * scaleX) - 6);
            int right = Math.min(pixelWidth, (int) Math.ceil((x0 + Math.max(0, dx) + Math.max(0, ex)) * scaleX) + 6);
            int top = Math.max(0, (int) Math.floor((y0 + Math.min(0, dy) + Math.min(0, ey)) * scaleY) - 6);
            int bottom = Math.min(pixelHeight, (int) Math.ceil((y0 + Math.max(0, dy) + Math.max(0, ey)) * scaleY) + 6);
            if (right <= left || bottom <= top) return null;
            return new CaptureArea(left, bottomOrigin ? pixelHeight - bottom : top, right - left, bottom - top);
        }
    }

    public record MaskLayout(int pixelWidth, int pixelHeight, float designWidth, float designHeight,
                             List<Panel> panels) {
        public MaskLayout {
            panels = List.copyOf(panels);
        }
    }

    private record MaskTexture(MaskLayout layout, DynamicTexture texture) {
    }
}
