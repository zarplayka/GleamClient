package ru.orion.client.basketball.engine;

public interface OrionBallCollider {
   boolean contact(double var1, double var3, double var5, double var7, OrionBallContact var9);

   record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, OrionBallMaterial material) implements OrionBallCollider {
      @Override
      public boolean contact(double var1, double var3, double var5, double var7, OrionBallContact var9) {
         double var10 = Math.clamp(var1, this.minX, this.maxX);
         double var12 = Math.clamp(var3, this.minY, this.maxY);
         double var14 = Math.clamp(var5, this.minZ, this.maxZ);
         double var16 = var1 - var10;
         double var18 = var3 - var12;
         double var20 = var5 - var14;
         double var22 = var16 * var16 + var18 * var18 + var20 * var20;
         if (var22 > var7 * var7) {
            return false;
         } else if (var22 > 1.0E-12) {
            double var24 = Math.sqrt(var22);
            var9.helper2(var16 / var24, var18 / var24, var20 / var24, var7 - var24, this.material);
            return true;
         } else {
            return this.DWDMWDOMTOCVRHWDIMTOCVRHWDIMTO(var1, var3, var5, var7, var9);
         }
      }

      private boolean DWDMWDOMTOCVRHWDIMTOCVRHWDIMTO(double var1, double var3, double var5, double var7, OrionBallContact var9) {
         double var10 = var1 - this.minX;
         double var12 = this.maxX - var1;
         double var14 = var3 - this.minY;
         double var16 = this.maxY - var3;
         double var18 = var5 - this.minZ;
         double var20 = this.maxZ - var5;
         double var22 = var10;
         double var24 = -1.0;
         double var26 = 0.0;
         double var28 = 0.0;
         if (var12 < var22) {
            var22 = var12;
            var24 = 1.0;
            var26 = 0.0;
            var28 = 0.0;
         }

         if (var14 < var22) {
            var22 = var14;
            var24 = 0.0;
            var26 = -1.0;
            var28 = 0.0;
         }

         if (var16 < var22) {
            var22 = var16;
            var24 = 0.0;
            var26 = 1.0;
            var28 = 0.0;
         }

         if (var18 < var22) {
            var22 = var18;
            var24 = 0.0;
            var26 = 0.0;
            var28 = -1.0;
         }

         if (var20 < var22) {
            var22 = var20;
            var24 = 0.0;
            var26 = 0.0;
            var28 = 1.0;
         }

         var9.helper2(var24, var26, var28, var7 + var22, this.material);
         return true;
      }
   }

   record OrientedBox(double cx, double cy, double cz, double halfX, double halfY, double halfZ, double cos, double sin, OrionBallMaterial material)
      implements OrionBallCollider {
      @Override
      public boolean contact(double var1, double var3, double var5, double var7, OrionBallContact var9) {
         double var10 = var1 - this.cx;
         double var12 = var5 - this.cz;
         double var14 = var10 * this.cos + var12 * this.sin;
         double var16 = -var10 * this.sin + var12 * this.cos;
         double var18 = var3 - this.cy;
         double var20 = Math.clamp(var14, -this.halfX, this.halfX);
         double var22 = Math.clamp(var18, -this.halfY, this.halfY);
         double var24 = Math.clamp(var16, -this.halfZ, this.halfZ);
         double var26 = var14 - var20;
         double var28 = var18 - var22;
         double var30 = var16 - var24;
         double var32 = var26 * var26 + var28 * var28 + var30 * var30;
         if (var32 > var7 * var7) {
            return false;
         }

         double var34;
         double var36;
         double var38;
         double var40;
         if (var32 > 1.0E-12) {
            double var42 = Math.sqrt(var32);
            var34 = var26 / var42;
            var36 = var28 / var42;
            var38 = var30 / var42;
            var40 = var7 - var42;
         } else {
            double var56 = this.halfX + var14;
            double var44 = this.halfX - var14;
            double var46 = this.halfY + var18;
            double var48 = this.halfY - var18;
            double var50 = this.halfZ + var16;
            double var52 = this.halfZ - var16;
            double var54 = var56;
            var34 = -1.0;
            var36 = 0.0;
            var38 = 0.0;
            if (var44 < var54) {
               var54 = var44;
               var34 = 1.0;
               var36 = 0.0;
               var38 = 0.0;
            }

            if (var46 < var54) {
               var54 = var46;
               var34 = 0.0;
               var36 = -1.0;
               var38 = 0.0;
            }

            if (var48 < var54) {
               var54 = var48;
               var34 = 0.0;
               var36 = 1.0;
               var38 = 0.0;
            }

            if (var50 < var54) {
               var54 = var50;
               var34 = 0.0;
               var36 = 0.0;
               var38 = -1.0;
            }

            if (var52 < var54) {
               var54 = var52;
               var34 = 0.0;
               var36 = 0.0;
               var38 = 1.0;
            }

            var40 = var7 + var54;
         }

         var9.helper2(var34 * this.cos - var38 * this.sin, var36, var34 * this.sin + var38 * this.cos, var40, this.material);
         return true;
      }
   }

   record Torus(double cx, double cy, double cz, double radius, double tube, OrionBallMaterial material) implements OrionBallCollider {
      @Override
      public boolean contact(double var1, double var3, double var5, double var7, OrionBallContact var9) {
         double var10 = var1 - this.cx;
         double var12 = var5 - this.cz;
         double var14 = Math.sqrt(var10 * var10 + var12 * var12);
         double var16;
         double var18;
         if (var14 > 1.0E-9) {
            var16 = var10 / var14;
            var18 = var12 / var14;
         } else {
            var16 = 1.0;
            var18 = 0.0;
         }

         double var20 = var1 - (this.cx + var16 * this.radius);
         double var22 = var3 - this.cy;
         double var24 = var5 - (this.cz + var18 * this.radius);
         double var26 = var20 * var20 + var22 * var22 + var24 * var24;
         double var28 = var7 + this.tube;
         if (var26 > var28 * var28) {
            return false;
         }

         if (var26 > 1.0E-12) {
            double var30 = Math.sqrt(var26);
            var9.helper2(var20 / var30, var22 / var30, var24 / var30, var28 - var30, this.material);
         } else {
            var9.helper2(var16, 0.0, var18, var28, this.material);
         }

         return true;
      }
   }
}
