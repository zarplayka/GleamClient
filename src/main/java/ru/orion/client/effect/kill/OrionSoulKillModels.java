package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;

/** Оригинальные скульптурные модели. Части кешируются один раз, кости анимируются отдельно. */
public final class OrionSoulKillModels {
    private static final float PI=(float)Math.PI;
    private static final int CLOTH=0xACCBD1,DARK=0x516A7D,IVORY=0xE2DEC7,GOLD=0xCEA15B,
            JADE=0x438E82,SCALE=0x405C78,ARMOR=0x7795AC,MEMBRANE=0x8AA0BB;
    private static final Mesh HOOD=ghostHood(),MASK=ghostFace(),SHOULDERS=ghostShoulders(),HAND=ghostHand(),MANTLE=ghostMantle();
    private static final Mesh[] RAGS=ghostRags(),ANGEL_FEATHERS=angelFeathers();
    private static final Mesh ANGEL_ROOT=angelRoot(),ANGEL_ARCH=angelArch();
    private static final Mesh IDOL_BODY=idolBody(),IDOL_HEAD=idolHead(),IDOL_ARM=idolArm(),IDOL_FOOT=idolFoot();
    private static final Mesh DRAGON_BODY=dragonBody(),DRAGON_NECK=dragonNeck(),DRAGON_HEAD=dragonHead(),
            DRAGON_JAW=dragonJaw(),THIGH=dragonThigh(),SHIN=dragonShin(),FOOT=dragonFoot();
    private static final float[][] FINGERS={{1.83F,.25F,-.27F},{1.74F,-.10F,.40F},{1.25F,-.38F,.94F},{.57F,-.54F,1.17F},{-.31F,-.50F,.84F}};
    private static final Mesh WING_ROOT=dragonWingRoot(),WING_HAND=dragonWingHand();
    private static final Mesh[] WING_PANELS=dragonWingPanels(),TAIL=dragonTail();
    private static final Mesh FEATHER=feather(0,0,0,.06F,-.48F,.04F,.075F,0xF1E9D6),FRAGMENT=fragment();
    private OrionSoulKillModels() { }

    public static void render(int type,PoseStack stack,VertexConsumer vertices,float seconds,int quality,float particles,long seed) {
        float t=clamp(seconds/5F);if(t<=0||t>=1)return;
        stack.pushPose();
        switch(type){case 0->ghost(stack,vertices,t,quality,particles);case 5->angel(stack,vertices,t,quality,particles);
            case 7->idol(stack,vertices,t,quality,particles);case 8->dragon(stack,vertices,t,quality,particles);default->{}}
        stack.popPose();
    }
    private static void ghost(PoseStack s,VertexConsumer v,float t,int quality,float particles) {
        float a=envelope(t,.15F,.68F);s.translate(.1F*sin(t*9),.25F+smooth(t)*2.2F,.12F*sin(t*6));
        s.rotate(Axis.YP.rotationDegrees(-12+sin(t*8)*13));s.rotate(Axis.ZP.rotationDegrees(sin(t*10)*3));
        float grow=.72F+.28F*smooth(t/.17F);s.scale(grow,grow,grow);
        draw(SHOULDERS,s,v,a);draw(MANTLE,s,v,a*.88F);
        s.pushPose();s.translate(0,1.45F,-.05F);s.rotate(Axis.XP.rotationDegrees(5+sin(t*9)*3));
        s.rotate(Axis.YP.rotationDegrees(sin(t*12)*6));draw(HOOD,s,v,a);draw(MASK,s,v,a);s.popPose();
        for(int i=0;i<RAGS.length;i++){
            float q=i/(float)RAGS.length;s.pushPose();s.translate(0,.68F,0);
            s.rotate(Axis.XP.rotationDegrees(sin(t*13+q*6)*8));s.rotate(Axis.ZP.rotationDegrees(sin(t*10+q*8)*6));
            draw(RAGS[i],s,v,a*(1-smooth((t-.75F+q*.07F)/.25F)));s.popPose();
        }
        for(int side=-1;side<=1;side+=2){s.pushPose();s.translate(side*.60F,1.12F,-.03F);s.scale(side,1,1);
            s.rotate(Axis.ZP.rotationDegrees(-10+sin(t*11+side)*9));s.rotate(Axis.YP.rotationDegrees(12+side*7));draw(HAND,s,v,a);s.popPose();}
        motes(s,v,t,quality,particles,a,0xA5F0ED,.8F,1.7F);
    }
    private static Mesh ghostHood(){
        Builder b=new Builder();
        b.tube(new float[][]{{0,-.20F,.10F,.31F,.25F},{0,.03F,.06F,.43F,.31F},{0,.39F,.10F,.37F,.28F},{-.07F,.63F,.23F,.18F,.14F},{-.15F,.69F,.33F,.025F,.025F}},CLOTH);
        b.profile(new float[]{-.34F,-.40F,-.29F,0,.29F,.40F,.34F,0},new float[]{-.2F,.11F,.42F,.53F,.42F,.11F,-.2F,-.34F},.09F,DARK,0,-.01F,-.25F);
        b.profile(new float[]{-.30F,-.32F,-.22F,0,.22F,.32F,.30F,0},new float[]{-.17F,.10F,.36F,.43F,.36F,.10F,-.17F,-.28F},.03F,0x233849,0,-.01F,-.312F);
        return b.build();
    }
    private static Mesh ghostFace(){
        Builder b=new Builder();b.profile(new float[]{-.20F,-.25F,-.16F,0,.16F,.25F,.20F,0},new float[]{-.16F,.08F,.24F,.28F,.24F,.08F,-.16F,-.28F},.095F,IVORY,0,0,-.36F);
        for(int side=-1;side<=1;side+=2){b.box(side*.115F,.065F,-.421F,.135F,.09F,.025F,0x1A3547);b.box(side*.115F,.074F,-.437F,.080F,.033F,.014F,0xAEFFF1);
            b.bone(side*.14F,-.08F,-.41F,side*.08F,-.21F,-.42F,.025F,.006F,0x6C929C);}
        b.bone(0,.04F,-.424F,0,-.08F,-.448F,.022F,.004F,0x63818C);
        for(int i=-2;i<=2;i++)b.box(i*.036F,-.178F,-.417F,.015F,.065F,.013F,0x426577);return b.build();
    }
    private static Mesh ghostShoulders(){
        Builder b=new Builder();b.tube(new float[][]{{0,.72F,.03F,.24F,.18F},{0,1.06F,.04F,.36F,.25F},{0,1.34F,.07F,.51F,.25F},{0,1.46F,.09F,.30F,.16F}},DARK);
        for(int side=-1;side<=1;side+=2){b.bone(side*.25F,1.30F,-.01F,side*.63F,1.14F,.02F,.19F,.11F,0x607F91);b.bone(side*.63F,1.14F,.02F,side*.72F,.90F,-.03F,.11F,.15F,DARK);
            b.bone(side*.2F,1.39F,-.12F,side*.6F,1.18F,-.13F,.045F,.022F,IVORY);for(int i=0;i<4;i++)b.box(side*(.12F+i*.07F),1.28F-i*.035F,-.23F,.034F,.034F,.025F,GOLD);}
        b.gem(0,1.18F,-.29F,.085F,.12F,.055F,GOLD);b.gem(0,1.18F,-.345F,.042F,.065F,.021F,0x80D6CA);return b.build();
    }
    private static Mesh ghostMantle(){
        Builder b=new Builder();b.tube(new float[][]{{0,.58F,.10F,.22F,.14F},{0,.83F,.08F,.32F,.21F},{0,1.14F,.07F,.33F,.22F}},DARK);
        for(int side=-1;side<=1;side+=2)b.ribbon(new float[][]{{side*.17F,1.32F,-.26F,.11F},{side*.28F,1.0F,-.25F,.16F},{side*.28F,.65F,-.13F,.15F},{side*.15F,.18F,.05F,.08F},{side*.1F,-.15F,.18F,0}},.045F,CLOTH);
        return b.build();
    }
    private static Mesh[] ghostRags(){
        Mesh[] out=new Mesh[7];for(int i=0;i<7;i++){float a=i*PI*2/7,x=(float)Math.cos(a),z=sin(a),len=.9F+(i%3)*.16F;Builder b=new Builder();
            b.ribbon(new float[][]{{x*.29F,.45F,z*.22F,.12F},{x*.30F,.05F,z*.23F,.13F},{x*.23F,-.38F,z*.29F,.09F},{x*.17F,-len*.75F,z*.37F,.055F},{x*.29F,-len,z*.52F,0}},.032F,i%2==0?CLOTH:0x7B9AAE);out[i]=b.build();}return out;
    }
    private static Mesh ghostHand(){
        Builder b=new Builder();b.bone(0,0,0,.13F,-.23F,-.15F,.09F,.065F,DARK);b.box(.14F,-.28F,-.16F,.16F,.17F,.09F,IVORY);
        for(int finger=0;finger<4;finger++){float x=.072F+finger*.045F,y=-.33F-(finger%2)*.025F;b.bone(x,y,-.19F,x+.025F,y-.13F,-.22F,.022F,.017F,IVORY);b.bone(x+.025F,y-.13F,-.22F,x+.06F,y-.20F,-.17F,.017F,.002F,IVORY);}
        b.bone(.045F,-.26F,-.16F,-.015F,-.35F,-.25F,.026F,.004F,IVORY);return b.build();
    }
    private static void angel(PoseStack s,VertexConsumer v,float t,int quality,float particles){
        float a=envelope(t,.18F,.72F),open=smooth(t/.32F),breakup=smooth((t-.65F)/.35F);s.translate(0,1.35F+smooth(t)*.65F,0);
        for(int side=-1;side<=1;side+=2){s.pushPose();s.scale(side,1,1);s.rotate(Axis.YP.rotationDegrees(78*(1-open)+sin(t*13)*7));s.rotate(Axis.ZP.rotationDegrees(4+sin(t*12)*5));
            draw(ANGEL_ROOT,s,v,a*(1-breakup));s.pushPose();s.translate(.62F,.39F,.06F);s.rotate(Axis.YP.rotationDegrees(30*(1-open)+sin(t*14-.6F)*8));s.rotate(Axis.ZP.rotationDegrees(13*(1-open)+sin(t*12-.5F)*4));
            draw(ANGEL_ARCH,s,v,a*(1-breakup));for(int i=0;i<ANGEL_FEATHERS.length;i++){float detach=clamp((t-.63F-(i%12)*.004F)/.30F);s.pushPose();
                if(detach>0){s.translate(detach*(.4F+(i%5)*.13F),detach*sin(i*2.4F)*.75F,detach*sin(i*1.7F)*.7F);s.rotate(Axis.YP.rotationDegrees(detach*sin(i)*65));s.rotate(Axis.ZP.rotationDegrees(detach*sin(i*1.4F)*50));}
                draw(ANGEL_FEATHERS[i],s,v,a*(1-smooth((detach-.5F)/.5F)));s.popPose();}s.popPose();s.popPose();}
        s.pushPose();s.translate(0,.92F,.03F);OrionKillGeometry.ring(s.last(),v,.32F,.018F,argb(GOLD,a),32);s.popPose();
        int count=Math.round((6+quality*4)*particles);for(int i=0;i<count;i++){float q=clamp((t-.22F-(i%5)*.024F)/.7F);if(q<=0)continue;s.pushPose();s.translate(sin(i*2.4F)*(.6F+q*1.9F),.4F-q*1.7F,(float)Math.cos(i*2.4F)*q);
            s.rotate(Axis.ZP.rotationDegrees(i*31+q*110));s.rotate(Axis.YP.rotationDegrees(i*42+q*160));draw(FEATHER,s,v,a*(1-q));s.popPose();}
    }
    private static Mesh angelRoot(){Builder b=new Builder();b.bone(0,0,0,.31F,.16F,.02F,.09F,.075F,GOLD);b.bone(.31F,.16F,.02F,.63F,.39F,.06F,.075F,.05F,GOLD);b.gem(.13F,.09F,-.065F,.08F,.12F,.045F,GOLD);b.gem(.13F,.09F,-.11F,.04F,.066F,.018F,0xBCDDE1);return b.build();}
    private static Mesh angelArch(){Builder b=new Builder();b.bone(0,0,0,.52F,.47F,.08F,.067F,.048F,GOLD);b.bone(.52F,.47F,.08F,1.18F,.62F,.14F,.048F,.018F,GOLD);for(int i=0;i<8;i++){float q=i/8F;b.gem(q*1.1F,.03F+.62F*sin(q*1.6F),q*.1F,.018F,.029F,.012F,0xF1DDAB);}return b.build();}
    private static Mesh[] angelFeathers(){List<Mesh> out=new ArrayList<>();
        for(int i=0;i<7;i++){float q=i/6F;out.add(feather(-.53F+q*.56F,-.29F+q*.3F,-.02F,-.43F+q*.7F,-.78F+q*.31F,.09F,.09F,0xDDE1DA));}
        for(int row=0;row<3;row++)for(int i=0;i<12;i++){float q=i/11F,x=.03F+q*1.12F,y=.10F+.51F*sin(q*1.48F)-row*.07F;
        float length=row==0?.48F+.65F*q:row==1?.4F+.35F*q:.23F+.2F*q;
        out.add(feather(x,y,.05F+row*.043F+q*.1F,x+.23F+q*.42F,y-length,.18F+row*.04F+q*.1F,row==0?.10F:row==1?.082F:.069F,row==0?0xEAE7DB:row==1?0xC9D4DA:0xF6EDD5));}return out.toArray(Mesh[]::new);}
    private static Mesh feather(float ax,float ay,float az,float bx,float by,float bz,float width,int color){
        Builder b=new Builder();float dx=bx-ax,dy=by-ay,len=(float)Math.hypot(dx,dy),nx=-dy/len,ny=dx/len;
        width*=.8F;
        for(int i=0;i<8;i++){float q=i/8F,r=(i+1F)/8,wa=(float)Math.sqrt(Math.max(0,sin(q*PI)))*width,wb=(float)Math.sqrt(Math.max(0,sin(r*PI)))*width;
            float xa=ax+dx*q-.1F*sin(q*PI),ya=ay+dy*q+.16F*sin(q*PI),za=az+(bz-az)*q-.10F*sin(q*PI),xb=ax+dx*r-.1F*sin(r*PI),yb=ay+dy*r+.16F*sin(r*PI),zb=az+(bz-az)*r-.10F*sin(r*PI);
            b.quad(xa+nx*wa,ya+ny*wa,za,xa,ya,za-.025F,xb,yb,zb-.025F,xb+nx*wb,yb+ny*wb,zb,color);
            b.quad(xa,ya,za-.025F,xa-nx*wa*.8F,ya-ny*wa*.8F,za,xb-nx*wb*.8F,yb-ny*wb*.8F,zb,xb,yb,zb-.025F,Builder.shade(color,.9F));
            b.quad(xa-nx*wa*.8F,ya-ny*wa*.8F,za+.021F,xa+nx*wa,ya+ny*wa,za+.021F,xb+nx*wb,yb+ny*wb,zb+.021F,xb-nx*wb*.8F,yb-ny*wb*.8F,zb+.021F,Builder.shade(color,.74F));
            b.quad(xa+nx*wa,ya+ny*wa,za,xb+nx*wb,yb+ny*wb,zb,xb+nx*wb,yb+ny*wb,zb+.021F,xa+nx*wa,ya+ny*wa,za+.021F,Builder.shade(color,.8F));
            b.quad(xa-nx*wa*.8F,ya-ny*wa*.8F,za+.021F,xb-nx*wb*.8F,yb-ny*wb*.8F,zb+.021F,xb-nx*wb*.8F,yb-ny*wb*.8F,zb,xa-nx*wa*.8F,ya-ny*wa*.8F,za,Builder.shade(color,.8F));}
        b.bone(ax,ay,az-.027F,bx,by,bz,.009F,.002F,0xB2B6AC);return b.build();
    }
    private static void idolTransform(PoseStack s,float t){s.translate(0,1.25F+.12F*sin(t*16),0);s.rotate(Axis.YP.rotationDegrees(-110+t*65+t*t*t*1000));float size=.45F+.55F*smooth(t/.16F);s.scale(size,size,size);}
    private static void idol(PoseStack s,VertexConsumer v,float t,int quality,float particles){
        float a=envelope(t,.13F,.75F),blast=smooth((t-.64F)/.36F);s.pushPose();idolTransform(s,t);
        if(t<.65F){draw(IDOL_BODY,s,v,a);draw(IDOL_HEAD,s,v,a);for(int side=-1;side<=1;side+=2){s.pushPose();s.translate(side*.29F,.65F,0);s.scale(side,1,1);s.rotate(Axis.ZP.rotationDegrees(13+smooth(t/.35F)*18+sin(t*19)*2));draw(IDOL_ARM,s,v,a);s.popPose();s.pushPose();s.translate(side*.17F,-.13F,0);draw(IDOL_FOOT,s,v,a);s.popPose();}}s.popPose();
        if(t>.64F)for(int i=0;i<30;i++){float theta=i*2.39996F,r=blast*(1.6F+(i%4)*.21F),y=1.9F+sin(i*2.8F)*r-blast*blast*.5F;s.pushPose();s.translate(Math.cos(theta)*r,y,sin(theta)*r);s.rotate(Axis.XP.rotationDegrees(i*43+blast*220));s.rotate(Axis.YP.rotationDegrees(i*37+blast*330));s.scale(.65F+(i%4)*.2F,.6F+(i%3)*.3F,.75F);draw(FRAGMENT,s,v,a*(1-blast));s.popPose();}
        if(t>.64F&&t<.75F){s.pushPose();s.translate(0,1.9F,0);OrionKillGeometry.ellipsoid(s.last(),v,0,0,0,blast*.9F,blast*.9F,blast*.9F,argb(0xFFF0B1,(1-blast/.35F)*.55F),12);s.popPose();}
        motes(s,v,t,quality,particles,a,0xE6CC82,1.1F,2.5F);
    }
    private static Mesh idolBody(){Builder b=new Builder();b.tube(new float[][]{{0,-.02F,0,.21F,.15F},{0,.17F,0,.26F,.18F},{0,.5F,.02F,.28F,.21F},{0,.76F,.015F,.35F,.22F},{0,.85F,.015F,.27F,.19F}},GOLD);
        b.box(0,.22F,-.185F,.34F,.07F,.09F,0xE5C57F);b.box(0,.09F,-.16F,.30F,.06F,.08F,0x8D6E40);b.gem(0,.56F,-.25F,.20F,.22F,.08F,0x795B37);b.gem(0,.56F,-.325F,.14F,.16F,.055F,JADE);b.gem(0,.59F,-.38F,.045F,.06F,.025F,0xD1ECC5);
        for(int side=-1;side<=1;side+=2)for(int i=0;i<3;i++){b.box(side*(.14F+i*.035F),.80F-i*.08F,-.225F,.035F,.085F,.04F,0xF0D28B);b.box(side*.16F,.35F-i*.04F,-.19F,.045F,.025F,.035F,JADE);}b.gem(0,.6F,.23F,.16F,.20F,.04F,JADE);return b.build();}
    private static Mesh idolHead(){Builder b=new Builder();b.tube(new float[][]{{0,.83F,0,.2F,.17F},{0,.94F,0,.31F,.22F},{0,1.30F,.015F,.32F,.23F},{0,1.47F,.04F,.25F,.19F}},GOLD);b.box(0,1.35F,-.25F,.58F,.07F,.10F,0xF0D38E);b.box(0,1.42F,.015F,.56F,.06F,.47F,0x9D7D48);
        for(int side=-1;side<=1;side+=2){b.box(side*.155F,1.20F,-.24F,.20F,.13F,.05F,0x5D5136);b.box(side*.155F,1.20F,-.271F,.115F,.075F,.025F,JADE);b.box(side*.142F,1.215F,-.289F,.035F,.042F,.012F,0xC7E7B9);b.bone(side*.25F,1.32F,-.14F,side*.34F,1.13F,-.12F,.06F,.045F,0xE1BD73);b.box(side*.25F,1.00F,-.17F,.095F,.19F,.07F,JADE);b.bone(side*.2F,1.47F,.025F,side*.28F,1.65F,.035F,.06F,.018F,0xE7CB87);
            b.box(side*.305F,1.17F,.015F,.035F,.17F,.19F,JADE);b.box(side*.326F,1.17F,.015F,.023F,.12F,.045F,0xE9D28E);}
        b.box(0,1.14F,-.26F,.075F,.19F,.12F,0xF3D58E);b.box(0,.99F,-.237F,.28F,.045F,.06F,0x604B31);b.box(0,.96F,-.245F,.18F,.035F,.045F,0xF0D38E);b.gem(0,1.49F,-.025F,.11F,.18F,.08F,GOLD);b.gem(0,1.5F,-.1F,.052F,.087F,.026F,JADE);return b.build();}
    private static Mesh idolArm(){Builder b=new Builder();b.bone(0,0,0,.34F,.07F,0,.15F,.13F,GOLD);b.bone(.34F,.07F,0,.43F,.38F,-.03F,.13F,.10F,GOLD);b.box(.43F,.37F,-.02F,.22F,.22F,.22F,0xD9B76F);b.box(.43F,.15F,-.12F,.19F,.07F,.045F,JADE);for(int i=0;i<3;i++)b.box(.36F+i*.068F,.48F,-.02F,.05F,.10F,.16F,0xF0D391);b.gem(.43F,.36F,-.15F,.067F,.085F,.035F,JADE);b.box(.555F,.36F,-.02F,.035F,.12F,.13F,JADE);return b.build();}
    private static Mesh idolFoot(){Builder b=new Builder();b.box(0,0,0,.19F,.25F,.21F,GOLD);b.box(0,-.12F,-.055F,.24F,.14F,.32F,GOLD);b.box(0,-.14F,-.23F,.16F,.055F,.035F,JADE);b.box(0,.025F,-.13F,.10F,.045F,.045F,0xE8CD87);return b.build();}
    public static void renderTotemTexture(PoseStack s,VertexConsumer v,float seconds,int quality,float particles,long seed){
        float t=clamp(seconds/5F);if(t<=0||t>=.65F)return;s.pushPose();idolTransform(s,t);int c=argb(0xFFFFFF,envelope(t,.13F,.75F));
        for(int side=-1;side<=1;side+=2){float z=side*.235F;v.addVertex(s.last(),-.23F,1,z).setUv(0,1).setColor(c);v.addVertex(s.last(),.23F,1,z).setUv(1,1).setColor(c);v.addVertex(s.last(),.23F,1.3F,z).setUv(1,0).setColor(c);v.addVertex(s.last(),-.23F,1.3F,z).setUv(0,0).setColor(c);}s.popPose();
    }
    private static void dragon(PoseStack s,VertexConsumer v,float t,int quality,float particles){
        float a=envelope(t,.20F,.73F),flight=smooth((t-.17F)/.83F);s.translate(sin(t*4)*flight*.9F,.4F+flight*3.3F,-flight*1.4F);s.rotate(Axis.YP.rotationDegrees(-12+sin(t*6)*14));s.rotate(Axis.XP.rotationDegrees(-8-flight*13));float grow=.65F+.35F*smooth(t/.2F);s.scale(grow,grow,grow);draw(DRAGON_BODY,s,v,a);
        s.pushPose();s.translate(0,1.0F,-.39F);s.rotate(Axis.YP.rotationDegrees(sin(t*12)*5));s.rotate(Axis.XP.rotationDegrees(sin(t*18)*3));draw(DRAGON_NECK,s,v,a);s.translate(0,.58F,-.55F);s.rotate(Axis.XP.rotationDegrees(-5+sin(t*15)*3));draw(DRAGON_HEAD,s,v,a);s.pushPose();s.translate(0,-.16F,-.06F);s.rotate(Axis.XP.rotationDegrees(8+sin(t*21)*5));draw(DRAGON_JAW,s,v,a);s.popPose();s.popPose();
        for(int side=-1;side<=1;side+=2){for(int leg=0;leg<2;leg++){s.pushPose();s.translate(side*(leg==0?.28F:.34F),.95F,leg==0?-.28F:.58F);s.scale(side,1,1);s.rotate(Axis.XP.rotationDegrees(leg==0?-8:11));s.rotate(Axis.ZP.rotationDegrees(-13+sin(t*18+leg*PI)*4));float hind=leg==0?.72F:1;s.scale(hind,hind,hind);draw(THIGH,s,v,a);s.translate(.21F,-.38F,.18F);s.rotate(Axis.XP.rotationDegrees(-14+sin(t*18+leg)*5));draw(SHIN,s,v,a);s.translate(-.04F,-.34F,-.12F);draw(FOOT,s,v,a);s.popPose();}
            s.pushPose();s.translate(side*.36F,1.2F,-.22F);s.scale(side,1,1);float flap=sin((t-.1F)*24),open=smooth(t/.24F);s.rotate(Axis.YP.rotationDegrees(60*(1-open)+3));s.rotate(Axis.ZP.rotationDegrees(33+flap*26));draw(WING_ROOT,s,v,a);s.translate(.77F,.45F,.08F);s.rotate(Axis.YP.rotationDegrees(15*(1-open)+sin(t*24-.8F)*9));s.rotate(Axis.ZP.rotationDegrees(sin(t*24-.5F)*8));draw(WING_HAND,s,v,a);for(Mesh panel:WING_PANELS)draw(panel,s,v,a*.9F);s.popPose();}
        s.pushPose();s.translate(0,.88F,.91F);s.scale(1,1,.86F);for(int i=0;i<TAIL.length;i++){s.rotate(Axis.YP.rotationDegrees(sin(t*13-i*.4F)*(4+i*.45F)));s.rotate(Axis.XP.rotationDegrees(-2+sin(t*10-i*.3F)*2));draw(TAIL[i],s,v,a);s.translate(0,0,.22F);}s.popPose();motes(s,v,t,quality,particles,a,0x91BDDA,.48F,.8F);
    }
    private static Mesh dragonBody(){Builder b=new Builder();b.tube(new float[][]{{0,.72F,.93F,.16F,.18F},{0,.83F,.61F,.32F,.31F},{0,.91F,.21F,.41F,.38F},{0,1.10F,-.17F,.48F,.46F},{0,1.24F,-.39F,.28F,.27F}},SCALE);
        for(int side=-1;side<=1;side+=2){b.bone(side*.30F,1.18F,-.28F,side*.46F,1.08F,.02F,.20F,.18F,ARMOR);b.bone(side*.46F,1.08F,.02F,side*.36F,.96F,.29F,.18F,.12F,0x627F99);for(int i=0;i<3;i++)b.gem(side*(.37F-i*.035F),1.01F-i*.025F,.26F+i*.18F,.12F,.18F,.05F,ARMOR);}
        for(int i=0;i<8;i++){float z=-.30F+i*.15F,y=.74F-i*.018F;b.box(0,y,z,.44F-i*.027F,.065F,.13F,0xC7C3AB);b.bone(0,1.48F-i*.06F,z,0,1.66F-i*.07F,z+.1F,.06F,.001F,0xB9C9C8);}return b.build();}
    private static Mesh dragonNeck(){Builder b=new Builder();b.tube(new float[][]{{0,0,0,.27F,.24F},{0,.19F,-.09F,.25F,.22F},{0,.43F,-.27F,.21F,.19F},{0,.57F,-.54F,.22F,.19F}},SCALE);for(int side=-1;side<=1;side+=2)for(int i=0;i<3;i++)b.gem(side*(.20F-i*.012F),.12F+i*.17F,-.08F-i*.17F,.11F,.13F,.045F,ARMOR);for(int i=0;i<4;i++)b.box(0,.1F+i*.13F,-.22F-i*.13F,.26F-i*.025F,.055F,.10F,0xC7C3AB);return b.build();}
    private static Mesh dragonHead(){Builder b=new Builder();b.tube(new float[][]{{0,.10F,.16F,.22F,.20F},{0,.13F,-.12F,.35F,.25F},{0,.02F,-.35F,.30F,.19F},{0,-.08F,-.76F,.23F,.13F},{0,-.06F,-.88F,.18F,.10F}},SCALE);
        for(int side=-1;side<=1;side+=2){b.gem(side*.26F,.02F,-.14F,.16F,.22F,.10F,ARMOR);b.bone(side*.23F,.24F,-.03F,side*.37F,.51F,.13F,.09F,.06F,IVORY);b.bone(side*.37F,.51F,.13F,side*.47F,.53F,.42F,.06F,.002F,IVORY);b.bone(side*.31F,.04F,.08F,side*.49F,.16F,.31F,.09F,.001F,ARMOR);b.box(side*.30F,.095F,-.36F,.06F,.12F,.24F,0x152B42);b.box(side*.338F,.104F,-.38F,.018F,.047F,.15F,0xC6F7EB);b.bone(side*.31F,.195F,-.47F,side*.32F,.23F,-.23F,.055F,.07F,ARMOR);b.box(side*.13F,-.015F,-.88F,.07F,.05F,.026F,0x162F45);for(int i=0;i<4;i++)b.bone(side*.20F,-.16F,-.37F-i*.11F,side*.20F,-.24F,-.37F-i*.11F,.025F,.001F,IVORY);}
        b.bone(0,.29F,-.13F,0,.38F,-.4F,.11F,.045F,ARMOR);return b.build();}
    private static Mesh dragonJaw(){Builder b=new Builder();b.tube(new float[][]{{0,-.04F,.06F,.18F,.07F},{0,-.06F,-.32F,.21F,.075F},{0,-.06F,-.78F,.17F,.05F}},0x8CA4AD);b.box(0,.013F,-.44F,.30F,.018F,.59F,0x294154);for(int side=-1;side<=1;side+=2)for(int i=0;i<4;i++)b.bone(side*.17F,.02F,-.22F-i*.13F,side*.17F,.08F,-.22F-i*.13F,.018F,.001F,IVORY);return b.build();}
    private static Mesh dragonThigh(){Builder b=new Builder();b.bone(0,0,0,.21F,-.38F,.18F,.20F,.14F,SCALE);b.gem(.10F,-.10F,-.11F,.18F,.22F,.09F,ARMOR);return b.build();}
    private static Mesh dragonShin(){Builder b=new Builder();b.bone(0,0,0,-.04F,-.34F,-.12F,.11F,.07F,SCALE);b.box(-.02F,-.13F,-.09F,.15F,.18F,.11F,0x708CA1);return b.build();}
    private static Mesh dragonFoot(){Builder b=new Builder();b.box(0,-.015F,-.035F,.23F,.14F,.32F,SCALE);for(int i=-1;i<=1;i++){b.bone(i*.083F,-.02F,-.12F,i*.09F,-.035F,-.30F,.037F,.025F,SCALE);b.bone(i*.09F,-.035F,-.30F,i*.1F,-.07F,-.43F,.028F,.001F,IVORY);}return b.build();}
    private static Mesh dragonWingRoot(){Builder b=new Builder();b.bone(0,0,0,.35F,.29F,.22F,.12F,.09F,ARMOR);b.bone(.35F,.29F,.22F,.77F,.45F,.08F,.09F,.065F,ARMOR);b.bone(.35F,.29F,.22F,.25F,.46F,.29F,.07F,.001F,IVORY);return b.build();}
    private static Mesh dragonWingHand(){Builder b=new Builder();for(float[] f:FINGERS){float mx=f[0]*.62F,my=.14F+f[1]*.55F,mz=f[2]*.45F;b.bone(0,0,0,mx,my,mz,.053F,.035F,ARMOR);b.bone(mx,my,mz,f[0],f[1],f[2],.035F,.008F,0xACC0CC);}b.bone(0,0,0,.04F,.35F,-.2F,.075F,.001F,IVORY);return b.build();}
    private static Mesh[] dragonWingPanels(){Mesh[] result=new Mesh[4];for(int p=0;p<4;p++){Builder b=new Builder();float[] a=FINGERS[p],c=FINGERS[p+1];for(int i=0;i<5;i++)for(int j=0;j<5;j++){float u=i/5F,w=j/5F,un=(i+1F)/5F,wn=(j+1F)/5F;float[] aa=wingPoint(a,c,u,w),ab=wingPoint(a,c,un,w),ba=wingPoint(a,c,u,wn),bb=wingPoint(a,c,un,wn);b.face(aa,ab,bb,ba,Builder.shade(MEMBRANE,.84F+.13F*w));b.face(offset(ba,.023F),offset(bb,.023F),offset(ab,.023F),offset(aa,.023F),Builder.shade(MEMBRANE,.72F));}
        for(int i=0;i<8;i++){float[] a0=wingPoint(a,c,i/8F,1),a1=wingPoint(a,c,(i+1F)/8F,1);b.bone(a0[0],a0[1],a0[2],a1[0],a1[1],a1[2],.013F,.013F,0xB7C6CD);}result[p]=b.build();}return result;}
    private static float[] wingPoint(float[] a,float[] b,float u,float w){float scallop=1-.30F*sin(u*PI),x=(a[0]+(b[0]-a[0])*u)*w*scallop,z=(a[2]+(b[2]-a[2])*u)*w*scallop;return new float[]{x,(a[1]+(b[1]-a[1])*u)*w-.12F*sin(u*PI)*sin(w*PI),z};}
    private static float[] offset(float[] p,float y){return new float[]{p[0],p[1]+y,p[2]};}
    private static Mesh[] dragonTail(){Mesh[] result=new Mesh[12];for(int i=0;i<result.length;i++){Builder b=new Builder();float a=.19F*(1-i/12F),c=.19F*(1-(i+1F)/12F)+.004F;b.bone(0,0,0,0,0,.22F,a,c,SCALE);if(i%2==0)b.bone(0,a*.7F,.08F,0,a+.13F*(1-i/12F),.19F,.04F,.001F,IVORY);if(i==11)b.profile(new float[]{-.04F,-.16F,0,.16F,.04F,0},new float[]{0,.10F,.35F,.10F,0,-.05F},.06F,ARMOR,0,0,.12F);result[i]=b.build();}return result;}
    private static Mesh fragment(){Builder b=new Builder();b.gem(0,0,0,.075F,.13F,.07F,GOLD);return b.build();}
    private static void motes(PoseStack s,VertexConsumer v,float t,int quality,float intensity,float alpha,int rgb,float radius,float height){int count=Math.round((8+quality*6)*intensity);for(int i=0;i<count;i++){float q=(i*.618F+t*.55F)%1,a=i*2.39996F+t*11;OrionKillGeometry.ellipsoid(s.last(),v,(float)Math.cos(a)*radius*q,q*height,sin(a)*radius*q,.018F,.023F,.018F,argb(rgb,alpha*sin(q*PI)*.65F),5);}}
    private static void draw(Mesh mesh,PoseStack s,VertexConsumer v,float alpha){if(alpha<=.002F)return;for(int i=0;i<mesh.colors.length;i++){int off=i*3;v.addVertex(s.last(),mesh.xyz[off],mesh.xyz[off+1],mesh.xyz[off+2]).setColor(argb(mesh.colors[i],alpha));}}
    private static float envelope(float t,float in,float out){return smooth(t/in)*(1-smooth((t-out)/(1-out)));}
    private static int argb(int rgb,float a){return Math.round(clamp(a)*255)<<24|rgb&0xFFFFFF;}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static float smooth(float x){x=clamp(x);return x*x*(3-2*x);}
    private static float sin(float x){return (float)Math.sin(x);}
    private record Mesh(float[] xyz,int[] colors) { }
    /** Скульптурный конструктор используется только при загрузке моделей. */
    private static final class Builder {
        final List<Float> xyz=new ArrayList<>();final List<Integer> colors=new ArrayList<>();
        void vertex(float x,float y,float z,int c){xyz.add(x);xyz.add(y);xyz.add(z);colors.add(c);}
        void quad(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,int c){vertex(ax,ay,az,c);vertex(bx,by,bz,c);vertex(cx,cy,cz,c);vertex(dx,dy,dz,c);}
        void face(float[] a,float[] b,float[] c,float[] d,int color){quad(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],d[0],d[1],d[2],color);}
        static int shade(int c,float f){return Math.min(255,Math.round((c>>16&255)*f))<<16|Math.min(255,Math.round((c>>8&255)*f))<<8|Math.min(255,Math.round((c&255)*f));}
        void box(float x,float y,float z,float sx,float sy,float sz,int c){
            float a=x-sx/2,b=x+sx/2,d=y-sy/2,e=y+sy/2,f=z-sz/2,g=z+sz/2;
            quad(a,d,f,b,d,f,b,e,f,a,e,f,shade(c,.97F));quad(b,d,g,a,d,g,a,e,g,b,e,g,shade(c,.72F));
            quad(a,d,g,a,d,f,a,e,f,a,e,g,shade(c,.64F));quad(b,d,f,b,d,g,b,e,g,b,e,f,shade(c,.83F));
            quad(a,e,f,b,e,f,b,e,g,a,e,g,c);quad(a,d,g,b,d,g,b,d,f,a,d,f,shade(c,.6F));
        }
        void gem(float x,float y,float z,float rx,float ry,float depth,int c){
            quad(x,y+ry,z,x+rx,y,z,x,y,z-depth,x,y,z-depth,c);quad(x+rx,y,z,x,y-ry,z,x,y,z-depth,x,y,z-depth,shade(c,.8F));
            quad(x,y-ry,z,x-rx,y,z,x,y,z-depth,x,y,z-depth,shade(c,.65F));quad(x-rx,y,z,x,y+ry,z,x,y,z-depth,x,y,z-depth,shade(c,.9F));quad(x-rx,y,z,x,y-ry,z,x+rx,y,z,x,y+ry,z,shade(c,.7F));
        }
        void profile(float[] x,float[] y,float depth,int c,float ox,float oy,float oz){
            float cx=0,cy=0;for(int i=0;i<x.length;i++){cx+=x[i];cy+=y[i];}cx/=x.length;cy/=y.length;
            for(int i=0;i<x.length;i++){int j=(i+1)%x.length;
                quad(ox+cx,oy+cy,oz-depth/2,ox+x[i],oy+y[i],oz-depth/2,ox+x[j],oy+y[j],oz-depth/2,ox+cx,oy+cy,oz-depth/2,shade(c,.95F));
                quad(ox+cx,oy+cy,oz+depth/2,ox+x[j],oy+y[j],oz+depth/2,ox+x[i],oy+y[i],oz+depth/2,ox+cx,oy+cy,oz+depth/2,shade(c,.7F));
                quad(ox+x[i],oy+y[i],oz-depth/2,ox+x[i],oy+y[i],oz+depth/2,ox+x[j],oy+y[j],oz+depth/2,ox+x[j],oy+y[j],oz-depth/2,shade(c,.78F));}
        }
        void bone(float ax,float ay,float az,float bx,float by,float bz,float ra,float rb,int c){
            float dx=bx-ax,dy=by-ay,dz=bz-az,len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(len<.00001F)return;
            dx/=len;dy/=len;dz/=len;float ux=Math.abs(dy)<.9F?-dz:dy,uy=Math.abs(dy)<.9F?0:-dx,uz=Math.abs(dy)<.9F?dx:0;
            float ul=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=ul;uy/=ul;uz/=ul;float vx=dy*uz-dz*uy,vy=dz*ux-dx*uz,vz=dx*uy-dy*ux;
            for(int i=0;i<8;i++){
                float a=PI*(.125F+i*.25F),b=PI*(.125F+(i+1)*.25F);
                float qx=ux*(float)Math.cos(a)+vx*sin(a),qy=uy*(float)Math.cos(a)+vy*sin(a),qz=uz*(float)Math.cos(a)+vz*sin(a);
                float rx=ux*(float)Math.cos(b)+vx*sin(b),ry=uy*(float)Math.cos(b)+vy*sin(b),rz=uz*(float)Math.cos(b)+vz*sin(b);
                int face=shade(c,.8F+.12F*qy+.08F*qx);
                quad(ax+qx*ra,ay+qy*ra,az+qz*ra,bx+qx*rb,by+qy*rb,bz+qz*rb,bx+rx*rb,by+ry*rb,bz+rz*rb,ax+rx*ra,ay+ry*ra,az+rz*ra,face);
                quad(ax,ay,az,ax+rx*ra,ay+ry*ra,az+rz*ra,ax+qx*ra,ay+qy*ra,az+qz*ra,ax,ay,az,shade(c,.7F));
                quad(bx,by,bz,bx+qx*rb,by+qy*rb,bz+qz*rb,bx+rx*rb,by+ry*rb,bz+rz*rb,bx,by,bz,c);
            }
        }
        void tube(float[][] path,int c){
            // Кольца используют общую рамку, чтобы в местах изгиба не было трещин.
            float[][][] rings=new float[path.length][8][3];
            for(int j=0;j<path.length;j++){
                float[] a=path[Math.max(0,j-1)],b=path[Math.min(path.length-1,j+1)],p=path[j];
                float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                dx/=len;dy/=len;dz/=len;
                float ux=1-dx*dx,uy=-dx*dy,uz=-dx*dz,ul=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=ul;uy/=ul;uz/=ul;
                float vx=dy*uz-dz*uy,vy=dz*ux-dx*uz,vz=dx*uy-dy*ux;
                for(int i=0;i<8;i++){float q=PI*(.125F+i*.25F),ca=(float)Math.cos(q),sa=sin(q);rings[j][i]=new float[]{p[0]+ux*ca*p[3]+vx*sa*p[4],p[1]+uy*ca*p[3]+vy*sa*p[4],p[2]+uz*ca*p[3]+vz*sa*p[4]};}
            }
            for(int j=0;j<path.length-1;j++)for(int i=0;i<8;i++){int next=(i+1)%8;face(rings[j][i],rings[j+1][i],rings[j+1][next],rings[j][next],shade(c,.78F+.12F*sin(PI*(.125F+i*.25F))));}
            for(int end=0;end<2;end++){int j=end==0?0:path.length-1;float[] center={path[j][0],path[j][1],path[j][2]};for(int i=0;i<8;i++)face(center,rings[j][i],rings[j][(i+1)%8],center,shade(c,end==0?.7F:.9F));}
        }
        void ribbon(float[][] path,float thickness,int c){
            for(int i=0;i<path.length-1;i++){float[] a=path[i],b=path[i+1];
                quad(a[0]-a[3],a[1],a[2],a[0],a[1],a[2]-.04F,b[0],b[1],b[2]-.04F,b[0]-b[3],b[1],b[2],shade(c,.86F));
                quad(a[0],a[1],a[2]-.04F,a[0]+a[3],a[1],a[2],b[0]+b[3],b[1],b[2],b[0],b[1],b[2]-.04F,c);
                quad(a[0]+a[3],a[1],a[2]+thickness,a[0]-a[3],a[1],a[2]+thickness,b[0]-b[3],b[1],b[2]+thickness,b[0]+b[3],b[1],b[2]+thickness,shade(c,.72F));
                quad(a[0]-a[3],a[1],a[2],b[0]-b[3],b[1],b[2],b[0]-b[3],b[1],b[2]+thickness,a[0]-a[3],a[1],a[2]+thickness,shade(c,.7F));
                quad(a[0]+a[3],a[1],a[2]+thickness,b[0]+b[3],b[1],b[2]+thickness,b[0]+b[3],b[1],b[2],a[0]+a[3],a[1],a[2],shade(c,.8F));}
        }
        Mesh build(){float[] data=new float[xyz.size()];int[] tint=new int[colors.size()];for(int i=0;i<data.length;i++)data[i]=xyz.get(i);for(int i=0;i<tint.length;i++)tint[i]=colors.get(i);return new Mesh(data,tint);}
    }
}
