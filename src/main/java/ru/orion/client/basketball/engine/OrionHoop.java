package ru.orion.client.basketball.engine;

import java.util.List;

public final class OrionHoop {
   public static final double HTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.2286;
   public static final double ITWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.01;
   public static final double OTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 3.05;
   public static final double RTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 1.829;
   public static final double DTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 1.067;
   public static final double TTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 2.9;
   public static final double VTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.05;
   public static final double WTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.15;
   public static final double MTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.59;
   public static final double CVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.45;
   public static final double HVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 6.75;
   public static final double IVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 4.225;
   public static final double OVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.4;
   public String id = "";
   public String dimension = "";
   public String court = "";
   public double x;
   public double y;
   public double z;
   public double base;
   public float yaw;
   public double scale = 1.0;

   public OrionHoop() {
   }

   public OrionHoop(String var1, String var2, double var3, double var5, double var7, double var9, float var11, double var12) {
      this(var1, var2, "", var3, var5, var7, var9, var11, var12);
   }

   public OrionHoop(String var1, String var2, String var3, double var4, double var6, double var8, double var10, float var12, double var13) {
      this.id = var1;
      this.dimension = var2;
      this.court = var3 == null ? "" : var3;
      this.x = var4;
      this.y = var6;
      this.z = var8;
      this.base = var10;
      this.yaw = var12;
      this.scale = var13;
   }

   public double rimRadius() {
      return 0.2286 * this.scale;
   }

   public double rimTube() {
      return 0.01 * this.scale;
   }

   public double boardWidth() {
      return 1.829 * this.scale;
   }

   public double boardHeight() {
      return 1.067 * this.scale;
   }

   public double forwardX() {
      return -Math.sin(Math.toRadians(this.yaw));
   }

   public double forwardZ() {
      return Math.cos(Math.toRadians(this.yaw));
   }

   public double boardOffset() {
      return scaledBoardOffset(this.scale);
   }

   public static double scaledBoardOffset(double var0) {
      return 0.4036 * var0;
   }

   public double boardX() {
      return this.x - this.forwardX() * this.boardOffset();
   }

   public double boardZ() {
      return this.z - this.forwardZ() * this.boardOffset();
   }

   public double boardY() {
      return this.base + 2.9 + 0.5335 * this.scale;
   }

   public void addColliders(List<OrionBallCollider> var1) {
      double var2 = Math.toRadians(this.yaw);
      double var4 = Math.cos(var2);
      double var6 = Math.sin(var2);
      var1.add(
         new OrionBallCollider.Torus(
            this.x,
            this.y,
            this.z,
            this.rimRadius(),
            this.rimTube(),
            OrionBallMaterial.TWMMWDOMTOCVRHWDIMTOCVRHWDIMTO
         )
      );
      var1.add(
         new OrionBallCollider.OrientedBox(
            this.boardX(),
            this.boardY(),
            this.boardZ(),
            this.boardWidth() * 0.5,
            this.boardHeight() * 0.5,
            0.05 * this.scale * 0.5,
            var4,
            var6,
            OrionBallMaterial.VWMMWDOMTOCVRHWDIMTOCVRHWDIMTO
         )
      );
      double var8 = 0.75 * this.scale;
      double var10 = this.boardX() - this.forwardX() * var8;
      double var12 = this.boardZ() - this.forwardZ() * var8;
      double var14 = this.base + 2.9 + 1.067 * this.scale * 0.6;
      var1.add(
         new OrionBallCollider.OrientedBox(
            var10,
            (this.base + var14) * 0.5,
            var12,
            0.09 * this.scale,
            (var14 - this.base) * 0.5,
            0.09 * this.scale,
            var4,
            var6,
            OrionBallMaterial.WWMMWDOMTOCVRHWDIMTOCVRHWDIMTO
         )
      );
      double var16 = this.base + 2.9 + 1.067 * this.scale * 0.35;
      var1.add(
         new OrionBallCollider.OrientedBox(
            (this.boardX() + var10) * 0.5,
            var16,
            (this.boardZ() + var12) * 0.5,
            0.07 * this.scale,
            0.07 * this.scale,
            var8 * 0.5,
            var4,
            var6,
            OrionBallMaterial.WWMMWDOMTOCVRHWDIMTOCVRHWDIMTO
         )
      );
   }

   public boolean crossesRim(double var1, double var3, double var5, double var7, double var9, double var11) {
      if (!(var3 <= this.y) && !(var9 > this.y)) {
         double var13 = var3 - var9;
         if (var13 < 1.0E-9) {
            return false;
         }

         double var15 = (var3 - this.y) / var13;
         double var17 = var1 + (var7 - var1) * var15 - this.x;
         double var19 = var5 + (var11 - var5) * var15 - this.z;
         double var21 = this.rimRadius();
         return var17 * var17 + var19 * var19 <= var21 * var21;
      } else {
         return false;
      }
   }

   public double boardHitTime(double var1, double var3, double var5, double var7, double var9, double var11) {
      double var13 = this.forwardX();
      double var15 = this.forwardZ();
      double var17 = var7 * var13 + var11 * var15;
      if (var17 > -1.0E-6) {
         return -1.0;
      }

      double var19 = this.boardOffset() - 0.05 * this.scale * 0.5;
      double var21 = this.x - var13 * var19;
      double var23 = this.z - var15 * var19;
      double var25 = this.boardY();
      double var27 = ((var21 - var1) * var13 + (var23 - var5) * var15) / var17;
      if (var27 <= 0.0) {
         return -1.0;
      }

      double var29 = var1 + var7 * var27 - var21;
      double var31 = var3 + var9 * var27 - var25;
      double var33 = var5 + var11 * var27 - var23;
      return !(Math.abs(var29 * var15 - var33 * var13) > this.boardWidth() * 0.5)
            && !(Math.abs(var31) > this.boardHeight() * 0.5)
         ? var27
         : -1.0;
   }

   public double shotDistance(double var1, double var3) {
      double var5 = var1 - this.x;
      double var7 = var3 - this.z;
      return Math.sqrt(var5 * var5 + var7 * var7);
   }

   public int points(double var1, double var3) {
      return this.shotDistance(var1, var3) >= 6.75 ? 3 : 2;
   }
}
