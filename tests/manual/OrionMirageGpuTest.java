package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDLInit;
import ru.orion.client.config.OrionConfig;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Проверяет настоящее отражение стены в полу на GPU, а не только компиляцию GLSL. */
public final class OrionMirageGpuTest {
    private static final int W = 320, H = 192;
    private static final class Sources implements ShaderSource {
        public String getShader(Identifier id, ShaderType type) {
            String path = "assets/" + id.getNamespace() + "/shaders/" + id.getPath() + (type == ShaderType.VERTEX ? ".vsh" : ".fsh");
            try {
                var local = Path.of("src/main/resources", path);
                if (Files.exists(local)) return Files.readString(local);
                try (var in = getClass().getClassLoader().getResourceAsStream(path)) { return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        public CachedIncludeSource getInclude(Identifier id) { throw new IllegalArgumentException(id.toString()); }
        public void close() { }
    }
    private static ByteBuffer read(GpuTexture texture) {
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        ByteBuffer data = BufferUtils.createByteBuffer(texture.getWidth(0) * texture.getHeight(0) * 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((GlTexture) texture).glId());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, old);
        if (GL11.glGetError() != 0) throw new AssertionError("GPU readback failed");
        return data;
    }
    private static void save(ByteBuffer data, Path path) throws Exception {
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = (y * W + x) * 4;
            image.setRGB(x, H - 1 - y, (data.get(i + 3) & 255) << 24 | (data.get(i) & 255) << 16 | (data.get(i + 1) & 255) << 8 | data.get(i + 2) & 255);
        }
        ImageIO.write(image, "png", path.toFile());
    }
    public static void main(String[] args) throws Exception {
        int quality = Integer.getInteger("orion.mirage.test.quality", 2);
        float ratio = quality == 0 ? .5F : quality == 1 ? .65F : .75F;
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) throw new IllegalStateException("SDL unavailable");
        var backend = new GlBackend(); backend.loadLibrary(); RenderSystem.initRenderThread();
        var device = backend.createDevice(new GpuDebugOptions(3, true, true, true));
        try {
            RenderSystem.initRenderer(device);
            var source = new Sources();
            var reflection = device.compilePipeline(OrionMirage.createPipeline(false), source, Runnable::run).join().finishCompile();
            var composite = device.compilePipeline(OrionMirage.createPipeline(true), source, Runnable::run).join().finishCompile();
            if (reflection == null || composite == null) throw new AssertionError("Mirage pipelines failed to compile");
            int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC;
            try (var color = device.createTexture("test world", usage, GpuFormat.RGBA8_UNORM, W, H, 1, 1);
                 var colorView = device.createTextureView(color);
                 var depth = device.createTexture("test depth", usage, GpuFormat.D32_FLOAT, W, H, 1, 1);
                 var depthView = device.createTextureView(depth);
                 var reflected = device.createTexture("test reflections", usage, GpuFormat.RGBA8_UNORM, Math.round(W * ratio), Math.round(H * ratio), 1, 1);
                 var reflectedView = device.createTextureView(reflected);
                 var output = device.createTexture("test output", usage, GpuFormat.RGBA8_UNORM, W, H, 1, 1);
                 var outputView = device.createTextureView(output);
                 var pixels = new NativeImage(W, H, false)) {
                var projection = new Matrix4f().perspective((float) Math.toRadians(70), W / (float) H, 100F, .1F, true);
                var inverseProjection = new Matrix4f(projection).invert();
                var camera = new Vector3f(0, 2.2F, 4);
                var view = new Matrix4f().lookAt(camera, new Vector3f(0, .5F, -5), new Vector3f(0, 1, 0));
                var inverseView = new Matrix4f(view).invert();
                FloatBuffer depths = BufferUtils.createFloatBuffer(W * H);
                boolean[] ground = new boolean[W * H];
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                    var rayPoint = inverseProjection.transform(new Vector4f((x + .5F) / W * 2 - 1, (y + .5F) / H * 2 - 1, 1, 1));
                    var direction = new Vector3f(rayPoint.x / rayPoint.w, rayPoint.y / rayPoint.w, rayPoint.z / rayPoint.w).normalize();
                    inverseView.transformDirection(direction);
                    float t = 10000; boolean floor = false; int rgb = 0xFF80B7DF;
                    if (direction.y < -.001F) { t = -camera.y / direction.y; floor = true; }
                    if (direction.z < -.001F) {
                        float wallT = (-8 - camera.z) / direction.z;
                        var hit = new Vector3f(direction).mul(wallT).add(camera);
                        if (wallT < t && Math.abs(hit.x) < 5 && hit.y > 0 && hit.y < 5) {
                            t = wallT; floor = false;
                            rgb = ((int) Math.floor(hit.x + 5) & 1) == 0 ? 0xFFE94D37 : 0xFF3ACB83;
                        }
                    }
                    if (t < 90) {
                        var point = new Vector3f(direction).mul(t).add(camera);
                        if (floor) rgb = ((int) Math.floor(point.x) + (int) Math.floor(point.z) & 1) == 0 ? 0xFF52575B : 0xFF62686C;
                        var clip = projection.transform(view.transform(new Vector4f(point, 1)));
                        depths.put(clip.z / clip.w); ground[y * W + x] = floor;
                    } else depths.put(0);
                    pixels.setPixel(x, y, rgb);
                }
                depths.flip();
                var encoder = device.createCommandEncoder(); encoder.writeToTexture(color, pixels);
                int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((GlTexture) depth).glId());
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, W, H, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depths);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, old);
                if (GL11.glGetError() != 0) throw new AssertionError("Depth upload failed");
                ByteBuffer original = read(color); save(original, Path.of(args[0] + "-source.png"));
                var config = OrionConfig.createDefault(); config.setMirageQuality(quality);
                for (int mode = 0; mode < 4; mode++) {
                    config.setMirageStrength(mode == 1 ? .35F : 0F);
                    config.setMirageBloom(mode == 2 ? .18F : 0F); config.setMirageColorGrade(mode == 2 ? .38F : 0F);
                    if (mode == 3) { for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) pixels.setPixel(x, y, 0); encoder.writeToTexture(color, pixels); }
                    OrionMirage.drawPasses(encoder, reflection, composite, colorView, depthView, reflectedView, outputView, OrionMirage.parameters(projection, config, 1, true));
                    ByteBuffer result = read(output), reflectionPixels = read(reflected);
                    int maximum = 0, changedGround = 0, opaque = 0, hits = 0;
                    for (int i = 0; i < reflectionPixels.capacity() / 4; i++) if ((reflectionPixels.get(i * 4 + 3) & 255) > 0) hits++;
                    for (int i = 0; i < W * H; i++) {
                        int error = 0;
                        for (int c = 0; c < 3; c++) error = Math.max(error, Math.abs((original.get(i * 4 + c) & 255) - (result.get(i * 4 + c) & 255)));
                        maximum = Math.max(maximum, error);
                        if (ground[i] && error > 2) changedGround++;
                        if ((result.get(i * 4 + 3) & 255) > 0) opaque++;
                    }
                    save(result, Path.of(args[0] + "-mode-" + mode + ".png"));
                    System.out.println("mode=" + mode + " hits=" + hits + " groundChanged=" + changedGround + " maxError=" + maximum + " opaque=" + opaque);
                    if (mode == 0 && maximum > 1) throw new AssertionError("Disabled effects must preserve source");
                    if (mode == 1 && (hits < 50 || changedGround < 100)) throw new AssertionError("Wall reflection did not reach the floor");
                    if (mode == 2 && changedGround < 100) throw new AssertionError("Beauty grading not applied");
                    if (mode == 3 && opaque > 0) throw new AssertionError("Transparent target became opaque");
                }
            }
            reflection.close(); composite.close(); RenderSystem.shutdownRenderer();
        } finally { device.close(); backend.unloadLibrary(); SDLInit.SDL_Quit(); }
        System.out.println("Mirage GPU: depth reconstruction, floor reflections, color grading and transparency passed");
    }
}
