package ru.orion.client.effect;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class GleamWingModel {
    public static final List<String> IDS = List.of("angel", "dragon", "evergreen", "ash_phoenix");
    // These IDs are shared with older clients and persisted in configurations.
    private static final List<Integer> WIRE_STYLES = List.of(1, 2, 4, 6);

    public static int toWireStyle(int style) {
        return WIRE_STYLES.get(style);
    }

    public static int fromWireStyle(int id) {
        return WIRE_STYLES.indexOf(id);
    }
    private static final int FACE_SIZE = 23;
    private static final List<GleamWingModel> MODELS = IDS.subList(2, IDS.size()).stream().map(GleamWingModel::new).toList();
    private final Identifier texture;
    private final float[] faces;

    private GleamWingModel(String id) {
        String file = "wing_" + id;
        texture = Identifier.fromNamespaceAndPath("orion-client", "textures/cosmetics/wings/" + file + ".png");
        String path = "/assets/orion-client/cosmetics/wings/" + file + ".json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(GleamWingModel.class.getResourceAsStream(path), path), StandardCharsets.UTF_8)) {
            faces = new Gson().fromJson(reader, float[].class);
            if (faces.length == 0 || faces.length % FACE_SIZE != 0) throw new IllegalArgumentException(path);
        } catch (IOException error) {
            throw new IllegalStateException(path, error);
        }
    }

    static void submit(PoseStack pose, SubmitNodeCollector collector, int style) {
        var model = MODELS.get(style - 3);
        var faces = model.faces;
        pose.pushPose();
        pose.scale(1F/16F, 1F/16F, 1F/16F);
        pose.translate(0F, -20.8F, -2.56F);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(model.texture), (matrix, vertices) -> {
            for (int f = 0; f < faces.length; f += FACE_SIZE) {
                for (int i = f + 3; i < f + FACE_SIZE; i += 5) {
                    vertices.addVertex(matrix, faces[i], faces[i+1], faces[i+2])
                            .setColor(-1).setUv(faces[i+3], faces[i+4]).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(0xF000F0).setNormal(matrix, faces[f], faces[f+1], faces[f+2]);
                }
            }
        });
        pose.popPose();
    }
}