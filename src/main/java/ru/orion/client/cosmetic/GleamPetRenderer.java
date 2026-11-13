package ru.orion.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
//? if <26.2 {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionBadgePresence;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GleamPetRenderer {
    private static final float SCALE = .68F;
    private static final long PREVIEW_ACTION_PERIOD = 5_000L;
    private static final EnumMap<GleamPet, GleamPetModel> MODELS = new EnumMap<>(GleamPet.class);

    private final OrionConfigManager manager;
    private LocalPlayer owner;
    private ClientLevel level;
    private GleamPet equipped;
    private final GleamPetFollow follow = new GleamPetFollow();
    private final Map<UUID, RemotePet> remotePets = new HashMap<>();
    private ClientLevel remoteLevel;

    private record RemotePet(Player owner, GleamPet pet, GleamPetFollow follow) {
        private RemotePet(Player owner, GleamPet pet) {
            this(owner, pet, new GleamPetFollow());
        }
    }

    public GleamPetRenderer(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void tick(Minecraft client) {
        tickRemote(client);
        GleamPet pet = GleamPet.byId(manager.getConfig().getCosmeticPet());
        LocalPlayer player = client.player;
        if (pet == null || player == null || client.level == null || !player.isAlive()
                || player.isSpectator() || player.isInvisible()) {
            clear();
            return;
        }
        if (owner != player || level != client.level || equipped != pet) {
            clear();
            owner = player;
            level = client.level;
            equipped = pet;
        }

        follow.tick(level, player.position(), pet);
    }

    private void tickRemote(Minecraft client) {
        if (remoteLevel != client.level || client.player == null) {
            remotePets.clear();
            remoteLevel = client.level;
        }
        if (remoteLevel == null || client.player == null) return;
        remotePets.entrySet().removeIf(entry -> {
            RemotePet state = entry.getValue();
            return state.owner().isRemoved() || !visibleRemote(state.owner(), client.player)
                    || OrionBadgePresence.pet(entry.getKey()) != state.pet();
        });
        for (Player player : remoteLevel.players()) {
            if (player == client.player || !visibleRemote(player, client.player)) continue;
            GleamPet pet = OrionBadgePresence.pet(player.getUUID());
            if (pet == null) continue;
            RemotePet state = remotePets.get(player.getUUID());
            if (state == null || state.owner() != player) {
                state = new RemotePet(player, pet);
                remotePets.put(player.getUUID(), state);
            }
            state.follow().tick(remoteLevel, player.position(), pet);
        }
    }

    private static boolean visibleRemote(Player player, LocalPlayer viewer) {
        return player.isAlive() && !player.isSpectator() && !player.isInvisibleTo(viewer);
    }

    private void clear() {
        owner = null;
        level = null;
        equipped = null;
        follow.reset();
    }

    public boolean obstructs(VoxelShape shape) {
        var client = Minecraft.getInstance();
        if (client.level != level || client.player != owner || owner == null || equipped == null
                || !equipped.id().equals(manager.getConfig().getCosmeticPet()) || !owner.isAlive()
                || owner.isSpectator() || owner.isInvisible() || shape.isEmpty()) return false;
        var current = follow.frame(1F);
        var last = follow.frame(0F);
        if (current == null || last == null) return false;
        AABB occupied = placementBounds(current.position()).minmax(placementBounds(last.position()));
        for (AABB block : shape.toAabbs()) {
            if (block.intersects(occupied)) return true;
        }
        return false;
    }

    private AABB placementBounds(Vec3 at) {
        if (equipped == GleamPet.MOON_MOTH) {
            return new AABB(at.x - .56D, at.y + .22D, at.z - .56D,
                    at.x + .56D, at.y + .92D, at.z + .56D);
        }
        return new AABB(at.x - .32D, at.y, at.z - .32D, at.x + .32D, at.y + .66D, at.z + .32D);
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        var camera = context.levelState().cameraRenderState;
        if (client.player == null || client.level == null || camera == null || !camera.initialized || camera.pos == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (equipped != null && client.player == owner && client.level == level) {
            renderPet(context, level, camera.pos, equipped, follow, partial);
        }
        if (client.level != remoteLevel) return;
        for (RemotePet state : remotePets.values()) {
            if (visibleRemote(state.owner(), client.player)) {
                renderPet(context, remoteLevel, camera.pos, state.pet(), state.follow(), partial);
            }
        }
    }

    private static void renderPet(LevelRenderContext context, ClientLevel level, Vec3 camera,
                                  GleamPet pet, GleamPetFollow movement, float partial) {
        var frame = movement.frame(partial);
        if (frame == null) return;
        Vec3 point = frame.position();
        Vec3 relative = point.subtract(camera);
        if (relative.lengthSqr() > 16384D) return;
        //? if >=26.2 {
        int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(point.add(0D, .5D, 0D)));
        //?} else {
        /*int light = LevelRenderer.getLightCoords(level, BlockPos.containing(point.add(0D, .5D, 0D)));
        *///?}
        PoseStack pose = context.poseStack();
        pose.pushPose();
        pose.translate(relative.x, relative.y, relative.z);
        ru.orion.client.render.GleamPose.rotateDegrees(pose, Axis.YP, 180F - frame.yaw());
        submit(pet, pose, context.submitNodeCollector(), light, frame.ageMillis(), frame.actionMillis(),
                frame.stride(), frame.movement());
        pose.popPose();
    }

    public static void submitPreview(GleamPet pet, PoseStack pose, SubmitNodeCollector collector, long ageMillis) {
        pose.translate(-.88F, pet.grounded() ? .02F : .87F, -.10F);
        long phase = ageMillis % PREVIEW_ACTION_PERIOD;
        submit(pet, pose, collector, LightCoordsUtil.FULL_BRIGHT, ageMillis, phase - 1_700L, 0F, 0F);
    }

    private static void submit(GleamPet pet, PoseStack pose, SubmitNodeCollector collector, int light,
                               long ageMillis, long actionTime, float stride, float movement) {
        if (!MODELS.containsKey(pet)) {
            GleamPetModel model = null;
            try {
                model = GleamPetModel.load(pet);
            } catch (IOException | RuntimeException exception) {
                LoggerFactory.getLogger(OrionClient.MOD_ID).error("Не удалось загрузить питомца {}", pet.id(), exception);
            }
            MODELS.put(pet, model);
        }
        var model = MODELS.get(pet);
        if (model == null) return;
        pose.scale(-SCALE, -SCALE, SCALE);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(pet.texture()),
                (transform, vertices) -> model.render(transform, vertices, light, ageMillis, actionTime, stride, movement));
    }
}
