package ru.orion.client.effect;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.render.GleamPose;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class GleamWingModel {
    public static final List<String> IDS = List.of("angel", "dragon", "midnight", "evergreen", "first_salvo", "ash_phoenix", "lunar_moth");
    private static final GleamWingModel[] MODELS = new GleamWingModel[IDS.size()];
    private final Identifier texture;
    private final Bone[] roots;

    private record Bone(float[] pivot, float[] faces, Bone[] children, float[] position, float[] rotation, float[] scale) {
        private static float sample(float[] keys, float time, int axis) {
            int last = keys.length - 4;
            int i = 0;
            while (i < last && keys[i + 4] <= time) i += 4;
            if (i == last || time <= keys[i]) return keys[i + 1 + axis];
            float weight = (time - keys[i]) / (keys[i + 4] - keys[i]);
            return keys[i + 1 + axis] + (keys[i + 5 + axis] - keys[i + 1 + axis]) * weight;
        }

        void submit(PoseStack pose, SubmitNodeCollector collector, Identifier texture, float time) {
            pose.pushPose();
            if (position != null) pose.translate(sample(position,time,0), sample(position,time,1), sample(position,time,2));
            pose.translate(pivot[0],pivot[1],pivot[2]);
            if (rotation != null) {
                GleamPose.rotateDegrees(pose, Axis.ZP, sample(rotation,time,2));
                GleamPose.rotateDegrees(pose, Axis.YP, sample(rotation,time,1));
                GleamPose.rotateDegrees(pose, Axis.XP, sample(rotation,time,0));
            }
            if (scale != null) pose.scale(sample(scale,time,0), sample(scale,time,1), sample(scale,time,2));
            pose.translate(-pivot[0],-pivot[1],-pivot[2]);
            if (faces.length > 0) collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (matrix, vertices) -> {
                for (int face = 0; face < faces.length; face += 23) {
                    for (int i = face + 3; i < face + 23; i += 5) {
                        vertices.addVertex(matrix,faces[i],faces[i+1],faces[i+2])
                                .setUv(faces[i+3],faces[i+4]).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(0xF000F0).setNormal(matrix,faces[face],faces[face+1],faces[face+2]);
                    }
                }
            });
            for (Bone bone : children) bone.submit(pose,collector,texture,time);
            pose.popPose();
        }
    }

    private GleamWingModel(String id) {
        String name = "wing_" + id;
        texture = Identifier.fromNamespaceAndPath("orion-client", "textures/cosmetics/wings/" + name + ".png");
        String resource = "/assets/orion-client/cosmetics/wings/" + name + ".json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(GleamWingModel.class.getResourceAsStream(resource), resource), StandardCharsets.UTF_8)) {
            roots = new Gson().fromJson(reader,Bone[].class);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(resource,error);
        }
    }

    public static void load() {
        for (int i = 2; i < IDS.size(); i++) MODELS[i] = new GleamWingModel(IDS.get(i));
    }

    static void submit(PoseStack pose, SubmitNodeCollector collector, int style, float time) {
        var model = MODELS[style-1];
        pose.pushPose();
        pose.scale(1F/16,1F/16,1F/16);
        pose.translate(0,-20.8F,-2.56F);
        for (Bone bone : model.roots) bone.submit(pose,collector,model.texture,time);
        pose.popPose();
    }
}