package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Клиентское тело из связанных масс, со столкновениями на каждом шаге симуляции. */
public final class OrionRagdoll {
    public static final double RANGE_SQUARED = 96 * 96;
    private static final int LEFT_SHOULDER = 0, RIGHT_SHOULDER = 1, LEFT_HIP = 2, RIGHT_HIP = 3;
    private static final int HEAD = 4, LEFT_HAND = 5, RIGHT_HAND = 6, LEFT_FOOT = 7, RIGHT_FOOT = 8;
    private static final double[] RADII = {.16, .16, .16, .16, .235, .105, .105, .12, .12};
    private static final double[] WEIGHTS = {.42, .42, .42, .42, .8, 1, 1, .85, .85};
    private static final int SUBSTEPS = 2, ITERATIONS = 5;
    private static final double STEP = .05 / SUBSTEPS;
    private static final double SINK_TIME = .9;
    private static final double LIMB_RADIUS = .105;
    private static final double[] LIMB_SAMPLES = {.35, .55, .75, .95};

    private final WeakReference<AbstractClientPlayer> source;
    private final PlayerModel model;
    private final RenderType renderType;
    private final int light;
    private final boolean preview;
    private final float hold;
    private final Vec3[] nodes = new Vec3[RADII.length];
    private final Vec3[] previousTick = new Vec3[nodes.length];
    private final Vec3[] beforeStep = new Vec3[nodes.length];
    private final Vec3[] velocity = new Vec3[nodes.length];
    private final boolean[] grounded = new boolean[nodes.length];
    private final Link[] links;
    private final Quaternionf initialBasis;
    private long lastTick = System.nanoTime();
    private int ticks, contactTicks, quietTicks;
    private int settledAt = -1;

    public OrionRagdoll(Minecraft client, AbstractClientPlayer player, Vec3 start, Vec3 direction,
                        float yaw, float hold, boolean preview) {
        source = new WeakReference<>(player);
        this.preview = preview;
        this.hold = hold;
        var skin = player.getSkin();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        model = new PlayerModel(client.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
        model.hat.visible = player.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = player.isModelPartShown(PlayerModelPart.JACKET);
        model.leftSleeve.visible = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        renderType = RenderTypes.entityCutout(skin.body().texturePath());
        light = client.getEntityRenderDispatcher().getPackedLightCoords(player, 1F);

        initialBasis = Axis.YP.rotationDegrees(180F - yaw).rotateZ((float) Math.PI);
        Vec3 origin = start.add(0, 1.5, 0);
        Vec3[] pose = {
                pivot(model.leftArm), pivot(model.rightArm), pivot(model.leftLeg), pivot(model.rightLeg),
                new Vec3(0, -.235, 0),
                pivot(model.leftArm).add(.11, .59, -.05), pivot(model.rightArm).add(-.11, .59, .04),
                pivot(model.leftLeg).add(.04, .63, .015), pivot(model.rightLeg).add(-.04, .63, -.015)
        };
        var random = new SplittableRandom(lastTick ^ player.getUUID().getLeastSignificantBits());
        Vec3 motion = preview ? Vec3.ZERO : player.getDeltaMovement().scale(20);
        double speed = Math.clamp(Math.hypot(motion.x, motion.z), 1.3, 5.5);
        Vec3 side = new Vec3(direction.z, 0, -direction.x);
        for (int i = 0; i < nodes.length; i++) {
            Vector3f offset = initialBasis.transform(vector(pose[i]));
            nodes[i] = previousTick[i] = origin.add(offset.x, offset.y, offset.z);
            double leverage = Math.clamp(1 - pose[i].y, .25, 1.3);
            velocity[i] = direction.scale(speed * leverage + random.nextDouble(-.35, .35))
                    .add(side.scale(random.nextDouble(-.75, .75)))
                    .add(0, Math.clamp(motion.y, -7, 3) + random.nextDouble(-.65, 1.25), 0);
        }
        links = new Link[]{
                link(LEFT_SHOULDER, RIGHT_SHOULDER), link(LEFT_HIP, RIGHT_HIP),
                link(LEFT_SHOULDER, LEFT_HIP), link(RIGHT_SHOULDER, RIGHT_HIP),
                link(LEFT_SHOULDER, RIGHT_HIP), link(RIGHT_SHOULDER, LEFT_HIP),
                link(LEFT_SHOULDER, HEAD), link(RIGHT_SHOULDER, HEAD),
                link(LEFT_SHOULDER, LEFT_HAND), link(RIGHT_SHOULDER, RIGHT_HAND),
                link(LEFT_HIP, LEFT_FOOT), link(RIGHT_HIP, RIGHT_FOOT),
                new Link(LEFT_HAND, RIGHT_HAND, .22, true),
                new Link(LEFT_FOOT, RIGHT_FOOT, .22, true),
                new Link(LEFT_HAND, LEFT_HIP, .24, true),
                new Link(RIGHT_HAND, RIGHT_HIP, .24, true),
                new Link(HEAD, LEFT_HIP, .65, true),
                new Link(HEAD, RIGHT_HIP, .65, true)
        };
    }

    private Link link(int a, int b) { return new Link(a, b, nodes[a].distanceTo(nodes[b]), false); }
    private record Link(int a, int b, double length, boolean minimum) { }

    public void tick(ClientLevel level) {
        lastTick = System.nanoTime();
        ticks++;
        if (settledAt >= 0) return;
        System.arraycopy(nodes, 0, previousTick, 0, nodes.length);
        AABB area = new AABB(nodes[0], nodes[0]);
        for (Vec3 point : nodes) area = area.minmax(new AABB(point, point));
        List<VoxelShape> blocks = new ArrayList<>();
        level.getBlockCollisions(null, area.inflate(.8)).forEach(blocks::add);
        stepPhysics(blocks);
    }

    private void stepPhysics(List<VoxelShape> blocks) {
        boolean contact = false;
        for (int step = 0; step < SUBSTEPS; step++) {
            for (int i = 0; i < nodes.length; i++) {
                grounded[i] = false;
                beforeStep[i] = nodes[i];
                velocity[i] = velocity[i].scale(.986).add(0, -19.5 * STEP, 0);
                move(i, velocity[i].scale(STEP), blocks);
            }
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                for (Link link : links) solve(link, blocks);
                resolveSelfCollisions(blocks);
            }
            for (int i = 0; i < nodes.length; i++) {
                Vec3 next = nodes[i].subtract(beforeStep[i]).scale(1 / STEP);
                if (grounded[i]) {
                    contact = true;
                    double impact = velocity[i].y;
                    next = new Vec3(next.x * .72, impact < -1.2 ? -impact * .19 : Math.max(0, next.y), next.z * .72);
                }
                velocity[i] = next.lengthSqr() > 144 ? next.normalize().scale(12) : next;
            }
        }

        if (contact) contactTicks++;
        double motion = 0;
        for (int i = 0; i < nodes.length; i++) motion = Math.max(motion, nodes[i].distanceToSqr(previousTick[i]));
        quietTicks = contact && motion < .0013 ? quietTicks + 1 : 0;
        if (quietTicks >= 5 || contactTicks >= 48) {
            settledAt = ticks;
            System.arraycopy(nodes, 0, previousTick, 0, nodes.length);
        }
    }

    private void solve(Link link, List<VoxelShape> blocks) {
        Vec3 delta = nodes[link.b].subtract(nodes[link.a]);
        double distance = delta.length();
        if (distance < 1e-7 || link.minimum && distance >= link.length) return;
        double correction = (distance - link.length) / (distance * (WEIGHTS[link.a] + WEIGHTS[link.b]));
        move(link.a, delta.scale(correction * WEIGHTS[link.a]), blocks);
        move(link.b, delta.scale(-correction * WEIGHTS[link.b]), blocks);
    }

    private void resolveSelfCollisions(List<VoxelShape> blocks) {
        Vec3 shoulders = nodes[LEFT_SHOULDER].lerp(nodes[RIGHT_SHOULDER], .5);
        Vec3 hips = nodes[LEFT_HIP].lerp(nodes[RIGHT_HIP], .5);
        Vec3 down = hips.subtract(shoulders).normalize();
        Vec3 forward = nodes[LEFT_SHOULDER].subtract(nodes[RIGHT_SHOULDER]).cross(down).normalize();
        Vec3 across = down.cross(forward);
        if (across.lengthSqr() < .5) return;
        Vec3 center = shoulders.lerp(hips, .5).subtract(down.scale(.0625));
        separateFromTorso(LEFT_HIP, LEFT_FOOT, center, across, down, forward, blocks);
        separateFromTorso(RIGHT_HIP, RIGHT_FOOT, center, across, down, forward, blocks);
        separateFromTorso(LEFT_SHOULDER, LEFT_HAND, center, across, down, forward, blocks);
        separateFromTorso(RIGHT_SHOULDER, RIGHT_HAND, center, across, down, forward, blocks);
    }

    private void separateFromTorso(int joint, int end, Vec3 center, Vec3 across, Vec3 down, Vec3 forward,
                                   List<VoxelShape> blocks) {
        // Корпус следует за плечами и тазом, а точки вдоль конечности проверяют её объём между суставами.
        final double halfWidth = .25, halfHeight = .375, halfDepth = .13;
        for (double t : LIMB_SAMPLES) {
            Vec3 relative = nodes[joint].lerp(nodes[end], t).subtract(center);
            double x = relative.dot(across), y = relative.dot(down), z = relative.dot(forward);
            double dx = x - Math.clamp(x, -halfWidth, halfWidth);
            double dy = y - Math.clamp(y, -halfHeight, halfHeight);
            double dz = z - Math.clamp(z, -halfDepth, halfDepth);
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= LIMB_RADIUS * LIMB_RADIUS) continue;
            Vec3 correction;
            if (distanceSquared > 1e-10) {
                double distance = Math.sqrt(distanceSquared);
                correction = across.scale(dx).add(down.scale(dy)).add(forward.scale(dz))
                        .scale((LIMB_RADIUS - distance) / distance);
            } else {
                double side = halfWidth - Math.abs(x), bottom = halfHeight - Math.abs(y), front = halfDepth - Math.abs(z);
                if (bottom <= side && bottom <= front) correction = down.scale(Math.copySign(bottom + LIMB_RADIUS, y));
                else if (side <= front) correction = across.scale(Math.copySign(side + LIMB_RADIUS, x));
                else correction = forward.scale(Math.copySign(front + LIMB_RADIUS, z));
            }
            // Сустав остаётся закреплён на корпусе: меняем угол конечности через её свободный конец.
            double strength = Math.min(1, .14 / correction.length());
            move(end, correction.scale(.85 * strength / t), blocks);
            Vec3 reaction = correction.scale(-.15 * strength);
            for (int anchor = LEFT_SHOULDER; anchor <= RIGHT_HIP; anchor++) move(anchor, reaction, blocks);
            center = center.add(reaction);
        }
    }

    private void move(int i, Vec3 delta, List<VoxelShape> blocks) {
        Vec3 from = nodes[i];
        if (blocks.isEmpty()) {
            nodes[i] = from.add(delta);
            return;
        }
        double r = RADII[i];
        AABB box = new AABB(from.x - r, from.y - r, from.z - r, from.x + r, from.y + r, from.z + r);
        double y = Shapes.collide(Direction.Axis.Y, box, blocks, delta.y);
        grounded[i] |= y > delta.y + 1e-6;
        box = box.move(0, y, 0);
        double x = Shapes.collide(Direction.Axis.X, box, blocks, delta.x);
        double z = Shapes.collide(Direction.Axis.Z, box.move(x, 0, 0), blocks, delta.z);
        nodes[i] = from.add(x, y, z);
    }

    public boolean preview() { return preview; }
    public boolean replaces(AbstractClientPlayer player) {
        return !preview && source.get() == player && !expired();
    }
    public boolean expired() {
        return settledAt < 0 ? ticks >= 100 : (ticks - settledAt) * .05 >= hold + SINK_TIME;
    }

    public void render(LevelRenderContext context, Vec3 camera, long now) {
        if (expired() || nodes[LEFT_HIP].distanceToSqr(camera) > RANGE_SQUARED) return;
        double partial = Math.clamp((now - lastTick) / 50_000_000D, 0, 1);
        double sink = settledAt < 0 ? 0 : Math.clamp(((ticks - settledAt + partial) * .05 - hold) / SINK_TIME, 0, 1);
        Vec3 offset = camera.add(0, .95 * sink * sink * (3 - 2 * sink), 0);
        Vec3 ls = point(LEFT_SHOULDER, partial), rs = point(RIGHT_SHOULDER, partial);
        Vec3 lh = point(LEFT_HIP, partial), rh = point(RIGHT_HIP, partial);
        Vec3 shoulders = ls.lerp(rs, .5), hips = lh.lerp(rh, .5);
        Vec3 down = hips.subtract(shoulders).normalize();
        Vec3 forward = ls.subtract(rs).normalize().cross(down).normalize();
        Vec3 across = down.cross(forward).normalize();
        Quaternionf basis = across.lengthSqr() < .5 ? initialBasis : new Quaternionf().setFromNormalized(
                new Matrix3f().setColumn(0, vector(across)).setColumn(1, vector(down)).setColumn(2, vector(forward)));
        Vec3 neck = shoulders.subtract(down.scale((model.leftArm.y + model.rightArm.y) / 32D));
        submit(context, model.body, neck, basis, offset);
        submit(context, model.head, neck, aim(basis, neck, point(HEAD, partial), -1), offset);
        submit(context, model.leftArm, ls, aim(basis, ls, point(LEFT_HAND, partial), 1), offset);
        submit(context, model.rightArm, rs, aim(basis, rs, point(RIGHT_HAND, partial), 1), offset);
        submit(context, model.leftLeg, lh, aim(basis, lh, point(LEFT_FOOT, partial), 1), offset);
        submit(context, model.rightLeg, rh, aim(basis, rh, point(RIGHT_FOOT, partial), 1), offset);
    }

    private Vec3 point(int index, double partial) { return previousTick[index].lerp(nodes[index], partial); }
    private static Vec3 pivot(ModelPart part) { return new Vec3(part.x / 16D, part.y / 16D, part.z / 16D); }
    private static Vector3f vector(Vec3 point) { return new Vector3f((float) point.x, (float) point.y, (float) point.z); }

    private static Quaternionf aim(Quaternionf basis, Vec3 from, Vec3 to, int sign) {
        Vec3 axis = to.subtract(from).normalize().scale(sign);
        if (axis.lengthSqr() < .5) return basis;
        return new Quaternionf().rotationTo(basis.transform(new Vector3f(0, 1, 0)), vector(axis)).mul(basis);
    }

    private void submit(LevelRenderContext context, ModelPart part, Vec3 pivot, Quaternionf rotation, Vec3 offset) {
        Vec3 relative = pivot.subtract(offset);
        PoseStack stack = context.poseStack();
        stack.pushPose();
        stack.translate(relative.x, relative.y, relative.z);
        stack.rotate(rotation);
        stack.translate(-part.x / 16F, -part.y / 16F, -part.z / 16F);
        context.submitNodeCollector().submitModelPart(part, stack, renderType, light, OverlayTexture.NO_OVERLAY, null);
        stack.popPose();
    }
}
