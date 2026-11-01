package ru.orion.client.basketball.engine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class OrionBasketballHands {
   private static final float RIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.25F;
   private static final float DIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.1875F;
   private static final Vec3 TIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new Vec3(0.33, -0.62, 0.16);
   private static final Vec3 VIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new Vec3(-0.33, -0.62, 0.16);
   private static final double WIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.17;
   private static final double MIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -0.62;

   private OrionBasketballHands() {
   }

   public static boolean renderHands(float var0, PoseStack var1, SubmitNodeCollector var2, LocalPlayer var3, int var4) {
      OrionBasketballSettings var5 = OrionBasketballSettings.get();
      if (var5.basket && var5.basketHands && OrionBasketballController.localHolding() && var3 != null) {
         Minecraft var6 = Minecraft.getInstance();
         if (var6.options != null && var6.options.getCameraType().isFirstPerson()) {
            Identifier var7 = var3.getSkin().body().texturePath();
            boolean var8 = var3.getSkin().model() == PlayerModelType.SLIM;
            float var9 = OrionBasketballController.charge(var0);
            if (OrionBasketballController.mode() == OrionBasketballController.Mode.HELD) {
               helper92(var1, var2, var4, var7, var8, var9);
            } else {
               helper93(var1, var2, var3, var4, var7, var8, var0);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void helper92(PoseStack var0, SubmitNodeCollector var1, int var2, Identifier var3, boolean var4, float var5) {
      Vec3 var6 = OrionBasketballController.helper39(var5);
      Vec3 var7 = new Vec3(var6.x, var6.y, -var6.z);
      double var8 = OrionBasketballController.ball().radius;
      Vec3 var10 = var7.add(var8 * (0.92 - 0.45 * var5), -var8 * (0.25 + 0.75 * var5), var8 * 0.25);
      Vec3 var11 = var7.add(-var8 * (1.05 - 0.15 * var5), -var8 * (0.2 + 0.1 * var5), var8 * 0.25);
      helper94(var0, var1, var2, var3, var4, true, TIWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var10);
      helper94(var0, var1, var2, var3, var4, false, VIWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var11);
      helper95(var0, var1, var2, var7, var8);
   }

   private static void helper93(
      PoseStack var0, SubmitNodeCollector var1, LocalPlayer var2, int var3, Identifier var4, boolean var5, float var6
   ) {
      OrionBallState var7 = OrionBasketballController.ball();
      Vec3 var8 = var2.getEyePosition(var6);
      float var9 = var2.getViewXRot(var6);
      float var10 = var2.getViewYRot(var6);
      Vec3 var11 = OrionBasketballController.lookDirection(var9, var10);
      Vec3 var12 = OrionBasketballController.lookDirection(var9 - 90.0F, var10);
      Vec3 var13 = var11.cross(var12);
      Vec3 var14 = new Vec3(
         Mth.lerp(var6, var7.prevX, var7.x) - var8.x, Mth.lerp(var6, var7.prevY, var7.y) - var8.y, Mth.lerp(var6, var7.prevZ, var7.z) - var8.z
      );
      Vec3 var15 = new Vec3(var14.dot(var13), var14.dot(var12), -var14.dot(var11));
      Vec3 var16 = new Vec3(Math.clamp(var15.x, -0.7, 0.9), Math.max(var15.y + 0.17 + var7.radius, -0.62), Math.clamp(var15.z, -1.1, -0.1));
      helper94(var0, var1, var3, var4, var5, true, TIWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var16);
   }

   private static void helper94(
      PoseStack var0, SubmitNodeCollector var1, int var2, Identifier var3, boolean var4, boolean var5, Vec3 var6, Vec3 var7
   ) {
      double var8 = var7.x - var6.x;
      double var10 = var7.y - var6.y;
      double var12 = var7.z - var6.z;
      double var14 = Math.sqrt(var8 * var8 + var10 * var10 + var12 * var12);
      if (!(var14 < 0.05)) {
         float var16 = var4 ? 0.1875F : 0.25F;
         float var17 = var16 * 0.5F;
         float var18 = (float)Math.clamp(var14, 0.35, 1.05);
         var0.pushPose();
         var0.translate(var6.x, var6.y, var6.z);
         var0.rotate(new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, (float)(var8 / var14), (float)(var10 / var14), (float)(var12 / var14)));
         var0.translate(0.0F, -var18 * 0.5F, 0.0F);
         float var19 = var18 * 0.5F;
         int var20 = var5 ? 40 : 32;
         int var21 = var5 ? 16 : 48;
         int var22 = var5 ? 40 : 48;
         int var23 = var5 ? 32 : 48;
         int var24 = var4 ? 3 : 4;
         var1.submitCustomGeometry(
            var0,
            RenderTypes.entityCutout(var3),
            (var6x, var7x) -> helper96(var6x, var7x, var2, var17, var19, var17, var20, var21, var24, 0.0F)
         );
         var1.submitCustomGeometry(
            var0,
            RenderTypes.entityTranslucent(var3),
            (var6x, var7x) -> helper96(var6x, var7x, var2, var17, var19, var17, var22, var23, var24, 0.012F)
         );
         var0.popPose();
      }
   }

   private static void helper95(PoseStack var0, SubmitNodeCollector var1, int var2, Vec3 var3, double var4) {
      var0.pushPose();
      var0.translate(var3.x, var3.y, var3.z);
      var0.rotate(Axis.YP.rotationDegrees(28.0F));
      var0.rotate(Axis.XP.rotationDegrees(-16.0F));
      var0.scale((float)var4, (float)var4, (float)var4);
      var1.submitCustomGeometry(
         var0,
         RenderTypes.entityCutout(OrionBasketballScene.helper118()),
         (var1x, var2x) -> OrionBasketballMesh.VDVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var1x, var2x, var2, -1)
      );
      var0.popPose();
   }

   private static void helper96(
      Pose var0, VertexConsumer var1, int var2, float var3, float var4, float var5, int var6, int var7, int var8, float var9
   ) {
      float var10 = var3 + var9;
      float var11 = var4 + var9;
      float var12 = var5 + var9;
      float var13 = 0.015625F;
      float var14 = var6 * var13;
      float var15 = var7 * var13;
      float var16 = 4.0F * var13;
      float var17 = var8 * var13;
      float var18 = 12.0F * var13;
      helper97(
         var0,
         var1,
         var2,
         -var10,
         var11,
         -var12,
         var10,
         var11,
         -var12,
         var10,
         var11,
         var12,
         -var10,
         var11,
         var12,
         0.0F,
         1.0F,
         0.0F,
         var14 + var16,
         var15,
         var14 + var16 + var17,
         var15 + var16
      );
      helper97(
         var0,
         var1,
         var2,
         -var10,
         -var11,
         var12,
         var10,
         -var11,
         var12,
         var10,
         -var11,
         -var12,
         -var10,
         -var11,
         -var12,
         0.0F,
         -1.0F,
         0.0F,
         var14 + var16 + var17,
         var15,
         var14 + var16 + var17 * 2.0F,
         var15 + var16
      );
      helper97(
         var0,
         var1,
         var2,
         -var10,
         var11,
         -var12,
         -var10,
         var11,
         var12,
         -var10,
         -var11,
         var12,
         -var10,
         -var11,
         -var12,
         -1.0F,
         0.0F,
         0.0F,
         var14,
         var15 + var16,
         var14 + var16,
         var15 + var16 + var18
      );
      helper97(
         var0,
         var1,
         var2,
         -var10,
         var11,
         var12,
         var10,
         var11,
         var12,
         var10,
         -var11,
         var12,
         -var10,
         -var11,
         var12,
         0.0F,
         0.0F,
         1.0F,
         var14 + var16,
         var15 + var16,
         var14 + var16 + var17,
         var15 + var16 + var18
      );
      helper97(
         var0,
         var1,
         var2,
         var10,
         var11,
         var12,
         var10,
         var11,
         -var12,
         var10,
         -var11,
         -var12,
         var10,
         -var11,
         var12,
         1.0F,
         0.0F,
         0.0F,
         var14 + var16 + var17,
         var15 + var16,
         var14 + var16 * 2.0F + var17,
         var15 + var16 + var18
      );
      helper97(
         var0,
         var1,
         var2,
         var10,
         var11,
         -var12,
         -var10,
         var11,
         -var12,
         -var10,
         -var11,
         -var12,
         var10,
         -var11,
         -var12,
         0.0F,
         0.0F,
         -1.0F,
         var14 + var16 * 2.0F + var17,
         var15 + var16,
         var14 + var16 * 2.0F + var17 * 2.0F,
         var15 + var16 + var18
      );
   }

   private static void helper97(
      Pose var0,
      VertexConsumer var1,
      int var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      float var13,
      float var14,
      float var15,
      float var16,
      float var17,
      float var18,
      float var19,
      float var20,
      float var21
   ) {
      var1.addVertex(var0, var3, var4, var5)
         .setColor(255, 255, 255, 255)
         .setUv(var18, var19)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var15, var16, var17);
      var1.addVertex(var0, var6, var7, var8)
         .setColor(255, 255, 255, 255)
         .setUv(var20, var19)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var15, var16, var17);
      var1.addVertex(var0, var9, var10, var11)
         .setColor(255, 255, 255, 255)
         .setUv(var20, var21)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var15, var16, var17);
      var1.addVertex(var0, var12, var13, var14)
         .setColor(255, 255, 255, 255)
         .setUv(var18, var21)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var15, var16, var17);
   }
}
