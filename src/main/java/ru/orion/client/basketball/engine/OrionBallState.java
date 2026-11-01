package ru.orion.client.basketball.engine;

import org.joml.Quaternionf;

public final class OrionBallState {
   public static final double TTVMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.121;
   public static final double VTVMWDOMTOCVRHWDIMTOCVRHWDIMTO = 0.624;
   public double x;
   public double y;
   public double z;
   public double vx;
   public double vy;
   public double vz;
   public double wx;
   public double wy;
   public double wz;
   public double radius = 0.121;
   public double mass = 0.624;
   public final Quaternionf turn = new Quaternionf();
   public double prevX;
   public double prevY;
   public double prevZ;
   public final Quaternionf prevTurn = new Quaternionf();
   public boolean asleep;
   public double still;
   public String owner = "";
   public int seq;

   public double speed() {
      return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
   }

   public double spinSpeed() {
      return Math.sqrt(this.wx * this.wx + this.wy * this.wy + this.wz * this.wz);
   }

   public double inertia() {
      return 0.6666666666666666 * this.mass * this.radius * this.radius;
   }

   public void setPosition(double var1, double var3, double var5) {
      this.x = var1;
      this.y = var3;
      this.z = var5;
      this.prevX = var1;
      this.prevY = var3;
      this.prevZ = var5;
   }

   public void addVelocity(double var1, double var3, double var5) {
      this.vx += var1;
      this.vy += var3;
      this.vz += var5;
      this.wake();
   }

   public void setSpin(double var1, double var3, double var5) {
      this.wx = var1;
      this.wy = var3;
      this.wz = var5;
   }

   public void stop() {
      this.vx = 0.0;
      this.vy = 0.0;
      this.vz = 0.0;
      this.wx = 0.0;
      this.wy = 0.0;
      this.wz = 0.0;
      this.still = 0.0;
      this.asleep = false;
   }

   public void wake() {
      this.asleep = false;
      this.still = 0.0;
   }

   public void savePrevious() {
      this.prevX = this.x;
      this.prevY = this.y;
      this.prevZ = this.z;
      this.prevTurn.set(this.turn);
   }
}
