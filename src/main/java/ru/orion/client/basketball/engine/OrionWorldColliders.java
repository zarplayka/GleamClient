package ru.orion.client.basketball.engine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OrionWorldColliders {
   private static final double CTWMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.3;
   private final List<OrionBallCollider> colliders = new ArrayList<>();
   private final MutableBlockPos cursor = new MutableBlockPos();

   public List<OrionBallCollider> colliders() {
      return this.colliders;
   }

   public void collect(ClientLevel var1, OrionBallState var2, double var3, List<OrionHoop> var5, AbstractClientPlayer var6) {
      this.colliders.clear();
      if (var1 != null) {
         double var7 = var2.radius + var3 + 0.3;
         double var9 = var2.x - var7;
         double var11 = var2.y - var7;
         double var13 = var2.z - var7;
         double var15 = var2.x + var7;
         double var17 = var2.y + var7;
         double var19 = var2.z + var7;
         this.helper178(var1, var9, var11, var13, var15, var17, var19);
         this.helper179(var1, var6, var9, var11, var13, var15, var17, var19);

         for (int var21 = 0; var21 < var5.size(); var21++) {
            OrionHoop var22 = (OrionHoop)var5.get(var21);
            if (Math.abs(var22.x - var2.x) < 12.0 && Math.abs(var22.z - var2.z) < 12.0 && Math.abs(var22.y - var2.y) < 12.0) {
               var22.addColliders(this.colliders);
            }
         }
      }
   }

   private void helper178(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12) {
      int var14 = Mth.floor(var2);
      int var15 = Mth.floor(var4);
      int var16 = Mth.floor(var6);
      int var17 = Mth.floor(var8);
      int var18 = Mth.floor(var10);
      int var19 = Mth.floor(var12);

      for (int var20 = var14; var20 <= var17; var20++) {
         for (int var21 = var15; var21 <= var18; var21++) {
            for (int var22 = var16; var22 <= var19; var22++) {
               this.cursor.set(var20, var21, var22);
               BlockState var23 = var1.getBlockState(this.cursor);
               if (!var23.isAir()) {
                  VoxelShape var24 = var23.getCollisionShape(var1, this.cursor);
                  if (!var24.isEmpty()) {
                     OrionBallMaterial var25 = helper180(var23);

                     for (AABB var27 : var24.toAabbs()) {
                        this.colliders
                           .add(
                              new OrionBallCollider.Box(
                                 var27.minX + var20, var27.minY + var21, var27.minZ + var22, var27.maxX + var20, var27.maxY + var21, var27.maxZ + var22, var25
                              )
                           );
                     }
                  }
               }
            }
         }
      }
   }

   private void helper179(
      ClientLevel var1, AbstractClientPlayer var2, double var3, double var5, double var7, double var9, double var11, double var13
   ) {
      List var15 = var1.players();

      for (int var16 = 0; var16 < var15.size(); var16++) {
         AbstractClientPlayer var17 = (AbstractClientPlayer)var15.get(var16);
         if (var17 != var2 && !var17.isSpectator()) {
            AABB var18 = var17.getBoundingBox();
            if (!(var18.maxX < var3) && !(var18.minX > var9) && !(var18.maxY < var5) && !(var18.minY > var11) && !(var18.maxZ < var7) && !(var18.minZ > var13)) {
               this.colliders
                  .add(
                     new OrionBallCollider.Box(
                        var18.minX, var18.minY, var18.minZ, var18.maxX, var18.maxY, var18.maxZ, OrionBallMaterial.RMMMWDOMTOCVRHWDIMTOCVRHWDIMTO
                     )
                  );
            }
         }
      }
   }

   private static OrionBallMaterial helper180(BlockState var0) {
      SoundType var1 = var0.getSoundType();
      if (var1 == SoundType.SLIME_BLOCK) {
         return OrionBallMaterial.IMMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      } else if (var1 == SoundType.HONEY_BLOCK) {
         return OrionBallMaterial.OMMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      } else if (var1 == SoundType.WOOL
         || var1 == SoundType.SNOW
         || var1 == SoundType.POWDER_SNOW
         || var1 == SoundType.GRASS
         || var1 == SoundType.WET_GRASS
         || var1 == SoundType.SWEET_BERRY_BUSH
         || var1 == SoundType.VINE) {
         return OrionBallMaterial.MWMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      } else if (var1 == SoundType.SAND || var1 == SoundType.GRAVEL) {
         return OrionBallMaterial.CMMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      } else {
         return var1 != SoundType.GLASS && var1 != SoundType.METAL
            ? OrionBallMaterial.DWMMWDOMTOCVRHWDIMTOCVRHWDIMTO
            : OrionBallMaterial.HMMMWDOMTOCVRHWDIMTOCVRHWDIMTO;
      }
   }
}
