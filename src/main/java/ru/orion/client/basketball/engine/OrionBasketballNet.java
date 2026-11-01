package ru.orion.client.basketball.engine;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;

final class OrionBasketballNet {
   private static final int ICMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 12;
   private static final int OCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 5;
   private static final double RCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.45;
   private static final double DCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.9;
   private static final double TCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.1;
   private static final int VCMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 3;
   private final double[] x = new double[60];
   private final double[] y = new double[60];
   private final double[] z = new double[60];
   private final double[] wasX = new double[60];
   private final double[] wasY = new double[60];
   private final double[] wasZ = new double[60];
   private final double[] restX = new double[60];
   private final double[] restY = new double[60];
   private final double[] restZ = new double[60];
   private final OrionHoop hoop;
   private boolean ready;

   OrionBasketballNet(OrionHoop var1) {
      this.hoop = var1;
      this.helper110();
      this.helper111();
   }

   private void helper110() {
      double var1 = this.hoop.rimRadius();
      double var3 = 0.4 * this.hoop.scale;

      for (int var5 = 0; var5 < 12; var5++) {
         double var6 = (Math.PI * 2) * var5 / 12.0;
         double var8 = Math.cos(var6);
         double var10 = Math.sin(var6);

         for (int var12 = 0; var12 < 5; var12++) {
            double var13 = (var12 + 1.0) / 5.0;
            double var15 = var1 * (1.0 - 0.45 * var13);
            int var17 = var5 * 5 + var12;
            this.restX[var17] = var8 * var15;
            this.restY[var17] = -var3 * var13;
            this.restZ[var17] = var10 * var15;
         }
      }
   }

   private void helper111() {
      for (int var1 = 0; var1 < this.x.length; var1++) {
         this.x[var1] = this.hoop.x + this.restX[var1];
         this.y[var1] = this.hoop.y + this.restY[var1];
         this.z[var1] = this.hoop.z + this.restZ[var1];
         this.wasX[var1] = this.x[var1];
         this.wasY[var1] = this.y[var1];
         this.wasZ[var1] = this.z[var1];
      }

      this.ready = true;
   }

   void IHRHWDOMTOCVRHWDIMTOCVRHWDIMTO(double var1, OrionBallState var3, boolean var4, double var5) {
      if (!this.ready) {
         this.helper111();
      }

      double var7 = Math.clamp(var1, 0.001, 0.05);
      double var9 = var5 * var7 * var7;

      for (int var11 = 0; var11 < this.x.length; var11++) {
         double var12 = (this.x[var11] - this.wasX[var11]) * 0.9;
         double var14 = (this.y[var11] - this.wasY[var11]) * 0.9;
         double var16 = (this.z[var11] - this.wasZ[var11]) * 0.9;
         this.wasX[var11] = this.x[var11];
         this.wasY[var11] = this.y[var11];
         this.wasZ[var11] = this.z[var11];
         this.x[var11] = this.x[var11] + var12;
         this.y[var11] = this.y[var11] + (var14 - var9);
         this.z[var11] = this.z[var11] + var16;
         this.x[var11] = this.x[var11] + (this.hoop.x + this.restX[var11] - this.x[var11]) * 0.1;
         this.y[var11] = this.y[var11] + (this.hoop.y + this.restY[var11] - this.y[var11]) * 0.1;
         this.z[var11] = this.z[var11] + (this.hoop.z + this.restZ[var11] - this.z[var11]) * 0.1;
      }

      for (int var18 = 0; var18 < 3; var18++) {
         this.helper112();
         if (var4) {
            this.helper115(var3);
         }
      }
   }

   private void helper112() {
      double var1 = this.hoop.rimRadius();
      double var3 = 0.4 * this.hoop.scale;

      for (int var5 = 0; var5 < 12; var5++) {
         double var6 = (Math.PI * 2) * var5 / 12.0;
         double var8 = Math.cos(var6);
         double var10 = Math.sin(var6);
         int var12 = var5 * 5;
         double var13 = this.hoop.x + var8 * var1;
         double var15 = this.hoop.z + var10 * var1;
         double var17 = Math.sqrt(Math.pow(var3 / 5.0, 2.0) + Math.pow(var1 * 0.45 / 5.0, 2.0));
         this.helper113(var12, var13, this.hoop.y, var15, var17);

         for (int var19 = 1; var19 < 5; var19++) {
            int var20 = var5 * 5 + var19;
            this.helper114(var20 - 1, var20, var17);
         }

         int var27 = (var5 + 1) % 12;

         for (int var28 = 0; var28 < 5; var28++) {
            int var21 = var5 * 5 + var28;
            int var22 = var27 * 5 + var28;
            double var23 = (var28 + 1.0) / 5.0;
            double var25 = var1 * (1.0 - 0.45 * var23);
            this.helper114(var21, var22, 2.0 * var25 * Math.sin(Math.PI / 12));
         }
      }
   }

   private void helper113(int var1, double var2, double var4, double var6, double var8) {
      double var10 = this.x[var1] - var2;
      double var12 = this.y[var1] - var4;
      double var14 = this.z[var1] - var6;
      double var16 = Math.sqrt(var10 * var10 + var12 * var12 + var14 * var14);
      if (!(var16 < 1.0E-9)) {
         double var18 = (var16 - var8) / var16;
         this.x[var1] = this.x[var1] - var10 * var18;
         this.y[var1] = this.y[var1] - var12 * var18;
         this.z[var1] = this.z[var1] - var14 * var18;
      }
   }

   private void helper114(int var1, int var2, double var3) {
      double var5 = this.x[var2] - this.x[var1];
      double var7 = this.y[var2] - this.y[var1];
      double var9 = this.z[var2] - this.z[var1];
      double var11 = Math.sqrt(var5 * var5 + var7 * var7 + var9 * var9);
      if (!(var11 < 1.0E-9)) {
         double var13 = (var11 - var3) / var11 * 0.5;
         this.x[var1] = this.x[var1] + var5 * var13;
         this.y[var1] = this.y[var1] + var7 * var13;
         this.z[var1] = this.z[var1] + var9 * var13;
         this.x[var2] = this.x[var2] - var5 * var13;
         this.y[var2] = this.y[var2] - var7 * var13;
         this.z[var2] = this.z[var2] - var9 * var13;
      }
   }

   private void helper115(OrionBallState var1) {
      double var2 = var1.radius + 0.012;

      for (int var4 = 0; var4 < this.x.length; var4++) {
         double var5 = this.x[var4] - var1.x;
         double var7 = this.y[var4] - var1.y;
         double var9 = this.z[var4] - var1.z;
         double var11 = Math.sqrt(var5 * var5 + var7 * var7 + var9 * var9);
         if (!(var11 > var2) && !(var11 < 1.0E-9)) {
            double var13 = (var2 - var11) / var11;
            this.x[var4] = this.x[var4] + var5 * var13;
            this.y[var4] = this.y[var4] + var7 * var13;
            this.z[var4] = this.z[var4] + var9 * var13;
         }
      }
   }

   void OHRHWDOMTOCVRHWDIMTOCVRHWDIMTO(Pose var1, VertexConsumer var2, int var3, int var4, double var5, double var7, double var9, float var11) {
      double var12 = this.hoop.rimRadius();

      for (int var14 = 0; var14 < 12; var14++) {
         double var15 = (Math.PI * 2) * var14 / 12.0;
         int var17 = var14 * 5;
         OrionBasketballMesh.TTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
            var1,
            var2,
            var3,
            var4,
            (float)(this.hoop.x + Math.cos(var15) * var12 - var5),
            (float)(this.hoop.y - var7),
            (float)(this.hoop.z + Math.sin(var15) * var12 - var9),
            (float)(this.x[var17] - var5),
            (float)(this.y[var17] - var7),
            (float)(this.z[var17] - var9),
            var11
         );

         for (int var18 = 1; var18 < 5; var18++) {
            int var19 = var14 * 5 + var18;
            OrionBasketballMesh.TTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var1,
               var2,
               var3,
               var4,
               (float)(this.x[var19 - 1] - var5),
               (float)(this.y[var19 - 1] - var7),
               (float)(this.z[var19 - 1] - var9),
               (float)(this.x[var19] - var5),
               (float)(this.y[var19] - var7),
               (float)(this.z[var19] - var9),
               var11
            );
         }

         int var22 = (var14 + 1) % 12;

         for (int var23 = 0; var23 < 4; var23++) {
            int var20 = var14 * 5 + var23;
            int var21 = var22 * 5 + var23;
            OrionBasketballMesh.TTVIWDOMTOCVRHWDIMTOCVRHWDIMTO(
               var1,
               var2,
               var3,
               var4,
               (float)(this.x[var20] - var5),
               (float)(this.y[var20] - var7),
               (float)(this.z[var20] - var9),
               (float)(this.x[var21] - var5),
               (float)(this.y[var21] - var7),
               (float)(this.z[var21] - var9),
               var11 * 0.8F
            );
         }
      }
   }
}
