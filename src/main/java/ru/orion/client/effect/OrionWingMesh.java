package ru.orion.client.effect;

import java.util.ArrayList;
import java.util.List;

/** Геометрия заранее подготовлена в двух уровнях детализации. */
public final class OrionWingMesh {
    public record Point(float x, float y, float z) { }
    public record Quad(Point a, Point b, Point c, Point d, int color) { }
    private static final List<Quad> ANGEL = angel(true), ANGEL_LOD = angel(false);
    private static final List<Quad> DEMON = demon(true), DEMON_LOD = demon(false);
    private OrionWingMesh() { }
    public static List<Quad> forStyle(int style) { return forStyle(style, true); }
    public static List<Quad> forStyle(int style, boolean detail) { return style == 2 ? detail ? DEMON : DEMON_LOD : detail ? ANGEL : ANGEL_LOD; }

    private static List<Quad> angel(boolean detail) {
        List<Quad> out = new ArrayList<>();
        // Длинные маховые перья: изогнутый стержень, выпуклые опахала, толщина и бородки.
        for (int i = 14; i >= 0; i--) {
            float t = i / 14F;
            Point root = p(.13F + 1.63F*t, .14F + .57F*(float)Math.sin(t*1.5), .22F + .17F*t);
            Point tip = p(.43F + 1.94F*t, -1.10F + 1.78F*t, .32F + .32F*t);
            feather(out, root, tip, .115F + .025F*(1-t), i%3==0?0xE1E6F7:0xF4F2E9, detail, i);
        }
        // Два перекрывающихся ряда кроющих перьев.
        for (int row = detail?1:0; row >= 0; row--) for (int i = 17; i >= 0; i-=detail?1:2) {
            float t=i/17F;
            Point root=p(.1F+1.64F*t, .17F+.55F*(float)Math.sin(t*1.5)-row*.15F, .11F+row*.055F+.15F*t);
            Point tip=p(root.x+.20F+row*.07F, root.y-.40F-row*.15F,root.z+.09F);
            feather(out,root,tip,.077F+row*.025F,row==0?0xFFF8E4:0xE8ECFA,detail,i+row*5);
        }
        // Изящный металлический обод у плеча, а не сплошная полоса поверх перьев.
        tube(out,p(.02F,.04F,.06F),p(.28F,.24F,.12F),.045F,.032F,0xB78B43,8);
        tube(out,p(.28F,.24F,.12F),p(.63F,.42F,.17F),.031F,.012F,0xE1C383,8);
        gem(out,p(.12F,.13F,.015F),.075F,.12F,.06F,0xD7B570);
        gem(out,p(.12F,.13F,-.045F),.035F,.065F,.025F,0xF5EFFF);
        if(detail) for(int i=0;i<6;i++) gem(out,p(.17F+i*.055F,.21F+i*.031F,.07F),.015F,.024F,.012F,0xFFF4CE);
        return List.copyOf(out);
    }

    private static void feather(List<Quad> out,Point root,Point tip,float width,int color,boolean detail,int index) {
        int steps=detail?12:3;
        Point control=p(root.x+(tip.x-root.x)*.72F,root.y+(tip.y-root.y)*.28F,root.z-.08F);
        for(int j=0;j<steps;j++) {
            float a=j/(float)steps,b=(j+1)/(float)steps;
            Point[] ra=featherRow(root,control,tip,a,width), rb=featherRow(root,control,tip,b,width);
            quad(out,ra[0],rb[0],rb[1],ra[1],shade(color,.91F));
            quad(out,ra[1],rb[1],rb[2],ra[2],shade(color,1.02F));
            quad(out,z(ra[2],.025F),z(rb[2],.025F),z(rb[0],.025F),z(ra[0],.025F),shade(color,.72F));
            quad(out,ra[0],z(ra[0],.025F),z(rb[0],.025F),rb[0],shade(color,.66F));
            quad(out,rb[2],z(rb[2],.025F),z(ra[2],.025F),ra[2],shade(color,.78F));
        }
        Point old=z(root,-.012F);
        for(int i=1;detail && i<=steps;i++) {
            Point next=z(curve(root,control,tip,i/(float)steps),-.035F);
            tube(out,old,next,.006F,.004F,i%3==0?0xCEC4A7:shade(color,.82F),4);old=next;
        }
        if(!detail) ribbon(out,z(root,-.012F),z(tip,-.012F),.005F,shade(color,.84F));
        if(detail) for(int i=2;i<17;i++) {
            float t=i/19F;
            Point center=z(curve(root,control,tip,t-.025F),-.038F);
            Point[] edge=featherRow(root,control,tip,t+.035F,width*.91F);
            for(int side:new int[]{0,2}) ribbon(out,center,z(edge[side],-.006F),.0018F,shade(color,index%2==0?.83F:.88F));
        }
    }
    private static Point[] featherRow(Point a,Point c,Point b,float t,float width) {
        Point mid=curve(a,c,b,t);
        float dx=2*((1-t)*(c.x-a.x)+t*(b.x-c.x)),dy=2*((1-t)*(c.y-a.y)+t*(b.y-c.y));
        float len=(float)Math.hypot(dx,dy), w=width*(float)Math.pow(Math.sin(Math.PI*t),.72)*(1-.28F*t);
        float nx=-dy/len*w,ny=dx/len*w;
        return new Point[]{p(mid.x+nx,mid.y+ny,mid.z+.012F),z(mid,-.026F*(float)Math.sin(Math.PI*t)),p(mid.x-nx*.84F,mid.y-ny*.84F,mid.z+.012F)};
    }

    private static List<Quad> demon(boolean detail) {
        List<Quad> out=new ArrayList<>();
        Point root=p(.05F,.03F,.12F), elbow=p(.76F,.55F,.27F);
        Point[] fingers={p(2.42F,1.08F,.59F),p(2.17F,.03F,.66F),p(1.65F,-.63F,.56F),p(1.02F,-1.01F,.40F),p(.42F,-1.06F,.24F)};
        int around=detail?12:5,radial=detail?6:3;
        for(int panel=0;panel<fingers.length-1;panel++) {
            Point a=fingers[panel],b=fingers[panel+1];
            for(int i=0;i<around;i++) for(int j=0;j<radial;j++) {
                Point aa=membrane(elbow,a,b,i/(float)around,j/(float)radial), ab=membrane(elbow,a,b,(i+1)/(float)around,j/(float)radial);
                Point ba=membrane(elbow,a,b,i/(float)around,(j+1)/(float)radial),bb=membrane(elbow,a,b,(i+1)/(float)around,(j+1)/(float)radial);
                float light=.69F+.25F*(float)Math.sin(Math.PI*(i+.5F)/around)+.14F*j/radial;
                int color=shade(panel%2==0?0x852F46:0x742D43,light);
                quad(out,aa,ba,bb,ab,color);quad(out,z(ab,.025F),z(bb,.025F),z(ba,.025F),z(aa,.025F),shade(color,.77F));
            }
            if(detail) for(int vein=1;vein<=5;vein++) {
                float t=vein/6F;
                Point previous=membrane(elbow,a,b,t,.23F);
                for(int j=3;j<=9;j++) {Point next=membrane(elbow,a,b,t,j/10F);ribbon(out,z(previous,-.004F),z(next,-.004F),.004F,0x52233A);previous=next;}
            }
            // Тонкий кант подчёркивает глубокие вырезы между пальцами.
            for(int i=0;i<around;i++) tube(out,membrane(elbow,a,b,i/(float)around,1),membrane(elbow,a,b,(i+1)/(float)around,1),.012F,.010F,0x3D2436,5);
        }
        tube(out,root,elbow,.079F,.056F,0x36283B,10);
        for(int i=0;i<fingers.length;i++) {
            Point end=fingers[i];tube(out,elbow,end,.049F,.016F,0x422C43,detail?10:6);
            tube(out,z(elbow,-.05F),z(mix(elbow,end,.94F),-.026F),.009F,.003F,0xD56659,5);
            Point claw=p(end.x+.10F,end.y+.16F,end.z-.06F);
            tube(out,end,claw,.027F,0F,0xC0A396,8);
        }
        // Коготь на локте и шипы вдоль верхней кости.
        tube(out,elbow,p(.84F,.88F,.20F),.075F,.018F,0x6C4C56,10);
        tube(out,p(.84F,.88F,.20F),p(1.02F,.84F,.14F),.020F,0F,0xC5ABA0,8);
        for(int i=1;i<8;i++) {
            Point base=mix(elbow,fingers[0],i/9F);
            tube(out,base,p(base.x-.025F,base.y+.12F+(i%3)*.04F,base.z-.025F),.033F,0F,0x6D4650,7);
        }
        if(detail) for(int i=0;i<12;i++) {
            Point c=mix(root,elbow,(i+.5F)/13F);gem(out,z(c,-.07F),.024F,.042F,.025F,i%3==0?0xB85251:0x6D344A);
        }
        gem(out,p(.16F,.14F,.015F),.085F,.14F,.055F,0x662C45);
        gem(out,p(.16F,.14F,-.04F),.039F,.079F,.025F,0xEE8064);
        return List.copyOf(out);
    }
    private static Point membrane(Point joint,Point a,Point b,float t,float r) {
        Point edge=mix(a,b,t);
        float scallop=.34F*(float)Math.sin(Math.PI*t);
        edge=mix(edge,joint,scallop);
        Point point=mix(joint,edge,r);
        return z(point,.11F*(float)(Math.sin(Math.PI*t)*Math.sin(Math.PI*r)));
    }

    private static void tube(List<Quad> out,Point a,Point b,float ra,float rb,int color,int sides) {
        float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z,len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len<.00001F)return;
        float nx=-dy,ny=dx,nz=0,nlen=(float)Math.hypot(nx,ny);
        if(nlen<.0001F){nx=1;ny=0;nlen=1;}
        nx/=nlen;ny/=nlen;
        float bx=(dy*nz-dz*ny)/len,by=(dz*nx-dx*nz)/len,bz=(dx*ny-dy*nx)/len;
        for(int i=0;i<sides;i++) {
            double t0=2*Math.PI*i/sides,t1=2*Math.PI*(i+1)/sides;
            Point aa=offset(a,nx,ny,nz,bx,by,bz,ra,t0),ab=offset(a,nx,ny,nz,bx,by,bz,ra,t1);
            Point ba=offset(b,nx,ny,nz,bx,by,bz,rb,t0),bb=offset(b,nx,ny,nz,bx,by,bz,rb,t1);
            quad(out,aa,ba,bb,ab,shade(color,.72F+.26F*(float)((Math.cos(t0)+1)*.5)));
        }
    }
    private static Point offset(Point c,float nx,float ny,float nz,float bx,float by,float bz,float r,double angle){float ca=(float)Math.cos(angle)*r,sa=(float)Math.sin(angle)*r;return p(c.x+nx*ca+bx*sa,c.y+ny*ca+by*sa,c.z+nz*ca+bz*sa);}
    private static void ribbon(List<Quad> out,Point a,Point b,float width,int color){float dx=b.x-a.x,dy=b.y-a.y,l=(float)Math.hypot(dx,dy);if(l<.0001F)return;float nx=-dy/l*width,ny=dx/l*width;quad(out,p(a.x+nx,a.y+ny,a.z),p(b.x+nx,b.y+ny,b.z),p(b.x-nx,b.y-ny,b.z),p(a.x-nx,a.y-ny,a.z),color);}
    private static void gem(List<Quad> out,Point c,float x,float y,float depth,int color){Point top=p(c.x,c.y+y,c.z),right=p(c.x+x,c.y,c.z),bottom=p(c.x,c.y-y,c.z),left=p(c.x-x,c.y,c.z),peak=z(c,-depth);quad(out,top,right,peak,peak,shade(color,1.1F));quad(out,right,bottom,peak,peak,color);quad(out,bottom,left,peak,peak,shade(color,.7F));quad(out,left,top,peak,peak,shade(color,.88F));}
    private static Point curve(Point a,Point c,Point b,float t){float u=1-t;return p(u*u*a.x+2*u*t*c.x+t*t*b.x,u*u*a.y+2*u*t*c.y+t*t*b.y,u*u*a.z+2*u*t*c.z+t*t*b.z);}
    private static Point mix(Point a,Point b,float t){return p(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}
    private static Point z(Point p,float z){return p(p.x,p.y,p.z+z);}
    private static Point p(float x,float y,float z){return new Point(x,y,z);}
    private static void quad(List<Quad> out,Point a,Point b,Point c,Point d,int color){out.add(new Quad(a,b,c,d,color|0xFF000000));}
    private static int shade(int c,float m){return Math.min(255,Math.round((c>>16&255)*m))<<16|Math.min(255,Math.round((c>>8&255)*m))<<8|Math.min(255,Math.round((c&255)*m));}
}
