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
    private static final int BALL_RINGS = 16, BALL_SEGMENTS = 32;
    private static final float[] BALL_MESH = ballMesh();
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
        float radius = (float) OrionFootballPhysics.RADIUS;
        for (int i = 0; i < BALL_MESH.length; i += 5) {
            float x = BALL_MESH[i], y = BALL_MESH[i + 1], z = BALL_MESH[i + 2];
            vc.addVertex(pose, x * radius, y * radius, z * radius)
                    .setUv(BALL_MESH[i + 3], BALL_MESH[i + 4]).setColor(-1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, x, y, z);
        }
    }
    private static float[] ballMesh() {
        float[] mesh = new float[BALL_RINGS * BALL_SEGMENTS * 4 * 5];
        int index = 0;
        for (int ring = 0; ring < BALL_RINGS; ring++) {
            for (int segment = 0; segment < BALL_SEGMENTS; segment++) {
                ballVertex(mesh, index, ring, segment);
                ballVertex(mesh, index + 5, ring, segment + 1);
                ballVertex(mesh, index + 10, ring + 1, segment + 1);
                ballVertex(mesh, index + 15, ring + 1, segment);
                index += 20;
            }
        }
        return mesh;
    }
    private static void ballVertex(float[] mesh, int index, int ring, int segment) {
        float u = segment / (float) BALL_SEGMENTS, v = ring / (float) BALL_RINGS;
        double latitude = Math.PI * (v - .5), longitude = Math.TAU * u;
        mesh[index] = (float) (Math.cos(latitude) * Math.cos(longitude));
        mesh[index + 1] = (float) Math.sin(latitude);
        mesh[index + 2] = (float) (Math.cos(latitude) * Math.sin(longitude));
        mesh[index + 3] = u;
        mesh[index + 4] = 1 - v;
    }
    static void pitch(LevelRenderContext context, OrionBasketballVector origin, OrionBasketballVector spawn,
                      int team, boolean showSpawn) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        PoseStack stack = context.poseStack();
        stack.pushPose();
        stack.translate(origin.x() - camera.x, origin.y() - camera.y + .025, origin.z() - camera.z);
        context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.lines(), (pose, vertices) -> {
            float w = OrionFootballMatch.HALF_WIDTH, l = OrionFootballMatch.HALF_LENGTH;
            int white = 0xDDEDEDEA;
            line(pose, vertices, -w, 0, -l, w, 0, -l, white);
            line(pose, vertices, w, 0, -l, w, 0, l, white);
            line(pose, vertices, w, 0, l, -w, 0, l, white);
            line(pose, vertices, -w, 0, l, -w, 0, -l, white);
            line(pose, vertices, -w, 0, 0, w, 0, 0, white);
            circle(pose, vertices, 0, 0, 2.2F, white);
            if (showSpawn) circle(pose, vertices, (float) spawn.x(), (float) spawn.z(), 1F,
                    team == 0 ? 0xFFFF6255 : 0xFF59A8FF);
        });
        stack.popPose();
    }
    private static void circle(PoseStack.Pose pose, VertexConsumer vertices, float x, float z, float radius, int color) {
        for (int i = 0; i < 32; i++) {
            double a = Math.TAU * i / 32, b = Math.TAU * (i + 1) / 32;
            line(pose, vertices, x + radius * (float) Math.cos(a), 0, z + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(b), 0, z + radius * (float) Math.sin(b), color);
        }
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
