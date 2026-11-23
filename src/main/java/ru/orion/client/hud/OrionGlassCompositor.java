package ru.orion.client.hud;

import ru.orion.client.GleamMinecraft;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
//? if >=26.2 {
import net.minecraft.client.renderer.BindGroupLayouts;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexFormat;
*///?}
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.GuiGraphicsExtractorAccessor;
import ru.orion.client.mixin.RenderPipelinesAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Копирует мир перед отрисовкой GUI, размывает копию на GPU и отдаёт её стеклянным поверхностям.
 * HUD получает маску формы с расстоянием до края и нормалью, Lumen считает форму в шейдере.
 */
public final class OrionGlassCompositor {
    public static final OrionGlassCompositor INSTANCE = new OrionGlassCompositor();
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    /** Должно совпадать с GLASS_BEVEL в liquid_glass.glsl. */
    private static final float BEVEL = 13F;
    /** Максимальное расстояние до края, хранимое в маске (канал R). */
    private static final float EDGE_RANGE = 96F;
    private static final RenderPipeline PIPELINE = RenderPipelinesAccessor.orion$registerOptional(
            RenderPipeline.builder(RenderPipelinesAccessor.orion$getGuiSnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/hud_glass"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/hud_glass"))
                    //? if >=26.2 {
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    //?} else {
                    /*.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    *///?}
                    //? if >=26.2 {
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                    //?} else {
                    /*.withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withSampler("Sampler2")
                    *///?}
                    .build());
    private static final RenderPipeline BLUR_DOWN = blurPipeline("glass_blur_down", "GLASS_BLUR_DOWN");
    private static final RenderPipeline BLUR_UP = blurPipeline("glass_blur_up", "GLASS_BLUR_UP");

    private GpuTexture scene;
    private GpuTextureView sceneView;
    private final BlurChain sceneBlur = new BlurChain("scene");
    private final BlurChain titleBlur = new BlurChain("title");
    private GpuTexture titleSource;
    private boolean titleRequested;
    private boolean titleReady;
    private boolean captureRequested;
    private boolean sceneCaptured;
    private boolean bottomOrigin;
    private final Map<Identifier, MaskTexture> masks = new HashMap<>();
    private boolean shaderWarningLogged;
    private DynamicTexture transparent;

    private OrionGlassCompositor() {
    }

    private static RenderPipeline blurPipeline(String name, String define) {
        return RenderPipelinesAccessor.orion$registerOptional(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/glass_blur"))
                .withShaderDefine(define)
                //? if >=26.3 {
                .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                //?} elif >=26.2 {
                /*.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                *///?} else {
                /*.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                *///?}
                .withCull(false)
                //? if >=26.1 {
                .withDepthStencilState(Optional.empty())
                //?} else {
                /*.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
                *///?}
                //? if >=26.3 {
                .withColorTargetState(com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT)
                //?} elif >=26.1 {
                /*.withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
                *///?} else {
                /*.withoutBlend()
                *///?}
                //? if >=26.2 {
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                //?} else {
                /*.withSampler("Sampler0")
                *///?}
                .build());
    }

    /** Ширина скоса в пикселях framebuffer, как glassBevel() в шейдере. */
    static float bevelPixels(float halfMinPixels, float scale) {
        return Math.max(Math.min(halfMinPixels * .5F, BEVEL * scale), 1F);
    }

    public void updateMask(Identifier id, MaskLayout layout) {
        MaskTexture existing = masks.get(id);
        DynamicTexture mask = existing == null ? null : existing.texture();
        MaskLayout maskLayout = existing == null ? null : existing.layout();
        if (layout.equals(maskLayout) && mask != null && mask.getPixels() != null
                && !ru.orion.client.render.GleamNativeImages.isClosed(mask.getPixels())) {
            return;
        }
        boolean recreate = mask == null || mask.getPixels() == null || ru.orion.client.render.GleamNativeImages.isClosed(mask.getPixels())
                || maskLayout == null || maskLayout.pixelWidth() != layout.pixelWidth()
                || maskLayout.pixelHeight() != layout.pixelHeight();
        NativeImage pixels = recreate ? new NativeImage(layout.pixelWidth(), layout.pixelHeight(), true) : mask.getPixels();
        if (!recreate) pixels.fillRect(0, 0, layout.pixelWidth(), layout.pixelHeight(), 0);
        float sx = layout.pixelWidth() / layout.designWidth(), sy = layout.pixelHeight() / layout.designHeight();
        float framebuffer = layout.framebufferPerDesign();
        float bevel = BEVEL * layout.scale();
        for (Panel panel : layout.panels()) {
            bevel = Math.min(bevel, bevelPixels(Math.min(panel.width(), panel.height()) / 2 * framebuffer, layout.scale()));
        }
        bevel = Math.max(bevel, 1F);
        float bevelDesign = bevel / framebuffer;
        for (Panel panel : layout.panels()) {
            float halfWidth = panel.width() / 2, halfHeight = panel.height() / 2;
            float halfMin = Math.min(halfWidth, halfHeight);
            float radius = Math.min(panel.arc() / 2, halfMin);
            // Радиус нормали не меньше скоса: так внутри скоса нет диагонального шва.
            float normalRadius = Math.min(Math.max(radius, bevelDesign), halfMin);
            int left = Math.max(0, (int) Math.floor(panel.x() * sx));
            int top = Math.max(0, (int) Math.floor(panel.y() * sy));
            int right = Math.min(layout.pixelWidth(), (int) Math.ceil((panel.x() + panel.width()) * sx));
            int bottom = Math.min(layout.pixelHeight(), (int) Math.ceil((panel.y() + panel.height()) * sy));
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                float px = (x + .5F) / sx - panel.x() - halfWidth;
                float py = (y + .5F) / sy - panel.y() - halfHeight;
                float dx = Math.abs(px) - halfWidth + radius, dy = Math.abs(py) - halfHeight + radius;
                float distance = (float) Math.hypot(Math.max(0, dx), Math.max(0, dy)) + Math.min(0, Math.max(dx, dy)) - radius;
                int alpha = Math.round(255 * Math.clamp(.5F - distance * Math.min(sx, sy), 0, 1));
                if (alpha == 0) continue;
                float nx0 = Math.abs(px) - halfWidth + normalRadius, ny0 = Math.abs(py) - halfHeight + normalRadius;
                float corner = (float) Math.hypot(Math.max(0, nx0), Math.max(0, ny0));
                float nx = corner > 0 ? Math.max(0, nx0) / corner : nx0 > ny0 ? 1 : 0;
                float ny = corner > 0 ? Math.max(0, ny0) / corner : nx0 > ny0 ? 0 : 1;
                int normalX = Math.round(127.5F * (1 + Math.copySign(nx, px)));
                int normalY = Math.round(127.5F * (1 + Math.copySign(ny, py)));
                int edge = Math.round(255 * Math.clamp(-distance * framebuffer / EDGE_RANGE, 0, 1));
                pixels.setPixel(x, y, alpha << 24 | edge << 16 | normalX << 8 | normalY);
            }
        }
        if (recreate) {
            if (mask != null) mask.close();
            mask = new DynamicTexture(() -> id + "_glass_mask", pixels);
        } else {
            mask.upload();
        }
        masks.put(id, new MaskTexture(layout, mask, bevel));
    }

    void submitBackground(Identifier id, GuiGraphicsExtractor context, int x, int y, int width, int height,
                          int designWidth, int designHeight, List<Panel> panels) {
        submitBackground(id, context, x, y, width, height, designWidth, designHeight, panels, false);
    }

    public boolean submitBackground(Identifier id, GuiGraphicsExtractor context, int x, int y, int width, int height,
                                 int designWidth, int designHeight, List<Panel> panels, boolean screenGlass) {
        float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        var transform = context.pose();
        float scaleX = guiScale * (float) Math.hypot(transform.m00(), transform.m01());
        float scaleY = guiScale * (float) Math.hypot(transform.m10(), transform.m11());
        // Разрешение маски ограничено, а расстояния в ней хранятся в настоящих пикселях экрана.
        float maskX = Math.clamp(scaleX, .25F, 2F), maskY = Math.clamp(scaleY, .25F, 2F);
        float framebuffer = width * scaleX / Math.max(1, designWidth);
        updateMask(id, new MaskLayout(Math.max(1, Math.round(width * maskX)),
                Math.max(1, Math.round(height * maskY)), designWidth, designHeight, panels,
                framebuffer, Math.max(.25F, Math.min(scaleX, scaleY))));
        if (transparent == null) {
            NativeImage pixels = new NativeImage(1, 1, true);
            pixels.setPixel(0, 0, 0);
            transparent = new DynamicTexture(() -> "orion_glass_transparent", pixels);
        }
        boolean drawn = submit(id, context, transparent, x, y, width, height, screenGlass);
        if (drawn) context.nextStratum();
        return drawn;
    }

    public boolean submit(Identifier id, GuiGraphicsExtractor context, DynamicTexture foreground,
                          int x, int y, int width, int height) {
        return submit(id,context,foreground,x,y,width,height,false);
    }

    public boolean submit(Identifier id, GuiGraphicsExtractor context, DynamicTexture foreground,
                          int x, int y, int width, int height, boolean screenGlass) {
        Minecraft client = Minecraft.getInstance();
        MaskTexture entry = masks.get(id);
        if (entry == null || (!screenGlass && !visibleOnHud(context, client))) {
            return false;
        }
        if (!screenGlass && !OrionClient.getInstance().getConfigManager().getConfig().isHudElementGlass(id.getPath())) {
            try {
                if (ru.orion.client.render.GleamGpu.pipelineOrNull(PIPELINE) == null) return false;
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
        if (!glassPipelinesReady(PIPELINE) || !requestScene(client)) return false;

        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        TextureSetup textures = new TextureSetup(foreground.getTextureView(), sceneBlur.output(), entry.texture().getTextureView(),
                sampler, sampler, sampler);
        ScreenRectangle scissorArea = new ScreenRectangle(0, 0, context.guiWidth(), context.guiHeight());
        // Цвет вершины передаёт шейдеру ориентацию framebuffer, масштаб и ширину скоса.
        int scale = Math.clamp(Math.round(entry.layout().scale() * 32F), 1, 255);
        int bevel = Math.clamp(Math.round(entry.bevel() * 4F), 4, 255);
        int parameters = 0xff000000 | (bottomOrigin ? 0xff0000 : 0) | scale << 8 | bevel;
        ((GuiGraphicsExtractorAccessor) (Object) context).orion$getGuiRenderState()
                .addGuiElement(new BlitRenderState(PIPELINE, textures,
                new Matrix3x2f(context.pose()), x, y, x + width, y + height,
                0.0F, 1.0F, 0.0F, 1.0F, parameters, scissorArea));
        return true;
    }

    private static boolean visibleOnHud(GuiGraphicsExtractor context, Minecraft client) {
        var screen = GleamMinecraft.screen(client);
        return (screen == null || screen instanceof ChatScreen
                || screen instanceof ru.orion.client.gui.OrionClickGuiScreen || OrionHudEditorScreen.editing(client))
                //? if >=26.2 {
                && !((GuiGraphicsExtractorAccessor) (Object) context).orion$getGuiRenderState().isHudHidden;
                //?} else {
                /*&& !client.options.hideGui;
                *///?}
    }

    /** Размытый мир для стеклянной поверхности Lumen; null — использовать обычную заливку. */
    public TextureSetup captureSurface(GuiGraphicsExtractor context, Identifier id, boolean screenGlass) {
        Minecraft client = Minecraft.getInstance();
        if (!screenGlass && (!visibleOnHud(context, client)
                || !OrionClient.getInstance().getConfigManager().getConfig().isHudElementGlass(id.getPath()))) return null;
        if (!glassPipelinesReady(null) || !requestScene(client)) return null;
        return TextureSetup.singleTexture(sceneBlur.output(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }

    /** Размытая статичная панорама главного меню; до первого размытия возвращает null. */
    public TextureSetup blurredBackdrop(GpuTextureView source) {
        if (source == null || source.isClosed() || !glassPipelinesReady(null)) return null;
        GpuTexture texture = source.texture();
        if (texture != titleSource || !titleBlur.matches(texture)) {
            if (titleRequested) return null;
            titleBlur.ensure(texture);
            titleSource = texture;
            titleReady = false;
        }
        if (!titleReady) {
            titleRequested = true;
            return null;
        }
        return TextureSetup.singleTexture(titleBlur.output(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }

    public boolean bottomOrigin() {
        return bottomOrigin;
    }

    private boolean glassPipelinesReady(RenderPipeline surface) {
        try {
            if ((surface != null && ru.orion.client.render.GleamGpu.pipelineOrNull(surface) == null)
                    || ru.orion.client.render.GleamGpu.pipelineOrNull(BLUR_DOWN) == null
                    || ru.orion.client.render.GleamGpu.pipelineOrNull(BLUR_UP) == null) {
                warnShaderUnavailable(null);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            warnShaderUnavailable(exception);
            return false;
        }
    }

    private boolean requestScene(Minecraft client) {
        GpuTexture source = GleamMinecraft.mainTarget(client).getColorTexture();
        if (source == null || source.isClosed() || source.getWidth(0) <= 0 || source.getHeight(0) <= 0
                || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) return false;
        if (scene == null || scene.isClosed() || sceneView == null || sceneView.isClosed()
                || scene.getWidth(0) != source.getWidth(0)
                || scene.getHeight(0) != source.getHeight(0) || scene.getFormat() != source.getFormat()
                || !sceneBlur.matches(source)) {
            // Уже собранные GUI-состояния продолжают использовать прежние текстуры до отрисовки.
            if (captureRequested) {
                return false;
            }
            releaseScene();
            scene = RenderSystem.getDevice().createTexture("orion_glass_scene",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    source.getFormat(), source.getWidth(0), source.getHeight(0), 1, 1);
            sceneView = RenderSystem.getDevice().createTextureView(scene);
            sceneBlur.ensure(source);
            //? if >=26.2 {
            bottomOrigin = RenderSystem.getDevice().getDeviceInfo().backendName()
                    .toLowerCase(java.util.Locale.ROOT).contains("opengl");
            //?} else {
            /*bottomOrigin = true;
            *///?}
        }
        captureRequested = true;
        return sceneCaptured;
    }

    private void warnShaderUnavailable(RuntimeException exception) {
        if (!shaderWarningLogged) {
            LOGGER.warn("HUD glass shader unavailable; using the transparent HUD fallback", exception);
            shaderWarningLogged = true;
        }
    }

    /** Копирует и размывает актуальный кадр непосредственно перед отрисовкой GUI. */
    public void captureBeforeGui(GpuTexture source) {
        if (titleRequested) {
            titleRequested = false;
            if (titleSource != null && !titleSource.isClosed() && titleBlur.matches(titleSource)) {
                titleReady = blur(RenderSystem.getDevice().createTextureView(titleSource), titleBlur, true);
            }
        }
        if (!captureRequested || source != GleamMinecraft.mainTarget(Minecraft.getInstance()).getColorTexture()) return;
        captureRequested = false;
        // Между extraction и render framebuffer может быть пересоздан при resize или смене мира.
        // В этом кадре сохраняем прежний фон, не закрывая текстуры уже собранных GUI-состояний.
        if (source == null || source.isClosed() || scene == null || scene.isClosed()
                || sceneView == null || sceneView.isClosed()
                || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0
                || source.getWidth(0) != scene.getWidth(0)
                || source.getHeight(0) != scene.getHeight(0)
                || source.getFormat() != scene.getFormat()
                || !sceneBlur.matches(source)) {
            sceneCaptured = false;
            return;
        }
        // Копия всего кадра стоит доли миллисекунды и не отстаёт от камеры.
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(source, scene, 0, 0, 0, 0, 0, scene.getWidth(0), scene.getHeight(0));
        //? if >=26.2 {
        encoder.submit();
        //?}
        sceneCaptured = blur(sceneView, sceneBlur, false);
    }

    /** Dual Kawase: три уменьшения и два увеличения до половины разрешения. */
    private boolean blur(GpuTextureView source, BlurChain chain, boolean closeSource) {
        try {
            var down = ru.orion.client.render.GleamGpu.pipelineOrNull(BLUR_DOWN);
            var up = ru.orion.client.render.GleamGpu.pipelineOrNull(BLUR_UP);
            if (down == null || up == null || !chain.ready()) return false;
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuTextureView input = source;
            for (int level = 0; level < BlurChain.LEVELS; level++) {
                var pipeline = level < BlurChain.DOWN_LEVELS ? down : up;
                GpuTextureView target = chain.views[level];
                //? if >=26.2 {
                try (var pass = encoder.createRenderPass(() -> "GleamClient glass blur", target, Optional.empty())) {
                //?} else {
                /*try (var pass = encoder.createRenderPass(() -> "GleamClient glass blur", target, java.util.OptionalInt.empty())) {
                *///?}
                    pass.setPipeline(pipeline);
                    //? if >=26.3 {
                    pass.setUniform("Sampler0", input, sampler);
                    //?} else {
                    /*pass.bindTexture("Sampler0", input, sampler);
                    *///?}
                    pass.disableScissor();
                    //? if >=26.2 {
                    pass.draw(3, 1, 0, 0);
                    //?} else {
                    /*pass.draw(0, 3);
                    *///?}
                }
                input = target;
            }
            //? if >=26.2 {
            encoder.submit();
            //?}
            return true;
        } catch (RuntimeException exception) {
            warnShaderUnavailable(exception);
            return false;
        } finally {
            if (closeSource) source.close();
        }
    }

    public void close() {
        if (transparent != null) transparent.close();
        transparent = null;
        captureRequested = false;
        releaseScene();
        titleBlur.close();
        titleSource = null;
        titleRequested = false;
        titleReady = false;
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
        if (sceneView != null) {
            sceneView.close();
            sceneView = null;
        }
        if (scene != null) {
            scene.close();
            scene = null;
        }
        sceneBlur.close();
    }

    public record Panel(float x, float y, float width, float height, float arc) {
    }

    /**
     * Текстуры размытия: 1/2, 1/4, 1/8 при уменьшении и 1/4, 1/2 при увеличении.
     * Результат — последняя, в половинном разрешении источника.
     */
    private static final class BlurChain {
        static final int DOWN_LEVELS = 3;
        static final int LEVELS = DOWN_LEVELS * 2 - 1;
        private final String label;
        private final GpuTexture[] textures = new GpuTexture[LEVELS];
        private final GpuTextureView[] views = new GpuTextureView[LEVELS];
        private int sourceWidth, sourceHeight;
        private Object format;

        BlurChain(String label) {
            this.label = label;
        }

        boolean ready() {
            for (int i = 0; i < LEVELS; i++) {
                if (textures[i] == null || textures[i].isClosed() || views[i] == null || views[i].isClosed()) return false;
            }
            return true;
        }

        boolean matches(GpuTexture source) {
            return ready() && sourceWidth == source.getWidth(0) && sourceHeight == source.getHeight(0)
                    && format == source.getFormat();
        }

        void ensure(GpuTexture source) {
            if (matches(source)) return;
            close();
            sourceWidth = source.getWidth(0);
            sourceHeight = source.getHeight(0);
            format = source.getFormat();
            int width = sourceWidth, height = sourceHeight;
            int[] widths = new int[LEVELS], heights = new int[LEVELS];
            for (int i = 0; i < DOWN_LEVELS; i++) {
                width = Math.max(1, (width + 1) / 2);
                height = Math.max(1, (height + 1) / 2);
                widths[i] = width;
                heights[i] = height;
            }
            for (int i = DOWN_LEVELS; i < LEVELS; i++) {
                width *= 2;
                height *= 2;
                widths[i] = width;
                heights[i] = height;
            }
            for (int i = 0; i < LEVELS; i++) {
                textures[i] = RenderSystem.getDevice().createTexture("orion_glass_blur_" + label + "_" + i,
                        GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                        source.getFormat(), widths[i], heights[i], 1, 1);
                views[i] = RenderSystem.getDevice().createTextureView(textures[i]);
            }
        }

        GpuTextureView output() {
            return views[LEVELS - 1];
        }

        void close() {
            for (int i = 0; i < LEVELS; i++) {
                if (views[i] != null) views[i].close();
                if (textures[i] != null) textures[i].close();
                views[i] = null;
                textures[i] = null;
            }
            sourceWidth = sourceHeight = 0;
            format = null;
        }
    }

    /**
     * framebufferPerDesign — пикселей экрана на единицу макета, scale — пикселей экрана на GUI-пиксель.
     * Расстояния в маске хранятся в пикселях экрана, поэтому не зависят от её разрешения.
     */
    public record MaskLayout(int pixelWidth, int pixelHeight, float designWidth, float designHeight,
                             List<Panel> panels, float framebufferPerDesign, float scale) {
        public MaskLayout {
            panels = List.copyOf(panels);
        }

        public MaskLayout(int pixelWidth, int pixelHeight, float designWidth, float designHeight, List<Panel> panels) {
            this(pixelWidth, pixelHeight, designWidth, designHeight, panels,
                    pixelWidth / designWidth, Math.max(.25F, pixelWidth / designWidth));
        }
    }

    private record MaskTexture(MaskLayout layout, DynamicTexture texture, float bevel) {
    }
}
