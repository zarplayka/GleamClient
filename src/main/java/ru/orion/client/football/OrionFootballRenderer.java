package ru.orion.client.football;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.model.geom.ModelLayers;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.OrionClient;
import ru.orion.client.basketball.OrionBasketballVector;

/** Текстурированный мяч и составные модели ворот и вратаря. */
public final class OrionFootballRenderer {
    private static final Identifier BALL=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"textures/football/ball.png");
    private static final Identifier KEEPER_TEXTURE=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"textures/football/goalkeeper.png");
    private static OrionGoalkeeperModel keeperModel;
    private static volatile int resourceVersion;
    private static int modelVersion=-1;
    public static void registerResources() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"football_models"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { resourceVersion++; }
        });
    }
    private OrionFootballRenderer() { }
    public static void ball(LevelRenderContext context,OrionBasketballVector position,float rotation){
        Vec3 camera=Minecraft.getInstance().gameRenderer.mainCamera().position();PoseStack pose=context.poseStack();pose.pushPose();
        pose.translate(position.x()-camera.x,position.y()-camera.y,position.z()-camera.z);pose.rotate(Axis.XP.rotation(rotation));
        var level=Minecraft.getInstance().level;var pos=BlockPos.containing(position.x(),position.y(),position.z());
        int light=level==null?0xF000F0:(level.getBrightness(LightLayer.BLOCK,pos)<<4)|(level.getBrightness(LightLayer.SKY,pos)<<20);
        context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.entityCutout(BALL),(p,vc)->sphere(p,vc,light));pose.popPose();
    }
    private static void sphere(PoseStack.Pose pose,VertexConsumer vc,int light){
        for(int i=0;i<24;i++)for(int j=0;j<48;j++){
            double a=-Math.PI/2+Math.PI*i/24,b=-Math.PI/2+Math.PI*(i+1)/24,c=Math.PI*2*j/48,d=Math.PI*2*(j+1)/48;
            vertex(pose,vc,a,c,light);vertex(pose,vc,a,d,light);vertex(pose,vc,b,d,light);vertex(pose,vc,b,c,light);
        }
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer vc,double lat,double lon,int light){
        float x=(float)(Math.cos(lat)*Math.cos(lon)),y=(float)Math.sin(lat),z=(float)(Math.cos(lat)*Math.sin(lon));
        vc.addVertex(p,x*(float)OrionFootballPhysics.RADIUS,y*(float)OrionFootballPhysics.RADIUS,z*(float)OrionFootballPhysics.RADIUS)
                .setUv((float)(lon/(Math.PI*2)),(float)(1-(lat/Math.PI+.5))).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,x,y,z);
    }
    public static void goal(LevelRenderContext context,OrionFootballGoal goal,OrionGoalkeeperAI keeper,boolean preview,float netMotion,float tick,OrionBasketballVector ballPosition){
        Vec3 camera=Minecraft.getInstance().gameRenderer.mainCamera().position();PoseStack pose=context.poseStack();pose.pushPose();
        pose.translate(goal.base().x()-camera.x,goal.base().y()-camera.y,goal.base().z()-camera.z);pose.rotate(Axis.YP.rotation((float)goal.yaw()));float scale=(float)goal.scale();pose.scale(scale,scale,scale);
        int a=preview?110:240;
        context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.debugQuads(),(p,vc)->goalBars(p,vc,a));
        context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.lines(),(p,vc)->net(p,vc,a,netMotion));
        if(keeper!=null&&keeper.enabled()&&!preview){
            if(keeperModel==null || modelVersion!=resourceVersion){
                keeperModel=new OrionGoalkeeperModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER));
                modelVersion=resourceVersion;
            }
            var target=ballPosition==null?new OrionBasketballVector(keeper.x(),1.6,-2):goal.local(ballPosition);
            OrionGoalkeeperRenderState state=new OrionGoalkeeperRenderState();
            state.id=-1;state.speedValue=1F;state.showHat=true;state.showLeftSleeve=true;state.showRightSleeve=true;
            state.showLeftPants=true;state.showRightPants=true;state.showJacket=true;
            state.keeperHeadYaw=(float)Math.max(-.9,Math.min(.9,Math.atan2(target.x()-keeper.x(),Math.max(.1,Math.abs(target.z())))));
            state.goalkeeperAction=keeper.action();
            state.keeperGait=keeper.action()==OrionGoalkeeperAI.Action.RUN?(float)Math.sin(tick*9)*.55F:0F;
            state.keeperHandsHigh=keeper.action()==OrionGoalkeeperAI.Action.JUMP || keeper.targetHeight()>1.65;
            pose.pushPose();pose.translate(keeper.x(),keeper.vertical(),0);pose.rotate(Axis.ZP.rotation((float)keeper.lean()));
            pose.scale(-1F,-1F,1F);pose.translate(0,-1.501F,0);
            var point=goal.world(new OrionBasketballVector(keeper.x(),1,0));
            var block=BlockPos.containing(point.x(),point.y(),point.z());var level=Minecraft.getInstance().level;
            int light=level==null?0xF000F0:(level.getBrightness(LightLayer.BLOCK,block)<<4)|(level.getBrightness(LightLayer.SKY,block)<<20);
            context.submitNodeCollector().submitModel(keeperModel,state,pose,RenderTypes.entityCutout(KEEPER_TEXTURE),light,OverlayTexture.NO_OVERLAY,-1);
            pose.popPose();
        }
        pose.popPose();
    }
    private static void goalBars(PoseStack.Pose p,VertexConsumer vc,int alpha){float hw=(float)(OrionFootballGoal.WIDTH/2),h=(float)OrionFootballGoal.HEIGHT,t=(float)OrionFootballGoal.POST_RADIUS;int white=ARGB.color(alpha,235,238,240);
        box(p,vc,-hw-t,0,-t,-hw+t,h+t,t,white);box(p,vc,hw-t,0,-t,hw+t,h+t,t,white);box(p,vc,-hw-t,h-t,-t,hw+t,h+t,t,white);
    }
    private static void net(PoseStack.Pose p,VertexConsumer vc,int a,float motion){
        int color=ARGB.color(Math.min(a,160),230,230,225);float w=(float)(OrionFootballGoal.WIDTH/2),h=(float)OrionFootballGoal.HEIGHT,d=(float)OrionFootballGoal.DEPTH;
        for(int i=0;i<=24;i++){float x=-w+2*w*i/24;line(p,vc,x,h,0,x+motion,h,d,color);line(p,vc,x+motion,h,d,x+motion,0,d,color);}
        for(int i=0;i<=8;i++){float y=h*i/8;line(p,vc,-w+motion,y,d,w+motion,y,d,color);line(p,vc,-w,y,0,-w+motion,y,d,color);line(p,vc,w,y,0,w+motion,y,d,color);}
    }
    private static void line(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float X,float Y,float Z,int c){float dx=X-x,dy=Y-y,dz=Z-z,l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(l<1e-5)return;dx/=l;dy/=l;dz/=l;v.addVertex(p,x,y,z).setColor(c).setNormal(p,dx,dy,dz).setLineWidth(1F);v.addVertex(p,X,Y,Z).setColor(c).setNormal(p,dx,dy,dz).setLineWidth(1F);}
    private static void box(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float X,float Y,float Z,int c){
        quad(p,v,x,y,z,X,y,z,X,Y,z,x,Y,z,c);quad(p,v,X,y,Z,x,y,Z,x,Y,Z,X,Y,Z,c);quad(p,v,x,y,Z,x,y,z,x,Y,z,x,Y,Z,c);quad(p,v,X,y,z,X,y,Z,X,Y,Z,X,Y,z,c);quad(p,v,x,Y,z,X,Y,z,X,Y,Z,x,Y,Z,c);quad(p,v,x,y,Z,X,y,Z,X,y,z,x,y,z,c);
    }
    private static void quad(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,int c){v.addVertex(p,x,y,z).setColor(c);v.addVertex(p,x1,y1,z1).setColor(c);v.addVertex(p,x2,y2,z2).setColor(c);v.addVertex(p,x3,y3,z3).setColor(c);}
}
