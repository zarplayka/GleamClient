package ru.orion.client.basketball.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class OrionBasketballAudio {
   public static final SoundEvent IDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper24(
      "basket.bounce"
   );
   public static final SoundEvent ODWMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper24(
      "basket.rim"
   );
   public static final SoundEvent RDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper24(
      "basket.board"
   );
   public static final SoundEvent DDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper24(
      "basket.swish"
   );
   public static final SoundEvent TDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper24(
      "basket.catch"
   );
   public static final SoundEvent VDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = helper24(
      "basket.hand"
   );
   private static final double WDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.9;
   private static final double MDWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 7.0;

   private OrionBasketballAudio() {
   }

   public static void helper20() {
   }

   public static void helper21(OrionBallMaterial var0, double var1, double var3, double var5, double var7) {
      if (!(var1 < 0.9)) {
         SoundEvent var9 = switch (var0.sound()) {
            case 1 -> ODWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
            case 2 -> RDWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
            default -> IDWMWDOMTOCVRHWDIMTOCVRHWDIMTO;
         };
         float var10 = (float)Math.clamp(var1 / 7.0, 0.1, 1.0);
         float var11 = (float)(0.92 + Math.min(0.3, var1 / 26.0));
         helper23(var9, var3, var5, var7, var10, var11);
      }
   }

   public static void helper22(double var0, double var2, double var4, boolean var6) {
      helper23(DDWMWDOMTOCVRHWDIMTOCVRHWDIMTO, var0, var2, var4, var6 ? 0.85F : 0.65F, var6 ? 1.06F : 0.98F);
   }

   public static void helper23(SoundEvent var0, double var1, double var3, double var5, float var7, float var8) {
      Minecraft var9 = Minecraft.getInstance();
      float var10 = Math.clamp(OrionBasketballSettings.get().basketVolume, 0.0F, 1.0F);
      if (var9.level != null && var0 != null && !(var10 <= 0.001F)) {
         var9.level.playLocalSound(var1, var3, var5, var0, SoundSource.PLAYERS, var7 * var10, var8, false);
      }
   }

   private static SoundEvent helper24(String var0) {
      Identifier var1 = Identifier.fromNamespaceAndPath("orion-client", var0);
      return SoundEvent.createVariableRangeEvent(var1);
   }

   // $VF: synthetic method
   private static String DTVWMCHIORDTVWMCHIORDTVWMCHIORDX(String var0) {
      char[] var1 = var0.toCharArray();

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = (char)(var1[var2] ^ 57044);
      }

      return new String(var1).intern();
   }
}
