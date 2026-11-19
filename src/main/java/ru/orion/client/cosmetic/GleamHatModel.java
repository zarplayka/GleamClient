package ru.orion.client.cosmetic;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GleamHatModel {
    public static final List<String> IDS = List.of("santa_hat", "fawn_hood", "snowman_top_hat",
            "gingerbread_house_hat", "newsboy_cap", "aviator_helmet", "traveller_hat");

    private static final int FACE_SIZE = 23;
    private static final Map<String, GleamHatModel> MODELS = IDS.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), GleamHatModel::new));

    private final Identifier texture;
    private final float[] faces;

    private GleamHatModel(String id) {
        texture = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/cosmetics/hats/" + id + ".png");
        String path = "/assets/orion-client/cosmetics/hats/" + id + ".json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(GleamHatModel.class.getResourceAsStream(path), path),
                StandardCharsets.UTF_8)) {
            faces = new Gson().fromJson(reader, float[].class);
            if (faces.length == 0 || faces.length % FACE_SIZE != 0) throw new IllegalArgumentException(path);
        } catch (IOException error) {
            throw new IllegalStateException(path, error);
        }
    }

    public static void submit(PlayerModel player, PoseStack pose, SubmitNodeCollector collector, String id, int light) {
        GleamHatModel hat = MODELS.get(id);
        if (hat == null) return;
        pose.pushPose();
        player.root().translateAndRotate(pose);
        player.translateToHead(pose);
        // The hat mesh is measured from the feet. The player head pivots at the neck, 24 pixels up.
        pose.scale(1F/16F, -1F/16F, -1F/16F);
        pose.translate(0F, -24F, 0F);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(hat.texture), (matrix, vertices) -> {
            float[] data = hat.faces;
            for (int f = 0; f < data.length; f += FACE_SIZE) {
                for (int i = f + 3; i < f + FACE_SIZE; i += 5) {
                    vertices.addVertex(matrix, data[i], data[i+1], data[i+2])
                            .setColor(-1).setUv(data[i+3], data[i+4]).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light).setNormal(matrix, data[f], data[f+1], data[f+2]);
                }
            }
        });
        pose.popPose();
    }
}
