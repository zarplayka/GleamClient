package ru.orion.client.basketball.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.effect.OrionBadgePresence.FootballConnection;
import ru.orion.client.gui.OrionGuiLanguage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Общий мяч на штатном движке Orion. Сервис ведёт владение и счёт, создатель считает физику. */
public final class OrionBasketballMatch {
    private final OrionBallState ball = new OrionBallState();
    private final OrionBallState simulation = new OrionBallState();
    private final OrionWorldColliders colliders = new OrionWorldColliders();
    private final ArrayDeque<JsonObject> commands = new ArrayDeque<>();
    private final ArrayDeque<JsonObject> collisions = new ArrayDeque<>();
    private FootballConnection connection;
    private CompletableFuture<JsonObject> pending;
    private ClientLevel level;
    private String scope, code = "", key = "", phase = "", status = "", owner = "", shooter = "";
    private Vec3 origin = Vec3.ZERO, previous = Vec3.ZERO, target = Vec3.ZERO;
    private List<OrionHoop> hoops = List.of();
    private boolean ready, attackDown, useDown, charging, host, liveShot, dribble, pendingOut;
    private Boolean readyWanted;
    private int slot, size, count, round, red, blue, commandId;
    private int ballRevision = -1, pendingGoal = -1;
    private Vec3 crossing;
    private long lastPickup;
    private int collisionId, lastSoundId;
    private int walkTicks;
    private long nextPoll, lastPacket, changedAt, chargeAt, scoreAt;
    private Vec3 spawn = Vec3.ZERO;

    public boolean engaged() { return level != null; }
    public boolean active() { return !code.isEmpty(); }
    public boolean holding() { return active() && !owner.isEmpty() && owner.equals(localId()); }
    public boolean charging() { return charging; }
    public boolean dribbling() { return holding() && dribble && !charging; }
    public float handBounce() { return dribbling() ? (float)(.5+.45*Math.sin(System.nanoTime()/1e9*8)) : 0; }
    public float charge() { return charging ? Math.clamp((System.nanoTime()-chargeAt)/1_000_000_000F,0,1) : 0; }
    public OrionBallState ball() { return ball; }
    public List<OrionHoop> hoops() { return hoops; }
    public int red() { return red; }
    public int blue() { return blue; }
    public long scoreAt() { return scoreAt; }
    public String code() { return code; }
    public String info() { return !active() ? "—" : code+" · "+count+"/"+(size*2)+" · "+OrionGuiLanguage.translate(slot%2==0?"Красные":"Синие"); }
    public String status() {
        if (!status.isEmpty()) return status;
        if (!active()) return engaged() ? "Подключаемся к матчу…" : "Комнаты нет";
        return switch (phase) {
            case "lobby" -> ready ? "Ждём остальных" : "Нажми «Готов»";
            case "kickoff" -> "Займи стартовую точку";
            case "finished" -> ready ? "Готов к новой игре · ждём остальных"
                    : (red >= 11 ? "Красные победили" : "Синие победили") + " · " + "Нажми «Готов» для новой игры";
            default -> holding() ? "ПКМ — бросок · ЛКМ — пас" : "Подбери свободный мяч";
        };
    }

    public boolean create(Minecraft client,int teamSize) {
        if (!canOpen(client)) return false;
        var center=BlockPos.containing(client.player.position());
        if (!flat(client.level,center)) { status="Нужна ровная площадка 21 × 35"; return false; }
        var request=new JsonObject();
        request.addProperty("action","create");
        request.addProperty("size",teamSize);
        request.add("origin",array(new Vec3(center.getX()+.5,center.getY(),center.getZ()+.5)));
        open(client,request);
        return true;
    }
    public boolean join(Minecraft client,String invite) {
        if (!canOpen(client)) return false;
        invite=invite.strip().toUpperCase(Locale.ROOT);
        if (!invite.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}")) { status="Введи код из 6 символов"; return false; }
        var request=new JsonObject();
        request.addProperty("action","join");
        request.addProperty("code",invite);
        request.add("position",array(client.player.position()));
        open(client,request);
        return true;
    }
    private boolean canOpen(Minecraft client) {
        if (engaged()) return false;
        if(client.player==null||client.level==null||client.getConnection()==null||client.isLocalServer()) {
            status="Зайди на Minecraft-сервер"; return false;
        }
        return true;
    }
    private void open(Minecraft client,JsonObject request) {
        level=client.level;
        String endpoint=client.getCurrentServer()==null ? client.getConnection().getConnection().getRemoteAddress().toString()
                : client.getCurrentServer().ip;
        try {
            scope=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (endpoint.strip().toLowerCase(Locale.ROOT)+"|"+level.dimension().identifier()).getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        connection=new FootballConnection(client.player.getUUID(),client.getUser().getName(),true);
        request.addProperty("scope",scope);
        status="";
        submit(client,request,null);
    }
    private static boolean flat(ClientLevel world,BlockPos center) {
        var pos=new BlockPos.MutableBlockPos();
        for(int x=-10;x<=10;x++) for(int z=-17;z<=17;z++) {
            pos.set(center.getX()+x,center.getY()-1,center.getZ()+z);
            if(!world.hasChunkAt(pos)||!world.getBlockState(pos).isCollisionShapeFullBlock(world,pos)) return false;
            for(int y=0;y<8;y++) {
                pos.setY(center.getY()+y);
                var state=world.getBlockState(pos);
                if(!state.getFluidState().isEmpty()||!state.getCollisionShape(world,pos).isEmpty())return false;
            }
        }
        return true;
    }
    public void ready() { if(active()&&(phase.equals("lobby")||phase.equals("finished"))) { readyWanted=!(readyWanted==null?ready:readyWanted); nextPoll=0; } }
    public void leave() { close("Ты вышел из комнаты",true); }
    private JsonObject request(String action) {
        var request=new JsonObject();
        request.addProperty("action",action);request.addProperty("scope",scope);
        request.addProperty("code",code);request.addProperty("key",key);
        return request;
    }
    private void close(String reason,boolean notify) {
        if(notify&&active()&&connection!=null) {
            var request=request("leave");var transport=connection;
            if(pending==null)transport.request(request);
            else pending.handle((result,error)->null).thenCompose(ignored->transport.request(request));
        }
        pending=null; connection=null; level=null; code=key=phase=owner="";
        hoops=List.of(); commands.clear(); readyWanted=null;
        charging=attackDown=useDown=false;commandId=0;nextPoll=0;red=blue=0;scoreAt=0;status=reason;
        pendingGoal=-1;pendingOut=false;ballRevision=-1;dribble=false;shooter="";
        collisions.clear();collisionId=lastSoundId=0;lastPickup=0;
    }
    public void pickup() {
        var client = Minecraft.getInstance();
        if (!active() || !phase.equals("play") || client.player == null || client.gui.screen() != null) return;
        if (holding()) {
            command(dribble ? "pickup" : "dribble", 0);
        } else if (owner.isEmpty() && canReachBall(client)) {
            long now = System.nanoTime();
            if (now - lastPickup >= 250_000_000L) {
                lastPickup = now;
                command("pickup", 0);
            }
        }
    }

    private boolean canReachBall(Minecraft client) {
        if (liveShot && target.y >= 3.05 - .121 * 2.1
                && Math.hypot(target.x, Math.abs(target.z) - 12) <= 1.5) return false;
        Vec3 offset = origin.add(target).subtract(client.player.getEyePosition());
        double distance = offset.length();
        return distance <= 2.25 && (distance <= .121 * 2.1 || offset.dot(client.player.getViewVector(1)) >= .5 * distance);
    }

    private void command(String kind,float power) {
        if(commands.size()>=4)return;
        if ((kind.equals("pickup") || kind.equals("dribble"))
                && commands.stream().anyMatch(queued -> kind.equals(queued.get("kind").getAsString()))) return;
        var command=new JsonObject();command.addProperty("id",++commandId);command.addProperty("kind",kind);
        command.addProperty("power",power);
        if (kind.equals("pickup")) command.addProperty("revision",ballRevision);
        if (kind.equals("shoot") || kind.equals("pass")) {
            var client = Minecraft.getInstance();
            var player = client.player;
            var rules = OrionBasketballSettings.get();
            Vec3 look = player.getViewVector(1).normalize();
            Vec3 up = OrionBasketballController.lookDirection(player.getXRot()-90, player.getYRot());
            Vec3 side = look.cross(up);
            Vec3 position = OrionBasketballController.helper38(player.getEyePosition(), look, up, side,
                    kind.equals("pass") ? .15F : power);
            Vec3 velocity = kind.equals("pass")
                    ? OrionBasketballController.helper51(client, position, look, rules.basketPass).scale(rules.basketPass)
                    : OrionBasketballController.helper47(player, position, look, power, rules);
            double spin = kind.equals("pass") ? 6 : OrionBasketballController.helper49(power, rules);
            command.add("position", array(position.subtract(origin)));
            command.add("velocity", array(velocity));
            command.add("spin", array(side.scale(spin)));
        }
        commands.addLast(command);nextPoll=0;
    }
    public void tick(Minecraft client,boolean enabled) {
        if(!engaged())return;
        if(!enabled||client.player==null||client.level!=level) { close("Матч завершён",true);return; }
        if(!active())return;
        long now=System.nanoTime();
        if(now-lastPacket>12_000_000_000L) { close("Связь с матчем потеряна",false);return; }
        var feet=client.player.position().subtract(origin);
        if(feet.lengthSqr()>10000) { close("Ты покинул площадку",true);return; }
        OrionBasketballInput.consumeHoop();
        if(OrionBasketballInput.consumeTake()&&client.gui.screen()==null)pickup();
        boolean use=client.gui.screen()==null&&client.options.keyUse.isDown();
        boolean attack=client.gui.screen()==null&&client.options.keyAttack.isDown();
        if(holding()&&phase.equals("play")&&client.gui.screen()==null) {
            if(use&&!useDown) {
                if(dribble)command("pickup",0);
                charging=true;chargeAt=now;
            }
            if(!use&&useDown&&charging) { command("shoot",charge());charging=false; }
            if(attack&&!attackDown) { command("pass",0);charging=false; }
        } else charging=false;
        useDown=use;attackDown=attack;
        if (holding()&&!dribble&&!charging&&OrionBasketballSettings.get().basketWalk
                &&client.player.getDeltaMovement().horizontalDistance()>.085&&client.player.onGround()) {
            if(++walkTicks>8) { command("dribble",0);walkTicks=0; }
        } else walkTicks=0;
        if (owner.isEmpty() && OrionBasketballSettings.get().basketCatch) pickup();
        if (host && owner.isEmpty() && phase.equals("play") && pendingGoal<0 && !pendingOut) {
            advance(client);
            target=new Vec3(simulation.x,simulation.y,simulation.z).subtract(origin);
            previous=new Vec3(simulation.prevX,simulation.prevY,simulation.prevZ).subtract(origin);
            changedAt=now;
        }
        ball.savePrevious();
        Vec3 point=previous.lerp(target,Math.clamp((now-changedAt)/50_000_000D,0,1));
        Vec3 world=origin.add(point);
        ball.x=world.x;ball.y=world.y;ball.z=world.z;
        ball.radius=.121*2.1;
        ball.turn.rotateX((float)(Math.hypot(ball.vx,ball.vz)*.05/.16));
        if (host && owner.isEmpty()) {
            ball.turn.set(simulation.turn);
            ball.vx=simulation.vx;ball.vy=simulation.vy;ball.vz=simulation.vz;
        }
        ball.owner=holding()?client.player.getName().getString():"";
        if(pending!=null||now<nextPoll)return;
        nextPoll=now+(phase.equals("lobby")||phase.equals("finished")?400_000_000L:45_000_000L);
        var request=request("sync");request.addProperty("round",round);request.add("position",array(feet));
        request.add("look",array(client.player.getViewVector(1).normalize()));
        if (holding()) {
            Vec3 held = heldPosition(client);
            request.add("held", array(held.subtract(origin)));
            if (dribbling()) {
                ball.x=held.x;ball.y=held.y;ball.z=held.z;
            }
        }
        if (host && owner.isEmpty() && phase.equals("play")) {
            var frame=new JsonObject();
            frame.addProperty("revision",ballRevision);
            frame.add("ball",array(pendingGoal>=0?crossing.subtract(origin):new Vec3(simulation.x,simulation.y,simulation.z).subtract(origin)));
            frame.add("velocity",array(new Vec3(simulation.vx,simulation.vy,simulation.vz)));
            frame.add("spin",array(new Vec3(simulation.wx,simulation.wy,simulation.wz)));
            var turn=new JsonArray();
            turn.add(simulation.turn.x);turn.add(simulation.turn.y);turn.add(simulation.turn.z);turn.add(simulation.turn.w);
            frame.add("turn",turn);
            if(pendingGoal>=0)frame.addProperty("goal",pendingGoal);
            if(pendingOut)frame.addProperty("out",true);
            if (!collisions.isEmpty()) {
                var sounds = new JsonArray();
                collisions.forEach(sounds::add);
                frame.add("sounds", sounds);
            }
            request.add("frame",frame);
        }
        if(readyWanted!=null)request.addProperty("ready",readyWanted);
        var command=commands.peekFirst();
        if(command!=null)request.add("command",command);
        submit(client,request,command);
    }
    private Vec3 heldPosition(Minecraft client) {
        var player=client.player;
        Vec3 look=player.getViewVector(1), up=OrionBasketballController.lookDirection(player.getXRot()-90,player.getYRot());
        if(dribbling()) {
            Vec3 forward=new Vec3(look.x,0,look.z).normalize();
            Vec3 side=forward.cross(new Vec3(0,1,0));
            return player.position().add(forward.scale(.44)).add(side.scale(.3)).add(0,
                    .121*2.1+(1.15-.121*2.1)*(1-handBounce()),0);
        }
        return OrionBasketballController.helper38(player.getEyePosition(),look,up,look.cross(up),charge());
    }

    private void advance(Minecraft client) {
        simulation.radius=.121*2.1;
        simulation.savePrevious();
        net.minecraft.client.player.AbstractClientPlayer thrower=client.player;
        if(!shooter.isEmpty()) {
            var player=client.level.getPlayerByUUID(UUID.fromString(shooter.replaceFirst(
                    "(........)(....)(....)(....)(............)","$1-$2-$3-$4-$5")));
            if(player instanceof net.minecraft.client.player.AbstractClientPlayer remote)thrower=remote;
        }
        colliders.collect(client.level,simulation,simulation.speed()*.05,hoops,thrower);
        OrionBallPhysics.advance(simulation,OrionBasketballController.helper45(OrionBasketballSettings.get()),colliders.colliders(),.05,
                new OrionBallPhysics.Listener() {
                    @Override public void hit(OrionBallMaterial material,double speed,double x,double y,double z) {
                        OrionBasketballAudio.helper21(material,speed,x,y,z);
                        if (speed < .9 || Math.abs(x - origin.x) > 13 || Math.abs(z - origin.z) > 18
                                || y - origin.y < -2 || y - origin.y > 25) return;
                        var sound = new JsonObject();
                        sound.addProperty("id", ++collisionId);
                        sound.addProperty("kind", Math.clamp(material.sound(), 0, 2));
                        sound.addProperty("speed", Math.min(speed, 100));
                        sound.add("point", array(new Vec3(x,y,z).subtract(origin)));
                        if (collisions.size() >= 12) collisions.removeFirst();
                        collisions.addLast(sound);
                    }
                    @Override public void segment(double x,double y,double z,double X,double Y,double Z) {
                        if(!liveShot||pendingGoal>=0)return;
                        for(int i=0;i<hoops.size();i++) {
                            var hoop=hoops.get(i);
                            if(hoop.crossesRim(x,y,z,X,Y,Z)) {
                                pendingGoal=i;
                                double part=(y-hoop.y)/(y-Y);
                                crossing=new Vec3(x+(X-x)*part,hoop.y,z+(Z-z)*part);
                                return;
                            }
                        }
                    }
                });
        if(pendingGoal<0)pendingOut=Math.abs(simulation.x-origin.x)>10+simulation.radius
                ||Math.abs(simulation.z-origin.z)>14+simulation.radius||simulation.y<origin.y-1;
        if(pendingGoal>=0||pendingOut)nextPoll=0;
    }
    private void submit(Minecraft client,JsonObject request,JsonObject sentCommand) {
        var transport=connection;
        var future=transport.request(request);pending=future;
        future.whenComplete((reply,error)->client.execute(()->{
            if(pending!=future) {
                if(error==null&&!request.has("key")&&reply.has("key")) {
                    var leave=new JsonObject();leave.addProperty("action","leave");leave.add("scope",request.get("scope"));
                    leave.add("key",reply.get("key"));leave.add("code",reply.get("code"));transport.request(leave);
                }
                return;
            }
            pending=null;
            if(error!=null) {
                Throwable cause=error.getCause()==null?error:error.getCause();
                String message=cause.getMessage();
                String reason=switch(message==null?"":message) {
                    case "football_missing","football_member" -> "Комната закрыта или не найдена";
                    case "football_locked" -> "Комната заполнена или матч уже начался";
                    case "football_world" -> "Ты в другом мире или на другом сервере";
                    case "football_near" -> "Подойди ближе к площадке";
                    default -> "Сервис матчей недоступен";
                };
                if(!active()||"football_missing".equals(message)||"football_member".equals(message))close(reason,false);
                else { status=reason;nextPoll=System.nanoTime()+1_000_000_000L; }
                return;
            }
            try {
                accept(reply);
                if (request.has("frame") && request.getAsJsonObject("frame").has("sounds")) {
                    var sentSounds = request.getAsJsonObject("frame").getAsJsonArray("sounds");
                    int sentId = sentSounds.get(sentSounds.size() - 1).getAsJsonObject().get("id").getAsInt();
                    while (!collisions.isEmpty() && collisions.peekFirst().get("id").getAsInt() <= sentId) collisions.removeFirst();
                }
                if(sentCommand!=null&&commands.peekFirst()==sentCommand)commands.removeFirst();
                status="";
            } catch(RuntimeException invalid) { close("Некорректный ответ матча",true); }
        }));
    }
    private void accept(JsonObject reply) {
        boolean first=!active();
        int nextRound=reply.get("round").getAsInt();
        int revision=reply.get("ballRevision").getAsInt();
        boolean reset=first||nextRound!=round;
        boolean physicalReset=reset||ballRevision!=revision;
        var score=reply.getAsJsonArray("score");
        int nextRed=score.get(0).getAsInt(),nextBlue=score.get(1).getAsInt();
        if(nextRed!=red||nextBlue!=blue)scoreAt=System.nanoTime();
        code=reply.get("code").getAsString();key=reply.get("key").getAsString();phase=reply.get("phase").getAsString();
        size=reply.get("size").getAsInt();count=reply.get("count").getAsInt();slot=reply.get("slot").getAsInt();
        red=nextRed;blue=nextBlue;round=nextRound;ready=reply.get("ready").getAsBoolean();
        origin=vector(reply.getAsJsonArray("origin"));spawn=vector(reply.getAsJsonArray("spawn"));
        owner=reply.get("owner").getAsString();
        host=reply.get("host").getAsBoolean();liveShot=reply.get("liveShot").getAsBoolean();
        dribble=reply.get("dribble").getAsBoolean();shooter=reply.get("shooter").getAsString();
        ballRevision=revision;
        long now=System.nanoTime();
        Vec3 point=vector(reply.getAsJsonArray("ball"));
        var velocity=vector(reply.getAsJsonArray("velocity"));
        if (!host || physicalReset || !owner.isEmpty()) {
            previous=reset?point:previous.lerp(target,Math.clamp((now-changedAt)/50_000_000D,0,1));
            target=point;changedAt=now;
        }
        lastPacket=now;
        ball.vx=velocity.x;ball.vy=velocity.y;ball.vz=velocity.z;
        if(physicalReset) {
            collisions.clear();
            simulation.setPosition(origin.x+point.x,origin.y+point.y,origin.z+point.z);
            simulation.stop();simulation.vx=velocity.x;simulation.vy=velocity.y;simulation.vz=velocity.z;
            var spin=vector(reply.getAsJsonArray("spin"));simulation.setSpin(spin.x,spin.y,spin.z);
            pendingGoal=-1;pendingOut=false;
        }
        var turn=reply.getAsJsonArray("turn");
        if(!host||physicalReset) {
            simulation.turn.set(turn.get(0).getAsFloat(),turn.get(1).getAsFloat(),turn.get(2).getAsFloat(),turn.get(3).getAsFloat());
            ball.turn.set(simulation.turn);
        }
        if(reset) { commands.clear();charging=false;ball.setPosition(origin.x+point.x,origin.y+point.y,origin.z+point.z); }
        if(readyWanted!=null&&readyWanted==ready)readyWanted=null;
        if(first) {
            String dimension=level.dimension().identifier().toString();
            hoops=List.of(new OrionHoop("online-red",dimension,origin.x,origin.y+3.05,origin.z+12,origin.y,180,2.1),
                    new OrionHoop("online-blue",dimension,origin.x,origin.y+3.05,origin.z-12,origin.y,0,2.1));
        }
        if (reply.has("sounds")) {
            for (var entry : reply.getAsJsonArray("sounds")) {
                var sound = entry.getAsJsonObject();
                int id = sound.get("id").getAsInt();
                if (id <= lastSoundId) continue;
                lastSoundId = id;
                int kind = sound.get("kind").getAsInt();
                if (host && kind <= 2) continue;
                OrionBasketballAudio.playMatchSound(kind, origin.add(vector(sound.getAsJsonArray("point"))),
                        sound.get("volume").getAsFloat(), sound.get("pitch").getAsFloat());
            }
        }
    }
    public Vec3 startMarker() { return origin.add(spawn); }
    public Vec3 origin() { return origin; }
    public boolean kickoff() { return phase.equals("kickoff"); }
    private static String localId() {
        var player=Minecraft.getInstance().player;
        return player==null?"":player.getUUID().toString().replace("-","");
    }
    private static JsonArray array(Vec3 point) { var result=new JsonArray();result.add(point.x);result.add(point.y);result.add(point.z);return result; }
    private static Vec3 vector(JsonArray array) { return new Vec3(array.get(0).getAsDouble(),array.get(1).getAsDouble(),array.get(2).getAsDouble()); }
}
