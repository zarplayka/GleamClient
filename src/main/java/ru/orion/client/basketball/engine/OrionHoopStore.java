package ru.orion.client.basketball.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class OrionHoopStore {
   private static final Gson RVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = new GsonBuilder().setPrettyPrinting().create();
   private static final Path DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
      .resolve("orion-basketball-courts-v2.json");
   private static final double TVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 24.0;
   private static final int VVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = 24;
   private static final List<OrionHoop> WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = new ArrayList<>();
   private static boolean MVMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static boolean CWMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   private static int HWMMWDOMTOCVRHWDIMTOCVRHWDIMTO;

   private OrionHoopStore() {
   }

   public static boolean hasStoredFile() { return Files.exists(DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO); }

   public static void load() {
      WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.clear();
      MVMMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;

      try {
         if (!Files.exists(DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO)) {
            return;
         }

         String var0 = Files.readString(DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO, StandardCharsets.UTF_8);
         List<OrionHoop> var1 = RVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.fromJson(var0, (new TypeToken<List<OrionHoop>>() {}).getType());
         if (var1 == null) {
            return;
         }

         for (OrionHoop var3 : var1) {
            if (var3 != null && var3.dimension != null && var3.id != null && Double.isFinite(var3.scale) && var3.scale > 0.0
                    && var3.scale <= 10 && Double.isFinite(var3.x) && Double.isFinite(var3.y) && Double.isFinite(var3.z)
                    && Double.isFinite(var3.base) && Float.isFinite(var3.yaw)) {
               if (var3.court == null) {
                  var3.court = "";
               }

               WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.add(var3);
            }
         }

         HWMMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
      } catch (Exception var4) {
         org.slf4j.LoggerFactory.getLogger("orion-client")
            .warn("Не удалось загрузить баскетбольные кольца: {}", var4.toString());
      }
   }

   public static void save() {
      if (CWMMWDOMTOCVRHWDIMTOCVRHWDIMTO) {
         CWMMWDOMTOCVRHWDIMTOCVRHWDIMTO = false;

         try {
            Files.createDirectories(DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.getParent());
            Path temporary=DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.resolveSibling(DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.getFileName()+".tmp");
            Files.writeString(temporary,RVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.toJson(WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO),StandardCharsets.UTF_8);
            try {
               Files.move(temporary,DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch(java.nio.file.AtomicMoveNotSupportedException ignored) {
               Files.move(temporary,DVMMWDOMTOCVRHWDIMTOCVRHWDIMTO,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var1) {
            org.slf4j.LoggerFactory.getLogger("orion-client")
               .warn("Не удалось сохранить баскетбольные кольца: {}", var1.toString());
         }
      }
   }

   public static List<OrionHoop> all() {
      helper175();
      return WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static List<OrionHoop> forWorld(String var0, String var1) {
      helper175();
      ArrayList var2 = new ArrayList();

      for (int var3 = 0; var3 < WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.size(); var3++) {
         OrionHoop var4 = WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(var3);
         if (var4.dimension.equals(var1) && helper156(var4.court, var0)) {
            var2.add(var4);
         }
      }

      return var2;
   }

   public static List<OrionHoop> inWorld(Minecraft var0) {
      return forWorld(OrionBasketballController.courtKey(var0), helper173(var0));
   }

   private static boolean helper156(String var0, String var1) {
      return var0 == null || var0.isEmpty() || var0.equals(var1);
   }

   public static int total() {
      helper175();
      return WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.size();
   }

   public static int revision() {
      helper175();
      return HWMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
   }

   public static void put(OrionHoop var0) {
      helper160(var0, true);
   }

   public static void helper160(OrionHoop var0, boolean var1) {
      helper175();
      WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.removeIf(var1x -> var1x.id.equals(var0.id));
      WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.add(var0);
      HWMMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
      CWMMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;
      save();
      if (var1) {

      }
   }

   public static boolean remove(OrionHoop var0) {
      return helper162(var0, true);
   }

   public static boolean helper162(OrionHoop var0, boolean var1) {
      helper175();
      boolean var2 = WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.removeIf(var1x -> var1x.id.equals(var0.id));
      if (var2) {
         HWMMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
         CWMMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;
         save();
         if (var1) {

         }
      }

      return var2;
   }

   public static int clearWorld(String var0, String var1) {
      return helper164(var0, var1, true);
   }

   public static int helper164(String var0, String var1, boolean var2) {
      helper175();
      int var3 = WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.size();
      WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.removeIf(var2x -> var2x.dimension.equals(var1) && helper156(var2x.court, var0));
      if (WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.size() != var3) {
         HWMMWDOMTOCVRHWDIMTOCVRHWDIMTO++;
         CWMMWDOMTOCVRHWDIMTOCVRHWDIMTO = true;
         save();

      }

      return var3 - WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.size();
   }









   public static OrionHoop helper169(String var0, double var1, double var3, double var5, double var7) {
      helper175();
      OrionHoop var9 = null;
      double var10 = var7 * var7;

      for (int var12 = 0; var12 < WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.size(); var12++) {
         OrionHoop var13 = WVMMWDOMTOCVRHWDIMTOCVRHWDIMTO.get(var12);
         if (var13.dimension.equals(var0)) {
            double var14 = var13.x - var1;
            double var16 = var13.y - var3;
            double var18 = var13.z - var5;
            double var20 = var14 * var14 + var16 * var16 + var18 * var18;
            if (var20 < var10) {
               var10 = var20;
               var9 = var13;
            }
         }
      }

      return var9;
   }

   public static OrionHoop placeAtLook(Minecraft var0, double var1) {
      LocalPlayer var3 = var0.player;
      if (var3 != null && var0.level != null) {
         Vec3 var4 = var3.getEyePosition();
         Vec3 var5 = var4.add(var3.getViewVector(1.0F).scale(24.0));
         BlockHitResult var6 = var0.level.clip(new ClipContext(var4, var5, Block.OUTLINE, Fluid.NONE, var3));
         if (var6.getType() != Type.BLOCK) {
            return null;
         }

         Vec3 var7 = var6.getLocation();
         double var8 = helper171(var0, var7);
         float var10 = helper172(var3.getYRot());
         double var11 = Math.toRadians(var10);
         double var13 = -Math.sin(var11);
         double var15 = Math.cos(var11);
         double var17 = OrionHoop.scaledBoardOffset(var1);
         OrionHoop var19 = new OrionHoop(
            helper174(),
            helper173(var0),
            OrionBasketballController.courtKey(var0),
            var7.x + var13 * var17,
            var8 + 3.05,
            var7.z + var15 * var17,
            var8,
            var10,
            var1
         );
         put(var19);
         return var19;
      } else {
         return null;
      }
   }

   private static double helper171(Minecraft var0, Vec3 var1) {
      Vec3 var2 = var1.add(0.0, 0.05, 0.0);
      Vec3 var3 = new Vec3(var1.x, var1.y - 24.0, var1.z);
      BlockHitResult var4 = var0.level.clip(new ClipContext(var2, var3, Block.COLLIDER, Fluid.NONE, var0.player));
      return var4.getType() == Type.BLOCK ? var4.getLocation().y : var1.y;
   }

   private static float helper172(float var0) {
      float var1 = var0 + 180.0F;

      while (var1 > 180.0F) {
         var1 -= 360.0F;
      }

      while (var1 < -180.0F) {
         var1 += 360.0F;
      }

      return var1;
   }

   public static String helper173(Minecraft var0) {
      return var0.level == null ? "" : var0.level.dimension().identifier().toString();
   }

   private static String helper174() {
      String var0 = Long.toHexString(System.nanoTime() ^ (long)(Math.random() * 9.223372E18F));
      return var0.length() > 8 ? var0.substring(var0.length() - 8) : var0;
   }

   private static void helper175() {
      if (!MVMMWDOMTOCVRHWDIMTOCVRHWDIMTO) {
         load();
      }
   }

   // $VF: synthetic method
   private static String DTVWMCHIORDTVWMCHIORDTVWMCHIORDX(String var0) {
      char[] var1 = var0.toCharArray();

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = (char)(var1[var2] ^ 20187);
      }

      return new String(var1).intern();
   }
}
