package ru.orion.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class GleamPetModel {
    private final ModelPart root;
    private final List<ModelPart> parts;
    private final KeyframeAnimation idle;
    private final KeyframeAnimation action;
    private final long actionMillis;
    private final ModelPart[] legs;
    private final float[] meshVertices;

    private GleamPetModel(ModelPart root, AnimationDefinition idle, AnimationDefinition action, boolean grounded) {
        this.meshVertices = null;
        this.root = root;
        this.parts = root.getAllParts();
        this.idle = idle.bake(root);
        this.action = action.bake(root);
        this.actionMillis = Math.round(action.lengthInSeconds() * 1000);
        var part = root.createPartLookup();
        this.legs = grounded ? new ModelPart[]{
                part.apply("front_l"), part.apply("back_r"), part.apply("front_r"), part.apply("back_l")
        } : new ModelPart[0];
    }

    private GleamPetModel(float[] meshVertices) {
        this.meshVertices = meshVertices;
        root = null;
        parts = List.of();
        idle = null;
        action = null;
        actionMillis = 0;
        legs = new ModelPart[0];
    }

    static GleamPetModel load(GleamPet pet) throws IOException {
        if (pet == GleamPet.TARNKAPPE) return loadMesh(pet);
        String path = "/assets/orion-client/cosmetics/pets/" + pet.id() + ".json";
        try (var stream = GleamPetModel.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Модель питомца отсутствует: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var data = JsonParser.parseReader(reader).getAsJsonObject();
                var mesh = new MeshDefinition();
                for (var bone : data.getAsJsonArray("bones")) {
                    addBone(mesh.getRoot(), bone.getAsJsonObject());
                }
                ModelPart root = LayerDefinition.create(mesh, data.get("width").getAsInt(), data.get("height").getAsInt()).bakeRoot();
                return new GleamPetModel(root, clip(data.getAsJsonObject("idle"), true),
                        clip(data.getAsJsonObject("action"), false), pet.grounded());
            }
        }
    }

    private static GleamPetModel loadMesh(GleamPet pet) throws IOException {
        String path = "/assets/orion-client/cosmetics/pets/" + pet.id() + ".mesh";
        try (var resource = GleamPetModel.class.getResourceAsStream(path)) {
            if (resource == null) throw new IOException("Модель питомца отсутствует: " + path);
            try (var data = new DataInputStream(new BufferedInputStream(resource))) {
                if (data.readInt() != 0x47504D31) throw new IOException("Неизвестный формат модели: " + path);
                int triangles = data.readInt();
                if (triangles < 1 || triangles > 10_000) throw new IOException("Неверный размер модели: " + path);
                // Each triangle contains a normal followed by three vertices with position and UV.
                float[] values = new float[triangles * 18];
                for (int i = 0; i < values.length; i++) {
                    values[i] = data.readFloat();
                    if (!Float.isFinite(values[i])) throw new IOException("Некорректная координата: " + path);
                }
                if (data.read() != -1) throw new IOException("Некорректный размер модели: " + path);
                return new GleamPetModel(values);
            }
        }
    }

    private static void addBone(PartDefinition parent, JsonObject data) {
        var builder = CubeListBuilder.create();
        for (var entry : data.getAsJsonArray("cubes")) {
            JsonArray cube = entry.getAsJsonArray();
            builder.texOffs(cube.get(0).getAsInt(), cube.get(1).getAsInt())
                    .addBox(cube.get(2).getAsFloat(), cube.get(3).getAsFloat(), cube.get(4).getAsFloat(),
                            cube.get(5).getAsFloat(), cube.get(6).getAsFloat(), cube.get(7).getAsFloat());
        }
        var pivot = data.getAsJsonArray("pivot");
        var rotation = data.getAsJsonArray("rotation");
        PartPose pose = PartPose.offsetAndRotation(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(),
                pivot.get(2).getAsFloat(), radians(rotation, 0), radians(rotation, 1), radians(rotation, 2));
        var part = parent.addOrReplaceChild(data.get("name").getAsString(), builder, pose);
        for (var child : data.getAsJsonArray("children")) {
            addBone(part, child.getAsJsonObject());
        }
    }

    private static float radians(JsonArray vector, int index) {
        return (float) Math.toRadians(vector.get(index).getAsFloat());
    }

    private static AnimationDefinition clip(JsonObject data, boolean loop) {
        var builder = AnimationDefinition.Builder.withLength(data.get("duration").getAsFloat());
        if (loop) builder.looping();
        for (var entry : data.getAsJsonArray("tracks")) {
            var track = entry.getAsJsonObject();
            String target = track.get("target").getAsString();
            boolean rotation = switch (target) {
                case "rotation" -> true;
                case "position" -> false;
                default -> throw new IllegalArgumentException("Неизвестный канал анимации: " + target);
            };
            var keys = track.getAsJsonArray("keys");
            var frames = new Keyframe[keys.size()];
            for (int i = 0; i < frames.length; i++) {
                var key = keys.get(i).getAsJsonArray();
                float x = key.get(1).getAsFloat(), y = key.get(2).getAsFloat(), z = key.get(3).getAsFloat();
                var value = rotation ? KeyframeAnimations.degreeVec(x, y, z) : KeyframeAnimations.posVec(x, y, z);
                frames[i] = new Keyframe(key.get(0).getAsFloat(), value, AnimationChannel.Interpolations.LINEAR);
            }
            builder.addAnimation(track.get("bone").getAsString(), new AnimationChannel(
                    rotation ? AnimationChannel.Targets.ROTATION : AnimationChannel.Targets.POSITION, frames));
        }
        return builder.build();
    }

    void render(PoseStack.Pose transform, VertexConsumer vertices, int light, long ageMillis, long actionTime,
                float stride, float movement) {
        if (meshVertices != null) {
            for (int triangle = 0; triangle < meshVertices.length; triangle += 18) {
                float nx = meshVertices[triangle], ny = meshVertices[triangle + 1], nz = meshVertices[triangle + 2];
                // Entity cutout batches quads. Repeating the final vertex makes the second triangle degenerate.
                for (int corner = 0; corner < 4; corner++) {
                    int i = triangle + 3 + Math.min(corner, 2) * 5;
                    vertices.addVertex(transform, meshVertices[i], meshVertices[i + 1], meshVertices[i + 2])
                            .setUv(meshVertices[i + 3], meshVertices[i + 4]).setColor(-1)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform, nx, ny, nz);
                }
            }
            return;
        }
        for (ModelPart part : parts) part.resetPose();
        if (actionTime >= 0 && actionTime <= actionMillis) {
            action.apply(actionTime, 1F);
        } else {
            idle.apply(ageMillis, 1F);
            if (legs.length > 0 && movement > 0F) {
                float swing = (float) Math.sin(stride) * movement * .65F;
                legs[0].xRot += swing;
                legs[1].xRot += swing;
                legs[2].xRot -= swing;
                legs[3].xRot -= swing;
            }
        }
        var pose = new PoseStack();
        pose.last().set(transform);
        root.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
    }
}
