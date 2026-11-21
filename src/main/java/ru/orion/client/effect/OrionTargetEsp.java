package ru.orion.client.effect;

import ru.orion.client.GleamMinecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * Target ESP rendered like the reference from zar.jar:
 * a smoothed, camera-facing world quad with fixed world scale and animated rotation.
 */
public final class OrionTargetEsp {
    public static final String[] TEXTURES = {
            "rhomb", "ghost", "chain", "crystals", "marker", "Молнии", "Орбиты"
    };

    private static final float TARGET_FPS = 60.0F;
    private static final float TARGET_FRAME_TIME = 1000.0F / TARGET_FPS;
    private static final float APPEAR_SPEED = 0.18F;
    private static final float DISAPPEAR_SPEED = 0.18F;
    private static final float BASE_SCALE = 0.50F;
    private static final float GLOW_SCALE = 0.72F;
    private static final double FRONT_OFFSET_MULTIPLIER = 0.72D;
    private static final long TARGET_LINGER_MS = 4_000L;
    private static final long HIT_HIGHLIGHT_MS = 350L;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int WHITE = ARGB.color(255, 255, 255, 255);
    private static final float CHAIN_SCALE = 0.5F;
    private static final float CHAIN_BAND_HEIGHT = 1.5F;
    private static final float CHAIN_BOTTOM_Y = -0.5F;
    private static final int CHAIN_PARTS = 12;
    private static final int CHAIN_POINTS = 720;
    private static final float CHAIN_U_REPEATS = 8.0F;
    private static final float CHAIN_TOP_COLOR_MULTIPLIER = 1.15F;
    private static final int CHAIN_MAX_ALPHA = 128;
    private static final int MARKER_SEGMENTS = 64;
    private static final float MARKER_STEP_SPEED = 0.06F;
    private static final float MARKER_RADIUS_PULSE = 0.18F;
    private static final float MARKER_OUTER_RADIUS = 1.35F;
    private static final double GHOST_OUTER_RADIUS = 0.7D;
    private static final double MARKER_OUTLINE_WIDTH = 0.035D;
    private static final double MARKER_SIDE_GLOW_WIDTH = 0.055D;
    private static final int RED_HIT_COLOR = 0xFFFF0000;
    private static final CrystalTemplate[] CRYSTALS = {
            new CrystalTemplate(0.0D, 0.85D, 0.8D, -49.0F, 0.0F, 40.0F),
            new CrystalTemplate(0.2D, 0.85D, -0.675D, 35.0F, 0.0F, -30.0F),
            new CrystalTemplate(0.6D, 1.35D, 0.6D, -30.0F, 0.0F, 35.0F),
            new CrystalTemplate(-0.74D, 1.05D, 0.4D, -25.0F, 0.0F, -30.0F),
            new CrystalTemplate(0.74D, 0.95D, -0.4D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.475D, 0.85D, -0.375D, 30.0F, 0.0F, -25.0F),
            new CrystalTemplate(0.0D, 1.35D, -0.6D, 45.0F, 0.0F, 0.0F),
            new CrystalTemplate(0.85D, 0.7D, 0.1D, -30.0F, 0.0F, 30.0F),
            new CrystalTemplate(-0.7D, 1.35D, -0.3D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.3D, 1.35D, 0.55D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.5D, 0.7D, 0.7D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(0.5D, 0.7D, 0.7D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.7D, 0.75D, 0.0D, 0.0F, 0.0F, 0.0F),
            new CrystalTemplate(-0.2D, 0.65D, -0.7D, 0.0F, 0.0F, 0.0F)
    };

    private final OrionConfigManager configManager;

    private Vec3 smoothedPosition;
    private LivingEntity lastTarget;
    private LivingEntity lingeringTarget;
    private long lingeringUntilMs;
    private float anim;
    private float hitAnimSmoothed;
    private float circleStep;
    private double markerSmoothY;
    private double markerSmoothY2;
    private UUID lastHitPlayerId;
    private long lastHitUntilMs;
    private float crystalRotation;
    private long lastFrameTime = System.currentTimeMillis();

    public OrionTargetEsp(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public static String textureName(int index) {
        return switch (index) {
            case 5 -> "ghost";
            case 6 -> "lightning";
            case 7 -> "orbits";
            default -> TEXTURES[Math.clamp(index, 0, 4)];
        };
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    public void recordHit(Entity target) {
        if (target instanceof LivingEntity living) {
            lastHitPlayerId = living.getUUID();
            lastHitUntilMs = System.currentTimeMillis() + HIT_HIGHLIGHT_MS;
        }
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        float deltaTime = getDeltaTime();
        LivingEntity target = resolveTarget(client);
        if (!configManager.getConfig().isTargetEspEnabled()) {
            clearTargetState();
            return;
        }

        long now = System.currentTimeMillis();
        if (target != null) {
            lingeringTarget = target;
            lingeringUntilMs = now + TARGET_LINGER_MS;
        } else if (lingeringTarget != null && lingeringTarget.isAlive() && now <= lingeringUntilMs) {
            target = lingeringTarget;
        }

        // Геометрия эффектов проходит depth-тест (не видна сквозь модельку и блоки),
        // а рейкаст дополнительно плавно гасит эффект, когда цель скрыта блоками.
        boolean hasTarget = target != null && isVisibleThroughBlocks(client, target);

        updateAnim(hasTarget, deltaTime);
        if (!hasTarget) {
            if (anim <= 0.0F || lastTarget == null || !lastTarget.isAlive()) {
                clearTargetState();
                return;
            }
            target = lastTarget;
        }

        if (anim <= 0.01F) {
            return;
        }

        Camera camera = GleamMinecraft.camera(client);
        Vec3 targetPosition = interpolatedPosition(target, client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (lastTarget != target || smoothedPosition == null) {
            smoothedPosition = targetPosition;
            lastTarget = target;
        } else {
            float smoothing = 1.0F - (float) Math.exp(-0.35F * deltaTime);
            smoothedPosition = smoothedPosition.lerp(targetPosition, smoothing);
        }

        PoseStack poseStack = context.poseStack();
        Vec3 cameraPosition = camera.position();
        String selected = textureName(configManager.getConfig().getTargetEspTexture());
        float hitProgress = getHitProgress(target);
        hitAnimSmoothed = Mth.lerp(Math.min(1.0F, 0.35F * deltaTime), hitAnimSmoothed, hitProgress);
        int baseColor = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(configManager.getConfig().getTargetEspChainColor()));
        if (configManager.getConfig().isTargetEspHealthColor()) {
            float health = Math.clamp(target.getHealth() / Math.max(1F, target.getMaxHealth()), 0F, 1F);
            baseColor = lerpColor(0xFFFF535D, 0xFF58D68D, health);
        }
        int color1 = lerpColor(baseColor, RED_HIT_COLOR, hitProgress);
        int color2 = lerpColor(lighten(baseColor, CHAIN_TOP_COLOR_MULTIPLIER), RED_HIT_COLOR, hitProgress);

        if ("chain".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y,
                    smoothedPosition.z - cameraPosition.z
            );
            submitChain(context, poseStack, target, anim, hitAnimSmoothed, color1, color2);
            poseStack.popPose();
            return;
        }

        if ("rhomb".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y,
                    smoothedPosition.z - cameraPosition.z
            );
            submitRhomb(context, poseStack, camera, target, anim, hitAnimSmoothed, color1, color2);
            poseStack.popPose();
            return;
        }

        if ("ghost".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y + target.getBbHeight() * 0.5F,
                    smoothedPosition.z - cameraPosition.z
            );
            int detail = detailAtDistance(cameraPosition.distanceToSqr(smoothedPosition));
            submitGhost(context, poseStack, camera, target.getBbWidth(), target.getBbHeight(),
                    anim, hitAnimSmoothed, color1, color2, detail);
            poseStack.popPose();
            return;
        }

        if ("lightning".equals(selected) || "orbits".equals(selected)) {
            Vec3 anchor = smoothedPosition.subtract(cameraPosition);
            poseStack.pushPose();
            poseStack.translate(anchor.x, anchor.y + target.getBbHeight() * .5, anchor.z);
            float radius = Math.max(.43F, target.getBbWidth() * .72F);
            float height = Math.max(.38F, target.getBbHeight() * .49F);
            int detail = detailAtDistance(anchor.lengthSqr());
            double time = now / 1000D;
            float visibility = anim, hit = hitAnimSmoothed;
            boolean lightning = "lightning".equals(selected);
            context.submitNodeCollector().submitCustomGeometry(poseStack, OrionEspRenderTypes.translucentColorQuadsDepthTested(),
                    (pose, vertices) -> {
                        if (lightning) buildLightning(pose, vertices, anchor, time, radius, height, detail, visibility, hit, color1, color2);
                        else buildOrbits(pose, vertices, time, radius, height, visibility, hit, color1, color2);
                    });
            poseStack.popPose();
            return;
        }

        if ("crystals".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y,
                    smoothedPosition.z - cameraPosition.z
            );
            submitCrystals(context, poseStack, camera, anim, hitAnimSmoothed, color1, deltaTime,
                    detailAtDistance(cameraPosition.distanceToSqr(smoothedPosition)));
            poseStack.popPose();
            return;
        }

        if ("marker".equals(selected)) {
            poseStack.pushPose();
            poseStack.translate(
                    smoothedPosition.x - cameraPosition.x,
                    smoothedPosition.y - cameraPosition.y + target.getBbHeight() * 0.5F,
                    smoothedPosition.z - cameraPosition.z
            );
            submitMarkerCircle(
                    context,
                    poseStack,
                    target,
                    anim,
                    hitAnimSmoothed,
                    hitProgress,
                    baseColor,
                    deltaTime
            );
            poseStack.popPose();
            return;
        }

        Vec3 renderPosition = frontFacingPosition(smoothedPosition, cameraPosition, target);
        Identifier texture = texture(selected);
        float scale = scale(selected) * anim;
        float rotation = rotation(selected);

        poseStack.pushPose();
        poseStack.translate(
                renderPosition.x - cameraPosition.x,
                renderPosition.y - cameraPosition.y + target.getBbHeight() * 0.5F,
                renderPosition.z - cameraPosition.z
        );
        ru.orion.client.render.GleamPose.rotate(poseStack, camera.rotation());

        if (shouldSpin(selected)) {
            ru.orion.client.render.GleamPose.rotate(poseStack, Axis.ZP.rotation(rotation));
        }

        poseStack.scale(scale, scale, 1.0F);
        if (drawReferenceGlow(selected)) {
            poseStack.pushPose();
            poseStack.scale(GLOW_SCALE / BASE_SCALE, GLOW_SCALE / BASE_SCALE, 1.0F);
            submitQuad(context, poseStack, texture("glow"), ARGB.color(90, 255, 255, 255));
            poseStack.popPose();
        }
        submitQuad(context, poseStack, texture, WHITE);

        poseStack.popPose();
    }

    private static void submitChain(LevelRenderContext context, PoseStack poseStack, LivingEntity target, float alpha, float hitAnim, int bottomBaseColor, int topBaseColor) {
        long now = System.currentTimeMillis();
        float timeDegrees = (now % 360_000L) / 1000.0F * 60.0F;
        float wave = (float) Math.sin(Math.toRadians(timeDegrees));
        float zTilt = 20.0F * Math.min(1.0F + wave, 1.0F);
        float xTilt = 20.0F * (Math.min(1.0F + wave, 2.0F) - 1.0F);
        float spin = (now % 720_000L) / 1000.0F * 30.0F;
        float radius = target.getBbWidth() * 3.0F;
        float hitScale = 1.0F - MARKER_RADIUS_PULSE * hitAnim;
        int bottomColor = chainColor(bottomBaseColor, alpha);
        int topColor = chainColor(topBaseColor, alpha);

        for (int ring = 0; ring < 2; ring++) {
            float radiusMultiplier = 1.2F - 0.5F * (ring == 0 ? 1.0F : 0.9F);
            float ringZTilt = ring == 0 ? zTilt : -zTilt;
            float ringXTilt = ring == 0 ? xTilt : -xTilt;

            poseStack.pushPose();
            poseStack.translate(0.0F, target.getBbHeight() * 0.5F, 0.0F);
            poseStack.scale(CHAIN_SCALE * hitScale, CHAIN_SCALE * hitScale, CHAIN_SCALE * hitScale);
            ru.orion.client.render.GleamPose.rotate(poseStack, Axis.ZP.rotationDegrees(ringZTilt));
            ru.orion.client.render.GleamPose.rotate(poseStack, Axis.XP.rotationDegrees(ringXTilt));

            RenderType renderType = OrionEspRenderTypes.texturedDepthTested(texture("chain"));
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, vertexConsumer) -> buildChainBand(
                            pose,
                            vertexConsumer,
                            radius,
                            radiusMultiplier,
                            spin,
                            ringZTilt / 100.0F,
                            -ringXTilt / 100.0F,
                            bottomColor,
                            topColor
                    )
            );
            poseStack.popPose();
        }
    }

    private static void submitRhomb(LevelRenderContext context, PoseStack poseStack, Camera camera, LivingEntity target, float alpha, float hitAnim, int color1, int color2) {
        poseStack.translate(0.0F, target.getBbHeight() * 0.5F, 0.0F);
        ru.orion.client.render.GleamPose.rotate(poseStack, camera.rotation());
        float time = (System.currentTimeMillis() % 6283L) / 1000.0F;
        ru.orion.client.render.GleamPose.rotate(poseStack, Axis.ZP.rotationDegrees((float) Math.sin(time) * 360.0F));
        float scale = 0.5F * (1.0F - MARKER_RADIUS_PULSE * hitAnim);
        poseStack.scale(scale, scale, 1.0F);

        int bottom = withAlpha(color2, Math.round(255.0F * alpha));
        int top = withAlpha(color1, Math.round(255.0F * alpha));
        RenderType renderType = OrionEspRenderTypes.textured(texture("rhomb"));
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildGradientQuad(pose, vertexConsumer, bottom, top)
        );
    }

    private int detailAtDistance(double distanceSquared) {
        int detail = configManager.getConfig().getTargetEspDetail();
        return distanceSquared > 24 * 24 ? 0 : distanceSquared > 12 * 12 ? Math.min(1, detail) : detail;
    }

    private static void submitGhost(LevelRenderContext context, PoseStack poseStack, Camera camera, float width, float height,
                                    float alpha, float hitAnim, int color1, int color2, int detail) {
        float radius = (float) (Math.max(GHOST_OUTER_RADIUS, width * .8F) * (1 + .10 * hitAnim));
        float rise = Math.min(.60F, height * .32F);
        double phase = System.nanoTime() / 1e9 * 2.3 % Math.TAU;
        var rotation = new Quaternionf(camera.rotation());
        int points = 8 + detail * 3;
        context.submitNodeCollector().submitCustomGeometry(poseStack,
                OrionEspRenderTypes.texturedDepthTested(texture("ghost-glow")),
                (pose, vertices) -> buildGhost(pose, vertices, rotation, phase, radius, rise, alpha, color1, color2, points));
    }

    private static void buildGhost(PoseStack.Pose pose, VertexConsumer vertices, Quaternionfc rotation, double phase,
                                   float radius, float rise, float alpha, int color1, int color2, int points) {
        PoseStack sprites = new PoseStack();
        sprites.last().pose().set(pose.pose());
        for (int lane = 0; lane < 3; lane++) {
            for (int i = points - 1; i >= 0; i--) {
                float strength = 1 - i / (float) points;
                double angle = phase + lane * Math.TAU / 3 - i * 1.78 / points;
                double y = Math.sin(angle * 2 + lane * .8) * rise;
                sprites.pushPose();
                sprites.translate(Math.sin(angle) * radius, y, Math.cos(angle) * radius);
                ru.orion.client.render.GleamPose.rotate(sprites, rotation);
                int color = withAlpha(lerpColor(color1, color2, strength), Math.round((i == 0 ? 172 : 64) * alpha * strength * strength));
                buildTexturedQuad(sprites.last(), vertices, .033F + (i == 0 ? .127F : .079F) * strength, color);
                sprites.popPose();
            }
        }
    }

    private static void buildLightning(PoseStack.Pose pose, VertexConsumer vertices, Vec3 anchor,
                                       double time, float radius, float height, int detail, float alpha, float hit,
                                       int color, int highlight) {
        long cycle = (long) (time * 4);
        float phase = (float) (time * 4 - cycle);
        float intensity = alpha * (.55F + .30F * (float) Math.sin(Math.PI * phase) + .15F * hit);
        int count = 2 + detail, steps = 7 + detail * 2;
        for (int arc = 0; arc < count; arc++) {
            double start = time * .85 + arc * Math.TAU / count;
            Vec3 previous = energyPoint(start, radius, -height);
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                long seed = cycle * 137 + arc * 113L + i * 971L;
                double jitter = Math.sin(seed * 3.116) * Math.sin(Math.PI * t);
                Vec3 point = energyPoint(start + t * 1.43 + jitter * .36, radius * (1 + jitter * .22),
                        height * (2 * t - 1) + jitter * .10);
                energyRibbon(pose, vertices, previous, point, anchor, .039F, withAlpha(color, Math.round(73 * intensity)));
                energyRibbon(pose, vertices, previous, point, anchor, .010F,
                        withAlpha(lerpColor(highlight, WHITE, .67F), Math.round(240 * intensity)));
                if (i == steps / 3 || i == steps * 2 / 3) {
                    Vec3 tip = energyPoint(start + t * 1.43 + .7 * Math.signum(jitter), radius * 1.47, point.y + .22);
                    energyRibbon(pose, vertices, point, tip, anchor, .009F, withAlpha(highlight, Math.round(137 * intensity)));
                }
                previous = point;
            }
        }
    }

    private static Vec3 energyPoint(double angle, double radius, double y) {
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    private static void energyRibbon(PoseStack.Pose pose, VertexConsumer vertices, Vec3 a, Vec3 b, Vec3 anchor,
                                     float thickness, int color) {
        Vec3 side = b.subtract(a).cross(anchor.add(a).add(b).scale(.5)).normalize().scale(thickness);
        addColorVertex(pose, vertices, a.subtract(side), color);
        addColorVertex(pose, vertices, a.add(side), color);
        addColorVertex(pose, vertices, b.add(side), color);
        addColorVertex(pose, vertices, b.subtract(side), color);
    }

    private static void buildOrbits(PoseStack.Pose pose, VertexConsumer vertices, double time, float radius,
                                     float height, float alpha, float hit, int color, int highlight) {
        double size = radius * (1.21 + .15 * hit);
        for (int ring = 0; ring < 2; ring++) {
            int direction = ring == 0 ? 1 : -1;
            for (int segment = 0; segment < 8; segment++) {
                double angle = segment * Math.TAU / 8 + time * direction * .95;
                int ink = withAlpha(lerpColor(color, highlight, (float) (.5 + .5 * Math.sin(angle - time * 2))),
                        Math.round(alpha * (ring == 0 ? 205 : 145)));
                Vec3 left = energyPoint(angle, size * .96, direction * height * .54 + Math.sin(angle) * .17);
                Vec3 middle = energyPoint(angle + .31, size * 1.10, direction * height * .54 + Math.sin(angle + .31) * .17);
                Vec3 right = energyPoint(angle + .63, size * .96, direction * height * .54 + Math.sin(angle + .63) * .17);
                Vec3 inset = energyPoint(angle + .31, size * .96, middle.y);
                addColorVertex(pose, vertices, left, ink);
                addColorVertex(pose, vertices, middle, ink);
                addColorVertex(pose, vertices, right, ink);
                addColorVertex(pose, vertices, inset, ink);
            }
        }
    }

    private void submitCrystals(LevelRenderContext context, PoseStack poseStack, Camera camera, float alpha, float hitAnim,
                                int color, float deltaTime, int detail) {
        crystalRotation = (crystalRotation + 0.5F * deltaTime) % 360.0F;
        ru.orion.client.render.GleamPose.rotate(poseStack, Axis.YP.rotationDegrees(crystalRotation));
        int crystalColor = withAlpha(color, Math.round(160F * alpha));
        RenderType crystalRenderType = OrionEspRenderTypes.colorQuadsDepthTested();

        var facets = new ArrayList<PoseStack.Pose>();
        var blooms = new ArrayList<PoseStack.Pose>();
        int stride = detail == 0 ? 3 : detail == 1 ? 2 : 1;
        for (int i = 0; i < CRYSTALS.length; i += stride) {
            CrystalTemplate crystal = CRYSTALS[i];
            poseStack.pushPose();
            crystal.applyTransform(poseStack, hitAnim);
            facets.add(poseStack.last().copy());
            ru.orion.client.render.GleamPose.rotate(poseStack, camera.rotation());
            blooms.add(poseStack.last().copy());
            poseStack.popPose();
        }
        context.submitNodeCollector().submitCustomGeometry(poseStack, crystalRenderType, (ignored, vertices) -> {
            for (var facet : facets) buildCrystal(facet, vertices, crystalColor);
        });
        int bloomColor = withAlpha(color, Math.round(48F * alpha));
        context.submitNodeCollector().submitCustomGeometry(poseStack, OrionEspRenderTypes.texturedDepthTested(texture("crystal-glow")),
                (ignored, vertices) -> {
                    for (var bloom : blooms) buildTexturedQuad(bloom, vertices, .4F, bloomColor);
                });
    }


    private static void buildGradientQuad(PoseStack.Pose pose, VertexConsumer vc, int bottomColor, int topColor) {
        addVertex(pose, vc, -1.0F, -1.0F, 0.0F, 0.0F, bottomColor);
        addVertex(pose, vc, -1.0F, 1.0F, 0.0F, 1.0F, topColor);
        addVertex(pose, vc, 1.0F, 1.0F, 1.0F, 1.0F, topColor);
        addVertex(pose, vc, 1.0F, -1.0F, 1.0F, 0.0F, bottomColor);
    }

    private static void buildTexturedQuad(PoseStack.Pose pose, VertexConsumer vc, float size, int color) {
        float half = size * 0.5F;
        addVertex(pose, vc, -half, -half, 0.0F, 1.0F, color);
        addVertex(pose, vc, half, -half, 1.0F, 1.0F, color);
        addVertex(pose, vc, half, half, 1.0F, 0.0F, color);
        addVertex(pose, vc, -half, half, 0.0F, 0.0F, color);
    }

    private static void buildCrystal(PoseStack.Pose pose, VertexConsumer vc, int color) {
        int sides = 5;
        float radius = 0.05F;
        float halfHeight = 0.025F;
        float tipOffset = 0.075F;
        int sideColor = color;
        int darkColor = darken(color, 0.7F);
        int lightColor = lighten(color, 1.2F);

        for (int i = 0; i < sides; i++) {
            double angle0 = Math.PI * 2.0D * i / sides;
            double angle1 = Math.PI * 2.0D * (i + 1) / sides;
            Vec3 bottom0 = new Vec3(Math.cos(angle0) * radius, -halfHeight, Math.sin(angle0) * radius);
            Vec3 bottom1 = new Vec3(Math.cos(angle1) * radius, -halfHeight, Math.sin(angle1) * radius);
            Vec3 top0 = new Vec3(Math.cos(angle0) * radius, halfHeight, Math.sin(angle0) * radius);
            Vec3 top1 = new Vec3(Math.cos(angle1) * radius, halfHeight, Math.sin(angle1) * radius);
            Vec3 topTip = new Vec3(0.0D, halfHeight + tipOffset, 0.0D);
            Vec3 bottomTip = new Vec3(0.0D, -halfHeight - tipOffset, 0.0D);
            int quadColor = i % 2 == 0 ? sideColor : darkColor;
            int capColor = i % 2 == 0 ? lightColor : sideColor;

            addColorVertex(pose, vc, bottom0, quadColor);
            addColorVertex(pose, vc, bottom1, quadColor);
            addColorVertex(pose, vc, top1, quadColor);
            addColorVertex(pose, vc, top0, quadColor);

            addColorVertex(pose, vc, topTip, capColor);
            addColorVertex(pose, vc, top1, capColor);
            addColorVertex(pose, vc, top0, capColor);
            addColorVertex(pose, vc, top0, capColor);

            addColorVertex(pose, vc, bottomTip, darkColor);
            addColorVertex(pose, vc, bottom0, darkColor);
            addColorVertex(pose, vc, bottom1, darkColor);
            addColorVertex(pose, vc, bottom1, darkColor);
        }
    }

    private static void buildChainBand(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float radius,
            float radiusMultiplier,
            float spinDegrees,
            float xOffset,
            float zOffset,
            int bottomColor,
            int topColor
    ) {
        float topY = CHAIN_BOTTOM_Y + CHAIN_BAND_HEIGHT;
        int step = CHAIN_PARTS / 2;
        for (int point = 0; point < CHAIN_POINTS; point += step) {
            float previousPoint = point - step;
            float previousAngle = (float) Math.toRadians(previousPoint + spinDegrees);
            float currentAngle = (float) Math.toRadians(point + spinDegrees);

            float x0 = (float) Math.sin(previousAngle) * radius * radiusMultiplier + xOffset;
            float z0 = (float) Math.cos(previousAngle) * radius * radiusMultiplier + zOffset;
            float x1 = (float) Math.sin(currentAngle) * radius * radiusMultiplier + xOffset;
            float z1 = (float) Math.cos(currentAngle) * radius * radiusMultiplier + zOffset;
            float u0 = previousPoint / 360.0F * CHAIN_U_REPEATS;
            float u1 = point / 360.0F * CHAIN_U_REPEATS;

            addChainVertex(pose, vc, x0, CHAIN_BOTTOM_Y, z0, u0, 0.0F, bottomColor);
            addChainVertex(pose, vc, x1, CHAIN_BOTTOM_Y, z1, u1, 0.0F, bottomColor);
            addChainVertex(pose, vc, x1, topY, z1, u1, 0.99F, topColor);
            addChainVertex(pose, vc, x0, topY, z0, u0, 0.99F, topColor);
        }
    }

    private void submitMarkerCircle(
            LevelRenderContext context,
            PoseStack poseStack,
            LivingEntity target,
            float alpha,
            float hitAnimProgress,
            float hitRedProgress,
            int baseColor,
            float deltaTime
    ) {
        circleStep += MARKER_STEP_SPEED * deltaTime;

        float radiusScale = 1.0F - (float) Math.sin(Math.min(hitAnimProgress * 2.0F, 1.0F) * Math.PI) * MARKER_RADIUS_PULSE;
        float radius = Math.max(target.getBbWidth() * MARKER_OUTER_RADIUS, target.getBbWidth() + 0.24F) * radiusScale;
        float height = target.getBbHeight();

        markerSmoothY = lerp(markerSmoothY, smoothSinAnimation(circleStep) * height, 1.0D - Math.pow(0.88D, deltaTime));
        markerSmoothY2 = lerp(markerSmoothY2, smoothSinAnimation(circleStep - 0.35D) * height, 1.0D - Math.pow(0.90D, deltaTime));

        int color1 = lerpColor(baseColor, RED_HIT_COLOR, hitRedProgress);
        int color2 = lerpColor(lighten(baseColor, CHAIN_TOP_COLOR_MULTIPLIER), RED_HIT_COLOR, hitRedProgress);
        RenderType renderType = OrionEspRenderTypes.colorQuadsDepthTested();

        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildMarkerCircle(pose, vertexConsumer, radius, height, alpha, color1, color2)
        );
    }

    private void buildMarkerCircle(PoseStack.Pose pose, VertexConsumer vc, float radius, float height, float alpha, int color1, int color2) {
        for (int i = 0; i < MARKER_SEGMENTS; i++) {
            float progress0 = i / (float) MARKER_SEGMENTS;
            float progress1 = ((i + 1) % MARKER_SEGMENTS) / (float) MARKER_SEGMENTS;
            float mix0 = 0.5F - 0.5F * (float) Math.cos(progress0 * Math.PI * 2.0D);
            float mix1 = 0.5F - 0.5F * (float) Math.cos(progress1 * Math.PI * 2.0D);

            int segmentColor0 = lerpColor(color1, color2, mix0);
            int segmentColor1 = lerpColor(color1, color2, mix1);
            int solid0 = multAlpha(segmentColor0, 0.80F * alpha);
            int solid1 = multAlpha(segmentColor1, 0.80F * alpha);
            int clear0 = multAlpha(segmentColor0, 0.0F);
            int clear1 = multAlpha(segmentColor1, 0.0F);
            int line0 = multAlpha(segmentColor0, alpha);
            int line1 = multAlpha(segmentColor1, alpha);
            int sideGlow0 = multAlpha(segmentColor0, 0.15F * alpha);
            int sideGlow1 = multAlpha(segmentColor1, 0.15F * alpha);

            double lowerY = markerSmoothY - height * 0.5D;
            double upperY = markerSmoothY2 - height * 0.5D;
            Vec3 lower0 = circlePoint(i, MARKER_SEGMENTS, radius, lowerY);
            Vec3 lower1 = circlePoint(i + 1, MARKER_SEGMENTS, radius, lowerY);
            Vec3 upper0 = circlePoint(i, MARKER_SEGMENTS, radius, upperY);
            Vec3 upper1 = circlePoint(i + 1, MARKER_SEGMENTS, radius, upperY);

            addColorVertex(pose, vc, lower0, solid0);
            addColorVertex(pose, vc, lower1, solid1);
            addColorVertex(pose, vc, upper1, clear1);
            addColorVertex(pose, vc, upper0, clear0);

            addMarkerVerticalGlow(pose, vc, lower0, upper0, sideGlow0, clear0);
            addMarkerOutline(pose, vc, i, radius, lowerY, line0, line1);
        }
    }

    private static void addMarkerVerticalGlow(PoseStack.Pose pose, VertexConsumer vc, Vec3 lower, Vec3 upper, int lowerColor, int upperColor) {
        Vec3 normal = new Vec3(lower.x, 0.0D, lower.z);
        if (normal.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 side = normal.normalize().scale(MARKER_SIDE_GLOW_WIDTH);
        addColorVertex(pose, vc, lower.subtract(side), lowerColor);
        addColorVertex(pose, vc, lower.add(side), lowerColor);
        addColorVertex(pose, vc, upper.add(side), upperColor);
        addColorVertex(pose, vc, upper.subtract(side), upperColor);
    }

    private static void addMarkerOutline(PoseStack.Pose pose, VertexConsumer vc, int index, float radius, double y, int color0, int color1) {
        Vec3 inner0 = circlePoint(index, MARKER_SEGMENTS, Math.max(0.01D, radius - MARKER_OUTLINE_WIDTH), y);
        Vec3 outer0 = circlePoint(index, MARKER_SEGMENTS, radius + MARKER_OUTLINE_WIDTH, y);
        Vec3 outer1 = circlePoint(index + 1, MARKER_SEGMENTS, radius + MARKER_OUTLINE_WIDTH, y);
        Vec3 inner1 = circlePoint(index + 1, MARKER_SEGMENTS, Math.max(0.01D, radius - MARKER_OUTLINE_WIDTH), y);

        addColorVertex(pose, vc, inner0, color0);
        addColorVertex(pose, vc, outer0, color0);
        addColorVertex(pose, vc, outer1, color1);
        addColorVertex(pose, vc, inner1, color1);
    }

    private static Vec3 frontFacingPosition(Vec3 basePosition, Vec3 cameraPosition, LivingEntity target) {
        Vec3 cameraDirection = new Vec3(
                cameraPosition.x - basePosition.x,
                0.0D,
                cameraPosition.z - basePosition.z
        );
        if (cameraDirection.lengthSqr() < 1.0E-6D) {
            return basePosition;
        }

        double frontOffset = Math.max(0.18D, target.getBbWidth() * FRONT_OFFSET_MULTIPLIER);
        return basePosition.add(cameraDirection.normalize().scale(frontOffset));
    }

    /** Пропускает цель, если от камеры до её глаз или центра есть прямая, не перекрытая блоками. */
    private static boolean isVisibleThroughBlocks(Minecraft client, LivingEntity target) {
        if (client.level == null || client.gameRenderer == null) {
            return true;
        }

        Vec3 from = GleamMinecraft.camera(client).position();
        Vec3 eye = target.getEyePosition();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        return isClearPath(client, from, eye, target) || isClearPath(client, from, center, target);
    }

    private static boolean isClearPath(Minecraft client, Vec3 from, Vec3 to, LivingEntity target) {
        return client.level.clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                target
        )).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private LivingEntity resolveTarget(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }

        Entity picked = client.crosshairPickEntity;
        if (!(picked instanceof LivingEntity living) || living == client.player || !living.isAlive()) {
            return null;
        }

        return living;
    }

    private void clearTargetState() {
        smoothedPosition = null;
        lastTarget = null;
        lingeringTarget = null;
        lingeringUntilMs = 0L;
        anim = 0.0F;
        hitAnimSmoothed = 0.0F;
        markerSmoothY = 0.0D;
        markerSmoothY2 = 0.0D;
        lastHitPlayerId = null;
        lastHitUntilMs = 0L;
    }

    private float getDeltaTime() {
        long now = System.currentTimeMillis();
        float deltaMs = Mth.clamp(now - lastFrameTime, 1.0F, 100.0F);
        lastFrameTime = now;
        return deltaMs / TARGET_FRAME_TIME;
    }

    private void updateAnim(boolean visible, float deltaTime) {
        float step = (visible ? APPEAR_SPEED : DISAPPEAR_SPEED) * deltaTime;
        anim = Mth.clamp(anim + (visible ? step : -step), 0.0F, 1.0F);
    }

    private float getHitProgress(LivingEntity target) {
        if (lastHitPlayerId == null || target == null) {
            return 0.0F;
        }

        long now = System.currentTimeMillis();
        if (now > lastHitUntilMs) {
            lastHitPlayerId = null;
            lastHitUntilMs = 0L;
            return 0.0F;
        }

        if (!lastHitPlayerId.equals(target.getUUID())) {
            return 0.0F;
        }

        return Mth.clamp((lastHitUntilMs - now) / (float) HIT_HIGHLIGHT_MS, 0.0F, 1.0F);
    }

    private static Vec3 interpolatedPosition(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    private static float scale(String textureName) {
        return switch (textureName) {
            case "chain", "kol" -> 0.42F;
            case "ghost-glow", "ghost-triangle" -> 0.36F;
            case "skull" -> 0.34F;
            case "glow", "bloom" -> 0.56F;
            default -> BASE_SCALE;
        };
    }

    private static float rotation(String textureName) {
        long now = System.currentTimeMillis();
        double time = (now % 6283L) / 1000.0D;
        float multiplier = switch (textureName) {
            case "target1", "marker" -> 420.0F;
            case "chain", "kol" -> 260.0F;
            default -> 360.0F;
        };
        return (float) Math.sin(time) * (float) Math.toRadians(multiplier);
    }

    private static boolean shouldSpin(String textureName) {
        return !"skull".equals(textureName);
    }

    private static boolean drawReferenceGlow(String textureName) {
        return "target".equals(textureName) || "target1".equals(textureName) || "marker".equals(textureName);
    }

    private static void submitQuad(LevelRenderContext context, PoseStack poseStack, Identifier texture, int color) {
        RenderType renderType = RenderTypes.entityTranslucentEmissive(texture);
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> buildQuad(pose, vertexConsumer, color)
        );
    }

    private static void buildQuad(PoseStack.Pose pose, VertexConsumer vc, int color) {
        addVertex(pose, vc, -1.0F, -1.0F, 0.0F, 1.0F, color);
        addVertex(pose, vc, 1.0F, -1.0F, 1.0F, 1.0F, color);
        addVertex(pose, vc, 1.0F, 1.0F, 1.0F, 0.0F, color);
        addVertex(pose, vc, -1.0F, 1.0F, 0.0F, 0.0F, color);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float u, float v, int color) {
        vc.addVertex(pose, x, y, 0.0F)
                .setUv(u, v)
                .setColor(color)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static void addChainVertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(pose, x, y, z)
                .setUv(u, v)
                .setColor(color)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void addColorVertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 point, int color) {
        vc.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(color);
    }

    private static Vec3 circlePoint(int index, int segments, double radius, double y) {
        int clamped = Math.min(index, segments);
        double angle = clamped * Math.PI * 2.0D / segments;
        return new Vec3(Math.cos(angle) * radius, y, -Math.sin(angle) * radius);
    }

    private static int chainColor(int baseColor, float alphaFactor) {
        int alpha = Math.round(Mth.clamp(alphaFactor, 0.0F, 1.0F) * CHAIN_MAX_ALPHA);
        return ARGB.color(alpha, ARGB.red(baseColor), ARGB.green(baseColor), ARGB.blue(baseColor));
    }

    private static int withAlpha(int color, int alpha) {
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }

    private static int multAlpha(int color, float multiplier) {
        int alpha = Math.round(ARGB.alpha(color) * multiplier);
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }

    private static int lerpColor(int first, int second, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        int alpha = Math.round(Mth.lerp(clamped, ARGB.alpha(first), ARGB.alpha(second)));
        int red = Math.round(Mth.lerp(clamped, ARGB.red(first), ARGB.red(second)));
        int green = Math.round(Mth.lerp(clamped, ARGB.green(first), ARGB.green(second)));
        int blue = Math.round(Mth.lerp(clamped, ARGB.blue(first), ARGB.blue(second)));
        return ARGB.color(alpha, red, green, blue);
    }

    private static double smoothSinAnimation(double value) {
        double t = (Math.sin(value) + 1.0D) / 2.0D;
        return -(Math.cos(Math.PI * t) - 1.0D) / 2.0D;
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static int lighten(int color, float multiplier) {
        return ARGB.color(
                ARGB.alpha(color),
                Mth.clamp(Math.round(ARGB.red(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.green(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.blue(color) * multiplier), 0, 255)
        );
    }

    private static int darken(int color, float multiplier) {
        return ARGB.color(
                ARGB.alpha(color),
                Mth.clamp(Math.round(ARGB.red(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.green(color) * multiplier), 0, 255),
                Mth.clamp(Math.round(ARGB.blue(color) * multiplier), 0, 255)
        );
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/targetesp/" + name + ".png");
    }

    private static final class CrystalTemplate {
        private final Vec3 position;
        private final float rotationX;
        private final float rotationY;
        private final float rotationZ;
        private final float rotationSpeed;

        private CrystalTemplate(double x, double y, double z, float rotationX, float rotationY, float rotationZ) {
            this.position = new Vec3(x, y, z);
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.rotationSpeed = 0.5F + Math.floorMod(Double.doubleToLongBits(x * 31.0D + y * 17.0D + z * 13.0D), 150L) / 100.0F;
        }

        private void applyTransform(PoseStack poseStack, float hitAnim) {
            double scale = 1.0D - 0.25D * hitAnim;
            poseStack.translate(position.x * scale, position.y * scale, position.z * scale);
            float timeRotation = (System.currentTimeMillis() % 36_000L) / 100.0F * rotationSpeed;
            float pulsation = 1.0F + (float) Math.sin(System.currentTimeMillis() % 31_416L / 1000.0D * 2.0D) * 0.1F;
            poseStack.scale(pulsation, pulsation, pulsation);
            ru.orion.client.render.GleamPose.rotate(poseStack, Axis.XP.rotationDegrees(rotationX));
            ru.orion.client.render.GleamPose.rotate(poseStack, Axis.YP.rotationDegrees(rotationY + timeRotation));
            ru.orion.client.render.GleamPose.rotate(poseStack, Axis.ZP.rotationDegrees(rotationZ));
        }
    }
}
