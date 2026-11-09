import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import ru.orion.client.effect.kill.OrionArcaneKillModels;
import ru.orion.client.effect.kill.OrionSoulKillModels;
import ru.orion.client.effect.kill.OrionKillMaterial;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/** Запускается без игрового окна, но использует настоящие классы Minecraft/JOML. */
public final class OrionKillGeometryTest {
    public static void main(String[] args) throws Exception {
        for (int type = 0; type < 9; type++) {
            if (type == 7) continue;
            int maximum = 0;
            long animationSignature = 0;
            for (int quality = 0; quality < 3; quality++) {
                for (int frame = 0; frame <= 50; frame++) {
                    PoseStack stack = new PoseStack();
                    Probe probe = new Probe();
                    render(type, stack, probe, duration(type) * frame / 50F, quality);
                    check(stack.isEmpty(), "Leaked pose stack, type " + type);
                    check(stack.last().pose().equals(new Matrix4f()), "Changed caller transform, type " + type);
                    check(probe.vertices % 4 == 0, "Incomplete quad, type " + type);
                    check(probe.vertices < 20_000, "Unbounded geometry, type " + type);
                    maximum = Math.max(maximum, probe.vertices);
                    animationSignature ^= Double.doubleToLongBits(probe.sumX + probe.sumY * 3 + probe.sumZ * 7);
                    if (frame == 18) {
                        check(probe.visible > 64, "Missing visible model, type " + type);
                        check(probe.maxX - probe.minX > .2F && probe.maxY - probe.minY > .2F, "Missing effect extent, type " + type);
                        if (type == 2 || type == 4) check(probe.maxZ - probe.minZ > .15F, "Flat main model, type " + type);
                        else check(probe.maxZ - probe.minZ < .08F, "Pixel effect became a volume, type " + type);
                    }
                }
            }
            check(animationSignature != 0, "Missing animated geometry, type " + type);
            System.out.println("Effect " + type + ": 153 frames checked, maximum vertices=" + maximum);
        }
        BufferedImage materials=ImageIO.read(Path.of("src/main/resources/assets/orion-client/textures/kill/materials.png").toFile());
        check(materials!=null&&materials.getWidth()==128&&materials.getHeight()==128,"Unreadable material atlas");
        Probe centered=new Probe();
        VertexConsumer centeredMaterial=OrionKillMaterial.textured(centered,4);
        centeredMaterial.addVertex(-.5F,-.5F,0).setColor(0xFF222222);
        centeredMaterial.addVertex(.5F,-.5F,0).setColor(0xFF222222);
        centeredMaterial.addVertex(.5F,.5F,0).setColor(0xFF222222);
        centeredMaterial.addVertex(-.5F,.5F,0).setColor(0xFF222222);
        check(centered.maxU-centered.minU>.001F&&centered.maxV-centered.minV>.001F,"Centered material quad collapsed to a line");
        for(int type=0;type<9;type++) {
            if (type == 7) continue;
            for(float phase:new float[]{.08F,.3F,.6F,.9F}) {
            float age = duration(type) * phase;
            PoseStack transformed=new PoseStack();transformed.translate(10,-8,5);transformed.scale(.7F,1.3F,1.1F);
            Probe textured=new Probe(),plain=new Probe(),localTexture=new Probe();
            textured.materialTiles=true;localTexture.materialTiles=true;
            render(type,transformed,OrionKillMaterial.textured(textured,type),age,2);
            render(type,transformed,plain,age,2);
            render(type,new PoseStack(),OrionKillMaterial.textured(localTexture,type),age,2);
            check(textured.vertices==plain.vertices&&textured.uvs==textured.vertices,"Missing material UV or buffered vertices, type "+type);
            check(Math.abs(textured.sumX-plain.sumX)<textured.vertices*.0001
                    &&Math.abs(textured.sumY-plain.sumY)<textured.vertices*.0001
                    &&Math.abs(textured.sumZ-plain.sumZ)<textured.vertices*.0001,"Material wrapper changed transforms");
            check(Math.abs(textured.sumU-localTexture.sumU)<.0001&&Math.abs(textured.sumV-localTexture.sumV)<.0001,
                    "Material UV moved with world transform, type "+type);
        }
        }
        System.out.println("PASS: two 3D effects, six planar effects, finite bounded geometry, quality levels and material UVs");
        if(args.length==2 && args[0].equals("--preview")) preview(Path.of(args[1]));
    }
    private static void render(int type, PoseStack s, VertexConsumer v, float seconds, int quality) {
        if (type == 2 || type == 4) OrionArcaneKillModels.render(type, s, v, seconds, quality, 1, 123);
        else OrionSoulKillModels.render(type, s, v, seconds, quality, 1, 123);
    }
    private static float duration(int type) {
        return type == 2 || type == 4 ? OrionArcaneKillModels.duration(type) : OrionSoulKillModels.duration(type);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private record Face(float[] xyz,int color) { }
    private static void preview(Path output) throws Exception {
        BufferedImage image=new BufferedImage(1260,1080,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x111924));g.fillRect(0,0,image.getWidth(),image.getHeight());
        String[] names={"Soul Bloom","Critical Burst","Sword Rain","Ender Rift","Black Hole","Sculk Echo","Frost Sigil","","Cherry Storm"};
        for(int type=0;type<9;type++) {
            if (type == 7) continue;
            Probe probe=new Probe();probe.faces=new ArrayList<>();
            render(type,new PoseStack(),probe,duration(type) * .36F,2);
            float left=Float.POSITIVE_INFINITY,right=Float.NEGATIVE_INFINITY,top=Float.POSITIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
            for(Face face:probe.faces)if((face.color>>>24)>5)for(int i=0;i<12;i+=3){
                float u=projectX(face.xyz[i],face.xyz[i+2]),v=projectY(face.xyz[i],face.xyz[i+1],face.xyz[i+2]);
                left=Math.min(left,u);right=Math.max(right,u);top=Math.min(top,v);bottom=Math.max(bottom,v);
            }
            float scale=Math.min(345F/(right-left),270F/(bottom-top));
            float ox=(type%3)*420+210-(left+right)*.5F*scale,oy=(type/3)*360+178-(top+bottom)*.5F*scale;
            probe.faces.sort(Comparator.comparingDouble(f->{float depth=0;for(int i=0;i<12;i+=3)depth+=f.xyz[i]*.57F-f.xyz[i+2]*.82F+f.xyz[i+1]*.3F;return depth;}));
            for(Face face:probe.faces) {
                if((face.color>>>24)<4)continue;
                Path2D.Float polygon=new Path2D.Float();
                for(int i=0;i<12;i+=3){float x=ox+projectX(face.xyz[i],face.xyz[i+2])*scale,y=oy+projectY(face.xyz[i],face.xyz[i+1],face.xyz[i+2])*scale;if(i==0)polygon.moveTo(x,y);else polygon.lineTo(x,y);}
                polygon.closePath();g.setColor(new Color(face.color,true));g.fill(polygon);
            }
            g.setFont(new Font("Segoe UI",Font.PLAIN,18));g.setColor(new Color(0xDFEAF5));g.drawString(names[type],type%3*420+24,type/3*360+340);
        }
        g.dispose();Files.createDirectories(output.toAbsolutePath().getParent());ImageIO.write(image,"png",output.toFile());
        System.out.println("CPU silhouette contact sheet: "+output.toAbsolutePath());
    }
    private static float projectX(float x,float z){return x*.82F+z*.57F;}
    private static float projectY(float x,float y,float z){return -y*.9F+z*.41F-x*.29F;}
    private static final class Probe implements VertexConsumer {
        List<Face> faces;
        float[] faceVertices=new float[12];
        int vertices, visible, uvs;
        boolean materialTiles;
        int uvTile;
        float x, y, z;
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        double sumX,sumY,sumZ;
        double sumU,sumV;
        float minU=Float.POSITIVE_INFINITY,minV=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        public VertexConsumer addVertex(float x,float y,float z) {
            check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z), "Nonfinite vertex");
            check(Math.abs(x)<50&&Math.abs(y)<50&&Math.abs(z)<50,"Exploding model coordinates");
            this.x=x;this.y=y;this.z=z;vertices++;sumX+=x;sumY+=y;sumZ+=z;return this;
        }
        public VertexConsumer setColor(int color) {return setColor(color>>16&255,color>>8&255,color&255,color>>>24);}
        public VertexConsumer setColor(int r,int g,int b,int a) {
            if(a>0){visible++;minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
                maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
            if(faces!=null){int offset=(vertices-1)%4*3;faceVertices[offset]=x;faceVertices[offset+1]=y;faceVertices[offset+2]=z;
                if(vertices%4==0){faces.add(new Face(faceVertices,a<<24|r<<16|g<<8|b));faceVertices=new float[12];}}
            return this;
        }
        public VertexConsumer setUv(float u,float v) {
            check(Float.isFinite(u)&&Float.isFinite(v)&&u>=0&&u<=1&&v>=0&&v<=1,"Invalid texture UV");
            if(materialTiles){
                int tile=(int)(u*4)+(int)(v*4)*4;
                if((vertices-1)%4==0)uvTile=tile;
                else check(tile==uvTile,"Material quad crosses atlas tiles");
            }
            minU=Math.min(minU,u);minV=Math.min(minV,v);maxU=Math.max(maxU,u);maxV=Math.max(maxV,v);
            sumU+=u;sumV+=v;uvs++;return this;
        }
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setUv3(float u,float v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
        public VertexConsumer setLineWidth(float width){return this;}
    }
}
