package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Объёмные крылья со сглаженным движением, закреплённые на спине игрока. */
public final class OrionWings {
    private final OrionConfigManager manager;
    private final Map<UUID, OrionWingMotion> motions = new HashMap<>();
    private long lastFrame;
    public OrionWings(OrionConfigManager manager) { this.manager = manager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) { motions.clear(); lastFrame=0; return; }
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        long now=System.nanoTime();
        float dt=lastFrame==0?0F:Math.min(1F,Math.max(0F,(now-lastFrame)/1_000_000_000F));
        lastFrame=now;
        Set<UUID> visible=new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || !player.isAlive() || player.isInvisibleTo(client.player)) continue;
            boolean local = player == client.player;
            if (local && client.options.getCameraType().isFirstPerson()) continue;
            int style = local ? (manager.getConfig().isWingsEnabled() ? manager.getConfig().getWingsStyle() + 1 : 0)
                    : OrionBadgePresence.wingsStyle(player.getUUID());
            if (style < 1 || style > 2) continue;
            if(player.position().distanceToSqr(camera.pos)>16384D) continue;
            visible.add(player.getUUID());
            OrionWingMotion motion=motions.computeIfAbsent(player.getUUID(),ignored->new OrionWingMotion());
            float speed=(float)Math.hypot(player.getX()-player.xOld,player.getZ()-player.zOld)*20F;
            motion.update(dt,speed,player.isSprinting(),player.isFallFlying(),player.isCrouching(),player.isVisuallySwimming(),!player.onGround(),player.getViewXRot(partial));
            Vec3 relative = player.getPosition(partial).subtract(camera.pos);
            float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
            boolean detail=player.position().distanceToSqr(camera.pos)<1024D;
            float tipAngle=(float)Math.toRadians(-motion.tipFold),tipLift=(float)Math.toRadians(motion.tipLift);
            float cy=(float)Math.cos(tipAngle),sy=(float)Math.sin(tipAngle),cz=(float)Math.cos(tipLift),sz=(float)Math.sin(tipLift);
            PoseStack pose = context.poseStack();
            pose.pushPose();
            pose.translate(relative.x, relative.y + motion.rootHeight, relative.z);
            pose.rotate(Axis.YP.rotationDegrees(180F - bodyYaw));
            pose.rotate(Axis.XP.rotationDegrees(motion.bodyPitch));
            pose.translate(0F, 0F, .16F);
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.scale(side, 1F, 1F);
                // Положительный Z — за спиной. При складывании кончики уходят назад.
                pose.rotate(Axis.YP.rotationDegrees(-motion.rootFold));
                pose.rotate(Axis.ZP.rotationDegrees(motion.flap));
                context.submitNodeCollector().submitCustomGeometry(pose, OrionEspRenderTypes.cosmeticWings(),
                        (currentPose, vertices) -> draw(currentPose, vertices, style, detail,cy,sy,cz,sz));
                pose.popPose();
            }
            pose.popPose();
        }
        motions.keySet().retainAll(visible);
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer vc, int style,boolean detail,float cy,float sy,float cz,float sz) {
        for (OrionWingMesh.Quad face : OrionWingMesh.forStyle(style,detail)) {
            vertex(pose, vc, face.a(), face.color(),cy,sy,cz,sz);
            vertex(pose, vc, face.b(), face.color(),cy,sy,cz,sz);
            vertex(pose, vc, face.c(), face.color(),cy,sy,cz,sz);
            vertex(pose, vc, face.d(), face.color(),cy,sy,cz,sz);
        }
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, OrionWingMesh.Point p, int color,float cy,float sy,float cz,float sz) {
        float x=p.x(),y=p.y(),z=p.z();
        if(x>.82F){
            float weight=Math.min(1F,(x-.82F)/.50F);weight=weight*weight*(3F-2F*weight);
            float dx=x-.82F,dy=y-.36F,dz=z-.25F,rx=dx*cy+dz*sy;
            float tx=.82F+rx*cz-dy*sz,ty=.36F+rx*sz+dy*cz,tz=.25F-dx*sy+dz*cy;
            x+=(tx-x)*weight;y+=(ty-y)*weight;z+=(tz-z)*weight;
        }
        vc.addVertex(pose, x, y, z).setColor(color);
    }
}
