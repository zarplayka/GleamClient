package ru.orion.client.cosmetic;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import ru.orion.client.OrionClient;
import ru.orion.client.render.GleamPose;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GleamTailModel {
    public static final List<String> IDS = List.of("ember_fox_tail", "verdigris_stinger", "amethyst_drake_tail");

    private static final int TRIANGLE_SIZE = 18;
    private static final float RADIANS = (float) (Math.PI / 180);
    private static final Map<String, GleamTailModel> MODELS = IDS.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), GleamTailModel::new));

    private final Identifier texture;
    private final Model model;

    private record Model(double duration, Bone root) {}
    private record Bone(float[] pivot, float[][] rotation, float[] triangles, Bone[] children) {}

    private record Motion(float gait, float walk, float swim, float flight, float stroke,
                          float pitchOffset, float idleWeight) {
        static Motion from(AvatarRenderState avatar, boolean sprinting, float verticalSpeed) {
            float swim = Math.clamp(avatar.swimAmount, 0F, 1F);
            float glide = avatar.isFallFlying ? avatar.fallFlyingScale() : 0F;
            float walk = Math.clamp(avatar.walkAnimationSpeed * 1.6F, 0F, 1F) * (1F - swim) * (1F - glide);
            float stride = walk * (sprinting ? 1.3F : 1F);
            float lift = Math.clamp(verticalSpeed * 27F, -10F, 12F) * (1F - swim) * (1F - glide);
            float pitchOffset = lift + (avatar.isCrouching ? 7F : 0F)
                    - (sprinting ? 8F * walk : 0F) - 8F * swim - 12F * glide;
            float idleWeight = 1F - .78F * Math.max(walk, Math.max(swim, glide));
            // Ноги Minecraft используют 0.6662 радиан на единицу фазы шага.
            return new Motion(avatar.walkAnimationPos * .6662F, stride, swim, glide,
                    avatar.ageInTicks, pitchOffset, idleWeight);
        }

        Quaternionf rotation(float idleX, float idleY, float idleZ, int depth) {
            // Свободный конец немного отстаёт от основания.
            float lag = depth * .43F;
            float joint = depth == 0 ? 1F : .17F;
            float gaitPhase = gait - lag;
            float waterPhase = stroke * .38F - lag;
            float flightPhase = stroke * .24F - lag;
            float x = idleX * idleWeight + (depth == 0 ? pitchOffset : 0F) + joint * (
                    walk * 10F * (float) Math.sin(2F * gaitPhase) + swim * 5F * (float) Math.cos(waterPhase));
            float y = idleY * idleWeight + joint * (
                    walk * 27F * (float) Math.sin(gaitPhase) + swim * 21F * (float) Math.sin(waterPhase)
                            + flight * 7F * (float) Math.sin(flightPhase));
            float z = idleZ * idleWeight + joint * walk * 5F * (float) Math.cos(gaitPhase);
            return new Quaternionf().rotationZYX(z * RADIANS, y * RADIANS, x * RADIANS);
        }
    }

    private GleamTailModel(String id) {
        texture = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/cosmetics/tails/" + id + ".png");
        String path = "/assets/orion-client/cosmetics/tails/" + id + ".json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(GleamTailModel.class.getResourceAsStream(path), path),
                StandardCharsets.UTF_8)) {
            model = new Gson().fromJson(reader, Model.class);
        } catch (IOException error) {
            throw new IllegalStateException(path, error);
        }
    }

    /** Загружаем геометрию во время инициализации клиента. */
    public static void preload() {}

    public static String sanitize(String id) {
        return id != null && IDS.contains(id) ? id : "";
    }

    public static String selected(Set<String> items) {
        return IDS.stream().filter(items::contains).findFirst().orElse("");
    }

    public static void submit(PlayerModel player, PoseStack pose, SubmitNodeCollector collector, String id,
                              AvatarRenderState avatar, boolean sprinting, float verticalSpeed) {
        GleamTailModel tail = MODELS.get(id);
        if (tail == null) return;
        long period = Math.round(tail.model.duration * 1_000_000_000L);
        float phase = (float) (Math.floorMod(System.nanoTime(), period) / (double) period);
        Motion motion = Motion.from(avatar, sprinting, verticalSpeed);

        pose.pushPose();
        try {
            player.root().translateAndRotate(pose);
            player.body.translateAndRotate(pose);
            // В Blockbench ноль на уровне стоп, у торса Minecraft он на уровне плеч.
            pose.scale(1F / 16F, -1F / 16F, 1F / 16F);
            pose.translate(0F, -24F, 0F);
            tail.submitBone(tail.model.root, pose, collector, avatar.lightCoords, phase, motion, 0);
        } finally {
            pose.popPose();
        }
    }

    private void submitBone(Bone bone, PoseStack pose, SubmitNodeCollector collector, int light, float phase,
                            Motion motion, int depth) {
        pose.pushPose();
        try {
            pose.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
            float[][] keys = bone.rotation;
            int count = keys.length - 1;
            float frame = phase * count;
            int index = (int) frame;
            int current = index % count;
            float progress = frame - index;
            float[] before = keys[(current + count - 1) % count], from = keys[current];
            float[] to = keys[(current + 1) % count], after = keys[(current + 2) % count];
            float x = interpolate(before[0], from[0], to[0], after[0], progress);
            float y = interpolate(before[1], from[1], to[1], after[1], progress);
            float z = interpolate(before[2], from[2], to[2], after[2], progress);
            GleamPose.rotate(pose, motion.rotation(x, y, z, depth));

            float[] data = bone.triangles;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (matrix, vertices) -> {
                for (int f = 0; f < data.length; f += TRIANGLE_SIZE) {
                    for (int corner = 0; corner < 4; corner++) {
                        // Последняя вершина повторяется, поскольку entityCutout принимает квадраты.
                        int v = f + 3 + Math.min(corner, 2) * 5;
                        vertices.addVertex(matrix, data[v], data[v + 1], data[v + 2])
                                .setColor(-1).setUv(data[v + 3], data[v + 4])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                                .setNormal(matrix, data[f], data[f + 1], data[f + 2]);
                    }
                }
            });
            for (Bone child : bone.children) submitBone(child, pose, collector, light, phase, motion, depth + 1);
        } finally {
            pose.popPose();
        }
    }

    private static float interpolate(float before, float from, float to, float after, float t) {
        return from + .5F * t * ((to - before) + t * (2 * before - 5 * from + 4 * to - after
                + t * (3 * (from - to) + after - before)));
    }
}
