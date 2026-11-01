package ru.orion.client.football;

import ru.orion.client.basketball.OrionBasketballVector;
import java.util.Random;

/** Локальная модель вратаря: решение не заменяет проверку столкновения. */
public final class OrionGoalkeeperAI {
    public enum Difficulty { EASY, NORMAL, HARD }
    public enum Action { IDLE, RUN, DIVE_LEFT, DIVE_RIGHT, JUMP, LOW_SAVE, RECOVER }
    private final Random random;
    private Difficulty difficulty=Difficulty.NORMAL;
    private Action action=Action.IDLE;
    private double x, vertical, lean, targetX, targetHeight, decisionDelay, actionTime, decisionBias;
    private boolean trackingShot;
    private boolean enabled=true;
    public OrionGoalkeeperAI(long seed){random=new Random(seed);}
    public void setDifficulty(Difficulty difficulty){this.difficulty=java.util.Objects.requireNonNull(difficulty);}
    public void setEnabled(boolean enabled){this.enabled=enabled;if(!enabled)reset();}
    public void reset(){x=vertical=lean=targetX=targetHeight=decisionDelay=actionTime=decisionBias=0;trackingShot=false;action=Action.IDLE;}
    public void update(double dt,OrionFootballGoal goal,OrionBasketballVector ball,OrionBasketballVector velocity,double gravity){
        if(!enabled||dt<=0)return;
        var p=goal.local(ball);var v=goal.local(ball.add(velocity)).subtract(p);
        actionTime+=dt;
        double react=switch(difficulty){case EASY->.32;case NORMAL->.19;case HARD->.10;};
        double speed=switch(difficulty){case EASY->2.2;case NORMAL->3.8;case HARD->5.2;};
        if(v.z()>.25 && p.z()<0){
            if(!trackingShot){
                trackingShot=true;decisionDelay=0;
                double error=switch(difficulty){case EASY->.42;case NORMAL->.14;case HARD->.035;};
                decisionBias=random.nextGaussian()*error;
            }
            decisionDelay+=dt;
            if(decisionDelay>=react){
                // Вратарь встречает мяч перед линией ворот, а не после неё.
                double time=(-.20-p.z())/v.z();
                if(time>0&&time<2.5){
                    targetX=clamp(p.x()+v.x()*time+decisionBias,-OrionFootballGoal.WIDTH/2+.35,OrionFootballGoal.WIDTH/2-.35);
                    targetHeight=clamp(p.y()+v.y()*time-.5*(gravity/goal.scale())*time*time,0,OrionFootballGoal.HEIGHT);
                    boolean committed=action==Action.DIVE_LEFT||action==Action.DIVE_RIGHT||action==Action.JUMP||action==Action.LOW_SAVE;
                    if(!committed && time<.8 && Math.abs(targetX-x)>speed*time*.8+.35 && actionTime>.18){
                        action=targetX<x?Action.DIVE_LEFT:Action.DIVE_RIGHT;actionTime=0;
                    } else if(!committed && time<.65 && targetHeight>1.7 && actionTime>.18){action=Action.JUMP;actionTime=0;}
                    else if(!committed && time<.45 && targetHeight<.45 && actionTime>.18){action=Action.LOW_SAVE;actionTime=0;}
                }
            }
        } else {trackingShot=false;decisionDelay=0;targetX=clamp(p.x()*.25,-1.1,1.1);targetHeight=0;}
        boolean dive=action==Action.DIVE_LEFT||action==Action.DIVE_RIGHT;
        double step=speed*(dive?1.5:1)*dt;x+=clamp(targetX-x,-step,step);
        if(dive || action==Action.JUMP || action==Action.LOW_SAVE){
            double t=clamp(actionTime/.65,0,1);double envelope=Math.sin(Math.PI*t);
            vertical=(action==Action.JUMP? .65 : dive?.20:0)*envelope;
            lean=(action==Action.DIVE_LEFT?1:action==Action.DIVE_RIGHT?-1:0)*.95*envelope;
            if(t>=1){action=Action.RECOVER;actionTime=0;}
        }else{vertical*=Math.exp(-dt*9);lean*=Math.exp(-dt*8);
            if(action==Action.RECOVER && actionTime>.25){action=Action.IDLE;actionTime=0;}
            if(action!=Action.RECOVER)action=Math.abs(targetX-x)>.08?Action.RUN:Action.IDLE;
        }
    }
    public record Contact(OrionBasketballVector normal,double depth) { }
    /** Реальное пересечение сферы с объёмами позы вратаря. */
    public Contact contact(OrionFootballGoal goal,OrionBasketballVector ball,double radius){
        if(!enabled)return null;
        var point=goal.local(ball);double r=radius/goal.scale();
        double dx=point.x()-x,dy=point.y()-vertical,c=Math.cos(lean),s=Math.sin(lean);
        var p=new OrionBasketballVector(dx*c+dy*s,-dx*s+dy*c,point.z());
        boolean high=action==Action.JUMP || targetHeight>1.65;
        double handBottom=high?1.25:.72, handTop=high?2.15:1.40;
        Contact[] hits={sphereBox(p,r,-.25,.72,-.13,.25,1.43,.13),
                sphereBox(p,r,-.24,1.43,-.20,.24,1.91,.20),
                sphereBox(p,r,-.48,handBottom,-.18,-.23,handTop,.18),
                sphereBox(p,r,.23,handBottom,-.18,.48,handTop,.18),
                sphereBox(p,r,-.23,.05,-.14,.23,.72,.14)};
        Contact closest=null;
        for(Contact hit:hits)if(hit!=null && (closest==null || hit.depth()>closest.depth()))closest=hit;
        if(closest==null)return null;
        var n=closest.normal();
        var local=new OrionBasketballVector(n.x()*c-n.y()*s,n.x()*s+n.y()*c,n.z());
        return new Contact(goal.worldDirection(local),closest.depth()*goal.scale());
    }
    public boolean touches(OrionFootballGoal goal,OrionBasketballVector ball,double radius){return contact(goal,ball,radius)!=null;}
    private static Contact sphereBox(OrionBasketballVector p,double r,double x,double y,double z,double X,double Y,double Z){
        double dx=p.x()-clamp(p.x(),x,X),dy=p.y()-clamp(p.y(),y,Y),dz=p.z()-clamp(p.z(),z,Z);
        double dist=Math.sqrt(dx*dx+dy*dy+dz*dz);if(dist>=r)return null;
        if(dist>1e-8)return new Contact(new OrionBasketballVector(dx/dist,dy/dist,dz/dist),r-dist);
        double[] d={p.x()-x,X-p.x(),p.y()-y,Y-p.y(),p.z()-z,Z-p.z()};int side=0;for(int i=1;i<6;i++)if(d[i]<d[side])side=i;
        var normal=switch(side){case 0->new OrionBasketballVector(-1,0,0);case 1->new OrionBasketballVector(1,0,0);case 2->new OrionBasketballVector(0,-1,0);case 3->new OrionBasketballVector(0,1,0);case 4->new OrionBasketballVector(0,0,-1);default->new OrionBasketballVector(0,0,1);};
        return new Contact(normal,r+d[side]);
    }
    private static double clamp(double x,double min,double max){return Math.max(min,Math.min(max,x));}
    public double x(){return x;} public double vertical(){return vertical;} public double lean(){return lean;}
    public Action action(){return action;} public boolean enabled(){return enabled;} public double targetHeight(){return targetHeight;}
}
