package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Общие объёмные формы с направленным освещением, без собственных GPU-ресурсов. */
public final class OrionKillGeometry {
    private static final Map<float[], int[]> PROFILE_TRIANGLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final float[][] SIN = new float[65][], COS = new float[65][];
    static {
        for (int n = 3; n <= 64; n++) {
            SIN[n] = new float[n + 1]; COS[n] = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                SIN[n][i] = (float)Math.sin(i * Math.PI * 2 / n);
                COS[n][i] = (float)Math.cos(i * Math.PI * 2 / n);
            }
        }
    }
    private OrionKillGeometry() { }
    /** Замкнутый профиль с фасками; контур задаётся в XY, толщина идёт вдоль Z. */
    public static void prism(PoseStack.Pose p, VertexConsumer v, float[] x, float[] y,
                             float depth, float bevel, int color) {
        if (x.length != y.length || x.length < 3) throw new IllegalArgumentException("Invalid mesh outline");
        float cx=0,cy=0;
        for(int i=0;i<x.length;i++){cx+=x[i];cy+=y[i];}
        cx/=x.length;cy/=y.length;
        float inset=Math.max(.5F,1-bevel*2),front=depth/2,shoulder=Math.max(0,front-bevel);
        int[] triangles=PROFILE_TRIANGLES.get(x);
        if(triangles==null){triangles=triangulate(x,y);PROFILE_TRIANGLES.put(x,triangles);}
        for(int i=0;i<triangles.length;i+=3){
            int a=triangles[i],b=triangles[i+1],c=triangles[i+2];
            float ax=cx+(x[a]-cx)*inset,ay=cy+(y[a]-cy)*inset;
            float bx=cx+(x[b]-cx)*inset,by=cy+(y[b]-cy)*inset;
            float dx=cx+(x[c]-cx)*inset,dy=cy+(y[c]-cy)*inset;
            quad(p,v,ax,ay,-front,bx,by,-front,dx,dy,-front,dx,dy,-front,shade(color,.88F));
            quad(p,v,dx,dy,front,bx,by,front,ax,ay,front,ax,ay,front,shade(color,.72F));
        }
        for(int i=0;i<x.length;i++) {
            int j=(i+1)%x.length;
            float ax=cx+(x[i]-cx)*inset,ay=cy+(y[i]-cy)*inset;
            float bx=cx+(x[j]-cx)*inset,by=cy+(y[j]-cy)*inset;
            float dx=x[j]-x[i],dy=y[j]-y[i],length=(float)Math.hypot(dx,dy);
            float light=length<.0001F?1:.72F+.23F*(dx/length*.5F+.5F);
            quad(p,v,x[i],y[i],-shoulder,x[j],y[j],-shoulder,x[j],y[j],shoulder,x[i],y[i],shoulder,shade(color,light*.82F));
            quad(p,v,ax,ay,-front,x[i],y[i],-shoulder,x[j],y[j],-shoulder,bx,by,-front,shade(color,light*1.19F));
            quad(p,v,bx,by,front,x[j],y[j],shoulder,x[i],y[i],shoulder,ax,ay,front,shade(color,light));
        }
    }
    /** Ear clipping сохраняет вогнутые вырезы силуэта, например промежутки между руками тотема. */
    private static int[] triangulate(float[] x,float[] y){
        int n=x.length;int[] remaining=new int[n],out=new int[(n-2)*3];float area=0;
        for(int i=0;i<n;i++){remaining[i]=i;int j=(i+1)%n;area+=x[i]*y[j]-x[j]*y[i];}
        float direction=area>=0?1:-1;int size=n,used=0;
        while(size>3){
            boolean clipped=false;
            for(int k=0;k<size;k++){
                int a=remaining[(k+size-1)%size],b=remaining[k],c=remaining[(k+1)%size];
                if(cross(x[a],y[a],x[b],y[b],x[c],y[c])*direction<.0000001F)continue;
                boolean occupied=false;
                for(int q=0;q<size;q++){
                    int point=remaining[q];if(point==a||point==b||point==c)continue;
                    float e0=cross(x[a],y[a],x[b],y[b],x[point],y[point])*direction;
                    float e1=cross(x[b],y[b],x[c],y[c],x[point],y[point])*direction;
                    float e2=cross(x[c],y[c],x[a],y[a],x[point],y[point])*direction;
                    if(e0>=-.0000001F&&e1>=-.0000001F&&e2>=-.0000001F){occupied=true;break;}
                }
                if(occupied)continue;
                out[used++]=a;out[used++]=b;out[used++]=c;
                System.arraycopy(remaining,k+1,remaining,k,size-k-1);size--;clipped=true;break;
            }
            if(!clipped){
                // Коллинеарные точки не меняют контур и могут быть безопасно пропущены.
                for(int k=0;k<size;k++){
                    int a=remaining[(k+size-1)%size],b=remaining[k],c=remaining[(k+1)%size];
                    if(Math.abs(cross(x[a],y[a],x[b],y[b],x[c],y[c]))<.000001F){
                        System.arraycopy(remaining,k+1,remaining,k,size-k-1);size--;clipped=true;break;
                    }
                }
                if(!clipped)throw new IllegalArgumentException("Self-intersecting mesh outline");
            }
        }
        if(size==3){out[used++]=remaining[0];out[used++]=remaining[1];out[used++]=remaining[2];}
        return used==out.length?out:Arrays.copyOf(out,used);
    }
    private static float cross(float ax,float ay,float bx,float by,float cx,float cy){return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);}
    public static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t*t*(3-2*t); }
    public static int alpha(int color, float alpha) {
        int a = color >>> 24;
        return Math.round(a * Math.max(0, Math.min(1, alpha))) << 24 | color & 0xFFFFFF;
    }
    public static int shade(int color, float brightness) {
        return color & 0xFF000000 | Math.min(255, Math.round((color >> 16 & 255)*brightness)) << 16
                | Math.min(255, Math.round((color >> 8 & 255)*brightness)) << 8
                | Math.min(255, Math.round((color & 255)*brightness));
    }
    public static void quad(PoseStack.Pose p, VertexConsumer v,
            float ax,float ay,float az,float bx,float by,float bz,
            float cx,float cy,float cz,float dx,float dy,float dz,int color) {
        v.addVertex(p, ax,ay,az).setColor(color); v.addVertex(p,bx,by,bz).setColor(color);
        v.addVertex(p, cx,cy,cz).setColor(color); v.addVertex(p,dx,dy,dz).setColor(color);
    }
    public static void box(PoseStack.Pose p, VertexConsumer v,float x,float y,float z,float sx,float sy,float sz,int color) {
        float a=x-sx/2,b=x+sx/2,c=y-sy/2,d=y+sy/2,e=z-sz/2,f=z+sz/2;
        quad(p,v,a,c,e,b,c,e,b,d,e,a,d,e,shade(color,.83F));
        quad(p,v,b,c,f,a,c,f,a,d,f,b,d,f,shade(color,.72F));
        quad(p,v,a,c,f,a,c,e,a,d,e,a,d,f,shade(color,.64F));
        quad(p,v,b,c,e,b,c,f,b,d,f,b,d,e,shade(color,.92F));
        quad(p,v,a,d,e,b,d,e,b,d,f,a,d,f,color);
        quad(p,v,a,c,f,b,c,f,b,c,e,a,c,e,shade(color,.52F));
    }
    public static void ellipsoid(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float rx,float ry,float rz,int color,int segments) {
        int n=Math.max(4,Math.min(64,segments)), bands=Math.max(4,n/2);
        for(int j=0;j<bands;j++) {
            double a=-Math.PI/2+j*Math.PI/bands,b=-Math.PI/2+(j+1)*Math.PI/bands;
            float ca=(float)Math.cos(a), cb=(float)Math.cos(b), sa=(float)Math.sin(a),sb=(float)Math.sin(b);
            for(int i=0;i<n;i++) {
                float light=.76F+.17F*sb+.09F*COS[n][i];
                quad(p,v,x+rx*ca*COS[n][i],y+ry*sa,z+rz*ca*SIN[n][i],
                        x+rx*ca*COS[n][i+1],y+ry*sa,z+rz*ca*SIN[n][i+1],
                        x+rx*cb*COS[n][i+1],y+ry*sb,z+rz*cb*SIN[n][i+1],
                        x+rx*cb*COS[n][i],y+ry*sb,z+rz*cb*SIN[n][i],shade(color,light));
            }
        }
    }
    public static void crystal(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float radius,float height,int color,int sides) {
        int n=Math.max(3,Math.min(64,sides));
        float lower=y+height*.24F,upper=y+height*.69F;
        for(int i=0;i<n;i++) {
            float ax=x+radius*COS[n][i], az=z+radius*SIN[n][i];
            float bx=x+radius*COS[n][i+1],bz=z+radius*SIN[n][i+1];
            int face=shade(color,.69F+.25F*(COS[n][i]*.5F+.5F));
            quad(p,v,x,y,z,bx,lower,bz,ax,lower,az,x,y,z,shade(face,.72F));
            quad(p,v,ax,lower,az,bx,lower,bz,bx,upper,bz,ax,upper,az,face);
            quad(p,v,ax,upper,az,bx,upper,bz,x,y+height,z,x,y+height,z,shade(face,1.10F));
        }
    }
    public static void beam(PoseStack.Pose p,VertexConsumer v,float ax,float ay,float az,float bx,float by,float bz,float radius,int color,int sides) {
        float dx=bx-ax,dy=by-ay,dz=bz-az,length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<.00001F || radius<=0) return;
        dx/=length;dy/=length;dz/=length;
        float ux,uy,uz;
        if(Math.abs(dy)<.9F){ux=-dz;uy=0;uz=dx;}else{ux=dy;uy=-dx;uz=0;}
        float ulen=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=ulen;uy/=ulen;uz/=ulen;
        float vx=dy*uz-dz*uy,vy=dz*ux-dx*uz,vz=dx*uy-dy*ux;
        int n=Math.max(3,Math.min(64,sides));
        for(int i=0;i<n;i++) {
            float qx=(ux*COS[n][i]+vx*SIN[n][i])*radius,qy=(uy*COS[n][i]+vy*SIN[n][i])*radius,qz=(uz*COS[n][i]+vz*SIN[n][i])*radius;
            float rx=(ux*COS[n][i+1]+vx*SIN[n][i+1])*radius,ry=(uy*COS[n][i+1]+vy*SIN[n][i+1])*radius,rz=(uz*COS[n][i+1]+vz*SIN[n][i+1])*radius;
            int face=shade(color,.75F+.22F*(COS[n][i]*.5F+.5F));
            quad(p,v,ax+qx,ay+qy,az+qz,bx+qx,by+qy,bz+qz,bx+rx,by+ry,bz+rz,ax+rx,ay+ry,az+rz,face);
            quad(p,v,ax,ay,az,ax+rx,ay+ry,az+rz,ax+qx,ay+qy,az+qz,ax,ay,az,shade(face,.8F));
            quad(p,v,bx,by,bz,bx+qx,by+qy,bz+qz,bx+rx,by+ry,bz+rz,bx,by,bz,face);
        }
    }
    public static void ring(PoseStack.Pose p,VertexConsumer v,float radius,float tube,int color,int segments) {
        int n=Math.max(6,Math.min(64,segments)),m=6;
        for(int i=0;i<n;i++)for(int j=0;j<m;j++) {
            float r0=radius+tube*COS[m][j],r1=radius+tube*COS[m][j+1];
            quad(p,v,r0*COS[n][i],tube*SIN[m][j],r0*SIN[n][i],
                    r0*COS[n][i+1],tube*SIN[m][j],r0*SIN[n][i+1],
                    r1*COS[n][i+1],tube*SIN[m][j+1],r1*SIN[n][i+1],
                    r1*COS[n][i],tube*SIN[m][j+1],r1*SIN[n][i],shade(color,.82F+.16F*SIN[m][j]));
        }
    }
}
