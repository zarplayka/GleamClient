package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import static ru.orion.client.effect.kill.OrionKillGeometry.*;

/** Отдельно спроектированные силуэты и объёмные детали пяти магических сцен. */
public final class OrionArcaneKillModels {
    private static final float PI=(float)Math.PI,TAU=PI*2;
    private static final float[] BLADE_X={0,.12F,.32F,.35F,.32F,.24F,.34F,.35F,.23F,.17F,-.17F,-.23F,-.35F,-.34F,-.24F,-.32F,-.35F,-.32F,-.12F};
    private static final float[] BLADE_Y={0,.28F,.65F,1.53F,1.66F,1.72F,1.89F,2.08F,2.17F,2.13F,2.13F,2.17F,2.08F,1.89F,1.72F,1.66F,1.53F,.65F,.28F};
    private static final float[] GUARD_X={.12F,.17F,.30F,.60F,.77F,.67F,.43F,.26F,.15F};
    private static final float[] GUARD_Y={2.14F,2.39F,2.53F,2.61F,2.84F,2.45F,2.32F,2.20F,2.08F};
    private static final float[] POMMEL_X={0,.12F,.15F,.09F,0,-.09F,-.15F,-.12F};
    private static final float[] POMMEL_Y={2.98F,3.06F,3.18F,3.29F,3.34F,3.29F,3.18F,3.06F};
    private static final float[] COFFIN_X={-.42F,.42F,.60F,.59F,.35F,-.35F,-.59F,-.60F};
    private static final float[] COFFIN_Y={-.94F,-.94F,-.48F,.54F,.94F,.94F,.54F,-.48F};
    private static final float[] SEAL_X={0,.16F,.27F,.16F,0,-.16F,-.27F,-.16F};
    private static final float[] SEAL_Y={-.33F,-.19F,0,.19F,.33F,.19F,0,-.19F};
    private static final float[][] LEFT_PYLON={{-.89F,.12F,.28F,.29F},{-.88F,.72F,.23F,.24F},{-.83F,1.54F,.19F,.21F},{-.69F,2.03F,.17F,.19F},{-.42F,2.42F,.15F,.17F},{-.07F,2.64F,.085F,.12F}};
    private static final float[][] RIGHT_PYLON={{.89F,.12F,.28F,.29F},{.88F,.72F,.23F,.24F},{.83F,1.54F,.19F,.21F},{.69F,2.03F,.17F,.19F},{.45F,2.36F,.15F,.17F}};
    private static final float[] SHELL_RADIUS={.27F,.73F,.81F,.75F,.58F,.06F},SHELL_HALF={.03F,.25F,.29F,.24F,.19F,.012F};
    private static final float[] ARCH_Y={.10F,.42F,.92F,1.48F,1.94F,2.30F,2.58F,2.83F};
    private static final float[] ARCH_X={1.28F,1.24F,1.19F,1.12F,.97F,.74F,.43F,.04F},ARCH_WIDTH={.20F,.24F,.18F,.17F,.16F,.14F,.11F,.015F};
    private OrionArcaneKillModels() { }
    public static void render(int type,PoseStack s,VertexConsumer v,float seconds,int quality,float particles,long seed){
        float t=Math.max(0,Math.min(1,seconds/5F));int detail=quality==0?8:quality==1?12:18;
        switch(type){case 1->grave(s,v,t,detail,particles,seed);case 2->swordRain(s,v,t,detail,particles,seed);case 3->monolith(s,v,t,detail,particles,seed);case 4->singularity(s,v,t,detail,particles,seed);case 6->prison(s,v,t,detail,particles,seed);default->throw new IllegalArgumentException("Unknown arcane effect: "+type);}
    }
    private static float sin(float x){return (float)Math.sin(x);}private static float cos(float x){return (float)Math.cos(x);}
    private static float fade(float t){return smooth(t/.09F)*(1-smooth((t-.85F)/.15F));}
    private static float random(long seed,int i){long n=seed+i*0x9E3779B97F4A7C15L;n=(n^(n>>>30))*0xBF58476D1CE4E5B9L;n=(n^(n>>>27))*0x94D049BB133111EBL;return ((n^(n>>>31))>>>40)/16777216F;}
    private static void grave(PoseStack s,VertexConsumer v,float t,int d,float particles,long seed){
        float a=fade(t),rise=smooth(t/.23F),breakup=Math.max(0,(t-.63F)/.37F);
        s.pushPose();s.translate(0,-2.7F*(1-rise),0);
        // Настоящий саркофаг: широкий корпус, уступы и парящая тяжёлая крышка перед порталом.
        s.pushPose();s.translate(0,.17F,.28F);s.rotate(Axis.XP.rotationDegrees(90));
        prism(s.last(),v,COFFIN_X,COFFIN_Y,.31F,.055F,alpha(0xFF505D72,a));s.popPose();
        s.pushPose();s.translate(breakup*.45F,.39F+breakup*.9F,.28F+breakup*.6F);s.rotate(Axis.ZP.rotationDegrees(breakup*38));s.rotate(Axis.XP.rotationDegrees(90));
        prism(s.last(),v,COFFIN_X,COFFIN_Y,.17F,.055F,alpha(0xFF89929C,a));
        for(int edge=0;edge<COFFIN_X.length;edge++){
            int next=(edge+1)%COFFIN_X.length;
            beam(s.last(),v,COFFIN_X[edge]*.92F,COFFIN_Y[edge]*.94F,-.097F,COFFIN_X[next]*.92F,COFFIN_Y[next]*.94F,-.097F,.018F,alpha(0xFFB8C5CB,a),5);
        }
        s.scale(.82F,.85F,1);s.translate(0,0,-.10F);prism(s.last(),v,COFFIN_X,COFFIN_Y,.035F,.025F,alpha(0xFF394858,a));s.popPose();
        // Четыре рельефных замка на боках саркофага.
        for(int side=-1;side<=1;side+=2)for(int k=0;k<2;k++){
            s.pushPose();s.translate(side*.58F,.22F,.05F+k*.52F);s.rotate(Axis.YP.rotationDegrees(90));s.scale(.31F,.40F,.65F);prism(s.last(),v,SEAL_X,SEAL_Y,.07F,.025F,alpha(0xFFBCA976,a));s.popPose();
        }
        for(int side=-1;side<=1;side+=2){
            s.pushPose();s.translate(side*breakup*1.5F,breakup*.5F-breakup*breakup, -.68F);
            s.rotate(Axis.ZP.rotationDegrees(-side*breakup*42));
            pylon(s.last(),v,side<0?LEFT_PYLON:RIGHT_PYLON,alpha(0xFF728298,a),0);
            for(int k=0;k<3;k++){
                float y=.45F+k*.53F,x=side*(.88F-k*.015F);
                beam(s.last(),v,x-.14F,y,-.02F,x+.14F,y,-.02F,.028F,alpha(0xFFB1BBC1,a),5);
            }
            s.popPose();
        }
        // Асимметричный разлом наверху подчёркнут двумя сдвинутыми клиньями камня.
        s.pushPose();s.translate(.20F+breakup*.7F,2.50F+breakup*.8F,-.68F);s.rotate(Axis.ZP.rotationDegrees(-23+breakup*120));
        crystal(s.last(),v,0,-.13F,0,.12F,.35F,alpha(0xFFA7B6BE,a),4);s.popPose();
        // Оригинальный объёмный погребальный рельеф внутри портала: маска, скула и ленты.
        if(breakup<.25F){
            beam(s.last(),v,-.055F,2.55F,-.68F,0,2.18F,-.69F,.029F,alpha(0xFF9BABAE,a),5);
            beam(s.last(),v,.40F,2.34F,-.68F,.14F,2.12F,-.69F,.022F,alpha(0xFF81959D,a),5);
            s.pushPose();s.translate(0,1.84F,-.69F);float enlarge=1-breakup*2;s.scale(enlarge,enlarge,enlarge);
            s.pushPose();s.translate(0,-.17F,.13F);s.scale(1.42F,1.70F,.8F);prism(s.last(),v,SEAL_X,SEAL_Y,.10F,.04F,alpha(0xFF3C526A,a));s.popPose();
            crystal(s.last(),v,0,-.38F,0,.24F,.71F,alpha(0xFFC1CABF,a),5);
            for(int side=-1;side<=1;side+=2){
                beam(s.last(),v,side*.035F,.05F,-.19F,side*.15F,.08F,-.17F,.024F,alpha(0xFF243F46,a),4);
                beam(s.last(),v,side*.09F,-.10F,-.18F,side*.06F,-.24F,-.15F,.027F,alpha(0xFF7D9190,a),4);
                beam(s.last(),v,side*.25F,-.32F,-.04F,-side*.25F,-.61F,-.04F,.037F,alpha(0xFFB6BDB3,a),5);
                ellipsoid(s.last(),v,side*.25F,-.32F,-.04F,.047F,.047F,.039F,alpha(0xFFBDC6BA,a),6);
                ellipsoid(s.last(),v,side*.25F,-.61F,-.04F,.047F,.047F,.039F,alpha(0xFFBDC6BA,a),6);
            }
            beam(s.last(),v,0,.02F,-.23F,0,-.17F,-.23F,.018F,alpha(0xFF527E78,a),4);s.popPose();
        }
        float crack=smooth((t-.33F)/.27F);
        for(int side=-1;side<=1;side+=2)for(int k=0;k<4;k++){
            float x=side*(.86F-.04F*k),y=.45F+k*.37F,z=-.96F;
            beam(s.last(),v,x,y,z,x-side*.07F,y+.20F,z,.012F,alpha(0xFF9DE7DA,a*crack),4);
        }
        if(breakup>0)shards(s,v,breakup,a,seed,0xFF7B8A99,10,d,.45F,1.4F);
        s.popPose();dust(s,v,t,particles,seed,d,t<.3F?t/.3F:breakup);
    }
    private static void pylon(PoseStack.Pose p,VertexConsumer v,float[][] rows,int color,float z){
        // Лофт восьмиугольных сечений образует изогнутую цельную каменную арку.
        for(int j=0;j<rows.length-1;j++)for(int k=0;k<8;k++){
            float[] a=rows[j],b=rows[j+1];float u=k*TAU/8,w=(k+1)*TAU/8;
            quad(p,v,a[0]+cos(u)*a[2],a[1],z+sin(u)*a[3],a[0]+cos(w)*a[2],a[1],z+sin(w)*a[3],b[0]+cos(w)*b[2],b[1],z+sin(w)*b[3],b[0]+cos(u)*b[2],b[1],z+sin(u)*b[3],shade(color,.69F+.28F*(cos(u+.5F)*.5F+.5F)));
        }
        for(int end=0;end<2;end++){float[] a=rows[end==0?0:rows.length-1];for(int k=0;k<8;k++){float u=k*TAU/8,w=(k+1)*TAU/8;quad(p,v,a[0],a[1],z,a[0]+cos(u)*a[2],a[1],z+sin(u)*a[3],a[0]+cos(w)*a[2],a[1],z+sin(w)*a[3],a[0],a[1],z,color);}}
    }
    private static void swordRain(PoseStack s,VertexConsumer v,float t,int d,float particles,long seed){
        for(int i=0;i<7;i++){
            float delay=i==0?0:.07F+i*.043F,local=t-delay;if(local<0)continue;
            float fall=smooth(local/.18F),r=i==0?0:1.72F+(i%2)*.19F,angle=(i-1)*TAU/6+.20F;
            float a=smooth(local/.035F)*(1-smooth((t-.83F)/.17F));
            float impact=Math.max(0,(local-.18F)/.14F),settle=impact<1?sin(impact*PI)*.045F:0;
            s.pushPose();s.translate(cos(angle)*r,5.7F*(1-fall)-.18F,sin(angle)*r);
            s.rotate(Axis.YP.rotationDegrees(i==0?0:angle*57.29578F+90));s.rotate(Axis.ZP.rotationDegrees((i==0?-4:(i%2==0?8:-11))+settle*160));
            float size=i==0?1.10F:.72F+(i%3)*.035F;s.scale(size,size,size);astralSword(s,v,a,d,t,i);
            if(fall<1)beam(s.last(),v,0,1.2F,0,0,3.7F+(1-fall),0,.035F,alpha(0xFFB6DAF1,a*(1-fall)*.50F),5);
            s.popPose();
            if(impact>0&&impact<1){s.pushPose();s.translate(cos(angle)*r,.03F,sin(angle)*r);ring(s.last(),v,.13F+impact*.9F,.025F,alpha(0xFFC5E6EF,(1-impact)*.70F),d*2);
                for(int j=0,n=(int)(particles*(d+3));j<n;j++){float theta=random(seed+i*20,j)*TAU,rr=impact*(.45F+random(seed,j+4));beam(s.last(),v,cos(theta)*rr,sin(impact*PI)*.3F,sin(theta)*rr,cos(theta)*(rr+.08F),sin(impact*PI)*.3F+.07F,sin(theta)*(rr+.08F),.012F,alpha(0xFFFFDDB0,1-impact),4);}s.popPose();}
        }
    }
    private static void astralSword(PoseStack s,VertexConsumer v,float a,int d,float t,int variant){
        int steel=alpha(0xFFB2C9D9,a),dark=alpha(0xFF263B5B,a),brass=alpha(0xFFC7AA69,a),glow=alpha(0xFFA6F4EB,a);
        prism(s.last(),v,BLADE_X,BLADE_Y,.19F,.085F,steel);
        // Центральное ребро с двумя спусками и тёмным долом: клинок объёмен с любого ракурса.
        for(int side=-1;side<=1;side+=2){float z=side*.106F;
            quad(s.last(),v,-.060F,.47F,z,.060F,.47F,z,.09F,1.98F,z,-.09F,1.98F,z,dark);
            quad(s.last(),v,-.26F,.72F,side*.08F,-.12F,.60F,side*.112F,-.13F,1.66F,side*.112F,-.29F,1.54F,side*.08F,alpha(0xFFE4F1F3,a));
            for(int i=0;i<5;i++)sigil(s,v,0,.76F+i*.21F,z+side*.005F,.039F,glow);
        }
        // Раздвоенные поднятые крылья гарды формируют открытое гнездо парящего сердца.
        for(int side=-1;side<=1;side+=2){s.pushPose();s.scale(side,1,1);prism(s.last(),v,GUARD_X,GUARD_Y,.23F,.048F,brass);
            quad(s.last(),v,.18F,2.16F,-.129F,.27F,2.26F,-.129F,.64F,2.48F,-.129F,.44F,2.31F,-.129F,alpha(0xFF77624B,a));
            beam(s.last(),v,.26F,2.21F,-.135F,.62F,2.51F,-.135F,.019F,dark,5);
            crystal(s.last(),v,.67F,2.47F,0,.067F,.25F,glow,4);s.popPose();}
        float bob=.025F*sin(t*40+variant);
        crystal(s.last(),v,0,2.10F+bob,0,.125F,.48F,alpha(0xFF84E5DD,a),5);
        for(int side=-1;side<=1;side+=2){beam(s.last(),v,side*.16F,2.28F,0,side*.10F,2.62F,0,.027F,dark,5);}
        beam(s.last(),v,0,2.60F,0,0,3.04F,0,.083F,dark,8);
        for(int k=0;k<7;k++){s.pushPose();s.translate(0,2.64F+k*.055F,0);s.rotate(Axis.ZP.rotationDegrees(-12));ring(s.last(),v,.085F,.011F,brass,8);s.popPose();}
        prism(s.last(),v,POMMEL_X,POMMEL_Y,.19F,.035F,brass);
        crystal(s.last(),v,0,3.08F,-.11F,.055F,.15F,glow,4);
    }
    private static void monolith(PoseStack s,VertexConsumer v,float t,int d,float particles,long seed){
        float a=fade(t),gather=1-smooth(t/.26F),burst=Math.max(0,(t-.56F)/.44F),charge=smooth((t-.28F)/.25F);
        s.pushPose();s.rotate(Axis.YP.rotationDegrees(t*20));
        // Шесть точно спроектированных секторов: от внешней оболочки к резному сердцу.
        for(int side=0;side<6;side++){
            float angle=side*TAU/6,separation=gather*1.9F+burst*(2.1F+random(seed,side)*.6F);
            s.pushPose();s.translate(cos(angle)*separation,(side%2==0?1:-1)*gather*.5F+burst*.5F-burst*burst*.8F,sin(angle)*separation);
            s.rotate(Axis.YP.rotationDegrees(-side*60));s.translate(.49F,0,0);
            s.rotate(Axis.ZP.rotationDegrees(burst*(side%2==0?100:-100)));s.translate(-.49F,0,0);
            shellBlade(s.last(),v,side,alpha(side==1||side==4?0xFF9BD8E5:side%2==0?0xFF4D719C:0xFF747BAF,a),charge);
            s.popPose();
        }
        if(burst<.16F){float visible=1-smooth(burst/.16F);
            crystal(s.last(),v,.075F,.22F,-.055F,.38F,2.70F,alpha(0xFFCBF7EF,a*visible),6);
            crystal(s.last(),v,.10F,.50F,-.07F,.19F,2.12F,alpha(0xFFEEFDF9,a*visible*.8F),5);
        }
        if(burst>0){shards(s,v,burst,a,seed,0xFF9CDDE5,d+5,d,.14F,1.6F);if(burst<.25F){s.pushPose();s.translate(0,1.4F,0);ring(s.last(),v,burst*7,.026F,alpha(0xFFE2EDFF,1-burst/.25F),d*2);s.popPose();}}
        s.popPose();motes(s,v,t,particles*.5F,seed,0xFFB6DFF5,d,1.55F);
    }
    private static void shellBlade(PoseStack.Pose p,VertexConsumer v,int side,int color,float charge){
        // Несимметричные плечи и скошенная вершина дают авторский контур, а не обычную призму.
        float top=side==0?3.12F:side==2?2.53F:side==4?2.86F:2.48F+(side%3)*.13F;
        float bottom=side==3?.49F:.12F+(side%2)*.18F;
        for(int j=0;j<5;j++){
            float x0=SHELL_RADIUS[j],x1=SHELL_RADIUS[j+1],z0=SHELL_HALF[j],z1=SHELL_HALF[j+1];
            float y0=shellY(j,bottom,top),y1=shellY(j+1,bottom,top);
            quad(p,v,x0,y0,-z0,x0+.07F,y0,0,x1+.07F,y1,0,x1,y1,-z1,shade(color,.95F));
            quad(p,v,x0+.07F,y0,0,x0,y0,z0,x1,y1,z1,x1+.07F,y1,0,shade(color,1.18F));
            quad(p,v,x0-.15F,y0,-z0*.80F,x0,y0,-z0,x1,y1,-z1,x1-.15F,y1,-z1*.8F,shade(color,.68F));
            quad(p,v,x0,y0,z0,x0-.15F,y0,z0*.8F,x1-.15F,y1,z1*.8F,x1,y1,z1,shade(color,.59F));
            quad(p,v,x0-.15F,y0,z0*.8F,x0-.15F,y0,-z0*.8F,x1-.15F,y1,-z1*.8F,x1-.15F,y1,z1*.8F,shade(color,.47F));
        }
        // Разрез оболочки выполнен как вдавленные тёмные плоскости и светлые внутренние рёбра.
        beam(p,v,.77F,.95F,-.05F,.76F,1.82F,-.05F,.014F,alpha(0xFFDAFBF3,(color>>>24)/255F*(.28F+charge*.65F)),4);
        beam(p,v,.72F,1.26F,.16F,.69F,1.57F,.15F,.012F,alpha(0xFFD8EFFB,(color>>>24)/255F*charge),4);
        for(int end=0;end<2;end++){
            int k=end==0?0:5;float x=SHELL_RADIUS[k],w=SHELL_HALF[k],y=shellY(k,bottom,top),cx=x-.03F;
            capTriangle(p,v,cx,y,0,x,y,-w,x+.07F,y,0,shade(color,.65F));
            capTriangle(p,v,cx,y,0,x+.07F,y,0,x,y,w,shade(color,.80F));
            capTriangle(p,v,cx,y,0,x,y,w,x-.15F,y,w*.8F,shade(color,.65F));
            capTriangle(p,v,cx,y,0,x-.15F,y,w*.8F,x-.15F,y,-w*.8F,shade(color,.55F));
            capTriangle(p,v,cx,y,0,x-.15F,y,-w*.8F,x,y,-w,shade(color,.65F));
        }
    }
    private static float shellY(int row,float bottom,float top){return switch(row){case 0->bottom;case 1->.58F;case 2->1.10F;case 3->2.10F;case 4->2.34F;default->top;};}
    private static void singularity(PoseStack s,VertexConsumer v,float t,int d,float particles,long seed){
        float a=fade(t),form=smooth(t/.18F),collapse=1-smooth((t-.76F)/.13F),size=form*collapse;
        s.pushPose();s.translate(0,1.50F,0);s.scale(size,size,size);
        ellipsoid(s.last(),v,0,0,0,.68F,.68F,.68F,alpha(0xFF060811,a),d*2);
        // Три пространственных сегмента фотонной линзы имеют собственную толщину и разрывы.
        for(int lobe=0;lobe<3;lobe++){
            s.pushPose();s.rotate(Axis.ZP.rotationDegrees(17));s.rotate(Axis.XP.rotationDegrees(-26));s.rotate(Axis.YP.rotationDegrees(t*180+lobe*120));
            for(int k=0,n=d*2;k<n;k++){
                float u=k/(float)n,w=(k+1)/(float)n,angle0=u*1.70F,angle1=w*1.70F;
                float r0=.72F+.05F*sin(u*PI),r1=.72F+.05F*sin(w*PI);
                beam(s.last(),v,cos(angle0)*r0,.14F*sin(u*PI),sin(angle0)*r0,cos(angle1)*r1,.14F*sin(w*PI),sin(angle1)*r1,.021F+.023F*sin(u*PI),alpha(0xFFE1D7FF,a),5);
            }s.popPose();
        }
        // Восемь узких турбулентных потоков: радиус и высота меняются вдоль каждой спирали.
        s.pushPose();s.rotate(Axis.ZP.rotationDegrees(15));s.rotate(Axis.XP.rotationDegrees(-21));
        for(int arm=0;arm<8;arm++)for(int k=0,n=d*3;k<n;k++){
            float u=k/(float)n,w=(k+1)/(float)n,angle=arm*TAU/8+t*(4.2F+arm*.08F);
            // Разрывы холодных внешних нитей дают ясное направление вращению.
            if(arm%3!=0&&u>.40F&&sin(u*23+arm*1.8F)<-.25F)continue;
            vortexSegment(s.last(),v,u,w,angle,arm,a);
        }s.popPose();
        for(int i=0,n=(int)(particles*d*2);i<n;i++){
            float phase=(t*.85F+random(seed,i))%1,angle=i*2.399963F+t*8+phase*6,r=.73F+(1-phase)*1.70F,y=(random(seed,i+10)-.5F)*(1-phase)*1.7F;
            beam(s.last(),v,cos(angle)*r,y,sin(angle)*r,cos(angle-.1F)*r,y+.025F,sin(angle-.1F)*r,.010F,alpha(0xFFB8A5E1,a*sin(phase*PI)*.65F),4);
        }s.popPose();
        float flash=(t-.88F)/.08F;if(flash>=0&&flash<1){s.pushPose();s.translate(0,1.5F,0);ring(s.last(),v,flash*2.5F,.025F,alpha(0xFFE9E1FF,1-flash),d*2);s.popPose();}
    }
    private static void vortexSegment(PoseStack.Pose p,VertexConsumer v,float u,float w,float phase,int arm,float alpha){
        float aa=phase+u*5.2F,bb=phase+w*5.2F,r0=.75F+u*1.40F,r1=.75F+w*1.40F;
        boolean primary=arm%3==0;
        float width0=(primary?.065F:.016F)+(primary?.10F:.035F)*sin(u*PI),width1=(primary?.065F:.016F)+(primary?.10F:.035F)*sin(w*PI);
        width0*=.78F+.22F*sin(u*20+arm);width1*=.78F+.22F*sin(w*20+arm);
        float y0=sin(aa*2.7F+arm)*(.055F+u*.16F),y1=sin(bb*2.7F+arm)*(.055F+w*.16F);
        float h0=primary?.055F+.055F*sin(u*PI):.012F,h1=primary?.055F+.055F*sin(w*PI):.012F;
        int color=u<.23F?0xFFF2E8FF:u<.61F?0xFFB69ADF:0xFF6D527F;color=OrionKillGeometry.alpha(color,alpha*(.95F-u*.62F));
        float ax=cos(aa)*(r0-width0),az=sin(aa)*(r0-width0),bx=cos(aa)*(r0+width0),bz=sin(aa)*(r0+width0),cx=cos(bb)*(r1+width1),cz=sin(bb)*(r1+width1),dx=cos(bb)*(r1-width1),dz=sin(bb)*(r1-width1);
        float mx=cos(aa)*r0,mz=sin(aa)*r0,nx=cos(bb)*r1,nz=sin(bb)*r1;
        // Треугольное сечение основных потоков даёт две освещённые грани и тёмное дно.
        quad(p,v,ax,y0-h0*.35F,az,mx,y0+h0,mz,nx,y1+h1,nz,dx,y1-h1*.35F,dz,shade(color,1.10F));
        quad(p,v,mx,y0+h0,mz,bx,y0-h0*.35F,bz,cx,y1-h1*.35F,cz,nx,y1+h1,nz,shade(color,.82F));
        quad(p,v,dx,y1-h1*.35F,dz,cx,y1-h1*.35F,cz,bx,y0-h0*.35F,bz,ax,y0-h0*.35F,az,shade(color,.57F));
    }
    private static void prison(PoseStack s,VertexConsumer v,float t,int d,float particles,long seed){
        float a=fade(t),burst=Math.max(0,(t-.62F)/.38F);
        for(int arch=0;arch<6;arch++){
            float angle=arch*TAU/6,grow=smooth((t-arch*.008F)/.23F);
            s.pushPose();s.rotate(Axis.YP.rotationDegrees(-arch*60));
            s.translate(burst*(1.5F+random(seed,arch)),burst*.7F-burst*burst*.5F,0);
            s.translate(.9F,1.2F,0);s.rotate(Axis.ZP.rotationDegrees(-burst*(55+arch*4)));s.translate(-.9F,-1.2F,0);
            s.scale(1,grow,1);
            crystalArch(s.last(),v,arch,alpha(arch%2==0?0xFF719FC9:0xFF8C9FCF,a),t,burst);
            // Широкие кристаллические корни врастают в общую шестиугольную печать.
            for(int foot=0;foot<3;foot++){
                s.pushPose();s.translate(1.22F,.02F,(foot-1)*.16F);s.rotate(Axis.ZP.rotationDegrees(foot==1?-10:foot==0?24:-30));
                crystal(s.last(),v,0,0,0,.14F,.55F+foot*.11F,alpha(0xFF9EC9E2,a),5);s.popPose();
            }
            if(burst==0){
                float next=TAU/6;
                beam(s.last(),v,1.26F,.12F,0,cos(next)*1.26F,.12F,sin(next)*1.26F,.075F,alpha(0xFF495C83,a),5);
                beam(s.last(),v,1.26F,.17F,0,cos(next)*1.26F,.17F,sin(next)*1.26F,.018F,alpha(0xFFB1E6EC,a),4);
            }
            s.popPose();
        }
        // Три крупные парящие огранённые печати замыкают центр между арками.
        if(burst==0)for(int seal=0;seal<3;seal++){
            float angle=seal*TAU/3+PI/6;
            s.pushPose();s.translate(cos(angle)*1.19F,1.15F,sin(angle)*1.19F);s.rotate(Axis.YP.rotationDegrees(-angle*57.29578F+90));
            prism(s.last(),v,SEAL_X,SEAL_Y,.11F,.045F,alpha(0xFF9DCBD5,a));s.translate(0,0,-.065F);s.scale(.67F,.67F,.8F);prism(s.last(),v,SEAL_X,SEAL_Y,.06F,.035F,alpha(0xFFCAEAF0,a));
            sigil(s,v,0,0,-.04F,.17F,alpha(0xFF6AAABC,a));s.popPose();
        }
        if(burst>0)shards(s,v,burst,a,seed,0xFFA5DBE8,d+10,d,.14F,1.3F);
        motes(s,v,t,particles*.45F,seed,0xFFB9DEF0,d,1.6F);
    }
    private static void crystalArch(PoseStack.Pose p,VertexConsumer v,int index,int color,float t,float burst){
        // Не прутья: шесть цельных, толстых, изогнутых хрустальных лезвий окружают полость.
        float offset=index%2==0?.035F:-.025F;
        for(int j=0;j<7;j++){
            float x0=ARCH_X[j]+offset,x1=ARCH_X[j+1]+offset,y0=ARCH_Y[j],y1=ARCH_Y[j+1],w0=ARCH_WIDTH[j],w1=ARCH_WIDTH[j+1];
            // Пятигранное лезвие: светлое наружное ребро и глубокая синяя внутренняя плоскость.
            quad(p,v,x0+w0*.65F,y0,-w0*.3F,x0+w0,y0,0,x1+w1,y1,0,x1+w1*.65F,y1,-w1*.3F,shade(color,1.17F));
            quad(p,v,x0+w0,y0,0,x0+w0*.65F,y0,w0*.3F,x1+w1*.65F,y1,w1*.3F,x1+w1,y1,0,shade(color,.98F));
            quad(p,v,x0+w0*.65F,y0,w0*.3F,x0-w0*.5F,y0,w0,x1-w1*.5F,y1,w1,x1+w1*.65F,y1,w1*.3F,shade(color,.73F));
            quad(p,v,x0-w0*.5F,y0,w0,x0-w0*.72F,y0,0,x1-w1*.72F,y1,0,x1-w1*.5F,y1,w1,shade(color,.52F));
            quad(p,v,x0-w0*.72F,y0,0,x0-w0*.5F,y0,-w0,x1-w1*.5F,y1,-w1,x1-w1*.72F,y1,0,shade(color,.63F));
            quad(p,v,x0-w0*.5F,y0,-w0,x0+w0*.65F,y0,-w0*.3F,x1+w1*.65F,y1,-w1*.3F,x1-w1*.5F,y1,-w1,shade(color,.86F));
            int glow=alpha(0xFFCFF9F5,(color>>>24)/255F*(.58F+.18F*sin(t*55+index)));
            beam(p,v,x0+w0+.007F,y0,0,x1+w1+.007F,y1,0,.010F,glow,4);
            if((j==1||j==5)&&burst==0){
                // Изогнутые кристальные связки идут по периметру, сохраняя пустой центр.
                for(int link=0;link<4;link++){
                    float u=link/4F,w=(link+1)/4F,aa=u*TAU/6,bb=w*TAU/6;
                    float ya=y0-.095F*sin(u*PI),yb=y0-.095F*sin(w*PI);
                    beam(p,v,cos(aa)*x0,ya,sin(aa)*x0,cos(bb)*x0,yb,sin(bb)*x0,j==1?.037F:.028F,alpha(0xFF91B5D3,(color>>>24)/255F*.90F),5);
                }
            }
        }
        for(int end=0;end<2;end++){
            int k=end==0?0:7;float x=ARCH_X[k]+offset,y=ARCH_Y[k],w=ARCH_WIDTH[k];
            capTriangle(p,v,x,y,0,x+w*.65F,y,-w*.3F,x+w,y,0,color);
            capTriangle(p,v,x,y,0,x+w,y,0,x+w*.65F,y,w*.3F,color);
            capTriangle(p,v,x,y,0,x+w*.65F,y,w*.3F,x-w*.5F,y,w,color);
            capTriangle(p,v,x,y,0,x-w*.5F,y,w,x-w*.72F,y,0,color);
            capTriangle(p,v,x,y,0,x-w*.72F,y,0,x-w*.5F,y,-w,color);
            capTriangle(p,v,x,y,0,x-w*.5F,y,-w,x+w*.65F,y,-w*.3F,color);
        }
    }
    private static void capTriangle(PoseStack.Pose p,VertexConsumer v,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,int color){quad(p,v,ax,ay,az,bx,by,bz,cx,cy,cz,cx,cy,cz,color);}
    private static void sigil(PoseStack s,VertexConsumer v,float x,float y,float z,float r,int color){
        beam(s.last(),v,x,y-r,z,x-r*.55F,y,z,.010F,color,4);beam(s.last(),v,x-r*.55F,y,z,x,y+r,z,.010F,color,4);beam(s.last(),v,x,y+r,z,x+r*.55F,y,z,.010F,color,4);beam(s.last(),v,x+r*.55F,y,z,x,y-r,z,.010F,color,4);
    }
    private static void shards(PoseStack s,VertexConsumer v,float burst,float a,long seed,int color,int count,int d,float radius,float height){
        for(int i=0;i<count;i++){float angle=i*2.399963F,speed=.8F+random(seed,i)*1.8F;
            s.pushPose();s.translate(cos(angle)*(.4F+burst*speed),height+random(seed,i+40)*.7F+burst*.5F-burst*burst*1.7F,sin(angle)*(.4F+burst*speed));s.rotate(Axis.XP.rotationDegrees(i*29+burst*210));s.rotate(Axis.YP.rotationDegrees(burst*160));crystal(s.last(),v,0,-radius,0,radius*(.45F+random(seed,i+70)*.7F),radius*2.7F,alpha(color,a),5);s.popPose();}
    }
    private static void dust(PoseStack s,VertexConsumer v,float t,float particles,long seed,int d,float phase){if(phase<0||phase>=1)return;for(int i=0,n=(int)(particles*(d+7));i<n;i++){float angle=random(seed,i)*TAU,r=.6F+phase*(.8F+random(seed,i+6)),size=.018F+random(seed,i+20)*.04F;crystal(s.last(),v,cos(angle)*r,.02F+sin(phase*PI)*.3F,sin(angle)*r,size,size*1.8F,alpha(0xFFAAA69A,(1-phase)*.65F),4);}}
    private static void motes(PoseStack s,VertexConsumer v,float t,float particles,long seed,int color,int d,float radius){for(int i=0,n=(int)(particles*d);i<n;i++){float phase=(random(seed,i)+t*.7F)%1,angle=i*2.399963F+t*2,r=radius*(.8F+random(seed,i+10)*.3F),size=.013F;crystal(s.last(),v,cos(angle)*r,phase*2.3F,sin(angle)*r,size,size*2,alpha(color,fade(t)*sin(phase*PI)*.6F),4);}}
}
