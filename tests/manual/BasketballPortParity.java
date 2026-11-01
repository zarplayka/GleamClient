import ru.orion.client.basketball.engine.*;
import java.net.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;
public class BasketballPortParity {
 static final String P="com.paradox.VVRHWDIMTOCVRHWDIMTOCVRHWDIMTO.";
 static int checks;
 static void equal(double a,double b){checks++;if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a-b)>1e-10)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args)throws Exception{
  try(var loader=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},BasketballPortParity.class.getClassLoader())){
   Class<?> state=Class.forName(P+"CVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Class<?> physics=Class.forName(P+"HVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Class<?> parameters=Class.forName(P+"HVRHWDIMTOCVRHWDIMTOCVRHWDIMTO$HVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Class<?> listener=Class.forName(P+"HVRHWDIMTOCVRHWDIMTOCVRHWDIMTO$CVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Class<?> material=Class.forName(P+"OWRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Class<?> box=Class.forName(P+"WVRHWDIMTOCVRHWDIMTOCVRHWDIMTO$CVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Class<?> torus=Class.forName(P+"WVRHWDIMTOCVRHWDIMTOCVRHWDIMTO$HVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Method advance=physics.getMethod("MWTIWDOMTOCVRHWDIMTOCVRHWDIMTO",state,parameters,List.class,double.class,listener);
   Object settings=parameters.getConstructors()[0].newInstance(9.81,.0208,.008,.12,.02,1.0,1.0);
   Object noOp=listener.getField("DMTIMDOMTOCVRHWDIMTOCVRHWDIMTO").get(null);
   Object surface=material.getConstructor(double.class,double.class,int.class).newInstance(.72,.2,0);
   var surfaceNew=new OrionBallMaterial(.72,.2,0);
   List<Object> oldColliders=List.of(box.getConstructors()[0].newInstance(-50.,-1.,-50.,50.,0.,50.,surface),box.getConstructors()[0].newInstance(5.,0.,-5.,5.2,10.,5.,surface),torus.getConstructors()[0].newInstance(0.,3.05,0.,.48,.02,surface));
   List<OrionBallCollider> newColliders=List.of(new OrionBallCollider.Box(-50,-1,-50,50,0,50,surfaceNew),new OrionBallCollider.Box(5,0,-5,5.2,10,5,surfaceNew),new OrionBallCollider.Torus(0,3.05,0,.48,.02,surfaceNew));
   Random random=new Random(918);
   String[] fields={"x","y","z","vx","vy","vz","wx","wy","wz","radius","mass","still"};
   for(int trial=0;trial<100;trial++){
    Object old=state.getConstructor().newInstance();var current=new OrionBallState();
    double[] values={random.nextDouble()*4,1+random.nextDouble()*8,random.nextDouble()*2-1,random.nextDouble()*50-25,random.nextDouble()*16-8,random.nextDouble()*8-4,random.nextDouble()*20,random.nextDouble()*20,random.nextDouble()*20,.2541,.624,0};
    for(int i=0;i<fields.length;i++){state.getField(fields[i]).setDouble(old,values[i]);OrionBallState.class.getField(fields[i]).setDouble(current,values[i]);}
    for(int step=0;step<60;step++){
     advance.invoke(null,old,settings,oldColliders,.05,noOp);
     OrionBallPhysics.advance(current,OrionBallPhysics.Parameters.TMTIMDOMTOCVRHWDIMTOCVRHWDIMTO,newColliders,.05,OrionBallPhysics.Listener.DMTIMDOMTOCVRHWDIMTOCVRHWDIMTO);
     for(String field:fields)equal(state.getField(field).getDouble(old),OrionBallState.class.getField(field).getDouble(current));
     org.joml.Quaternionf original=(org.joml.Quaternionf)state.getField("turn").get(old);
     equal(original.x,current.turn.x);equal(original.y,current.turn.y);equal(original.z,current.turn.z);equal(original.w,current.turn.w);
    }
   }

   Class<?> hoopType=Class.forName(P+"HWRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Object oldHoop=hoopType.getConstructor().newInstance();var newHoop=new OrionHoop();
   String[] geometryFields={"x","y","z","base","scale"};double[] geometry={2,3.05,4,0,2.1};
   for(int i=0;i<geometry.length;i++){hoopType.getField(geometryFields[i]).setDouble(oldHoop,geometry[i]);OrionHoop.class.getField(geometryFields[i]).setDouble(newHoop,geometry[i]);}
   Method crossing=hoopType.getMethod("WCRHWDOMTOCVRHWDIMTOCVRHWDIMTO",double.class,double.class,double.class,double.class,double.class,double.class);
   for(int i=0;i<2000;i++){
    double x=2+random.nextDouble()*2-1,z=4+random.nextDouble()*2-1,y=3+random.nextDouble();
    boolean original=(Boolean)crossing.invoke(oldHoop,x,y,z,x+.1,y-.5,z+.1);
    boolean ported=newHoop.crossesRim(x,y,z,x+.1,y-.5,z+.1);
    if(original!=ported)throw new AssertionError("rim crossing");checks++;
   }

   Class<?> oldController=Class.forName(P+"IVRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Method oldAnchor=oldController.getMethod("MCTIWDOMTOCVRHWDIMTOCVRHWDIMTO",float.class);
   for(int i=0;i<=100;i++){
    float power=i/100F;
    net.minecraft.world.phys.Vec3 oldPosition=(net.minecraft.world.phys.Vec3)oldAnchor.invoke(null,power);
    var newPosition=OrionBasketballController.helper39(power);
    equal(oldPosition.x,newPosition.x);equal(oldPosition.y,newPosition.y);equal(oldPosition.z,newPosition.z);
   }
   Class<?> netType=Class.forName(P+"DWRHWDIMTOCVRHWDIMTOCVRHWDIMTO",true,loader);
   Constructor<?> oldConstructor=netType.getDeclaredConstructor(hoopType);oldConstructor.setAccessible(true);Object oldNet=oldConstructor.newInstance(oldHoop);
   Class<?> newNetType=Class.forName("ru.orion.client.basketball.engine.OrionBasketballNet");
   Constructor<?> newConstructor=newNetType.getDeclaredConstructor(OrionHoop.class);newConstructor.setAccessible(true);Object newNet=newConstructor.newInstance(newHoop);
   Method oldUpdate=netType.getDeclaredMethod("IHRHWDOMTOCVRHWDIMTOCVRHWDIMTO",double.class,state,boolean.class,double.class);oldUpdate.setAccessible(true);
   Method newUpdate=newNetType.getDeclaredMethod("IHRHWDOMTOCVRHWDIMTOCVRHWDIMTO",double.class,OrionBallState.class,boolean.class,double.class);newUpdate.setAccessible(true);
   Object oldBall=state.getConstructor().newInstance();var newBall=new OrionBallState();newBall.radius=.2541;
   for(int frame=0;frame<120;frame++){
    newBall.x=2;newBall.y=3.3-frame*.018;newBall.z=4;
    for(String field:new String[]{"x","y","z","radius"})state.getField(field).setDouble(oldBall,OrionBallState.class.getField(field).getDouble(newBall));
    oldUpdate.invoke(oldNet,.016,oldBall,true,9.81);newUpdate.invoke(newNet,.016,newBall,true,9.81);
    for(String field:new String[]{"x","y","z"}){
     Field of=netType.getDeclaredField(field);of.setAccessible(true);Field nf=newNetType.getDeclaredField(field);nf.setAccessible(true);
     double[] oa=(double[])of.get(oldNet),na=(double[])nf.get(newNet);
     for(int node=0;node<oa.length;node++)equal(oa[node],na[node]);
    }
   }
   var legacy=new ru.orion.client.basketball.OrionBasketballHoop(new ru.orion.client.basketball.OrionBasketballVector(10,64,20),Math.PI/2);
   var converted=ru.orion.client.basketball.OrionBasketballMigration.convert(Map.of("server:Example.org|minecraft:overworld",List.of(legacy)));
   if(converted.size()!=1||!converted.get(0).court.equals("server:example.org|minecraft:overworld"))throw new AssertionError("legacy world binding");checks++;
   var center=legacy.toWorld(new ru.orion.client.basketball.OrionBasketballVector(0,ru.orion.client.basketball.OrionBasketballHoop.RIM_HEIGHT,ru.orion.client.basketball.OrionBasketballHoop.RIM_FORWARD));
   equal(converted.get(0).x,center.x());equal(converted.get(0).y,center.y());equal(converted.get(0).z,center.z());equal(converted.get(0).base,64);
  }
  System.out.println("Original Paradox vs Orion engine: "+checks+" numeric comparisons passed");
 }
}
