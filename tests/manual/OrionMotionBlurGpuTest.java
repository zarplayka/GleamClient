package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Полный GPU-проход на скрытом OpenGL-контексте, без запуска Minecraft. */
public final class OrionMotionBlurGpuTest {
    private static final int W = 160, H = 96;
    private static final class Sources implements ShaderSource {
        @Override public String getShader(Identifier id, ShaderType type) {
            String path = "assets/" + id.getNamespace() + "/shaders/" + id.getPath() + (type == ShaderType.VERTEX ? ".vsh" : ".fsh");
            try {
                Path local = Path.of("src/main/resources", path);
                String override = System.getProperty("orion.motion.test.fragment");
                if (type == ShaderType.FRAGMENT && override != null) return Files.readString(Path.of(override));
                if (Files.exists(local)) return Files.readString(local);
                try (var in = getClass().getClassLoader().getResourceAsStream(path)) {
                    return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public CachedIncludeSource getInclude(Identifier id) { throw new IllegalArgumentException("Unexpected include " + id); }
        @Override public void close() { }
    }
    private static ByteBuffer read(GpuTexture texture) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((GlTexture) texture).glId());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        if (GL11.glGetError() != 0) throw new AssertionError("OpenGL readback failed");
        return pixels;
    }
    private static void verifyHudCacheRedirect(Path modJar) throws Exception {
        // Проверяем реальную логику установленного мода без зависимости от него в проекте.
        var main = new com.mojang.blaze3d.pipeline.TextureTarget("world target", W, H, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        var hud = new com.mojang.blaze3d.pipeline.TextureTarget("HUD cache", W, H, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{modJar.toUri().toURL()}, OrionMotionBlurGpuTest.class.getClassLoader())) {
            Class<?> cache = Class.forName("zako.opt.hud.HudCache", true, loader);
            var mainField = cache.getDeclaredField("main"); mainField.setAccessible(true); mainField.set(null, main);
            var targetField = cache.getDeclaredField("target"); targetField.setAccessible(true); targetField.set(null, hud);
            var drawing = cache.getDeclaredField("drawing"); drawing.setAccessible(true);
            var redirect = cache.getMethod("redirect", com.mojang.blaze3d.pipeline.RenderTarget.class);
            drawing.setBoolean(null, true);
            if (redirect.invoke(null, main) != hud) throw new AssertionError("HUD cache redirect not reproduced");
            drawing.setBoolean(null, false);
            if (redirect.invoke(null, main) != null) throw new AssertionError("World pass is still redirected");
            mainField.set(null, null); targetField.set(null, null);
            System.out.println("ZakoOpt: confirmed world target redirects to HUD cache inside GuiRenderer, not before it");
        } finally { hud.destroyBuffers(); main.destroyBuffers(); }
    }

    public static void main(String[] args) throws Exception {
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) throw new IllegalStateException("SDL unavailable");
        var backend = new GlBackend(); backend.loadLibrary();
        RenderSystem.initRenderThread();
        var device = backend.createDevice(new GpuDebugOptions(3, true, true, true));
        try {
            RenderSystem.initRenderer(device);
            System.out.println("GPU: " + device.getDeviceInfo());
            var compiled = device.compilePipeline(OrionMotionBlur.createPipeline(), new Sources(), Runnable::run).join().finishCompile();
            if (compiled == null) throw new AssertionError("Production pipeline failed to compile");
            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
            try (var scene = device.createTexture("motion_test_world", usage, GpuFormat.RGBA8_UNORM, W, H, 1, 1);
                 var sceneView = device.createTextureView(scene);
                 var copy = device.createTexture("motion_test_copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, W, H, 1, 1);
                 var copyView = device.createTextureView(copy);
                 var image = new NativeImage(W, H, false)) {
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                    boolean checker = ((x / 6 + y / 6) & 1) == 0;
                    image.setPixel(x, y, checker ? 0xFFFF9C38 : 0xFF305EB8);
                }
                var encoder = device.createCommandEncoder();
                for (int mode = 0; mode < 3; mode++) {
                    if (mode == 2) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) image.setPixel(x, y, 0);
                    encoder.writeToTexture(scene, image);
                    ByteBuffer original = read(scene);
                    encoder.copyTextureToTexture(scene, copy, 0, 0, 0, 0, 0, W, H);
                    ByteBuffer copied = read(copy);
                    int copyError = 0;
                    for (int i = 0; i < W * H * 4; i++) copyError = Math.max(copyError, Math.abs((original.get(i) & 255) - (copied.get(i) & 255)));
                    if (copyError > 0) throw new AssertionError("Scene copy corrupted: " + copyError);
                    var uniforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), mode == 0 ? new Vector4f(0, 0, 0, 1) : new Vector4f(.65F, -.25F, .5F, 1));
                    try (var pass = encoder.createRenderPass(() -> "Motion blur GPU regression", sceneView, Optional.empty())) {
                        pass.setPipeline(compiled);
                        pass.setUniform("DynamicTransforms", uniforms);
                        pass.setUniform("Sampler0", copyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                        pass.disableScissor(); pass.draw(3, 1, 0, 0);
                    }
                    ByteBuffer output = read(scene);
                    int maxError = 0, changed = 0, opaque = 0; long brightness = 0;
                    BufferedImage preview = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                        int off = (y * W + x) * 4, rgb = 0;
                        if ((output.get(off + 3) & 255) > 0) opaque++;
                        for (int c = 0; c < 3; c++) {
                            int value = output.get(off + c) & 255;
                            brightness += value; rgb = rgb << 8 | value;
                            int error = Math.abs(value - (original.get(off + c) & 255));
                            maxError = Math.max(maxError, error); if (error > 3) changed++;
                        }
                        preview.setRGB(x, H - 1 - y, rgb);
                    }
                    double mean = brightness / (double) (W * H * 3);
                    ImageIO.write(preview, "png", Path.of(args[0] + "-" + mode + ".png").toFile());
                    System.out.println("mode=" + mode + " mean=" + mean + " maxError=" + maxError + " changed=" + changed + " opaquePixels=" + opaque);
                    if (mode < 2 && mean < 30) throw new AssertionError("Black frame reproduced");
                    if (mode == 2 && opaque > 0) throw new AssertionError("Transparent HUD cache became an opaque black overlay");
                    if (mode == 0 && maxError > 2) throw new AssertionError("Zero blur does not preserve source pixels");
                    if (mode == 1 && changed < W * H / 4) throw new AssertionError("Blur not applied");
                }
            }
            if (args.length > 1) verifyHudCacheRedirect(Path.of(args[1]));
            compiled.close();
            RenderSystem.shutdownRenderer();
        } finally { device.close(); backend.unloadLibrary(); SDLInit.SDL_Quit(); }
        System.out.println("Motion blur GPU test passed");
    }
}
