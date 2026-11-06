package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import ru.orion.client.OrionClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Объёмные пиксели внешнего слоя через штатный пайплайн модели игрока. */
public final class OrionSkinLayers {
    public static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("skinlayers3d");
    private static final int MAX_SKINS = 64;
    private static final Map<Identifier, Skin> SKINS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<ModelPart, Mesh> PARTS = Collections.synchronizedMap(new WeakHashMap<>());

    private record Point(float x, float y, float z) {
        Point move(Vector3fc normal, float amount) {
            return new Point(x + normal.x() * amount, y + normal.y() * amount, z + normal.z() * amount);
        }
    }
    private record Face(Point a, Point b, Point c, Point d, float u, float v, Vector3fc normal) {}
    private record Mesh(List<Face> faces) {}
    private static final class Skin {
        final int[] pixels;
        final Map<ModelPart, Mesh> meshes = new WeakHashMap<>();
        float thickness = -1;
        Skin(int[] pixels) { this.pixels = pixels; }
    }

    private OrionSkinLayers() {}

    public static void capture(Identifier id, NativeImage image) {
        if (EXTERNAL || image == null || image.isClosed() || image.getWidth() != 64 || image.getHeight() != 64) return;
        int[] pixels = new int[4096];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) pixels[y * 64 + x] = image.getPixel(x, y);
        synchronized (SKINS) {
            SKINS.put(id, new Skin(pixels));
            while (SKINS.size() > MAX_SKINS) SKINS.remove(SKINS.keySet().iterator().next());
        }
    }

    public static void release(Identifier id) { SKINS.remove(id); }

    public static void prepare(PlayerModel model, AvatarRenderState state) {
        var config = OrionClient.getConfigOrNull();
        boolean active = config != null && config.isSkinLayersEnabled() && !EXTERNAL && state.skin != null
                && !state.isSpectator && state.distanceToCameraSq <= config.getSkinLayersDistance() * config.getSkinLayersDistance();
        prepare(model, active ? state.skin.body().texturePath() : null);
    }

    public static void prepareHand(PlayerModel model, Identifier texture) {
        var config = OrionClient.getConfigOrNull();
        prepare(model, config != null && config.isSkinLayersEnabled() && config.isSkinLayersHands() && !EXTERNAL ? texture : null);
    }

    private static void prepare(PlayerModel model, Identifier texture) {
        var config = OrionClient.getConfigOrNull();
        Skin skin = texture == null ? null : SKINS.get(texture);
        if (texture != null && skin == null) {
            var loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (loaded instanceof DynamicTexture dynamic) {
                capture(texture, dynamic.getPixels());
                skin = SKINS.get(texture);
            }
        }
        ModelPart[] parts = {model.hat, model.jacket, model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants};
        for (ModelPart part : parts) {
            if (skin == null || config == null) { PARTS.remove(part); continue; }
            float thickness = config.getSkinLayersThickness();
            if (skin.thickness != thickness) { skin.meshes.clear(); skin.thickness = thickness; }
            Skin current = skin;
            PARTS.put(part, skin.meshes.computeIfAbsent(part, key -> build(key, current.pixels, thickness)));
        }
    }

    static Mesh build(ModelPart part, int[] pixels, float thickness) {
        List<Face> faces = new ArrayList<>();
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (!path.isEmpty()) return;
            for (ModelPart.Polygon polygon : cube.polygons) buildFace(faces, polygon, pixels, thickness);
        });
        return new Mesh(List.copyOf(faces));
    }

    private static void buildFace(List<Face> faces, ModelPart.Polygon polygon, int[] pixels, float thickness) {
        var vertices = polygon.vertices();
        var a = vertices[0];
        var b = vertices[1];
        var d = vertices[3];
        int columns = Math.round(64 * Math.abs(b.u() - a.u()) + 64 * Math.abs(b.v() - a.v()));
        int rows = Math.round(64 * Math.abs(d.u() - a.u()) + 64 * Math.abs(d.v() - a.v()));
        if (columns < 1 || rows < 1 || columns > 64 || rows > 64) return;
        boolean[][] solid = new boolean[rows][columns];
        int[][] alpha = new int[rows][columns];
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            float t = (x + .5F) / columns, s = (y + .5F) / rows;
            int u = Math.clamp((int) (64 * (a.u() + (b.u() - a.u()) * t + (d.u() - a.u()) * s)), 0, 63);
            int v = Math.clamp((int) (64 * (a.v() + (b.v() - a.v()) * t + (d.v() - a.v()) * s)), 0, 63);
            alpha[y][x] = pixels[v * 64 + u] >>> 24;
            solid[y][x] = alpha[y][x] >= 128;
        }
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            if (alpha[y][x] == 0) continue;
            float t = (x + .5F) / columns, s = (y + .5F) / rows;
            float u = a.u() + (b.u() - a.u()) * t + (d.u() - a.u()) * s;
            float v = a.v() + (b.v() - a.v()) * t + (d.v() - a.v()) * s;
            Point p0 = point(a, b, d, (float) x / columns, (float) y / rows);
            Point p1 = point(a, b, d, (x + 1F) / columns, (float) y / rows);
            Point p2 = point(a, b, d, (x + 1F) / columns, (y + 1F) / rows);
            Point p3 = point(a, b, d, (float) x / columns, (y + 1F) / rows);
            var normal = polygon.normal();
            if (!solid[y][x]) {
                faces.add(new Face(p0, p1, p2, p3, u, v, normal));
                continue;
            }
            Point q0 = p0.move(normal, thickness), q1 = p1.move(normal, thickness);
            Point q2 = p2.move(normal, thickness), q3 = p3.move(normal, thickness);
            faces.add(new Face(q0, q1, q2, q3, u, v, normal));
            faces.add(new Face(p3, p2, p1, p0, u, v, new Vector3f(normal).negate()));
            if (y == 0 || !solid[y - 1][x]) side(faces, p0, p1, q1, q0, u, v);
            if (x == columns - 1 || !solid[y][x + 1]) side(faces, p1, p2, q2, q1, u, v);
            if (y == rows - 1 || !solid[y + 1][x]) side(faces, p2, p3, q3, q2, u, v);
            if (x == 0 || !solid[y][x - 1]) side(faces, p3, p0, q0, q3, u, v);
        }
    }

    private static Point point(ModelPart.Vertex a, ModelPart.Vertex b, ModelPart.Vertex d, float t, float s) {
        return new Point(a.x() + (b.x() - a.x()) * t + (d.x() - a.x()) * s,
                a.y() + (b.y() - a.y()) * t + (d.y() - a.y()) * s,
                a.z() + (b.z() - a.z()) * t + (d.z() - a.z()) * s);
    }

    private static void side(List<Face> faces, Point a, Point b, Point c, Point d, float u, float v) {
        Vector3f edge = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z);
        Vector3f normal = edge.cross(new Vector3f(d.x - a.x, d.y - a.y, d.z - a.z)).normalize();
        faces.add(new Face(a, b, c, d, u, v, normal));
    }

    public static boolean render(ModelPart part, PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay, int color) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isSkinLayersEnabled() || EXTERNAL) return false;
        Mesh mesh = PARTS.get(part);
        if (mesh == null) return false;
        for (Face face : mesh.faces) {
            vertex(vertices, pose, face.a, face, light, overlay, color);
            vertex(vertices, pose, face.b, face, light, overlay, color);
            vertex(vertices, pose, face.c, face, light, overlay, color);
            vertex(vertices, pose, face.d, face, light, overlay, color);
        }
        return true;
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Point point, Face face, int light, int overlay, int color) {
        vertices.addVertex(pose, point.x / 16F, point.y / 16F, point.z / 16F)
                .setUv(face.u, face.v).setColor(color).setOverlay(overlay).setLight(light)
                .setNormal(pose, face.normal.x(), face.normal.y(), face.normal.z());
    }
}
