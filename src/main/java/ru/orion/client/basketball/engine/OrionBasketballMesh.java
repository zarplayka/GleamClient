package ru.orion.client.basketball.engine;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class OrionBasketballMesh {
   private static final int DMMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 16;
   private static final int TMMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 24;
   private static final int VMMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 32;
   private static final int WMMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 8;
   private static final float MMMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.04374453F;
   private static final float[] CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper105();
   private static final float[] HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper107();

   private OrionBasketballMesh() {
   }

   static void VDVIWDOMTOCVRHWDIMTOCVRHWDIMTO(Pose var0, VertexConsumer var1, int var2, int var3) {
      int var4 = var3 >> 16 & 0xFF;
      int var5 = var3 >> 8 & 0xFF;
      int var6 = var3 & 0xFF;
      int var7 = var3 >>> 24;

      for (int var8 = 0; var8 < CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO.length; var8 += 5) {
         float var9 = CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8];
         float var10 = CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 1];
         float var11 = CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 2];
         var1.addVertex(var0, var9, var10, var11)
            .setColor(var4, var5, var6, var7)
            .setUv(CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 3], CCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 4])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(var2)
            .setNormal(var0, var9, var10, var11);
      }
   }

   private static float[] helper105() {
      float[] var0 = new float[7680];
      int var1 = 0;

      for (int var2 = 0; var2 < 16; var2++) {
         double var3 = Math.PI * var2 / 16.0;
         double var5 = Math.PI * (var2 + 1) / 16.0;

         for (int var7 = 0; var7 < 24; var7++) {
            double var8 = (Math.PI * 2) * var7 / 24.0;
            double var10 = (Math.PI * 2) * (var7 + 1) / 24.0;
            var1 = helper106(var0, var1, var3, var8, var2, var7);
            var1 = helper106(var0, var1, var3, var10, var2, var7 + 1);
            var1 = helper106(var0, var1, var5, var10, var2 + 1, var7 + 1);
            var1 = helper106(var0, var1, var5, var8, var2 + 1, var7);
         }
      }

      return var0;
   }

   private static int helper106(float[] var0, int var1, double var2, double var4, int var6, int var7) {
      double var8 = Math.sin(var2);
      var0[var1] = (float)(var8 * Math.cos(var4));
      var0[var1 + 1] = (float)Math.cos(var2);
      var0[var1 + 2] = (float)(var8 * Math.sin(var4));
      var0[var1 + 3] = var7 / 24.0F;
      var0[var1 + 4] = var6 / 16.0F;
      return var1 + 5;
   }

   static void CTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(Pose var0, VertexConsumer var1, int var2, int var3) {
      int var4 = var3 >> 16 & 0xFF;
      int var5 = var3 >> 8 & 0xFF;
      int var6 = var3 & 0xFF;
      int var7 = var3 >>> 24;

      for (int var8 = 0; var8 < HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO.length; var8 += 6) {
         var1.addVertex(var0, HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8], HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 1], HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 2])
            .setColor(var4, var5, var6, var7)
            .setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(var2)
            .setNormal(var0, HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 3], HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 4], HCMMWDOMTOCVRHWDIMTOCVRHWDIMTO[var8 + 5]);
      }
   }

   private static float[] helper107() {
      float[] var0 = new float[6144];
      int var1 = 0;

      for (int var2 = 0; var2 < 32; var2++) {
         double var3 = (Math.PI * 2) * var2 / 32.0;
         double var5 = (Math.PI * 2) * (var2 + 1) / 32.0;

         for (int var7 = 0; var7 < 8; var7++) {
            double var8 = (Math.PI * 2) * var7 / 8.0;
            double var10 = (Math.PI * 2) * (var7 + 1) / 8.0;
            var1 = helper108(var0, var1, var3, var8);
            var1 = helper108(var0, var1, var3, var10);
            var1 = helper108(var0, var1, var5, var10);
            var1 = helper108(var0, var1, var5, var8);
         }
      }

      return var0;
   }

   private static int helper108(float[] var0, int var1, double var2, double var4) {
      double var6 = Math.cos(var2);
      double var8 = Math.sin(var2);
      double var10 = Math.cos(var4) * var6;
      double var12 = Math.sin(var4);
      double var14 = Math.cos(var4) * var8;
      var0[var1] = (float)(var6 + 0.04374453F * var10);
      var0[var1 + 1] = (float)(0.04374453F * var12);
      var0[var1 + 2] = (float)(var8 + 0.04374453F * var14);
      var0[var1 + 3] = (float)var10;
      var0[var1 + 4] = (float)var12;
      var0[var1 + 5] = (float)var14;
      return var1 + 6;
   }

   static void OTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(Pose var0, VertexConsumer var1, int var2, int var3, float var4, float var5, float var6) {
      helper109(var0, var1, var2, var3, var4, var5, var6, 0);
      helper109(var0, var1, var2, var3, var4, var5, var6, 1);
      helper109(var0, var1, var2, var3, var4, var5, var6, 2);
      helper109(var0, var1, var2, var3, var4, var5, var6, 3);
      helper109(var0, var1, var2, var3, var4, var5, var6, 4);
      helper109(var0, var1, var2, var3, var4, var5, var6, 5);
   }

   private static void helper109(Pose var0, VertexConsumer var1, int var2, int var3, float var4, float var5, float var6, int var7) {
      switch (var7) {
         case 0:
            DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var0, var1, var2, var3, -var4, -var5, var6, var4, -var5, var6, var4, var5, var6, -var4, var5, var6, 0.0F, 0.0F, 1.0F);
            break;
         case 1:
            DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var0, var1, var2, var3, var4, -var5, -var6, -var4, -var5, -var6, -var4, var5, -var6, var4, var5, -var6, 0.0F, 0.0F, -1.0F
            );
            break;
         case 2:
            DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var0, var1, var2, var3, var4, -var5, var6, var4, -var5, -var6, var4, var5, -var6, var4, var5, var6, 1.0F, 0.0F, 0.0F);
            break;
         case 3:
            DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var0, var1, var2, var3, -var4, -var5, -var6, -var4, -var5, var6, -var4, var5, var6, -var4, var5, -var6, -1.0F, 0.0F, 0.0F
            );
            break;
         case 4:
            DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(var0, var1, var2, var3, -var4, var5, var6, var4, var5, var6, var4, var5, -var6, -var4, var5, -var6, 0.0F, 1.0F, 0.0F);
            break;
         default:
            DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var0, var1, var2, var3, -var4, -var5, -var6, var4, -var5, -var6, var4, -var5, var6, -var4, -var5, var6, 0.0F, -1.0F, 0.0F
            );
      }
   }

   static void DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
      Pose var0,
      VertexConsumer var1,
      int var2,
      int var3,
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
      float var18
   ) {
      int var19 = var3 >> 16 & 0xFF;
      int var20 = var3 >> 8 & 0xFF;
      int var21 = var3 & 0xFF;
      int var22 = var3 >>> 24;
      var1.addVertex(var0, var4, var5, var6)
         .setColor(var19, var20, var21, var22)
         .setUv(0.25F, 0.25F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var16, var17, var18);
      var1.addVertex(var0, var7, var8, var9)
         .setColor(var19, var20, var21, var22)
         .setUv(0.75F, 0.25F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var16, var17, var18);
      var1.addVertex(var0, var10, var11, var12)
         .setColor(var19, var20, var21, var22)
         .setUv(0.75F, 0.75F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var16, var17, var18);
      var1.addVertex(var0, var13, var14, var15)
         .setColor(var19, var20, var21, var22)
         .setUv(0.25F, 0.75F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var0, var16, var17, var18);
   }

   static void TTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
      Pose var0, VertexConsumer var1, int var2, int var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10
   ) {
      float var11 = var7 - var4;
      float var12 = var8 - var5;
      float var13 = var9 - var6;
      float var14 = (float)Math.sqrt(var11 * var11 + var12 * var12 + var13 * var13);
      if (!(var14 < 1.0E-5F)) {
         float var15 = var12 * 0.0F - var13 * 1.0F;
         float var16 = 0.0F;
         float var17 = var11 * 1.0F;
         float var18 = (float)Math.sqrt(var15 * var15 + var16 * var16 + var17 * var17);
         if (var18 < 1.0E-5F) {
            var15 = 1.0F;
            var16 = 0.0F;
            var17 = 0.0F;
            var18 = 1.0F;
         }

         var15 = var15 / var18 * var10;
         var16 = var16 / var18 * var10;
         var17 = var17 / var18 * var10;
         float var19 = (var12 * var17 - var13 * var16) / var14;
         float var20 = (var13 * var15 - var11 * var17) / var14;
         float var21 = (var11 * var16 - var12 * var15) / var14;
         DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
            var0,
            var1,
            var2,
            var3,
            var4 - var15,
            var5 - var16,
            var6 - var17,
            var4 + var15,
            var5 + var16,
            var6 + var17,
            var7 + var15,
            var8 + var16,
            var9 + var17,
            var7 - var15,
            var8 - var16,
            var9 - var17,
            var19,
            var20,
            var21
         );
         DTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
            var0,
            var1,
            var2,
            var3,
            var4 - var19,
            var5 - var20,
            var6 - var21,
            var4 + var19,
            var5 + var20,
            var6 + var21,
            var7 + var19,
            var8 + var20,
            var9 + var21,
            var7 - var19,
            var8 - var20,
            var9 - var21,
            var15,
            var16,
            var17
         );
      }
   }
}
