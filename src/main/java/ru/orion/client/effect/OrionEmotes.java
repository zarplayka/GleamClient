package ru.orion.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.orion.client.OrionClient;

import static ru.orion.client.effect.OrionEmoteClips.*;

import java.util.HashMap;
import java.util.Map;

import static ru.orion.client.effect.OrionEmoteClips.*;

/** Ключевые позы танцев и состояние синхронизированных моделей игроков. */
public final class OrionEmotes {
    public static final String[] NAMES = {"Шаффл", "Робот", "Диско", "Флосс", "Волна", "Праздник"};
    public static final double DURATION = 12D;
    private record Dance(int id, long start) {}
    private static final Map<Integer, Dance> VISIBLE = new HashMap<>();
    private static Dance local;
    private static Vec3 origin;
    private static ClientLevel world;

    private OrionEmotes() {}

    public static void register() { ClientTickEvents.END_CLIENT_TICK.register(OrionEmotes::tick); }

    public static boolean canDance(Player player) {
        return player != null && player.isAlive() && player.onGround() && !player.isPassenger()
                && !player.isCrouching() && !player.isUsingItem() && !player.isFallFlying()
                && !player.isVisuallySwimming() && !player.isSleeping();
    }

    public static void start(int id) {
        Minecraft client = Minecraft.getInstance();
        if (id < 1 || id > NAMES.length || !canDance(client.player)) return;
        world = client.level;
        local = new Dance(id, System.nanoTime());
        origin = client.player.position();
        VISIBLE.put(client.player.getId(), local);
        OrionBadgePresence.sendEmote(id);
    }

    public static void stop() {
        if (local == null) return;
        Minecraft client = Minecraft.getInstance();
        local = null;
        origin = null;
        if (client.player != null) VISIBLE.remove(client.player.getId());
        OrionBadgePresence.sendEmote(0);
    }

    private static void tick(Minecraft client) {
        if (client.level != world) {
            local = null;
            origin = null;
            VISIBLE.clear();
            world = client.level;
        }
        if (client.player == null || client.level == null) return;
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isEmotesEnabled()) {
            stop();
            VISIBLE.clear();
            return;
        }
        long now = System.nanoTime();
        if (local != null && (!canDance(client.player) || (now - local.start) / 1e9 >= DURATION
                || client.player.position().distanceToSqr(origin) > .0025
                || client.options.keyUp.isDown() || client.options.keyDown.isDown()
                || client.options.keyLeft.isDown() || client.options.keyRight.isDown()
                || client.options.keyJump.isDown() || client.options.keyAttack.isDown())) stop();
        VISIBLE.clear();
        if (local != null) VISIBLE.put(client.player.getId(), local);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == client.player || !canDance(player)) continue;
            if (player.getDeltaMovement().horizontalDistanceSqr() > .0025) continue;
            var remote = OrionBadgePresence.emote(player.getUUID());
            if (remote != null && now - remote.localStart() < (long) (DURATION * 1e9)) {
                VISIBLE.put(player.getId(), new Dance(remote.id(), remote.localStart()));
            }
        }
    }

    public static void apply(HumanoidModel<?> model, AvatarRenderState state) {
        if (state instanceof ru.orion.client.gui.OrionCosmeticPreview preview) {
            if (preview.dances()) applyPose(model, 3, System.nanoTime() / 1e9, 1F);
            return;
        }
        var config = OrionClient.getConfigOrNull();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.isEmotesEnabled() || (config.isNoAnimationsEnabled()
                && client.player != null && state.id != client.player.getId())) return;
        Dance dance = VISIBLE.get(state.id);
        if (dance == null) return;
        double age = (System.nanoTime() - dance.start) / 1e9;
        if (age < 0 || age >= DURATION) return;
        float blend = (float) Math.min(1D, Math.min(age / .20D, (DURATION - age) / .25D));
        applyPose(model, dance.id, age, blend * blend * (3F - 2F * blend));
    }

    private static void applyPose(HumanoidModel<?> model, int danceId, double age, float blend) {
        float[] pose = OrionEmoteClips.sample(danceId, age);
        float swing = pose[SHIFT] * blend, rise = pose[RISE] * blend;
        float bodyX = radians(pose[BODY]), bodyY = radians(pose[BODY + 1]), bodyZ = radians(pose[BODY + 2]);
        Quaternionf torso = new Quaternionf().rotationZYX(bodyZ, bodyY, bodyX);
        translateTorsoPart(model.body, torso, swing, rise, blend);
        translateTorsoPart(model.head, torso, swing, rise, blend);
        translateTorsoPart(model.rightArm, torso, swing, rise, blend);
        translateTorsoPart(model.leftArm, torso, swing, rise, blend);
        rotate(model.body, bodyX, bodyY, bodyZ, blend);
        rotate(model.head, radians(pose[HEAD]), model.head.yRot * .35F + radians(pose[HEAD + 1]),
                radians(pose[HEAD + 2]), blend);
        poseArm(model.rightArm, pose, RIGHT_ARM, torso, blend);
        poseArm(model.leftArm, pose, LEFT_ARM, torso, blend);
        poseLeg(model.rightLeg, pose[RIGHT_LEG], pose[RIGHT_LEG + 1], swing, blend);
        poseLeg(model.leftLeg, pose[LEFT_LEG], pose[LEFT_LEG + 1], swing, blend);
    }

    private static void translateTorsoPart(ModelPart part, Quaternionf torso, float sway, float rise, float blend) {
        // Rotate the shoulders and neck around the hips so adjacent parts travel together.
        Vector3f moved = torso.transform(new Vector3f(part.x, part.y - 12F, part.z));
        part.x += sway + (moved.x - part.x) * blend;
        part.y += -rise + (moved.y + 12F - part.y) * blend;
        part.z += (moved.z - part.z) * blend;
    }

    private static void poseArm(ModelPart arm, float[] pose, int channel, Quaternionf torso, float blend) {
        // Roll opens the arm away from the shoulder even when the pitch raises it overhead.
        Quaternionf target = new Quaternionf(torso).rotateXYZ(radians(pose[channel]),
                radians(pose[channel + 1]), radians(pose[channel + 2]));
        Quaternionf rotation = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).slerp(target, blend);
        Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
        arm.setRotation(angles.x, angles.y, angles.z);
    }

    private static void poseLeg(ModelPart leg, float pitch, float lift, float sway, float blend) {
        float angle = radians(pitch);
        leg.x += sway;
        // Account for the rotation shortening the vertical reach of a 12-pixel leg.
        leg.y += (12F * (1F - (float) Math.cos(angle)) - lift) * blend;
        rotate(leg, angle, 0F, 0F, blend);
    }

    private static void rotate(ModelPart part, float x, float y, float z, float blend) {
        part.xRot += (x - part.xRot) * blend;
        part.yRot += (y - part.yRot) * blend;
        part.zRot += (z - part.zRot) * blend;
    }

    private static float radians(float degrees) { return degrees * ((float) Math.PI / 180F); }
}
