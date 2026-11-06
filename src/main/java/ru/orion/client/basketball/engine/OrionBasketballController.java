package ru.orion.client.basketball.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class OrionBasketballController {
   private static OrionBasketballMatch network() {
      var game = ru.orion.client.OrionClient.getBasketballOrNull();
      return game == null ? null : game.match();
   }
   private static final double WVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 1.15;
   private static final double MVWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 3.2;
   private static final double CWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 2.0;
   private static final double HWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 26.0;
   private static final long IWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 420L;
   private static final double OWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 180.0;
   private static final double RWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 4.5;
   private static final int DWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 9;
   private static final double TWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.28;
   private static final long VWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 2000L;
   private static final long WWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 3000L;
   private static final double MWWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 2.0;
   private static final double CMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.45;
   private static final double HMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 40.0;
   private static final List<String> IMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = List.of(
      "spawn", "grab"
   );
   private static final List<String> OMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = List.of(
      "spawn",
      "grab",
      "shoot",
      "pass",
      "own"
   );
   private static final OrionBallState RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new OrionBallState();
   private static final OrionWorldColliders DMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new OrionWorldColliders();
   private static OrionBasketballController.Mode TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.GONE;
   private static String VMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = "";
   private static List<OrionHoop> WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = List.of();
   private static int MMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -1;
   private static String CCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = "";
   private static long HCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static long ICWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static final Map<String, Integer> OCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new HashMap<>();
   private static final Map<String, Long> RCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new HashMap<>();
   private static boolean DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static float TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static float VCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static boolean WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static boolean MCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static double CHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static double HHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static long IHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int OHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int RHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int DHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static long THWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int VHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static boolean WHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int MHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int CIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -100;
   private static boolean HIWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int IIWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static final OrionBallPhysics.Listener OIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = new OrionBallPhysics.Listener() {
      @Override
      public void hit(OrionBallMaterial var1, double var2, double var4, double var6, double var8) {
         if (var1.sound() != 0) {
            OrionBasketballController.MCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
         }

         OrionBasketballAudio.helper21(var1, var2, var4, var6, var8);
      }

      @Override
      public void segment(double var1, double var3, double var5, double var7, double var9, double var11) {
         if (OrionBasketballController.WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO) {
            for (int var13 = 0; var13 < OrionBasketballController.WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.size(); var13++) {
               OrionHoop var14 = OrionBasketballController.WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(var13);
               if (var14.crossesRim(var1, var3, var5, var7, var9, var11)) {
                  OrionBasketballController.helper61(var14);
                  return;
               }
            }
         }
      }
   };

   private OrionBasketballController() {
   }

   public static void register() {
      net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(OrionBasketballController::tick);
   }

   private static void tick(Minecraft var0) {
      OrionBasketballSettings var1 = OrionBasketballSettings.get();
      var match = network();
      if (match != null && match.engaged()) {
         match.tick(var0, var1.basket);
         TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = Mode.GONE;
         DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
         TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = VCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = "";
         return;
      }
      if (var1.basket && var0.level != null && var0.player != null) {
         MHWMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
         VCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
         String var2 = var0.level.dimension().identifier().toString();
         String var3 = courtKey(var0);
         if (!var3.equals(CCWMWDOMTOCVRHWDIMTOCVRHWDIMTO)) {
            CCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var3;
            HCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = System.currentTimeMillis();
            VMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var2;
            MMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -1;
            TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.GONE;
            OCWMWDOMTOCVRHWDIMTOCVRHWDIMTO.clear();
            RCWMWDOMTOCVRHWDIMTOCVRHWDIMTO.clear();
            ICWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0L;
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = "";
         }

         if (MMWMWDOMTOCVRHWDIMTOCVRHWDIMTO != OrionHoopStore.revision()) {
            MMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionHoopStore.revision();
            WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionHoopStore.forWorld(CCWMWDOMTOCVRHWDIMTOCVRHWDIMTO, VMWMWDOMTOCVRHWDIMTOCVRHWDIMTO);
         }

         helper29(var0);
         boolean var4 = var0.gui == null || OrionBasketballClientAccess.screen(var0) != null;
         helper35(var0, var1, var4);
         if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO != OrionBasketballController.Mode.GONE) {
            helper30(var0);
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.savePrevious();
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.radius = 0.121 * visualScale(var1) * var1.basketBallScale;
            boolean var5 = localOwner(var0);
            switch (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO) {
               case LOOSE:
                  updatePhysics(var0, var1);
                  helper57(var0, var1);
                  break;
               case HELD:
                  if (var5) {
                     updateHeld(var0, var1);
                  } else {
                     helper33(var0);
                  }
                  break;
               case DRIBBLE:
                  updateDribble(var0, var1, var5);
                  updatePhysics(var0, var1);
            }

            helper63(var0);
         }
      } else {
         TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.GONE;
         DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
         TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.0F;
         VCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.0F;
         CCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = "";
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = "";
      }
   }

   private static void helper29(Minecraft var0) {
      if (!WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.isEmpty() && TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.GONE) {
         long var1 = System.currentTimeMillis();
         if (var1 - HCWMWDOMTOCVRHWDIMTOCVRHWDIMTO >= 3000L && var1 - ICWMWDOMTOCVRHWDIMTOCVRHWDIMTO >= 2000L) {
            Vec3 var3 = helper32(var0);
            if (helper31(var0, var3)) {
               RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setPosition(var3.x, var3.y + 1.0, var3.z);
               RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
               RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = var0.player.getName().getString();
               TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.LOOSE;
               WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
               RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wake();

            }
         }
      }
   }

   private static void helper30(Minecraft var0) {
      if (!localOwner(var0)) {
         long var1 = System.currentTimeMillis();
         if (RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner.isEmpty() || var1 - ICWMWDOMTOCVRHWDIMTOCVRHWDIMTO >= 2000L) {
            if (helper31(
               var0, new Vec3(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z)
            )) {
               RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = var0.player.getName().getString();
               if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD
                  || TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.DRIBBLE) {
                  TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.LOOSE;
                  RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wake();
               }

            }
         }
      }
   }

   private static boolean helper31(Minecraft var0, Vec3 var1) {
      String var2 = var0.player.getName().getString();
      double var3 = var0.player.position().distanceToSqr(var1);
      long var5 = System.currentTimeMillis();
      List var7 = var0.level.players();

      for (int var8 = 0; var8 < var7.size(); var8++) {
         AbstractClientPlayer var9 = (AbstractClientPlayer)var7.get(var8);
         String var10 = var9.getName().getString();
         if (var9 != var0.player && !var9.isSpectator()) {
            Long var11 = RCWMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(var10);
            if (var11 != null && var5 - var11 <= 10000L) {
               double var12 = var9.position().distanceToSqr(var1);
               if (var12 < var3 || var12 == var3 && var10.compareTo(var2) < 0) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private static Vec3 helper32(Minecraft var0) {
      if (WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.isEmpty()) {
         return var0.player.position();
      } else if (WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.size() == 1) {
         OrionHoop var3 = WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(0);
         return new Vec3(var3.x + var3.forwardX() * 4.0, var3.base + 0.5, var3.z + var3.forwardZ() * 4.0);
      } else {
         OrionHoop var1 = WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(0);
         OrionHoop var2 = WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(1);
         return new Vec3((var1.x + var2.x) * 0.5, (var1.base + var2.base) * 0.5 + 0.5, (var1.z + var2.z) * 0.5);
      }
   }

   private static void helper33(Minecraft var0) {
      AbstractClientPlayer var1 = helper34(var0, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner);
      if (var1 != null) {
         Vec3 var2 = lookDirection(var1.getXRot(), var1.getYRot());
         Vec3 var3 = lookDirection(var1.getXRot() - 90.0F, var1.getYRot());
         Vec3 var4 = helper38(var1.getEyePosition(), var2, var3, var2.cross(var3), 0.0F);
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x = var4.x;
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y = var4.y;
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z = var4.z;
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = 0.0;
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy = 0.0;
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = 0.0;
      }
   }

   private static AbstractClientPlayer helper34(Minecraft var0, String var1) {
      if (var1 != null && !var1.isEmpty() && var0.level != null) {
         List var2 = var0.level.players();

         for (int var3 = 0; var3 < var2.size(); var3++) {
            if (((AbstractClientPlayer)var2.get(var3)).getName().getString().equals(var1)) {
               return (AbstractClientPlayer)var2.get(var3);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static void helper35(Minecraft var0, OrionBasketballSettings var1, boolean var2) {
      boolean var3 = OrionBasketballInput.consumeHoop();
      boolean var4 = OrionBasketballInput.consumeTake();
      if (var2) {
         DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
         TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.0F;
         HIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
      } else {
         if (var3) {
            placeHoop(var0, var1);
         }

         if (var4) {
            takeBall(var0, var1);
         }

         boolean var5 = var0.options.keyUse.isDown();
         boolean var6 = var0.options.keyAttack.isDown();
         boolean var7 = localOwner(var0);
         if (!var7) {
            DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
            TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.0F;
            HIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var6;
         } else {
            if (var5
               && (
                  TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD
                     || TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.DRIBBLE
               )) {
               if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.DRIBBLE) {
                  helper56(var0, true);
               }

               if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD) {
                  DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;
                  TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = Math.min(1.0F, TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO + (float)(0.05 / Math.max(0.15, var1.basketCharge)));
               }
            } else {
               if (DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO && TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD) {
                  throwBall(var0, var1);
               }

               DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
               TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.0F;
            }

            if (var6 && !HIWMWDOMTOCVRHWDIMTOCVRHWDIMTO && TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD) {
               passBall(var0, var1);
            }

            HIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var6;
         }
      }
   }

   private static void updateHeld(Minecraft var0, OrionBasketballSettings var1) {
      LocalPlayer var2 = var0.player;
      Vec3 var3 = lookDirection(var2.getXRot(), var2.getYRot());
      Vec3 var4 = lookDirection(var2.getXRot() - 90.0F, var2.getYRot());
      Vec3 var5 = var3.cross(var4);
      Vec3 var6 = helper38(var2.getEyePosition(), var3, var4, var5, TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO);
      double var7 = (var6.x - RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x) * 20.0;
      double var9 = (var6.y - RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y) * 20.0;
      double var11 = (var6.z - RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z) * 20.0;
      double var13 = Math.sqrt(var7 * var7 + var9 * var9 + var11 * var11);
      double var15 = var13 > 7.0 ? 7.0 / var13 : 1.0;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = var7 * var15;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy = var9 * var15;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = var11 * var15;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x = var6.x;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y = var6.y;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z = var6.z;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.asleep = false;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.still = 0.0;
      beginWalkingDribble(var0, var1, var2);
   }

   private static void beginWalkingDribble(Minecraft var0, OrionBasketballSettings var1, LocalPlayer var2) {
      if (var1.basketWalk && !DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO) {
         double var3 = var2.getDeltaMovement().horizontalDistance() * 20.0;
         if (!(var3 < 1.7) && var2.onGround()) {
            if (++IIWMWDOMTOCVRHWDIMTOCVRHWDIMTO > 8) {
               IIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
               helper55(var0, var1);
            }
         } else {
            IIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
         }
      } else {
         IIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
      }
   }

   public static Vec3 helper38(Vec3 var0, Vec3 var1, Vec3 var2, Vec3 var3, float var4) {
      Vec3 var5 = helper39(var4);
      return var0.add(var3.scale(var5.x)).add(var2.scale(var5.y)).add(var1.scale(var5.z));
   }

   public static Vec3 helper39(float var0) {
      return new Vec3(0.31 - 0.22 * var0, -0.27 + 0.36 * var0, 0.52 - 0.09 * var0);
   }

   private static void updateDribble(Minecraft var0, OrionBasketballSettings var1, boolean var2) {
      AbstractClientPlayer var3 = var2 ? var0.player : helper34(var0, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner);
      if (var3 != null) {
         Vec3 var4 = helper90(lookDirection(0.0F, var3.getYRot()));
         Vec3 var5 = helper90(var4.cross(new Vec3(0.0, 1.0, 0.0)));
         Vec3 var6 = helper42((AbstractClientPlayer)var3);
         double var7 = var3.getX() + var4.x * 0.44 + var5.x * 0.3;
         double var9 = var3.getZ() + var4.z * 0.44 + var5.z * 0.3;
         double var11 = var3.getY() + 1.15;
         double var13 = var7 - RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x;
         double var15 = var9 - RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z;
         double var17 = Math.sqrt(var13 * var13 + var15 * var15);
         if (!(var17 > 3.2) && !(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y > var11 + 1.6)) {
            double var19 = var6.x + Math.clamp(var13 * 4.5, -5.0, 5.0);
            double var21 = var6.z + Math.clamp(var15 * 4.5, -5.0, 5.0);
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx * 0.65 + var19 * 0.35;
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz * 0.65 + var21 * 0.35;
            if (MHWMWDOMTOCVRHWDIMTOCVRHWDIMTO - CIWMWDOMTOCVRHWDIMTOCVRHWDIMTO >= 9) {
               if (!(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.speed() < 0.8) || !(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y < var3.getY() + 0.45)) {
                  boolean var23 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y + RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.radius >= var11 - 0.28 && var17 < 1.8;
                  boolean var24 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy > -1.0;
                  if (var23 && var24) {
                     helper41(var0, var2, -Math.max(4.8, var1.basketDribble), var19, var21);
                  }
               } else if (MHWMWDOMTOCVRHWDIMTOCVRHWDIMTO - CIWMWDOMTOCVRHWDIMTOCVRHWDIMTO >= 10) {
                  helper41(var0, var2, Math.max(4.6, var1.basketDribble), var19, var21);
               }
            }
         } else {
            if (var2) {
               TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.LOOSE;
            }
         }
      }
   }

   private static void helper41(Minecraft var0, boolean var1, double var2, double var4, double var6) {
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy = var2;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = var4;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = var6;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wx = 0.0;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wy = 0.0;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wz = 0.0;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wake();
      CIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = MHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      if (var1) {
         OrionBasketballAudio.helper23(
            OrionBasketballAudio.VDWMWDOMTOCVRHWDIMTOCVRHWDIMTO,
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x,
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y,
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z,
            0.35F,
            1.0F
         );

      }
   }

   private static Vec3 helper42(AbstractClientPlayer var0) {
      return var0.getKnownSpeed().scale(20.0);
   }

   private static void updatePhysics(Minecraft var0, OrionBasketballSettings var1) {
      double var2 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.speed() * 0.05;
      DMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.collect(
         var0.level, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var2, WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO, helper44(var0)
      );
      OrionBallPhysics.advance(
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO,
         helper45(var1),
         DMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.colliders(),
         0.05,
         OIWMWDOMTOCVRHWDIMTOCVRHWDIMTO
      );
   }

   private static AbstractClientPlayer helper44(Minecraft var0) {
      return (AbstractClientPlayer)(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner != null
            && !RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner.isEmpty()
            && !localOwner(var0)
         ? helper34(var0, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner)
         : var0.player);
   }

   public static OrionBallPhysics.Parameters helper45(OrionBasketballSettings var0) {
      OrionBallPhysics.Parameters var1 = OrionBallPhysics.Parameters.TMTIMDOMTOCVRHWDIMTOCVRHWDIMTO;
      return new OrionBallPhysics.Parameters(
         Math.max(0.5, var0.basketGravity),
         var1.drag(),
         var1.magnus() * Math.max(0.0, var0.basketSpin),
         var1.spinDecay(),
         var1.roll(),
         Math.clamp(var0.basketBounce, 0.1, 2.0),
         var1.friction()
      );
   }

   private static void throwBall(Minecraft var0, OrionBasketballSettings var1) {
      LocalPlayer var2 = var0.player;
      Vec3 var3 = lookDirection(var2.getXRot(), var2.getYRot());
      Vec3 var4 = lookDirection(var2.getXRot() - 90.0F, var2.getYRot());
      Vec3 var5 = var3.cross(var4);
      Vec3 var6 = helper38(var2.getEyePosition(), var3, var4, var5, TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO);
      Vec3 var7 = helper47(var2, var6, var3, TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var1);
      double var8 = helper49(TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var1);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setPosition(var6.x, var6.y, var6.z);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = var7.x;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy = var7.y;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = var7.z;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setSpin(var5.x * var8, var5.y * var8, var5.z * var8);
      helper52(var2, OrionBasketballController.Mode.LOOSE);
      DHWMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
      OrionBasketballAudio.helper23(OrionBasketballAudio.VDWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var6.x, var6.y, var6.z, 0.5F, 1.25F);

   }

   public static Vec3 helper47(LocalPlayer var0, Vec3 var1, Vec3 var2, float var3, OrionBasketballSettings var4) {
      double var5 = Math.max(5.5, var4.basketPower);
      double var7 = 4.5 + (var5 - 4.5) * var3;
      Vec3 var9 = var0.getDeltaMovement().scale(7.0);
      Vec3 var10 = helper48(var0, var1, var2);
      return new Vec3(var10.x * var7 + var9.x, var10.y * var7 + var9.y, var10.z * var7 + var9.z);
   }

   public static Vec3 helper48(LocalPlayer var0, Vec3 var1, Vec3 var2) {
      Vec3 var3 = var0.getEyePosition();
      double var4 = 40.0;
      Minecraft var6 = Minecraft.getInstance();
      if (var6.level != null) {
         BlockHitResult var7 = var6.level.clip(new ClipContext(var3, var3.add(var2.scale(40.0)), Block.COLLIDER, Fluid.NONE, var0));
         if (var7.getType() == Type.BLOCK) {
            var4 = var7.getLocation().distanceTo(var3);
         }
      }

      for (int var10 = 0; var10 < hoops().size(); var10++) {
         double var8 = hoops().get(var10).boardHitTime(var3.x, var3.y, var3.z, var2.x, var2.y, var2.z);
         if (var8 > 0.0 && var8 < var4) {
            var4 = var8;
         }
      }

      Vec3 var11 = var3.add(var2.scale(Math.max(1.5, var4))).subtract(var1);
      double var12 = var11.length();
      return var12 < 1.0E-6 ? var2 : var11.scale(1.0 / var12);
   }

   public static double helper49(float var0, OrionBasketballSettings var1) {
      return 13.0 * Math.max(0.0, var1.basketSpin) * (0.6 + 0.4 * var0);
   }

   private static void passBall(Minecraft var0, OrionBasketballSettings var1) {
      LocalPlayer var2 = var0.player;
      Vec3 var3 = lookDirection(var2.getXRot(), var2.getYRot());
      Vec3 var4 = lookDirection(var2.getXRot() - 90.0F, var2.getYRot());
      Vec3 var5 = var3.cross(var4);
      Vec3 var6 = helper38(var2.getEyePosition(), var3, var4, var5, 0.15F);
      double var7 = Math.max(6.0, var1.basketPass);
      Vec3 var9 = helper51(var0, var6, var3, var7);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setPosition(var6.x, var6.y, var6.z);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = var9.x * var7;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy = var9.y * var7;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = var9.z * var7;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setSpin(var5.x * 6.0, var5.y * 6.0, var5.z * 6.0);
      helper52(var2, OrionBasketballController.Mode.LOOSE);
      OrionBasketballAudio.helper23(OrionBasketballAudio.VDWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var6.x, var6.y, var6.z, 0.45F, 0.95F);

   }

   static Vec3 helper51(Minecraft var0, Vec3 var1, Vec3 var2, double var3) {
      AbstractClientPlayer var5 = null;
      double var6 = 0.86;
      List var8 = var0.level.players();

      for (int var9 = 0; var9 < var8.size(); var9++) {
         AbstractClientPlayer var10 = (AbstractClientPlayer)var8.get(var9);
         if (var10 != var0.player && !var10.isSpectator()) {
            Vec3 var11 = var10.position().add(0.0, 1.1, 0.0);
            Vec3 var12 = var11.subtract(var1);
            double var13 = var12.length();
            if (!(var13 < 1.0) && !(var13 > 40.0)) {
               double var15 = var12.scale(1.0 / var13).dot(var2);
               if (var15 > var6) {
                  var6 = var15;
                  var5 = var10;
               }
            }
         }
      }

      if (var5 == null) {
         return helper48(var0.player, var1, var2);
      }

      Vec3 var17 = var5.position().add(var5.getDeltaMovement().scale(4.0)).add(0.0, 1.1, 0.0);
      Vec3 var18 = var17.subtract(var1);
      double var19 = var18.length() / var3;
      return var18.add(0.0, 4.905 * var19 * var19, 0.0).normalize();
   }

   private static void helper52(LocalPlayer var0, OrionBasketballController.Mode var1) {
      TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var1;
      WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;
      MCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;
      CHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var0.getX();
      HHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var0.getZ();
      IHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = System.currentTimeMillis();
      DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
      TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.0F;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = var0.getName().getString();
   }

   private static void takeBall(Minecraft var0, OrionBasketballSettings var1) {
      switch (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO) {
         case GONE:
            helper54(var0, var1);
            break;
         case LOOSE:
            if (helper56(var0, false)) {
               return;
            }

            helper62(
               var0, OrionBasketballText.get("orion.basket.myach_daleko_podoydi_k")
            );
            break;
         case HELD:
         case DRIBBLE:
            if (!localOwner(var0)) {
               helper62(
                  var0,
                  OrionBasketballText.format(
                     "orion.basket.myach_u_s_dozhdis_pasa", new Object[]{RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner}
                  )
               );
               return;
            }

            if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD) {
               helper55(var0, var1);
            } else if (!helper56(var0, false)) {
               helper62(
                  var0,
                  OrionBasketballText.get("orion.basket.myach_daleko_podoydi_k")
               );
            }
      }
   }

   private static void helper54(Minecraft var0, OrionBasketballSettings var1) {
      LocalPlayer var2 = var0.player;
      Vec3 var3 = lookDirection(var2.getXRot(), var2.getYRot());
      Vec3 var4 = lookDirection(var2.getXRot() - 90.0F, var2.getYRot());
      Vec3 var5 = var3.cross(var4);
      Vec3 var6 = helper38(var2.getEyePosition(), var3, var4, var5, 0.0F);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.radius = 0.121 * visualScale(var1) * var1.basketBallScale;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setPosition(var6.x, var6.y, var6.z);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.turn.identity();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.prevTurn.identity();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = var2.getName().getString();
      TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.HELD;
      WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
      OrionBasketballAudio.helper23(OrionBasketballAudio.TDWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var6.x, var6.y, var6.z, 0.5F, 1.0F);

   }

   private static void helper55(Minecraft var0, OrionBasketballSettings var1) {
      LocalPlayer var2 = var0.player;
      Vec3 var3 = helper90(lookDirection(0.0F, var2.getYRot()));
      Vec3 var4 = helper90(var3.cross(new Vec3(0.0, 1.0, 0.0)));
      Vec3 var5 = helper42(var2);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setPosition(
         var2.getX() + var3.x * 0.44 + var4.x * 0.3, var2.getY() + 1.15, var2.getZ() + var3.z * 0.44 + var4.z * 0.3
      );
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vx = var5.x;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vz = var5.z;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.vy = -Math.max(4.6, var1.basketDribble);
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = var2.getName().getString();
      TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.DRIBBLE;
      WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
      CIWMWDOMTOCVRHWDIMTOCVRHWDIMTO = MHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      OrionBasketballAudio.helper23(
         OrionBasketballAudio.VDWMWDOMTOCVRHWDIMTOCVRHWDIMTO,
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x,
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y,
         RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z,
         0.35F,
         1.0F
      );

   }

   private static boolean helper56(Minecraft var0, boolean var1) {
      LocalPlayer var2 = var0.player;
      Vec3 var3 = var2.getEyePosition();
      double var4 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x - var3.x;
      double var6 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y - var3.y;
      double var8 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z - var3.z;
      double var10 = Math.sqrt(var4 * var4 + var6 * var6 + var8 * var8);
      if (var10 > 2.0 || RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.speed() > 26.0) {
         return false;
      }

      if (System.currentTimeMillis() - IHWMWDOMTOCVRHWDIMTOCVRHWDIMTO < 420L) {
         return false;
      }

      TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = OrionBasketballController.Mode.HELD;
      WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner = var2.getName().getString();
      if (!var1) {
         OrionBasketballAudio.helper23(
            OrionBasketballAudio.TDWMWDOMTOCVRHWDIMTOCVRHWDIMTO,
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x,
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y,
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z,
            0.55F,
            1.0F
         );
      }

      return true;
   }

   private static void helper57(Minecraft var0, OrionBasketballSettings var1) {
      if (var1.basketCatch && !RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.asleep) {
         LocalPlayer var2 = var0.player;
         Vec3 var3 = var2.getEyePosition();
         Vec3 var4 = lookDirection(var2.getXRot(), var2.getYRot());
         Vec3 var5 = new Vec3(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x - var3.x, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y - var3.y, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z - var3.z);
         double var6 = var5.length();
         if (!(var6 > 2.0) && !(var6 < 1.0E-6)) {
            if (!(var5.scale(1.0 / var6).dot(var4) < -0.15)) {
               helper56(var0, false);
            }
         }
      }
   }

   private static void placeHoop(Minecraft var0, OrionBasketballSettings var1) {
      if (!placeHoopAtLook(var0)) {
         helper62(
            var0, OrionBasketballText.get("orion.basket.nekuda_stavit_kolco_nuzhen")
         );
      } else {
         helper62(
            var0, OrionBasketballText.get("orion.basket.kolco_postavleno")
         );
         OrionBasketballAudio.helper23(
            OrionBasketballAudio.ODWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var0.player.getX(), var0.player.getY(), var0.player.getZ(), 0.4F, 1.1F
         );
      }
   }

   public static boolean placeHoopAtLook(Minecraft var0) {
      OrionHoop var1 = OrionHoopStore.placeAtLook(
         var0, visualScale(OrionBasketballSettings.get())
      );
      if (var1 == null) {
         return false;
      }

      MMWMWDOMTOCVRHWDIMTOCVRHWDIMTO = -1;
      return true;
   }

   public static void spawnBall(Minecraft var0) {
      if (var0.player != null && var0.level != null) {
         helper54(var0, OrionBasketballSettings.get());
      }
   }

   private static void helper61(OrionHoop var0) {
      WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
      int var1 = var0.points(CHWMWDOMTOCVRHWDIMTOCVRHWDIMTO, HHWMWDOMTOCVRHWDIMTOCVRHWDIMTO);
      OHWMWDOMTOCVRHWDIMTOCVRHWDIMTO += var1;
      RHWMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
      THWMWDOMTOCVRHWDIMTOCVRHWDIMTO = System.currentTimeMillis();
      VHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = var1;
      WHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = MCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      OrionBasketballAudio.helper22(var0.x, var0.y, var0.z, MCWMWDOMTOCVRHWDIMTOCVRHWDIMTO);
      Minecraft var2 = Minecraft.getInstance();
      if (var2.player != null) {
         String var3 = MCWMWDOMTOCVRHWDIMTOCVRHWDIMTO
            ? OrionBasketballText.get("orion.basket.chisto")
            : OrionBasketballText.get("orion.basket.est");
         if (!var3.endsWith("+")) {
            var3 = (var3.endsWith(" ") ? var3 : var3 + " ")
               + "+";
         }

         helper62(var2, var3 + var1);

      }
   }

   private static void helper62(Minecraft var0, String var1) {
      if (var0.player != null) {
         OrionBasketballClientAccess.message(var0.player, Component.literal(var1));
      }
   }

   private static void helper63(Minecraft var0) {
      if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.LOOSE
         && var0.level != null
         && var0.player != null
         && localOwner(var0)) {
         Vec3 var1 = helper32(var0);
         double var2 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.x - var1.x;
         double var4 = RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.z - var1.z;
         double var6 = var2 * var2 + var4 * var4;
         double var8 = WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.isEmpty() ? 180.0 : 36.0;
         if (!(var6 <= var8 * var8) || !(RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y > var0.level.getMinY() - 5)) {
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.setPosition(var1.x, var1.y + 1.0, var1.z);
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.stop();
            WCWMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;
            RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.wake();

            helper62(
               var0, OrionBasketballText.get("orion.basket.myach_uletel_za_predely")
            );
         }
      }
   }

   public static OrionBallState ball() {
      var match = network();
      if (match != null && match.engaged()) return match.ball();
      return RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static OrionBasketballController.Mode mode() {
      var match = network();
      if (match != null && match.engaged()) {
         if (!match.active()) return Mode.GONE;
         return match.holding() ? match.dribbling() ? Mode.DRIBBLE : Mode.HELD : Mode.LOOSE;
      }
      return TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static boolean ballExists() {
      var match = network();
      if (match != null && match.engaged()) return match.active();
      return TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO != OrionBasketballController.Mode.GONE && OrionBasketballSettings.get().basket;
   }

   public static boolean localHolding() {
      var match = network();
      if (match != null && match.engaged()) return match.holding();
      return ballExists()
         && (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.HELD || TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.DRIBBLE)
         && localOwner(Minecraft.getInstance());
   }

   public static float charge(float var0) {
      var match = network();
      if (match != null && match.engaged()) return match.charge();
      return VCWMWDOMTOCVRHWDIMTOCVRHWDIMTO + (TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO - VCWMWDOMTOCVRHWDIMTOCVRHWDIMTO) * var0;
   }

   public static boolean charging() {
      var match = network();
      if (match != null && match.engaged()) return match.charging();
      return DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static List<OrionHoop> hoops() {
      var match = network();
      if (match != null && match.engaged()) return match.hoops();
      return WMWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static int score() {
      var match = network();
      if (match != null && match.engaged()) return match.red() + match.blue();
      return OHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static int hits() {
      return RHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static int attempts() {
      return DHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static long scoredAt() {
      return THWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static int lastPoints() {
      return VHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static boolean swish() {
      return WHWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static float handBounce(AbstractClientPlayer var0, float var1) {
      var match = network();
      if (match != null && match.engaged()) return match.handBounce();
      if (TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO == OrionBasketballController.Mode.DRIBBLE && var0 != null) {
         double var2 = Mth.lerp(var1, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.prevY, RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.y) + RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.radius;
         double var4 = Mth.lerp(var1, var0.yOld, var0.getY()) + 1.15;
         double var6 = Math.max(0.2, 1.15 - 2.0 * RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.radius - 0.28);
         return (float)Math.clamp((var4 - var2 - 0.28) / var6, 0.0, 1.0);
      } else {
         return 0.0F;
      }
   }

   public static void resetScore() {
      OHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
      RHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
      DHWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0;
      THWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0L;
   }

   public static void removeBall() {
      TMWMWDOMTOCVRHWDIMTOCVRHWDIMTO=Mode.GONE;
      RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner="";
      DCWMWDOMTOCVRHWDIMTOCVRHWDIMTO=false;
      TCWMWDOMTOCVRHWDIMTOCVRHWDIMTO=0F;
   }

   public static String courtKey(Minecraft var0) {
      if (var0 != null && var0.level != null) {
         ServerData var1 = var0.getCurrentServer();
         String var2;
         if (var1 != null && var1.ip != null && !var1.ip.isBlank()) {
            var2 = "server:"+var1.ip;
         } else {
            IntegratedServer var3 = var0.getSingleplayerServer();
            var2 = var3 == null ? "local:unknown" : "single:" + var3.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
         }

         return var2.toLowerCase(java.util.Locale.ROOT) + "|" + var0.level.dimension().identifier();
      } else {
         return "";
      }
   }

   public static boolean localOwner(Minecraft var0) {
      var match = network();
      if (match != null && match.engaged()) return match.holding();
      return var0 != null
         && var0.player != null
         && RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner != null
         && RMWMWDOMTOCVRHWDIMTOCVRHWDIMTO.owner.equals(var0.player.getName().getString());
   }













   public static double visualScale(OrionBasketballSettings var0) {
      return 2.1;
   }

   public static void pickup() {
      var match = network();
      if (match != null && match.engaged()) { match.pickup(); return; }
      Minecraft client=Minecraft.getInstance();
      if(client.player!=null && client.level!=null && mode()==Mode.LOOSE) helper56(client,false);
   }

   public static Vec3 lookDirection(float var0, float var1) {
      double var2 = Math.toRadians(var0);
      double var4 = Math.toRadians(-var1);
      double var6 = Math.cos(var2);
      return new Vec3(Math.sin(var4) * var6, -Math.sin(var2), Math.cos(var4) * var6);
   }

   private static Vec3 helper90(Vec3 var0) {
      double var1 = Math.sqrt(var0.x * var0.x + var0.z * var0.z);
      return var1 < 1.0E-9 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(var0.x / var1, 0.0, var0.z / var1);
   }

   // $VF: synthetic method
   private static String DTVWMCHIORDTVWMCHIORDTVWMCHIORDX(String var0) {
      char[] var1 = var0.toCharArray();

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = (char)(var1[var2] ^ 37130);
      }

      return new String(var1).intern();
   }

   public enum Mode {
      GONE,
      LOOSE,
      HELD,
      DRIBBLE;

      // $VF: synthetic method
      private static OrionBasketballController.Mode[] RWDMWDOMTOCVRHWDIMTOCVRHWDIMTO() {
         return new OrionBasketballController.Mode[]{GONE, LOOSE, HELD, DRIBBLE};
      }

      // $VF: synthetic method
      private static String DTVWMCHIORDTVWMCHIORDTVWMCHIORDX(String var0) {
         char[] var1 = var0.toCharArray();

         for (int var2 = 0; var2 < var1.length; var2++) {
            var1[var2] = (char)(var1[var2] ^ 27474);
         }

         return new String(var1).intern();
      }
   }
}
