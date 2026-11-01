package ru.orion.client.basketball.engine;

public final class OrionBallContact {
   public double nx;
   public double ny;
   public double nz;
   public double depth;
   public OrionBallMaterial material = OrionBallMaterial.DWMMWDOMTOCVRHWDIMTOCVRHWDIMTO;

   public void helper2(double var1, double var3, double var5, double var7, OrionBallMaterial var9) {
      this.nx = var1;
      this.ny = var3;
      this.nz = var5;
      this.depth = var7;
      this.material = var9;
   }
}
