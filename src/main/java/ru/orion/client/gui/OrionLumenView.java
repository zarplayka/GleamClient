package ru.orion.client.gui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.paradox.lumen.LumenNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.RenderPipelinesAccessor;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import org.joml.Matrix3x2f;

/** Локальная страница Lumen и отрисовка её команд в интерфейсе Minecraft. */
final class OrionLumenView {
    private static final String DLL_SHA256 = "3649f329387a11e4870f95cec92fb17d1f9029d0fa5e866c371ceb486b9b73c1";
    private static final int ATLAS_SIZE = 1024;
    private static final int COMMAND_SIZE = 64;
    private static final long THEME_REVEAL_NANOS = 410_000_000L;
    private static final byte[] UPDATE = bytes("update");
    private static final byte[] OPENING = bytes("opening");

    private final String resource, texturePrefix, fontResource;
    private final Supplier<byte[]> pageSource;
    private final Map<Integer, DynamicTexture> atlases = new HashMap<>();
    private final Map<Integer, DynamicTexture> images = new HashMap<>();
    private ByteBuffer display, alpha;
    private int displayLength;
    private ThemeReveal themeReveal;
    private long engine, page, frameAt;
    private int viewportWidth = 160, viewportHeight = 104, atlasRevision = -1;
    private float pixelRatio = 1F;
    private boolean unavailable;

    OrionLumenView(String resource, String texturePrefix, String fontResource) {
        this(resource,texturePrefix,fontResource,null);
    }

    OrionLumenView(String resource, String texturePrefix, String fontResource, Supplier<byte[]> pageSource) {
        this.resource = "/assets/orion-client/html/" + resource;
        this.texturePrefix = texturePrefix;
        this.fontResource = fontResource;
        this.pageSource = pageSource;
    }

    boolean ready() {
        return !unavailable && (page != 0 || initialize());
    }

    private boolean initialize() {
        try (var html = OrionLumenView.class.getResourceAsStream(resource)) {
            String appData = System.getenv("APPDATA");
            String configured = System.getProperty("orion.lumen.path");
            if (html == null || configured == null && appData == null) return false;
            Path dll = configured == null
                    ? Path.of(appData, "paradoxclient/artifacts/26.3/ui/b330a04b9612e26f39964139365e2f13921cf30812db3dd2d7e143292d1fedfe/assets/native/windows-x86_64/lumen.dll")
                    : Path.of(configured).toAbsolutePath();
            if (!Files.isRegularFile(dll)) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dll));
            if (!DLL_SHA256.equals(HexFormat.of().formatHex(digest))) return false;
            System.load(dll.toString());
            engine = LumenNative.create();
            if (engine == 0) return false;
            byte[] font;
            if (fontResource == null) {
                font = Files.readAllBytes(Path.of(System.getenv().getOrDefault("WINDIR", "C:/Windows"), "Fonts/segoeui.ttf"));
            } else {
                try (var stream = OrionLumenView.class.getResourceAsStream(fontResource)) {
                    if (stream == null) return false;
                    font = stream.readAllBytes();
                }
            }
            if (!LumenNative.setFontData(engine, font)) return false;
            LumenNative.setViewport(engine, viewportWidth, viewportHeight);
            byte[] source = pageSource == null ? html.readAllBytes() : pageSource.get();
            page = LumenNative.pageOpen(engine, source, bytes(""));
            if (page == 0) return false;
            // Первый тик инициализирует скрипт до вызова window.paradox.update.
            LumenNative.pageTick(page, engine, 0F);
            return true;
        } catch (IOException | NoSuchAlgorithmException | RuntimeException | LinkageError failure) {
            return false;
        } finally {
            if (page == 0) {
                unavailable = true;
                dispose();
            }
        }
    }

    void dispose() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (int index : atlases.keySet()) textures.release(atlasId(index));
        atlases.clear();
        for (int id : images.keySet()) textures.release(imageId(id));
        images.clear();
        if (page != 0) LumenNative.pageClose(page);
        if (engine != 0) LumenNative.destroy(engine);
        page = engine = 0;
        displayLength = 0;
        themeReveal = null;
        atlasRevision = -1;
        pixelRatio = 1F;
        frameAt = 0;
    }

    void resize(int width, int height, float ratio) {
        if (pixelRatio != ratio) {
            pixelRatio = ratio;
            LumenNative.setPixelRatio(engine, ratio);
        }
        if (viewportWidth != width || viewportHeight != height) {
            themeReveal = null;
            displayLength = 0;
            viewportWidth = width;
            viewportHeight = height;
            LumenNative.setViewport(engine, width, height);
        }
    }

    void update(JsonObject state) {
        LumenNative.pageCall(page, engine, UPDATE, bytes(state.toString()));
    }

    void opening(float progress) {
        LumenNative.pageCall(page, engine, OPENING, bytes(Float.toString(progress)));
    }

    void frame() {
        long now = System.nanoTime();
        float dt = Math.clamp((now - frameAt) / 1_000_000_000F, 0F, .05F);
        frameAt = now;
        LumenNative.pageTick(page, engine, dt);
        LumenNative.frame(engine, dt);
        LumenNative.pageAfterFrame(page, engine);
    }

    void move(float x, float y) { LumenNative.pageMove(page, engine, x, y); }
    void press(float x, float y) { LumenNative.pageDown(page, engine, 0, x, y); }
    void release(float x, float y) { LumenNative.pageUp(page, engine, 0, x, y); }
    byte[] messages() { return LumenNative.pageMessages(page); }
    byte[] logs() { return LumenNative.pageLogs(page); }
    boolean wheel(float x,float y,float h,float v) { return LumenNative.pageWheel(page,engine,x,y,h,v); }
    boolean key(String key,String code,int modifiers,boolean pressed) {
        return LumenNative.pageKey(page,engine,bytes(key),bytes(code),modifiers,pressed);
    }
    boolean text(String text) { return LumenNative.pageText(page,engine,bytes(text)); }

    void revealTheme(float x, float y, float reach) {
        if (displayLength == 0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(reach) || reach <= 0) return;
        long now = System.nanoTime();
        ThemeReveal running = themeReveal;
        float from = 0, to = 1;
        if (running != null && !running.finished(now)) {
            x = running.x();
            y = running.y();
            reach = running.reach();
            from = running.progress(now);
            to = 1 - running.to();
        }
        ByteBuffer previous = ByteBuffer.allocate(displayLength).order(ByteOrder.LITTLE_ENDIAN);
        previous.put(0, display, 0, displayLength);
        themeReveal = new ThemeReveal(previous, x, y, reach, from, to, now);
    }

    boolean draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float sx, float sy) {
        displayLength = 0;
        int size = LumenNative.displaySize(engine);
        if (size < 16 || size > 1_048_576) return false;
        if (display == null || display.capacity() < size)
            display = ByteBuffer.allocateDirect(Math.max(8192, Integer.highestOneBit(size - 1) << 1)).order(ByteOrder.LITTLE_ENDIAN);
        display.clear();
        if (LumenNative.copyDisplay(engine, display) != size) return false;
        int count = display.getInt(0);
        int clips = display.getInt(4);
        long clipBase = 16L + COMMAND_SIZE * (long) count;
        if (count <= 0 || clips < 0 || clipBase + 16L * clips > size) return false;
        if (RenderSystem.getCompiledPipelineNullable(Paint.SHAPE) == null
                || RenderSystem.getCompiledPipelineNullable(Paint.TEXTURED) == null) return false;
        int revision = LumenNative.atlasRevision(engine);
        boolean refresh = revision != atlasRevision;
        atlasRevision = revision;
        if (refresh) for (int index : atlases.keySet()) uploadAtlas(index);
        displayLength = size;
        long now = System.nanoTime();
        ThemeReveal running = themeReveal;
        if (running == null || running.finished(now)) {
            themeReveal = null;
            drawDisplay(graphics, display, x, y, width, height, sx, sy, CircleClip.NONE);
        } else {
            float radius = Math.max(.0001F, running.reach() * running.progress(now) * sx);
            int centerX = Math.clamp(Math.round((x + running.x() * sx) * 4F), -32768, 32767);
            int centerY = Math.clamp(Math.round((y + running.y() * sy) * 4F), -32768, 32767);
            float signedRadius = running.to() == 1F ? radius : -radius;
            drawDisplay(graphics, running.previous(), x, y, width, height, sx, sy,
                    new CircleClip(centerX, centerY, -signedRadius));
            drawDisplay(graphics, display, x, y, width, height, sx, sy,
                    new CircleClip(centerX, centerY, signedRadius));
        }
        return true;
    }

    private void drawDisplay(GuiGraphicsExtractor graphics, ByteBuffer commands, int x, int y, int width, int height,
                             float sx, float sy, CircleClip circle) {
        int count = commands.getInt(0), clips = commands.getInt(4);
        int clipBase = 16 + COMMAND_SIZE * count;
        graphics.nextStratum();
        graphics.enableScissor(x, y, x + width, y + height);
        var pose = graphics.pose();
        pose.pushMatrix();
        int transforms = 0;
        try {
            pose.translate(x, y);
            pose.scale(sx, sy);
            Matrix3x2f base = new Matrix3x2f(pose);
            ScreenRectangle viewport = graphics.scissorStack.peek();
            for (int i = 0; i < count; i++) {
                int at = 16 + i * COMMAND_SIZE;
                int kind = commands.getInt(at);
                if (kind == 6 && transforms < 16) {
                    pose.pushMatrix();
                    pose.mul(new Matrix3x2f(commands.getFloat(at+4),commands.getFloat(at+8),commands.getFloat(at+12),
                            commands.getFloat(at+16),commands.getFloat(at+20),commands.getFloat(at+24)));
                    transforms++;
                    continue;
                }
                if (kind == 7) {
                    if (transforms > 0) { pose.popMatrix(); transforms--; }
                    continue;
                }

                float left = commands.getFloat(at + 4);
                float top = commands.getFloat(at + 8);
                float w = commands.getFloat(at + 12);
                float h = commands.getFloat(at + 16);
                int color = commands.getInt(at + 36);
                if (w <= 0 || h <= 0 || color >>> 24 == 0) continue;
                float radius = commands.getFloat(at + 40);
                float parameter = commands.getFloat(at + 44);
                TextureSetup texture = TextureSetup.noTexture();
                float u0 = 0, v0 = 0, u1 = 0, v1 = 0;
                int variant = 0;
                float padding;
                switch (kind) {
                    case 0, 1, 9 -> {
                        if (kind == 1) radius = 0;
                        u0 = u1 = parameter;
                        padding = 1F;
                    }
                    case 11 -> {
                        variant = commands.getInt(at + 56);
                        if (variant == 2) continue; // У меню только внешние тени.
                        u0 = u1 = Math.max(.5F, parameter);
                        padding = u0;
                    }
                    case 5, 8, 12 -> {
                        DynamicTexture pixels;
                        FilterMode filter = FilterMode.NEAREST;
                        if (kind == 5) {
                            int index = commands.getInt(at + 48) - 1;
                            if (index < 0 || index > 15 || !atlases.containsKey(index) && !uploadAtlas(index)) continue;
                            pixels = atlases.get(index);
                        } else {
                            int id = commands.getInt(at + 48) - 65536;
                            if (id < 0) continue;
                            pixels = images.get(id);
                            if (pixels == null) {
                                long sizeOfImage = LumenNative.imageSize(id);
                                int tw = (int)(sizeOfImage >>> 32), th = (int)sizeOfImage;
                                if (tw <= 0 || th <= 0 || tw > 2048 || th > 2048) continue;
                                pixels = uploadImage(id, tw, th);
                                if (pixels == null) continue;
                            }
                            variant = Math.round(parameter);
                            if (kind == 8 && commands.getInt(at + 60) == 0) filter = FilterMode.LINEAR;
                        }
                        texture = TextureSetup.singleTexture(pixels.getTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(filter));
                        u0 = commands.getFloat(at + 20);
                        v0 = commands.getFloat(at + 24);
                        u1 = commands.getFloat(at + 28);
                        v1 = commands.getFloat(at + 32);
                        padding = 0F;
                    }
                    default -> { continue; }
                }

                float x0 = left - padding, x1 = left + w + padding;
                float y0 = top - padding, y1 = top + h + padding;
                if (kind == 0 || kind == 9) {
                    // При разных радиусах ядро передаёт полную фигуру и границы её четверти в UV.
                    float qx0 = commands.getFloat(at + 20), qy0 = commands.getFloat(at + 24);
                    float qx1 = commands.getFloat(at + 28), qy1 = commands.getFloat(at + 32);
                    if (qx1 > qx0 && qy1 > qy0) {
                        if (qx0 > left) x0 = qx0;
                        if (qy0 > top) y0 = qy0;
                        if (qx1 < left + w) x1 = qx1;
                        if (qy1 < top + h) y1 = qy1;
                    }
                }
                ScreenRectangle scissor = viewport;
                int clip = commands.getInt(at + 52);
                if (clip > 0 && clip < clips) {
                    int rect = (int)clipBase + clip * 16;
                    float clipX = commands.getFloat(rect), clipY = commands.getFloat(rect + 4);
                    float clipW = commands.getFloat(rect + 8), clipH = commands.getFloat(rect + 12);
                    if (clipW <= 0 || clipH <= 0) continue;
                    // Обрезаем в CSS-координатах до округления Minecraft к guiScale.
                    if (pose.m01() == 0F && pose.m10() == 0F && pose.m00() > 0F && pose.m11() > 0F) {
                        x0 = Math.max(x0, (base.m00() * clipX + base.m20() - pose.m20()) / pose.m00());
                        y0 = Math.max(y0, (base.m11() * clipY + base.m21() - pose.m21()) / pose.m11());
                        x1 = Math.min(x1, (base.m00() * (clipX + clipW) + base.m20() - pose.m20()) / pose.m00());
                        y1 = Math.min(y1, (base.m11() * (clipY + clipH) + base.m21() - pose.m21()) / pose.m11());
                    } else {
                        scissor = viewport.intersection(bounds(base, clipX, clipY, clipX + clipW, clipY + clipH));
                        if (scissor == null) continue;
                    }
                }
                if (x1 <= x0 || y1 <= y0) continue;
                var bounds = scissor.intersection(bounds(pose, x0, y0, x1, y1));
                if (bounds == null) continue;
                RenderPipeline pipeline = kind == 5 || kind == 8 || kind == 12 ? Paint.TEXTURED : Paint.SHAPE;
                graphics.guiRenderState.addGuiElement(new Paint(pipeline, texture, scissor, bounds, new Matrix3x2f(pose),
                        x0, y0, x1, y1, left, top, w, h, u0, v0, u1, v1, color, radius, kind, variant, circle));
            }
        } finally {
            while (transforms-- > 0) pose.popMatrix();
            pose.popMatrix();
            graphics.disableScissor();
        }
    }

    private DynamicTexture uploadImage(int id,int width,int height) {
        ByteBuffer rgba = ByteBuffer.allocateDirect(width*height*4);
        if (LumenNative.copyImage(id,rgba) != rgba.capacity()) return null;
        var pixels = new NativeImage(width,height,false);
        for (int i = 0; i < width*height; i++) {
            int at = i*4;
            pixels.setPixel(i%width,i/width,(rgba.get(at+3)&255)<<24 | (rgba.get(at)&255)<<16
                    | (rgba.get(at+1)&255)<<8 | (rgba.get(at+2)&255));
        }
        Identifier key = imageId(id);
        var texture = new DynamicTexture(key::getPath, pixels);
        Minecraft.getInstance().getTextureManager().register(key, texture);
        images.put(id, texture);
        return texture;
    }

    private Identifier imageId(int id) {
        return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, texturePrefix + "_image_" + id);
    }

    private static ScreenRectangle bounds(Matrix3x2f pose, float x0, float y0, float x1, float y1) {
        if (pose.m01() == 0F && pose.m10() == 0F) {
            float ax = pose.m00() * x0 + pose.m20(), bx = pose.m00() * x1 + pose.m20();
            float ay = pose.m11() * y0 + pose.m21(), by = pose.m11() * y1 + pose.m21();
            int x = (int)Math.floor(Math.min(ax, bx)), y = (int)Math.floor(Math.min(ay, by));
            return new ScreenRectangle(x, y, (int)Math.ceil(Math.max(ax, bx)) - x,
                    (int)Math.ceil(Math.max(ay, by)) - y);
        }
        int x = (int)Math.floor(x0), y = (int)Math.floor(y0);
        return new ScreenRectangle(x, y, (int)Math.ceil(x1) - x, (int)Math.ceil(y1) - y).transformMaxBounds(pose);
    }

    private record ThemeReveal(ByteBuffer previous, float x, float y, float reach, float from, float to, long startedAt) {
        private float phase(long now) {
            double duration = Math.max(1, THEME_REVEAL_NANOS * Math.abs(to - from));
            return Math.clamp((float)((now - startedAt) / duration), 0F, 1F);
        }

        boolean finished(long now) { return phase(now) >= 1F; }

        float progress(long now) {
            // Та же кривая cubic-bezier(.2,.7,.2,1), что и в веб-макете.
            float phase = phase(now);
            if (phase <= 0F || phase >= 1F) return phase <= 0F ? from : to;
            float low = 0F, high = 1F;
            for (int i = 0; i < 14; i++) {
                float t = (low + high) / 2F;
                if (bezier(t, .2F, .2F) < phase) low = t;
                else high = t;
            }
            return from + (to - from) * bezier((low + high) / 2F, .7F, 1F);
        }

        private static float bezier(float t, float a, float b) {
            float inv = 1F - t;
            return 3F * inv * inv * t * a + 3F * inv * t * t * b + t * t * t;
        }
    }

    // Центр хранится в четвертях GUI-пикселя. Знак радиуса выбирает сторону круга.
    private record CircleClip(int x, int y, float signedRadius) {
        private static final CircleClip NONE = new CircleClip(0, 0, 0);
    }

    private record Paint(RenderPipeline pipeline, TextureSetup textureSetup, ScreenRectangle scissorArea,
                         ScreenRectangle bounds, Matrix3x2f pose, float x0, float y0, float x1, float y1,
                         float left, float top, float width, float height, float u0, float v0, float u1, float v1,
                         int color, float radius, int kind, int variant, CircleClip circle) implements GuiElementRenderState {
        private static final Identifier SHADER = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "core/lumen_paint");
        private static final VertexFormat FORMAT = VertexFormat.builder(0)
                .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                .addAttribute("Color", GpuFormat.RGBA8_UNORM)
                .addAttribute("UV0", GpuFormat.RG32_FLOAT)
                .addAttribute("UV1", GpuFormat.RG16_SINT)
                .addAttribute("UV2", GpuFormat.RG16_SINT)
                .addAttribute("UV3", GpuFormat.RG32_FLOAT)
                .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
                .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
                .build();
        private static final RenderPipeline SHAPE = pipeline("lumen_shape", false);
        private static final RenderPipeline TEXTURED = pipeline("lumen_texture", true);

        private static RenderPipeline pipeline(String name, boolean textured) {
            var builder = RenderPipeline.builder(RenderPipelinesAccessor.orion$getGuiSnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pipeline/" + name))
                    .withVertexShader(SHADER).withFragmentShader(SHADER)
                    .withVertexBinding(0, FORMAT);
            if (textured) builder.withShaderDefine("LUMEN_TEXTURED").withBindGroupLayout(BindGroupLayouts.SAMPLER0);
            return RenderPipelinesAccessor.orion$registerOptional(builder.build());
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            vertex(vertices, x0, y0);
            vertex(vertices, x0, y1);
            vertex(vertices, x1, y1);
            vertex(vertices, x1, y0);
        }

        private void vertex(VertexConsumer vertices, float x, float y) {
            float dx = x - left, dy = y - top;
            // В Normal передаём тип, вариант и радиус скругления с шагом 1/16.
            int flags = kind + 16 * Math.clamp(variant, 0, 4);
            int corner = Math.clamp(Math.round(radius * 16F), 0, 8191);
            vertices.addVertexWith2DPose(pose, x, y).setColor(color)
                    .setUv(u0 + (u1 - u0) * dx / width, v0 + (v1 - v0) * dy / height)
                    .setUv1(circle.x(), circle.y())
                    .setUv2(Math.min(32767, Math.round(width * 4F)), Math.min(32767, Math.round(height * 4F)))
                    .setUv3(dx - width / 2F, dy - height / 2F)
                    .setNormal(normalByte(flags), normalByte(corner / 64), normalByte(corner % 64))
                    .setLineWidth(circle.signedRadius());
        }

        private static float normalByte(int value) {
            return Math.min(1F, (value + .25F) / 127F);
        }
    }

    static DynamicTexture registerTexture(Identifier id, BufferedImage image) {
        var pixels = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int py = 0; py < image.getHeight(); py++)
            for (int px = 0; px < image.getWidth(); px++) pixels.setPixel(px, py, image.getRGB(px, py));
        var texture = new DynamicTexture(id::getPath, pixels);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return texture;
    }

    private boolean uploadAtlas(int index) {
        if (alpha == null) alpha = ByteBuffer.allocateDirect(ATLAS_SIZE * ATLAS_SIZE);
        alpha.clear();
        if (LumenNative.copyAtlas(engine, index, alpha) != alpha.capacity()) return false;
        DynamicTexture texture = atlases.get(index);
        NativeImage pixels = texture == null ? new NativeImage(ATLAS_SIZE, ATLAS_SIZE, false) : texture.getPixels();
        for (int i = 0; i < alpha.capacity(); i++)
            pixels.setPixel(i % ATLAS_SIZE, i / ATLAS_SIZE, ((alpha.get(i) & 255) << 24) | 0xFFFFFF);
        if (texture == null) {
            texture = new DynamicTexture(() -> "GleamClient Lumen glyph atlas", pixels);
            Minecraft.getInstance().getTextureManager().register(atlasId(index), texture);
            atlases.put(index, texture);
        } else texture.upload();
        return true;
    }

    private Identifier atlasId(int index) {
        return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, texturePrefix + "_atlas_" + index);
    }
    static String hex(Color color) { return "#" + HexFormat.of().toHexDigits(color.getRGB()).substring(2); }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
