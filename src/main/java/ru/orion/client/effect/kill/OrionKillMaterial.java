package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Собственная палитра материалов: UV сохраняют привязку к модели при движении и повороте. */
public final class OrionKillMaterial {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("orion-client", "textures/kill/materials.png");
    private static final int ATLAS_SIZE = 128, TILE_SIZE = 32;
    private OrionKillMaterial() { }

    public static VertexConsumer textured(VertexConsumer delegate, int type) {
        return new TexturedVertices(delegate, type);
    }

    private static int material(int type,int argb) {
        int r=argb>>16&255,g=argb>>8&255,b=argb&255,max=Math.max(r,Math.max(g,b));
        return type == 4 && max < 55 ? 12 : 15;
    }

    private static final class TexturedVertices implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int type;
        private final float[] local=new float[12],world=new float[12];
        private final int[] colors=new int[4];
        private final float[] uvU=new float[4],uvV=new float[4];
        private final Vector3f transformed=new Vector3f();
        private int count;

        private TexturedVertices(VertexConsumer delegate,int type){this.delegate=delegate;this.type=type;}

        private VertexConsumer vertex(float x,float y,float z,Matrix4fc pose) {
            if(count>=4)throw new IllegalStateException("Kill mesh vertex has no color");
            int offset=count*3;local[offset]=x;local[offset+1]=y;local[offset+2]=z;
            if(pose!=null){pose.transformPosition(x,y,z,transformed);x=transformed.x;y=transformed.y;z=transformed.z;}
            world[offset]=x;world[offset+1]=y;world[offset+2]=z;count++;return this;
        }
        @Override public VertexConsumer addVertex(float x,float y,float z){return vertex(x,y,z,null);}
        @Override public VertexConsumer addVertex(PoseStack.Pose pose,float x,float y,float z){return vertex(x,y,z,pose.pose());}
        @Override public VertexConsumer addVertex(PoseStack.Pose pose,Vector3fc v){return vertex(v.x(),v.y(),v.z(),pose.pose());}
        @Override public VertexConsumer addVertex(Matrix4fc pose,float x,float y,float z){return vertex(x,y,z,pose);}
        @Override public VertexConsumer addVertex(Vector3fc v){return vertex(v.x(),v.y(),v.z(),null);}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor(a<<24|r<<16|g<<8|b);}
        @Override public VertexConsumer setColor(int color) {
            if(count<1)throw new IllegalStateException("Kill mesh color has no vertex");
            colors[count-1]=color;if(count==4)flush();return this;
        }
        private void flush() {
            float ax=local[3]-local[0],ay=local[4]-local[1],az=local[5]-local[2];
            float bx=local[6]-local[0],by=local[7]-local[1],bz=local[8]-local[2];
            float nx=Math.abs(ay*bz-az*by),ny=Math.abs(az*bx-ax*bz),nz=Math.abs(ax*by-ay*bx);
            int tile=material(type,colors[0]);
            float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++){
                int offset=i*3;
                uvU[i]=(nz>=nx&&nz>=ny?local[offset]:nx>=ny?local[offset+2]:local[offset])*10F;
                uvV[i]=(ny>nx&&ny>nz?local[offset+2]:local[offset+1])*10F;
                minU=Math.min(minU,uvU[i]);maxU=Math.max(maxU,uvU[i]);minV=Math.min(minV,uvV[i]);maxV=Math.max(maxV,uvV[i]);
            }
            float spanU=maxU-minU,spanV=maxV-minV;
            float scaleU=spanU>TILE_SIZE-2?(TILE_SIZE-2)/spanU:1,scaleV=spanV>TILE_SIZE-2?(TILE_SIZE-2)/spanV:1;
            float shiftU=(float)Math.floor(minU/TILE_SIZE)*TILE_SIZE,shiftV=(float)Math.floor(minV/TILE_SIZE)*TILE_SIZE;
            boolean centerU=spanU>TILE_SIZE-2||maxU-shiftU>TILE_SIZE-1||minU-shiftU<1;
            boolean centerV=spanV>TILE_SIZE-2||maxV-shiftV>TILE_SIZE-1||minV-shiftV<1;
            for(int i=0;i<4;i++) {
                int offset=i*3;
                // Знаковые координаты сохраняют развёртку симметричных граней; широкие грани вписываются целиком.
                float pixelU=centerU?1+(uvU[i]-minU)*scaleU:uvU[i]-shiftU;
                float pixelV=centerV?1+(uvV[i]-minV)*scaleV:uvV[i]-shiftV;
                if(tile==15){pixelU=15.5F;pixelV=15.5F;}
                float uvX=((tile%4)*TILE_SIZE+pixelU)/ATLAS_SIZE,uvY=((tile/4)*TILE_SIZE+pixelV)/ATLAS_SIZE;
                delegate.addVertex(world[offset],world[offset+1],world[offset+2]).setUv(uvX,uvY).setColor(colors[i]);
            }
            count=0;
        }
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setUv3(float u,float v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        @Override public VertexConsumer setLineWidth(float width){return this;}
    }
}
