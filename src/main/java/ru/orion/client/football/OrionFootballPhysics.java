package ru.orion.client.football;

import ru.orion.client.basketball.OrionBasketballBox;
import ru.orion.client.basketball.OrionBasketballVector;
import java.util.List;

/** Сферическая симуляция с фиксированным шагом, вращением и динамическими воротами. */
public final class OrionFootballPhysics {
    public static final double STEP=1.0/120.0;
    public static final double RADIUS=.22;
    private static final int MAX_STEPS=24;
    private OrionBasketballVector position=OrionBasketballVector.ZERO,previous=OrionBasketballVector.ZERO,velocity=OrionBasketballVector.ZERO,spin=OrionBasketballVector.ZERO;
    private double accumulator,roll;
    private boolean scored,keeperContact;
    public interface World {
        List<OrionBasketballBox> obstacles(OrionBasketballVector from,double radius,OrionBasketballVector delta);
        double groundFriction(OrionBasketballVector position);
    }
    public enum Contact { GROUND,WALL,POST,NET,KEEPER }
    @FunctionalInterface public interface Listener { void contact(Contact kind,OrionBasketballVector position,double speed); }
    public record Result(int goals,int saves,int steps) { }
    public void reset(OrionBasketballVector point,OrionBasketballVector initialVelocity){
        if(!finite(point)||!finite(initialVelocity))throw new IllegalArgumentException("Некорректное состояние мяча");
        position=previous=point;velocity=initialVelocity;spin=OrionBasketballVector.ZERO;accumulator=0;roll=0;scored=false;keeperContact=false;
    }
    public void kick(OrionBasketballVector impulse,OrionBasketballVector angularVelocity){
        if(!finite(impulse)||!finite(angularVelocity))throw new IllegalArgumentException("Некорректный удар");
        velocity=velocity.add(impulse);spin=angularVelocity;scored=false;keeperContact=false;
    }
    public Result advance(double elapsed,World world,List<OrionFootballGoal> goals,List<OrionGoalkeeperAI> keepers,Listener listener){
        if(!Double.isFinite(elapsed)||elapsed<0||world==null||goals==null||keepers==null||goals.size()!=keepers.size()||listener==null)throw new IllegalArgumentException("Некорректный шаг");
        accumulator=Math.min(accumulator+elapsed,STEP*MAX_STEPS);int count=0,goalCount=0,saves=0;
        while(accumulator>=STEP&&count<MAX_STEPS){
            previous=position;
            int substeps=Math.max(1,Math.min(32,(int)Math.ceil((velocity.length()+9.81*STEP)*STEP/(RADIUS*.45))));double dt=STEP/substeps;
            for(int sub=0;sub<substeps;sub++){
                var start=position;
                for(int i=0;i<goals.size();i++)keepers.get(i).update(dt,goals.get(i),position,velocity,9.81);
                var magnus=cross(spin,velocity).scale(.004);
                velocity=velocity.add(new OrionBasketballVector(magnus.x(),magnus.y()-9.81,magnus.z()).scale(dt)).scale(Math.exp(-.025*dt));
                var delta=velocity.scale(dt);position=position.add(delta);
                var obstacles=world.obstacles(start,RADIUS,delta);
                for(int iteration=0;iteration<3;iteration++){
                    for(var box:obstacles)collide(box,world,listener,null);
                    for(int i=0;i<goals.size();i++)collideGoal(goals.get(i),world,listener);
                }
                boolean touched=false;
                for(int i=0;i<goals.size();i++){
                    var goal=goals.get(i);var keeper=keepers.get(i);
                    var keeperHit=keeper.contact(goal,position,RADIUS);
                    if(keeperHit!=null){
                        touched=true;
                        position=position.add(keeperHit.normal().scale(keeperHit.depth()+.00001));
                        if(!keeperContact && velocity.length()>.2){
                            var normal=keeperHit.normal();
                            double incoming=velocity.dot(normal);
                            if(incoming<0){
                                if(velocity.length()<3.0D){listener.contact(Contact.KEEPER,position,velocity.length());velocity=OrionBasketballVector.ZERO;}
                                else reflect(normal,.48,.16,listener,Contact.KEEPER);
                                saves++;
                            }
                        }
                    }
                    if(!scored && goal.crossedLine(start,position,RADIUS)){scored=true;goalCount++;}
                }
                keeperContact=touched;
                roll+=Math.sqrt(velocity.x()*velocity.x()+velocity.z()*velocity.z())*dt/RADIUS;
                spin=spin.scale(Math.exp(-.15*dt));
            }
            accumulator-=STEP;count++;
        }
        return new Result(goalCount,saves,count);
    }
    private void collideGoal(OrionFootballGoal goal,World world,Listener listener){
        position=goal.local(position);velocity=goal.local(goal.base().add(velocity));
        double r=RADIUS/goal.scale(),half=OrionFootballGoal.WIDTH/2,post=OrionFootballGoal.POST_RADIUS;
        OrionBasketballBox[] bars={new OrionBasketballBox(-half-post,0,-post,-half+post,OrionFootballGoal.HEIGHT+post,post),
                new OrionBasketballBox(half-post,0,-post,half+post,OrionFootballGoal.HEIGHT+post,post),
                new OrionBasketballBox(-half-post,OrionFootballGoal.HEIGHT-post,-post,half+post,OrionFootballGoal.HEIGHT+post,post)};
        for(var box:bars)collideLocal(box,r,listener,goal,Contact.POST,.67);
        double net=.025,depth=OrionFootballGoal.DEPTH,height=OrionFootballGoal.HEIGHT;
        OrionBasketballBox[] netWalls={
                new OrionBasketballBox(-half,0,depth-net,half,height,depth+net),
                new OrionBasketballBox(-half-net,0,0,-half+net,height,depth),
                new OrionBasketballBox(half-net,0,0,half+net,height,depth),
                new OrionBasketballBox(-half,height-net,0,half,height+net,depth)
        };
        for(var box:netWalls)collideLocal(box,r,listener,goal,Contact.NET,.18);
        position=goal.world(position);velocity=goal.worldDirection(velocity).scale(goal.scale());
    }
    private void collideLocal(OrionBasketballBox box,double radius,Listener listener,OrionFootballGoal goal,Contact kind,double restitution){
        var hit=contact(position,box,radius);if(hit==null)return;
        position=position.add(hit.normal().scale(hit.depth()+.00001));double toward=velocity.dot(hit.normal());
        if(toward<0){double speed=-toward*goal.scale();velocity=velocity.subtract(hit.normal().scale((1+restitution)*toward));if(kind==Contact.NET)velocity=velocity.scale(.78);if(speed>.4)listener.contact(kind,goal.world(position),speed);}
    }
    private void collide(OrionBasketballBox box,World world,Listener listener,Contact forced){
        var hit=contact(position,box,RADIUS);if(hit==null)return;
        position=position.add(hit.normal().scale(hit.depth()+.00001));
        Contact kind=forced!=null?forced:hit.normal().y()>.5?Contact.GROUND:Contact.WALL;
        double friction=kind==Contact.GROUND?clamp(world.groundFriction(position),0,1):.08;
        reflect(hit.normal(),kind==Contact.GROUND?.50:.65,friction,listener,kind);
    }
    private void reflect(OrionBasketballVector normal,double bounce,double friction,Listener listener,Contact kind){
        double toward=velocity.dot(normal);if(toward>=0)return;
        var normalVelocity=normal.scale(toward);velocity=velocity.subtract(normalVelocity).scale(1-friction).subtract(normalVelocity.scale(bounce));
        if(kind==Contact.GROUND&&Math.abs(velocity.y())<.13)velocity=new OrionBasketballVector(velocity.x(),0,velocity.z());
        if(-toward>.45)listener.contact(kind,position,-toward);
    }
    private record Hit(OrionBasketballVector normal,double depth) { }
    private static Hit contact(OrionBasketballVector p,OrionBasketballBox b,double r){
        double dx=p.x()-clamp(p.x(),b.minX(),b.maxX()),dy=p.y()-clamp(p.y(),b.minY(),b.maxY()),dz=p.z()-clamp(p.z(),b.minZ(),b.maxZ());
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);if(distance>=r)return null;
        if(distance>1e-8)return new Hit(new OrionBasketballVector(dx/distance,dy/distance,dz/distance),r-distance);
        double[] depths={p.x()-b.minX(),b.maxX()-p.x(),p.y()-b.minY(),b.maxY()-p.y(),p.z()-b.minZ(),b.maxZ()-p.z()};int side=0;for(int i=1;i<6;i++)if(depths[i]<depths[side])side=i;
        var n=switch(side){case 0->new OrionBasketballVector(-1,0,0);case 1->new OrionBasketballVector(1,0,0);case 2->new OrionBasketballVector(0,-1,0);case 3->new OrionBasketballVector(0,1,0);case 4->new OrionBasketballVector(0,0,-1);default->new OrionBasketballVector(0,0,1);};return new Hit(n,r+depths[side]);
    }
    private static OrionBasketballVector cross(OrionBasketballVector a,OrionBasketballVector b){return new OrionBasketballVector(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
    private static boolean finite(OrionBasketballVector v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static double clamp(double x,double min,double max){return Math.max(min,Math.min(max,x));}
    public OrionBasketballVector position(){return position;} public OrionBasketballVector velocity(){return velocity;}
    public OrionBasketballVector interpolate(){return previous.add(position.subtract(previous).scale(clamp(accumulator/STEP,0,1)));}
    public double roll(){return roll;}
}
