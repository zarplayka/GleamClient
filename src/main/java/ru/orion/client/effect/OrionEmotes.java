package ru.orion.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;

import java.util.HashMap;
import java.util.Map;

/** Процедурные танцы и состояние синхронизированных моделей игроков. */
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
        var config = OrionClient.getConfigOrNull();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.isEmotesEnabled() || (config.isNoAnimationsEnabled()
                && client.player != null && state.id != client.player.getId())) return;
        Dance dance = VISIBLE.get(state.id);
        if (dance == null) return;
        double age = (System.nanoTime() - dance.start) / 1e9;
        if (age < 0 || age >= DURATION) return;
        float blend = (float) Math.min(1D, Math.min(age / .20D, (DURATION - age) / .25D));
        float t = (float) (age * Math.PI * 2D);
        float beat = (float) Math.sin(t * 1.8F);
        float alternate = (float) Math.cos(t * 1.8F);
        float bodyX = 0, bodyY = 0, bodyZ = 0;
        float rightX = 0, leftX = 0, rightZ = 0, leftZ = 0, rightLeg = 0, leftLeg = 0;
        switch (dance.id) {
            case 1 -> {
                rightLeg = beat * .8F; leftLeg = -rightLeg;
                rightX = alternate * .65F; leftX = -rightX;
                rightZ = .18F; leftZ = -.18F; bodyZ = beat * .08F;
            }
            case 2 -> {
                float step = Math.round(beat * 3F) / 3F;
                rightX = -1.1F + step * .7F; leftX = -1.1F - step * .7F;
                rightZ = -.4F; leftZ = .4F; bodyY = Math.round(alternate * 3F) / 3F * .25F;
            }
            case 3 -> {
                rightX = -2.4F - beat * .4F; rightZ = -.65F;
                leftX = .3F; leftZ = -.3F;
                bodyZ = beat * .18F; bodyY = alternate * .2F;
                rightLeg = beat * .25F; leftLeg = -rightLeg;
            }
            case 4 -> {
                rightX = beat * 1.15F; leftX = rightX;
                rightZ = alternate * .65F; leftZ = alternate * .65F;
                bodyZ = -alternate * .18F; bodyY = beat * .22F;
            }
            case 5 -> {
                rightZ = -1.35F + beat * .65F;
                leftZ = 1.35F + (float) Math.sin(t * 1.8F - 1.4F) * .65F;
                rightX = alternate * .25F; leftX = -alternate * .25F;
                bodyZ = beat * .12F;
            }
            case 6 -> {
                rightX = leftX = -2.5F + beat * .55F;
                rightZ = -.4F; leftZ = .4F;
                bodyX = Math.abs(beat) * .14F;
                rightLeg = beat * .35F; leftLeg = -rightLeg;
            }
            default -> { return; }
        }
        model.body.xRot = lerp(model.body.xRot, bodyX, blend);
        model.body.yRot = lerp(model.body.yRot, bodyY, blend);
        model.body.zRot = lerp(model.body.zRot, bodyZ, blend);
        model.head.xRot = lerp(model.head.xRot, -bodyX + beat * .08F, blend);
        model.head.zRot = lerp(model.head.zRot, bodyZ * .5F, blend);
        model.rightArm.xRot = lerp(model.rightArm.xRot, rightX, blend);
        model.leftArm.xRot = lerp(model.leftArm.xRot, leftX, blend);
        model.rightArm.zRot = lerp(model.rightArm.zRot, rightZ, blend);
        model.leftArm.zRot = lerp(model.leftArm.zRot, leftZ, blend);
        model.rightLeg.xRot = lerp(model.rightLeg.xRot, rightLeg, blend);
        model.leftLeg.xRot = lerp(model.leftLeg.xRot, leftLeg, blend);
        // В 26.3 верхние слои скина — дочерние части и наследуют позу.
    }

    private static float lerp(float from, float to, float blend) { return from + (to - from) * blend; }
}
