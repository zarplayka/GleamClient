package ru.orion.client.football;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;
import ru.orion.client.basketball.OrionBasketballVector;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Весь матч, мяч и вратари существуют только в этом клиенте. */
public final class OrionFootball {
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"football"));
    private final OrionConfigManager manager;
    private final OrionFootballPhysics ball=new OrionFootballPhysics();
    private final OrionFootballStorage storage=new OrionFootballStorage();
    private final List<OrionFootballGoal> goals=new ArrayList<>();
    private final List<OrionGoalkeeperAI> keepers=new ArrayList<>();
    private Map<String,List<OrionFootballGoal>> stored=Map.of();
    private boolean loaded,ballExists,placing,charging,spinCharge,suppressRelease,shotActive,shotSaved;
    private ClientLevel level;private String worldKey;
    private int movingIndex=-1,shots,score,saves,misses;
    private long chargeAt,goalAt,netAt,lastSound,shotAt,kickAt,lastDribble;
    private float yawOffset,oldRoll,newRoll;private OrionFootballGoal preview;
    private OrionBasketballVector previous=OrionBasketballVector.ZERO,current=OrionBasketballVector.ZERO;
    private KeyMapping ballKey,goalKey,moveKey,removeKey,keeperKey,rotateLeft,rotateRight,penaltyKey;
    public OrionFootball(OrionConfigManager manager){this.manager=manager;}
    public void register(){
        OrionFootballRenderer.registerResources();
        ballKey=key("ball",InputConstants.KEY_R);goalKey=key("goal",InputConstants.KEY_G);keeperKey=key("keeper",InputConstants.KEY_H);
        moveKey=key("move",InputConstants.UNKNOWN.getValue());removeKey=key("remove",InputConstants.UNKNOWN.getValue());
        rotateLeft=key("rotate_left",InputConstants.UNKNOWN.getValue());rotateRight=key("rotate_right",InputConstants.UNKNOWN.getValue());penaltyKey=key("penalty",InputConstants.UNKNOWN.getValue());
        storage.load(map->{stored=map;loaded=true;worldKey=null;});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }
    private static KeyMapping key(String id,int defaultKey){return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orion-client.football."+id,InputConstants.Type.KEYBOARD,defaultKey,CATEGORY));}
    public KeyMapping getBallKey() { return ballKey; }
    public KeyMapping getGoalKey() { return goalKey; }
    public KeyMapping getMoveKey() { return moveKey; }
    public KeyMapping getRemoveKey() { return removeKey; }
    public KeyMapping getKeeperKey() { return keeperKey; }
    public KeyMapping getRotateLeftKey() { return rotateLeft; }
    public KeyMapping getRotateRightKey() { return rotateRight; }
    public KeyMapping getPenaltyKey() { return penaltyKey; }
    private void tick(Minecraft client){
        var config=manager.getConfig();
        if(!config.isFootballEnabled()||client.player==null||client.level==null){clear();drain();return;}
        String key=worldKey(client);
        if(client.level!=level||!key.equals(worldKey)){
            clear();level=client.level;worldKey=key;
            if(loaded){List<OrionFootballGoal> saved=stored.get(key);if(saved!=null)for(var goal:saved)if(goal!=null&&goals.size()<32)addGoal(goal);}
        }
        if(client.gui.screen()!=null){charging=false;drain();return;}
        while(ballKey.consumeClick())resetBall(client);
        while(goalKey.consumeClick()){
            if(!loaded){message(client,"Дождитесь загрузки ворот");continue;}
            if(!placing){
                placing=true;movingIndex=-1;yawOffset=0;
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("[Футбол] Наведи на землю и нажми ")
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(goalKey.getTranslatedKeyMessage().getString()).withStyle(ChatFormatting.GOLD))
                        .append(" ещё раз"));
            }
            else if(preview!=null){if(movingIndex>=0){goals.set(movingIndex,preview);keepers.get(movingIndex).reset();}else if(goals.size()<32)addGoal(preview);persist();placing=false;movingIndex=-1;}
        }
        while(moveKey.consumeClick()){int index=nearest(client);if(!placing&&index>=0){placing=true;movingIndex=index;yawOffset=0;}}
        while(removeKey.consumeClick()){if(placing)continue;int index=nearest(client);if(index>=0){goals.remove(index);keepers.remove(index);persist();}}
        while(keeperKey.consumeClick())manager.setFootballGoalkeeperEnabled(!config.isFootballGoalkeeperEnabled());
        while(rotateLeft.consumeClick())yawOffset-=Math.PI/12F;
        while(rotateRight.consumeClick())yawOffset+=Math.PI/12F;
        while(penaltyKey.consumeClick()){manager.setFootballPenaltyMode(!config.isFootballPenaltyMode());if(config.isFootballPenaltyMode())setupPenalty(client);}
        if(placing)updatePreview(client);
        for(var keeper:keepers){keeper.setEnabled(config.isFootballGoalkeeperEnabled());keeper.setDifficulty(OrionGoalkeeperAI.Difficulty.values()[config.getFootballGoalkeeperDifficulty()]);}
        if(ballExists){
            previous=ball.position();oldRoll=newRoll;
            var result=ball.advance(.05,new OrionFootballWorld(client.level),goals,keepers,(kind,p,speed)->sound(client,kind,p,speed));
            current=ball.position();newRoll=(float)ball.roll();
            if(result.goals()>0){score+=result.goals();goalAt=System.nanoTime();shotActive=false;client.level.playLocalSound(current.x(),current.y(),current.z(),SoundEvents.NOTE_BLOCK_PLING.value(),SoundSource.PLAYERS,.7F,1.4F,false);}
            if(result.saves()>0 && shotActive && !shotSaved){saves++;shotSaved=true;shotActive=false;}
            if(shotActive && System.nanoTime()-shotAt>4_000_000_000L){misses++;shotActive=false;}
            if(!charging)dribble(client);
        }
    }
    private void dribble(Minecraft client){
        long now=System.nanoTime();if(now-lastDribble<250_000_000L)return;
        Vec3 point=client.player.position();OrionBasketballVector diff=current.subtract(new OrionBasketballVector(point.x,point.y,point.z));
        Vec3 velocity=client.player.getDeltaMovement();if(diff.lengthSquared()>1.2||velocity.horizontalDistance()<.015||Math.abs(diff.y())>.9)return;
        Vec3 motion=new Vec3(velocity.x,0,velocity.z).normalize();
        if(diff.x()*motion.x+diff.z()*motion.z<-.10)return;
        double power=Math.min(2.5,.6+velocity.horizontalDistance()*12);
        ball.kick(new OrionBasketballVector(motion.x*power,.08,motion.z*power),new OrionBasketballVector(-motion.z*8,0,motion.x*8));lastDribble=now;kickAt=now;
    }
    private void resetBall(Minecraft client){
        if(manager.getConfig().isFootballPenaltyMode()&&!goals.isEmpty()){setupPenalty(client);return;}
        Vec3 p=client.player.position(),view=client.player.getViewVector(1);Vec3 horizontal=new Vec3(view.x,0,view.z).normalize();
        Vec3 point=p.add(horizontal.scale(1.0)).add(0,.25,0);
        ball.reset(new OrionBasketballVector(point.x,point.y,point.z),OrionBasketballVector.ZERO);current=previous=ball.position();ballExists=true;shotActive=false;
    }
    private void setupPenalty(Minecraft client){int index=nearest(client);if(index<0){message(client,"Сначала установите ворота");return;}var goal=goals.get(index);
        var pos=goal.world(new OrionBasketballVector(0,(OrionFootballPhysics.RADIUS+.02)/goal.scale(),-Math.min(11,7/goal.scale())));ball.reset(pos,OrionBasketballVector.ZERO);current=previous=pos;ballExists=true;keepers.get(index).reset();shotActive=false;message(client,"Пенальти: зарядите удар рядом с мячом.");}
    private void updatePreview(Minecraft client){Vec3 eye=client.player.getEyePosition(),look=client.player.getViewVector(1);
        var hit=client.level.clip(new ClipContext(eye,eye.add(look.scale(14)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        Vec3 candidate=hit.getType()==HitResult.Type.MISS?eye.add(look.scale(5)):hit.getLocation();
        var floor=client.level.clip(new ClipContext(candidate.add(0,2,0),candidate.add(0,-12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        if(floor.getType()==HitResult.Type.MISS){preview=null;return;}
        Vec3 p=floor.getLocation();double yaw=Math.atan2(look.x,-look.z)+yawOffset;
        preview=new OrionFootballGoal(new OrionBasketballVector(p.x,p.y,p.z),yaw,manager.getConfig().getFootballGoalScale());
    }
    private void addGoal(OrionFootballGoal goal){goals.add(goal);keepers.add(new OrionGoalkeeperAI(Double.doubleToLongBits(goal.base().x()+goal.base().z())));}
    private int nearest(Minecraft client){int idx=-1;double best=400;Vec3 p=client.player.position();for(int i=0;i<goals.size();i++){var v=goals.get(i).base();double d=p.distanceToSqr(v.x(),v.y(),v.z());if(d<best){best=d;idx=i;}}return idx;}
    private void persist(){if(!loaded||worldKey==null)return;var copy=List.copyOf(goals);stored.put(worldKey,copy);storage.save(worldKey,copy);}
    private static String worldKey(Minecraft c){String server=c.getCurrentServer()!=null?"server:"+c.getCurrentServer().ip.toLowerCase(Locale.ROOT):c.hasSingleplayerServer()?"single:"+c.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize():"local:unknown";return server+"|"+c.level.dimension().identifier();}
    private void render(LevelRenderContext context){Minecraft c=Minecraft.getInstance();if(!manager.getConfig().isFootballEnabled()||c.player==null||c.level!=level)return;
        float part=c.getDeltaTracker().getGameTimeDeltaPartialTick(true);float time=(float)(System.nanoTime()/1e9%1000);
        if(ballExists){var pos=previous.add(current.subtract(previous).scale(part));OrionFootballRenderer.ball(context,pos,oldRoll+(newRoll-oldRoll)*part);}
        long impact=Math.max(goalAt,netAt);
        float net=impact==0?0:(float)(Math.sin((System.nanoTime()-impact)/1e8)*.10*Math.exp(-(System.nanoTime()-impact)/4e8));
        for(int i=0;i<goals.size();i++)OrionFootballRenderer.goal(context,goals.get(i),keepers.get(i),false,net,time,ballExists?current:null);
        if(placing&&preview!=null)OrionFootballRenderer.goal(context,preview,null,true,0,time,null);
    }
    public boolean button(int button,boolean press,boolean shift){Minecraft c=Minecraft.getInstance();
        if(button==InputConstants.MOUSE_BUTTON_RIGHT&&!press&&suppressRelease){suppressRelease=false;if(!charging)return true;}
        if(!manager.getConfig().isFootballEnabled()||!ballExists||c.player==null||c.level==null||c.gui.screen()!=null)return false;
        if(OrionClient.getBasketballOrNull()!=null && OrionClient.getBasketballOrNull().holdingBall())return false;
        var p=c.player.position();
        if(current.subtract(new OrionBasketballVector(p.x,p.y,p.z)).lengthSquared()>4 && !(button==InputConstants.MOUSE_BUTTON_RIGHT && !press && charging))return false;
        if(button==InputConstants.MOUSE_BUTTON_LEFT){if(press)kick(c,.23F,false);return press;}
        if(button!=InputConstants.MOUSE_BUTTON_RIGHT)return false;
        if(press){if(!charging){charging=true;chargeAt=System.nanoTime();spinCharge=shift;suppressRelease=true;}}
        else if(charging){float power=charge();charging=false;kick(c,power,spinCharge);}
        return true;
    }
    private void kick(Minecraft client,float power,boolean curve){
        Vec3 look=client.player.getViewVector(1),motion=client.player.getDeltaMovement().scale(20);
        double strength=(3+power*15)*manager.getConfig().getFootballKickPower();
        var impulse=new OrionBasketballVector(look.x*strength+motion.x*.5,look.y*strength+motion.y*.25+.4,look.z*strength+motion.z*.5);
        ball.kick(impulse,new OrionBasketballVector(0,curve?25:0,0));kickAt=shotAt=System.nanoTime();shots++;shotActive=true;shotSaved=false;
        client.level.playLocalSound(current.x(),current.y(),current.z(),SoundEvents.WOOL_HIT,SoundSource.PLAYERS,.8F,.7F,false);
    }
    public float charge(){return charging?Math.max(0,Math.min(1,(System.nanoTime()-chargeAt)/1e9F*manager.getConfig().getFootballChargeSpeed())):0;}
    public boolean charging(){return charging;}public int goals(){return score;}public int shots(){return shots;}public int saves(){return saves;}public int misses(){return misses;}public long goalAt(){return goalAt;}public long kickAt(){return kickAt;}
    private void sound(Minecraft c,OrionFootballPhysics.Contact kind,OrionBasketballVector p,double speed){long now=System.nanoTime();if(kind==OrionFootballPhysics.Contact.NET)netAt=now;if(now-lastSound<100_000_000)return;lastSound=now;
        var sound=kind==OrionFootballPhysics.Contact.POST?SoundEvents.METAL_HIT:SoundEvents.WOOL_HIT;
        c.level.playLocalSound(p.x(),p.y(),p.z(),sound,SoundSource.PLAYERS,(float)Math.min(1,speed/8),.8F,false);}
    private static void message(Minecraft c,String text){c.gui.hud.getChat().addClientSystemMessage(Component.literal("[Футбол] "+text));}
    private void clear(){level=null;worldKey=null;goals.clear();keepers.clear();ballExists=false;placing=false;charging=false;shotActive=false;preview=null;score=shots=saves=misses=0;goalAt=netAt=0;}
    private void drain(){if(ballKey==null)return;for(var key:new KeyMapping[]{ballKey,goalKey,moveKey,removeKey,keeperKey,rotateLeft,rotateRight,penaltyKey})while(key.consumeClick()){} }
}
