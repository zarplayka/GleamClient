package ru.orion.client.basketball.engine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class OrionBasketballScene {
   private static final Identifier MOWMWDOMTOCVRHWDIMTOCVRHWDIMTO = Identifier.fromNamespaceAndPath(
      "orion-client", "textures/basketball.png"
   );
   private static final Identifier CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = Identifier.fromNamespaceAndPath(
      "orion-client", "textures/basket_white.png"
   );
   private static final int HRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -1551330;
   private static final int IRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 1156248831;
   private static final int ORWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -855310;
   private static final int RRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -14013133;
   private static final int DRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -723728;
   private static final int TRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -1325400065;
   private static final double VRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 96.0;
   private static final double WRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 42.0;
   private static final int MRWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 120;
   private static final Map<String, OrionBasketballNet> CDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new HashMap<>();
   private static long HDWMWDOMTOCVRHWDIMTOCVRHWDIMTO;

   private OrionBasketballScene() {
   }

   public static void register() {
      net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(OrionBasketballScene::render);
   }

   private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0) {
      Minecraft var1 = Minecraft.getInstance();
      OrionBasketballSettings var2 = OrionBasketballSettings.get();
      if (var2.basket && var1.level != null && var1.player != null) {
         LevelRenderState var3 = var0.levelState();
         if (var3 != null && var3.cameraRenderState != null) {
            CameraRenderState var4 = var3.cameraRenderState;
            if (var4.pos != null) {
               long var5 = System.nanoTime();
               double var7 = HDWMWDOMTOCVRHWDIMTOCVRHWDIMTO == 0L ? 0.02 : (var5 - HDWMWDOMTOCVRHWDIMTOCVRHWDIMTO) / 1.0E9;
               HDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var5;
               float var9 = var1.getDeltaTracker().getGameTimeDeltaPartialTick(false);
               helper121(var0, var1, var2, var4.pos, var7);
               helper119(var0, var1, var4.pos, var9);
               helper128(var0, var1, var2, var4.pos, var9);
               var game = ru.orion.client.OrionClient.getBasketballOrNull();
               if (game != null && game.match().active()) renderMatchCourt(var0, game.match(), var4.pos);
            }
         }
      } else {
         CDWMWDOMTOCVRHWDIMTOCVRHWDIMTO.clear();
      }
   }

   private static void renderMatchCourt(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
                                        OrionBasketballMatch match, Vec3 camera) {
      Vec3 origin = match.origin();
      PoseStack stack = context.poseStack();
      stack.pushPose();
      try {
         stack.translate(origin.x-camera.x, origin.y-camera.y+.025, origin.z-camera.z);
         var marker = match.startMarker().subtract(origin);
         boolean kickoff = match.kickoff();
         context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
               (pose, vertices) -> {
                  int light = 0xF000F0;
                  helper131(pose,vertices,light,-10,-14,10,-14,.05F);
                  helper131(pose,vertices,light,10,-14,10,14,.05F);
                  helper131(pose,vertices,light,10,14,-10,14,.05F);
                  helper131(pose,vertices,light,-10,14,-10,-14,.05F);
                  helper131(pose,vertices,light,-10,0,10,0,.05F);
                  helper133(pose,vertices,light,0,0,1.8F,.05F);
                  if (kickoff) helper133(pose,vertices,light,(float)marker.x,(float)marker.z,1F,.10F);
               });
      } finally {
         stack.popPose();
      }
   }

   public static Identifier helper118() {
      return MOWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   private static void helper119(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, Minecraft var1, Vec3 var2, float var3) {
      if (OrionBasketballController.ballExists()) {
         if (OrionBasketballController.mode() != OrionBasketballController.Mode.HELD
            || !OrionBasketballController.localOwner(var1)
            || !OrionBasketballSettings.get().basketHands
            || !var1.options.getCameraType().isFirstPerson()) {
            OrionBallState var4 = OrionBasketballController.ball();
            Vec3 var5 = helper120(var1, var4, var3);
            int var6 = helper134(var1, var5.x, var5.y, var5.z);
            PoseStack var7 = new PoseStack();
            var7.translate(var5.x - var2.x, var5.y - var2.y, var5.z - var2.z);
            var7.rotate(new Quaternionf(var4.prevTurn).slerp(var4.turn, var3));
            var7.scale((float)var4.radius, (float)var4.radius, (float)var4.radius);
            var0.submitNodeCollector()
               .submitCustomGeometry(
                  var7,
                  RenderTypes.entityCutout(MOWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
                  (var1x, var2x) -> OrionBasketballMesh.VDVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var1x, var2x, var6, -1)
               );
         }
      }
   }

   private static Vec3 helper120(Minecraft var0, OrionBallState var1, float var2) {
      if (OrionBasketballController.mode() != OrionBasketballController.Mode.HELD) {
         return new Vec3(Mth.lerp(var2, var1.prevX, var1.x), Mth.lerp(var2, var1.prevY, var1.y), Mth.lerp(var2, var1.prevZ, var1.z));
      }

      LocalPlayer var3 = var0.player;
      String var4 = var1.owner;
      if (var4 != null && !var4.isEmpty() && var3 != null && !var4.equals(var3.getName().getString()) && var0.level != null) {
         for (AbstractClientPlayer var6 : var0.level.players()) {
            if (var6.getName().getString().equals(var4)) {
               float var7 = var6.getViewXRot(var2);
               float var8 = var6.getViewYRot(var2);
               Vec3 var9 = OrionBasketballController.lookDirection(var7, var8);
               Vec3 var10 = OrionBasketballController.lookDirection(var7 - 90.0F, var8);
               return OrionBasketballController.helper38(var6.getEyePosition(var2), var9, var10, var9.cross(var10), 0.0F);
            }
         }
      }

      float var11 = var3.getViewXRot(var2);
      float var12 = var3.getViewYRot(var2);
      Vec3 var13 = OrionBasketballController.lookDirection(var11, var12);
      Vec3 var14 = OrionBasketballController.lookDirection(var11 - 90.0F, var12);
      return OrionBasketballController.helper38(
         var3.getEyePosition(var2), var13, var14, var13.cross(var14), OrionBasketballController.charge(var2)
      );
   }

   private static void helper121(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, Minecraft var1, OrionBasketballSettings var2, Vec3 var3, double var4) {
      List var6 = OrionBasketballController.hoops();
      if (var6.isEmpty()) {
         CDWMWDOMTOCVRHWDIMTOCVRHWDIMTO.clear();
      } else {
         OrionBallState var7 = OrionBasketballController.ball();
         boolean var8 = OrionBasketballController.ballExists();
         HashSet var9 = new HashSet();

         for (int var10 = 0; var10 < var6.size(); var10++) {
            OrionHoop var11 = (OrionHoop)var6.get(var10);
            var9.add(var11.id);
            double var12 = var11.x - var3.x;
            double var14 = var11.y - var3.y;
            double var16 = var11.z - var3.z;
            double var18 = var12 * var12 + var14 * var14 + var16 * var16;
            if (!(var18 > 9216.0)) {
               int var20 = helper134(var1, var11.x, var11.y + 0.4, var11.z);
               helper123(var0, var11, var3, var20);
               helper124(var0, var11, var3, var20);
               if (var18 < 1764.0) {
                  helper125(var0, var11, var3, var7, var8, var4, var2, var20);
               }

               var game = ru.orion.client.OrionClient.getBasketballOrNull();
               if (var2.basketLines && (game == null || !game.match().active())) {
                  helper126(var0, var11, var3, var20);
               }
            }
         }

         var game = ru.orion.client.OrionClient.getBasketballOrNull();
         if (var2.basketLines && var6.size() >= 2 && (game == null || !game.match().active())) {
            helper127(var0, var6, var3, var1);
         }

         CDWMWDOMTOCVRHWDIMTOCVRHWDIMTO.keySet().retainAll(var9);
      }
   }

   private static PoseStack helper122(OrionHoop var0, Vec3 var1) {
      PoseStack var2 = new PoseStack();
      var2.translate(var0.x - var1.x, var0.y - var1.y, var0.z - var1.z);
      var2.rotate(Axis.YP.rotationDegrees(-var0.yaw));
      return var2;
   }

   private static void helper123(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, OrionHoop var1, Vec3 var2, int var3) {
      float var4 = (float)var1.scale;
      float var5 = (float)var1.rimRadius();
      float var6 = (float)var1.boardOffset();
      float var7 = (float)(var1.boardY() - var1.y);
      float var8 = (float)(var1.boardHeight() * 0.5);
      float var9 = 0.75F * var4;
      float var10 = -var6 - var9;
      float var11 = (float)(var1.base - var1.y);
      float var12 = var7 + var8 * 0.2F;
      PoseStack var13 = helper122(var1, var2);
      var13.scale(var5, var5, var5);
      var0.submitNodeCollector()
         .submitCustomGeometry(
            var13,
            RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
            (var1x, var2x) -> OrionBasketballMesh.CTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var1x, var2x, var3, -1551330)
         );
      float var14 = var6 - (float)(0.05 * var1.scale * 0.5);
      PoseStack var15 = helper122(var1, var2);
      var15.translate(0.0F, 0.0F, -(var5 + var14) * 0.5F);
      var0.submitNodeCollector()
         .submitCustomGeometry(
            var15,
            RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
            (var4x, var5x) -> OrionBasketballMesh.OTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var4x, var5x, var3, -1551330, 0.05F * var4, 0.028F * var4, (var14 - var5) * 0.5F
            )
         );
      PoseStack var16 = helper122(var1, var2);
      var16.translate(0.0F, (var11 + var12) * 0.5F, var10);
      var0.submitNodeCollector()
         .submitCustomGeometry(
            var16,
            RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
            (var4x, var5x) -> OrionBasketballMesh.OTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var4x, var5x, var3, -14013133, 0.09F * var4, (var12 - var11) * 0.5F, 0.09F * var4
            )
         );
      PoseStack var17 = helper122(var1, var2);
      var17.translate(0.0F, var7 - var8 * 0.3F, (var10 - var6) * 0.5F);
      var0.submitNodeCollector()
         .submitCustomGeometry(
            var17,
            RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
            (var3x, var4x) -> OrionBasketballMesh.OTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var3x, var4x, var3, -14013133, 0.07F * var4, 0.07F * var4, var9 * 0.5F)
         );
   }

   private static void helper124(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, OrionHoop var1, Vec3 var2, int var3) {
      float var4 = (float)var1.scale;
      float var5 = (float)var1.boardOffset();
      float var6 = (float)(var1.boardY() - var1.y);
      float var7 = (float)(var1.boardWidth() * 0.5);
      float var8 = (float)(var1.boardHeight() * 0.5);
      float var9 = (float)(0.05 * var1.scale * 0.5);
      float var10 = -var5 + var9 + 0.003F;
      float var11 = 0.05F * var4;
      float var12 = (float)(0.59 * var1.scale * 0.5);
      float var13 = (float)(0.45 * var1.scale);
      float var14 = 0.035F * var4;
      float var15 = 0.06F * var4;
      PoseStack var16 = helper122(var1, var2);
      var0.submitNodeCollector().submitCustomGeometry(var16, RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO), (var10x, var11x) -> {
         helper130(var10x, var11x, var3, -var7, var6 + var8 - var11, var7, var6 + var8, var10);
         helper130(var10x, var11x, var3, -var7, var6 - var8, var7, var6 - var8 + var11, var10);
         helper130(var10x, var11x, var3, -var7, var6 - var8, -var7 + var11, var6 + var8, var10);
         helper130(var10x, var11x, var3, var7 - var11, var6 - var8, var7, var6 + var8, var10);
         helper130(var10x, var11x, var3, -var12, var15, var12, var15 + var14, var10);
         helper130(var10x, var11x, var3, -var12, var15 + var13 - var14, var12, var15 + var13, var10);
         helper130(var10x, var11x, var3, -var12, var15, -var12 + var14, var15 + var13, var10);
         helper130(var10x, var11x, var3, var12 - var14, var15, var12, var15 + var13, var10);
      });
      PoseStack var17 = helper122(var1, var2);
      var17.translate(0.0F, var6, -var5);
      var0.submitNodeCollector()
         .submitCustomGeometry(
            var17,
            RenderTypes.entityTranslucent(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
            (var4x, var5x) -> OrionBasketballMesh.OTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var4x, var5x, var3, 1156248831, var7, var8, var9)
         );
   }

   private static void helper125(
      net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, OrionHoop var1, Vec3 var2, OrionBallState var3, boolean var4, double var5, OrionBasketballSettings var7, int var8
   ) {
      OrionBasketballNet var9 = CDWMWDOMTOCVRHWDIMTOCVRHWDIMTO.computeIfAbsent(var1.id, var1x -> new OrionBasketballNet(var1));
      var9.IHRHWDOMTOCVRHWDIMTOCVRHWDIMTO(var5, var3, var4, Math.max(0.5F, var7.basketGravity));
      float var10 = (float)(0.007 * var1.scale);
      PoseStack var11 = new PoseStack();
      var0.submitNodeCollector()
         .submitCustomGeometry(
            var11,
            RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
            (var4x, var5x) -> var9.OHRHWDOMTOCVRHWDIMTOCVRHWDIMTO(var4x, var5x, var8, -723728, var2.x, var2.y, var2.z, var10)
         );
   }

   private static void helper126(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, OrionHoop var1, Vec3 var2, int var3) {
      PoseStack var4 = new PoseStack();
      var4.translate(var1.x - var2.x, var1.base + 0.015 - var2.y, var1.z - var2.z);
      var4.rotate(Axis.YP.rotationDegrees(-var1.yaw));
      var0.submitNodeCollector().submitCustomGeometry(var4, RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO), (var1x, var2x) -> {
         float var3x = 0.05F;
         float var4x = 6.75F;
         float var5 = 6.6F;
         float var6 = -1.575F;
         float var7 = 4.225F;
         float var8 = 2.45F;
         float var9 = (float)Math.sqrt(var4x * var4x - var5 * var5);
         float var10 = (float)Math.toDegrees(Math.atan2(var9, var5));
         helper132(var1x, var2x, var3, var4x, var10, 180.0F - var10, var3x);
         helper131(var1x, var2x, var3, -var5, var6, -var5, var9, var3x);
         helper131(var1x, var2x, var3, var5, var6, var5, var9, var3x);
         helper131(var1x, var2x, var3, -var5, var6, var5, var6, var3x);
         helper131(var1x, var2x, var3, -var8, var6, -var8, var7, var3x);
         helper131(var1x, var2x, var3, var8, var6, var8, var7, var3x);
         helper131(var1x, var2x, var3, -var8, var7, var8, var7, var3x);
         helper133(var1x, var2x, var3, 0.0F, var7, 1.8F, var3x);
      });
   }

   private static void helper127(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, List<OrionHoop> var1, Vec3 var2, Minecraft var3) {
      if (var1.size() >= 2) {
         OrionHoop var4 = (OrionHoop)var1.get(0);
         OrionHoop var5 = (OrionHoop)var1.get(1);
         float var6 = 6.6F;
         float var7 = 0.05F;
         double var8 = var4.forwardX();
         double var10 = var4.forwardZ();
         double var12 = Math.cos(Math.toRadians(var4.yaw));
         double var14 = Math.sin(Math.toRadians(var4.yaw));
         double var16 = var4.x - var8 * 1.575;
         double var18 = var4.z - var10 * 1.575;
         double var20 = var16 - var12 * var6;
         double var22 = var18 - var14 * var6;
         double var24 = var16 + var12 * var6;
         double var26 = var18 + var14 * var6;
         double var28 = var5.forwardX();
         double var30 = var5.forwardZ();
         double var32 = Math.cos(Math.toRadians(var5.yaw));
         double var34 = Math.sin(Math.toRadians(var5.yaw));
         double var36 = var5.x - var28 * 1.575;
         double var38 = var5.z - var30 * 1.575;
         double var40 = var36 - var32 * var6;
         double var42 = var38 - var34 * var6;
         double var44 = var36 + var32 * var6;
         double var46 = var38 + var34 * var6;
         double var48 = (var20 - var44) * (var20 - var44) + (var22 - var46) * (var22 - var46);
         double var50 = (var20 - var40) * (var20 - var40) + (var22 - var42) * (var22 - var42);
         double var52 = var48 < var50 ? var44 : var40;
         double var54 = var48 < var50 ? var46 : var42;
         double var56 = var48 < var50 ? var40 : var44;
         double var58 = var48 < var50 ? var42 : var46;
         double var60 = Math.max(var4.base, var5.base) + 0.015;
         double var62 = (var20 + var52 + var24 + var56) * 0.25;
         double var64 = (var22 + var54 + var26 + var58) * 0.25;
         int var66 = helper134(var3, var62, var60 + 0.2, var64);
         PoseStack var67 = new PoseStack();
         var67.translate(-var2.x, var60 - var2.y, -var2.z);
         var0.submitNodeCollector().submitCustomGeometry(var67, RenderTypes.entityCutout(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO), (var22x, var23) -> {
            helper131(var22x, var23, var66, (float)var20, (float)var22, (float)var52, (float)var54, var7);
            helper131(var22x, var23, var66, (float)var24, (float)var26, (float)var56, (float)var58, var7);
            double var24x = (var20 + var52) * 0.5;
            double var26x = (var22 + var54) * 0.5;
            double var28x = (var24 + var56) * 0.5;
            double var30x = (var26 + var58) * 0.5;
            helper131(var22x, var23, var66, (float)var24x, (float)var26x, (float)var28x, (float)var30x, var7);
            helper133(var22x, var23, var66, (float)var62, (float)var64, 1.8F, var7);
            helper133(var22x, var23, var66, (float)var62, (float)var64, 0.6F, var7);
         });
      }
   }

   private static void helper128(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext var0, Minecraft var1, OrionBasketballSettings var2, Vec3 var3, float var4) {
      if (var2.basketPreview
         && OrionBasketballController.charging()
         && OrionBasketballController.mode() == OrionBasketballController.Mode.HELD) {
         LocalPlayer var5 = var1.player;
         float var6 = var5.getViewXRot(var4);
         float var7 = var5.getViewYRot(var4);
         Vec3 var8 = OrionBasketballController.lookDirection(var6, var7);
         Vec3 var9 = OrionBasketballController.lookDirection(var6 - 90.0F, var7);
         Vec3 var10 = var8.cross(var9);
         float var11 = OrionBasketballController.charge(var4);
         Vec3 var12 = OrionBasketballController.helper38(var5.getEyePosition(var4), var8, var9, var10, var11);
         Vec3 var13 = OrionBasketballController.helper47(var5, var12, var8, var11, var2);
         double var14 = OrionBasketballController.helper49(var11, var2);
         OrionBallState var16 = new OrionBallState();
         var16.radius = OrionBasketballController.ball().radius;
         var16.setPosition(var12.x, var12.y, var12.z);
         var16.vx = var13.x;
         var16.vy = var13.y;
         var16.vz = var13.z;
         var16.setSpin(var10.x * var14, var10.y * var14, var10.z * var14);
         ArrayList var17 = new ArrayList();
         List var18 = List.of();
         OrionBallPhysics.Parameters var19 = OrionBasketballController.helper45(var2);
         MutableBlockPos var20 = new MutableBlockPos();

         for (int var21 = 0; var21 < 120; var21++) {
            OrionBallPhysics.advance(var16, var19, var18, 0.02, OrionBallPhysics.Listener.DMTIMDOMTOCVRHWDIMTOCVRHWDIMTO);
            var20.set(Mth.floor(var16.x), Mth.floor(var16.y), Mth.floor(var16.z));
            if (!var1.level.getBlockState(var20).getCollisionShape(var1.level, var20).isEmpty()) {
               break;
            }

            if (var21 % 2 == 0) {
               var17.add(new Vec3(var16.x, var16.y, var16.z));
            }
         }

         if (!var17.isEmpty()) {
            PoseStack var22 = new PoseStack();
            var0.submitNodeCollector()
               .submitCustomGeometry(
                  var22,
                  RenderTypes.entityTranslucent(CRWMWDOMTOCVRHWDIMTOCVRHWDIMTO),
                  (var4x, var5x) -> {
                     for (int var6x = 0; var6x < var17.size(); var6x++) {
                        Vec3 var7x = (Vec3)var17.get(var6x);
                        float var8x = 1.0F - (float)var6x / var17.size();
                        int var9x = (int)(210.0F * var8x * var8x);
                        float var10x = 0.02F + 0.014F * var8x;
                        helper129(
                           var4x, var5x, var9x << 24 | 16777215, var7x.x - var3.x, var7x.y - var3.y, var7x.z - var3.z, var10, var9, var10x
                        );
                     }
                  }
               );
         }
      }
   }

   private static void helper129(
      Pose var0, VertexConsumer var1, int var2, double var3, double var5, double var7, Vec3 var9, Vec3 var10, float var11
   ) {
      float var12 = (float)var9.x * var11;
      float var13 = (float)var9.y * var11;
      float var14 = (float)var9.z * var11;
      float var15 = (float)var10.x * var11;
      float var16 = (float)var10.y * var11;
      float var17 = (float)var10.z * var11;
      float var18 = (float)var3;
      float var19 = (float)var5;
      float var20 = (float)var7;
      OrionBasketballMesh.DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
         var0,
         var1,
         15728880,
         var2,
         var18 - var12 - var15,
         var19 - var13 - var16,
         var20 - var14 - var17,
         var18 + var12 - var15,
         var19 + var13 - var16,
         var20 + var14 - var17,
         var18 + var12 + var15,
         var19 + var13 + var16,
         var20 + var14 + var17,
         var18 - var12 + var15,
         var19 - var13 + var16,
         var20 - var14 + var17,
         (float)(-var9.y),
         1.0F,
         0.0F
      );
   }

   private static void helper130(Pose var0, VertexConsumer var1, int var2, float var3, float var4, float var5, float var6, float var7) {
      OrionBasketballMesh.DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
         var0, var1, var2, -855310, var3, var4, var7, var5, var4, var7, var5, var6, var7, var3, var6, var7, 0.0F, 0.0F, 1.0F
      );
   }

   private static void helper131(Pose var0, VertexConsumer var1, int var2, float var3, float var4, float var5, float var6, float var7) {
      float var8 = var5 - var3;
      float var9 = var6 - var4;
      float var10 = (float)Math.sqrt(var8 * var8 + var9 * var9);
      if (!(var10 < 1.0E-5F)) {
         float var11 = -var9 / var10 * var7 * 0.5F;
         float var12 = var8 / var10 * var7 * 0.5F;
         OrionBasketballMesh.DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
            var0,
            var1,
            var2,
            -1325400065,
            var3 - var11,
            0.0F,
            var4 - var12,
            var5 - var11,
            0.0F,
            var6 - var12,
            var5 + var11,
            0.0F,
            var6 + var12,
            var3 + var11,
            0.0F,
            var4 + var12,
            0.0F,
            1.0F,
            0.0F
         );
      }
   }

   private static void helper132(Pose var0, VertexConsumer var1, int var2, float var3, float var4, float var5, float var6) {
      int var7 = Math.max(8, (int)Math.abs(var5 - var4) / 4);
      float var8 = 0.0F;
      float var9 = 0.0F;

      for (int var10 = 0; var10 <= var7; var10++) {
         double var11 = Math.toRadians(var4 + (var5 - var4) * var10 / var7);
         float var13 = (float)(Math.cos(var11) * var3);
         float var14 = (float)(Math.sin(var11) * var3);
         if (var10 > 0) {
            helper131(var0, var1, var2, var8, var9, var13, var14, var6);
         }

         var8 = var13;
         var9 = var14;
      }
   }

   private static void helper133(Pose var0, VertexConsumer var1, int var2, float var3, float var4, float var5, float var6) {
      byte var7 = 36;
      float var8 = var3 + var5;
      float var9 = var4;

      for (int var10 = 1; var10 <= var7; var10++) {
         double var11 = (Math.PI * 2) * var10 / var7;
         float var13 = var3 + (float)Math.cos(var11) * var5;
         float var14 = var4 + (float)Math.sin(var11) * var5;
         helper131(var0, var1, var2, var8, var9, var13, var14, var6);
         var8 = var13;
         var9 = var14;
      }
   }

   private static int helper134(Minecraft var0, double var1, double var3, double var5) {
      return OrionBasketballClientAccess.light(var0.level, BlockPos.containing(var1, var3, var5));
   }

   // $VF: synthetic method
   private static String DTVWMCHIORDTVWMCHIORDTVWMCHIORDX(String var0) {
      char[] var1 = var0.toCharArray();

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = (char)(var1[var2] ^ 10384);
      }

      return new String(var1).intern();
   }
}
