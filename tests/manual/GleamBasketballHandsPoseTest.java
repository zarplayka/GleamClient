package ru.orion.client.basketball.engine;
import net.minecraft.world.phys.Vec3;
import java.util.Random;
// Инварианты реального дефекта: рука не растягивается, близкая цель достижима, дальняя ограничена длиной.
public final class GleamBasketballHandsPoseTest {
 public static void main(String[] args){
  int checks=0;Random random=new Random(42);Vec3 shoulder=new Vec3(.52,-.66,-.08);
  for(int i=0;i<1000;i++){
   Vec3 target=new Vec3(random.nextDouble()*3-1.5,random.nextDouble()*3-1.5,-random.nextDouble()*3);
   var arm=GleamBasketballHands.fixedArm(shoulder,target);
   if(Math.abs(arm.shoulder().distanceTo(arm.wrist())-.75)>1E-8)throw new AssertionError("Skin stretched");checks++;
   if(shoulder.distanceTo(target)<=.75){if(arm.wrist().distanceTo(target)>1E-8)throw new AssertionError("Reachable wrist moved");}
   else if(shoulder.distanceTo(arm.wrist())>.75000001)throw new AssertionError("Unreachable wrist extended arm");checks++;
  }
  for(float charge:new float[]{-1,0,.5F,1,2}){
   Vec3 visual=GleamBasketballHands.heldOffset(charge),physical=OrionBasketballController.helper39(charge);
   if(visual.distanceTo(physical)>1E-9)throw new AssertionError("Held visual/physical origin mismatch");checks++;
   if(visual.z<.82 || visual.z>.96 || Math.abs(visual.x)>.09)throw new AssertionError("Held ball clips framing");checks++;
  }
  System.out.println("Basketball hand pose checks passed: "+checks);
 }
}
