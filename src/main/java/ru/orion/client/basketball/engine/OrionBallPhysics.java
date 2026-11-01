package ru.orion.client.basketball.engine;

import java.util.List;
import org.joml.Quaternionf;

public final class OrionBallPhysics {
   public static final double WTVMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.005;
   private static final double MTVMWDOMTOCVRHWDIMTOCVRHWDIMTO = 1.5625E-4;
   private static final double CVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.35;
   private static final double HVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 60.0;
   private static final double IVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 130.0;
   private static final double OVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.12;
   private static final double RVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.62;
   private static final double DVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.35;
   private static final double TVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.02;
   private static final double VVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.25;

   private OrionBallPhysics() {
   }

   public static void advance(
      OrionBallState var0, OrionBallPhysics.Parameters var1, List<OrionBallCollider> var2, double var3, OrionBallPhysics.Listener var5
   ) {
      if (!(var3 <= 0.0)) {
         OrionBallContact var6 = new OrionBallContact();
         Quaternionf var7 = new Quaternionf();
         if (var0.asleep) {
            double var17 = helper4(var0, var1, var2, Math.min(var3, 0.02), var5, var6, var7);
            if (var17 > 0.5 && var0.speed() < 0.25) {
               var0.asleep = true;
               var0.vx = 0.0;
               var0.vy = 0.0;
               var0.vz = 0.0;
               var0.wx = 0.0;
               var0.wy = 0.0;
               var0.wz = 0.0;
            }
         } else {
            double var8 = var3;
            int var10 = 0;

            while (var8 > 1.0E-9 && var10++ < 4096) {
               double var11 = Math.min(0.005, var8);
               double var13 = var0.speed();
               double var15 = var0.radius * 0.35;
               if (var13 * var11 > var15) {
                  var11 = Math.max(var15 / var13, 1.5625E-4);
                  var11 = Math.min(var11, var8);
               }

               var8 -= var11;
               helper4(var0, var1, var2, var11, var5, var6, var7);
            }
         }
      }
   }

   private static double helper4(
      OrionBallState var0,
      OrionBallPhysics.Parameters var1,
      List<OrionBallCollider> var2,
      double var3,
      OrionBallPhysics.Listener var5,
      OrionBallContact var6,
      Quaternionf var7
   ) {
      double var8 = var0.speed();
      double var10 = 0.0;
      double var12 = -var1.gravity();
      double var14 = 0.0;
      if (var8 > 1.0E-6) {
         double var16 = var1.drag() * var8;
         var10 -= var16 * var0.vx;
         var12 -= var16 * var0.vy;
         var14 -= var16 * var0.vz;
      }

      if (var1.magnus() != 0.0) {
         var10 += var1.magnus() * (var0.wy * var0.vz - var0.wz * var0.vy);
         var12 += var1.magnus() * (var0.wz * var0.vx - var0.wx * var0.vz);
         var14 += var1.magnus() * (var0.wx * var0.vy - var0.wy * var0.vx);
      }

      var0.vx += var10 * var3;
      var0.vy += var12 * var3;
      var0.vz += var14 * var3;
      double var26 = Math.exp(-var1.spinDecay() * var3);
      var0.wx *= var26;
      var0.wy *= var26;
      var0.wz *= var26;
      double var18 = var0.x;
      double var20 = var0.y;
      double var22 = var0.z;
      var0.x = var0.x + var0.vx * var3;
      var0.y = var0.y + var0.vy * var3;
      var0.z = var0.z + var0.vz * var3;
      helper8(var0, var3, var7);
      double var24 = helper5(var0, var1, var2, var5, var6);
      if (var24 > 0.5) {
         helper7(var0, var1, var3);
      }

      helper9(var0);
      var5.segment(var18, var20, var22, var0.x, var0.y, var0.z);
      helper10(var0, var3, var24);
      return var24;
   }

   private static double helper5(
      OrionBallState var0, OrionBallPhysics.Parameters var1, List<OrionBallCollider> var2, OrionBallPhysics.Listener var3, OrionBallContact var4
   ) {
      double var5 = 0.0;

      for (int var7 = 0; var7 < 4; var7++) {
         boolean var8 = false;

         for (int var9 = 0; var9 < var2.size(); var9++) {
            if (((OrionBallCollider)var2.get(var9)).contact(var0.x, var0.y, var0.z, var0.radius, var4)) {
               var8 = true;
               var5 = Math.max(var5, var4.ny);
               helper6(var0, var1, var4, var3);
            }
         }

         if (!var8) {
            break;
         }
      }

      return var5;
   }

   private static void helper6(
      OrionBallState var0, OrionBallPhysics.Parameters var1, OrionBallContact var2, OrionBallPhysics.Listener var3
   ) {
      var0.x = var0.x + var2.nx * var2.depth;
      var0.y = var0.y + var2.ny * var2.depth;
      var0.z = var0.z + var2.nz * var2.depth;
      double var4 = -var0.radius * var2.nx;
      double var6 = -var0.radius * var2.ny;
      double var8 = -var0.radius * var2.nz;
      double var10 = var0.vx + (var0.wy * var8 - var0.wz * var6);
      double var12 = var0.vy + (var0.wz * var4 - var0.wx * var8);
      double var14 = var0.vz + (var0.wx * var6 - var0.wy * var4);
      double var16 = var10 * var2.nx + var12 * var2.ny + var14 * var2.nz;
      if (!(var16 >= 0.0)) {
         double var18 = -var16;
         double var20 = var2.material.bounce() * var1.bounce() * Math.clamp((var18 - 0.12) / 0.5, 0.0, 1.0);
         double var22 = -(1.0 + var20) * var16 * var0.mass;
         var0.vx = var0.vx + var2.nx * var22 / var0.mass;
         var0.vy = var0.vy + var2.ny * var22 / var0.mass;
         var0.vz = var0.vz + var2.nz * var22 / var0.mass;
         double var24 = var10 - var16 * var2.nx;
         double var26 = var12 - var16 * var2.ny;
         double var28 = var14 - var16 * var2.nz;
         double var30 = Math.sqrt(var24 * var24 + var26 * var26 + var28 * var28);
         if (var30 > 1.0E-5) {
            double var32 = var2.material.friction() * var1.friction();
            double var34 = 0.4 * var0.mass * var30;
            double var36 = Math.min(var32 * var22, var34);
            double var38 = -var24 / var30 * var36;
            double var40 = -var26 / var30 * var36;
            double var42 = -var28 / var30 * var36;
            var0.vx = var0.vx + var38 / var0.mass;
            var0.vy = var0.vy + var40 / var0.mass;
            var0.vz = var0.vz + var42 / var0.mass;
            double var44 = var0.inertia();
            var0.wx += (var6 * var42 - var8 * var40) / var44;
            var0.wy += (var8 * var38 - var4 * var42) / var44;
            var0.wz += (var4 * var40 - var6 * var38) / var44;
         }

         if (var18 > 0.25) {
            var0.wake();
         }

         var3.hit(var2.material, var18, var0.x + var4, var0.y + var6, var0.z + var8);
      }
   }

   private static void helper7(OrionBallState var0, OrionBallPhysics.Parameters var1, double var2) {
      double var4 = Math.sqrt(var0.vx * var0.vx + var0.vz * var0.vz);
      if (!(var4 < 1.0E-5)) {
         double var6 = var1.roll() * var1.gravity() * var2;
         double var8 = Math.max(0.0, var4 - var6) / var4;
         var0.vx *= var8;
         var0.vz *= var8;
      }
   }

   private static void helper8(OrionBallState var0, double var1, Quaternionf var3) {
      double var4 = var0.spinSpeed();
      if (!(var4 < 1.0E-4)) {
         float var6 = (float)(var4 * var1);
         var3.identity().rotateAxis(var6, (float)(var0.wx / var4), (float)(var0.wy / var4), (float)(var0.wz / var4));
         var0.turn.premul(var3).normalize();
      }
   }

   private static void helper9(OrionBallState var0) {
      double var1 = var0.speed();
      if (var1 > 60.0) {
         double var3 = 60.0 / var1;
         var0.vx *= var3;
         var0.vy *= var3;
         var0.vz *= var3;
      }

      double var7 = var0.spinSpeed();
      if (var7 > 130.0) {
         double var5 = 130.0 / var7;
         var0.wx *= var5;
         var0.wy *= var5;
         var0.wz *= var5;
      }
   }

   private static void helper10(OrionBallState var0, double var1, double var3) {
      if (!(var3 <= 0.5) && !(var0.speed() > 0.09) && !(var0.spinSpeed() > 0.6)) {
         var0.still += var1;
         if (var0.still >= 0.35) {
            var0.asleep = true;
            var0.vx = 0.0;
            var0.vy = 0.0;
            var0.vz = 0.0;
            var0.wx = 0.0;
            var0.wy = 0.0;
            var0.wz = 0.0;
         }
      } else {
         var0.still = 0.0;
         var0.asleep = false;
      }
   }

   public interface Listener {
      OrionBallPhysics.Listener DMTIMDOMTOCVRHWDIMTOCVRHWDIMTO = new OrionBallPhysics.Listener() {};

      default void hit(OrionBallMaterial var1, double var2, double var4, double var6, double var8) {
      }

      default void segment(double var1, double var3, double var5, double var7, double var9, double var11) {
      }
   }

   public record Parameters(double gravity, double drag, double magnus, double spinDecay, double roll, double bounce, double friction) {
      public static final OrionBallPhysics.Parameters TMTIMDOMTOCVRHWDIMTOCVRHWDIMTO = new OrionBallPhysics.Parameters(
         9.81, 0.0208, 0.008, 0.12, 0.02, 1.0, 1.0
      );
   }
}
