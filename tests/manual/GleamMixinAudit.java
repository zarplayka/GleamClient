import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;

/** Проверяет реальные цели и точки INVOKE зарегистрированных миксинов без запуска игры. */
public final class GleamMixinAudit {
    static ZipFile minecraft;
    static ClassNode read(String name) throws Exception {
        var entry=minecraft.getEntry(name+".class"); if(entry==null)return null;
        var node=new ClassNode();new ClassReader(minecraft.getInputStream(entry)).accept(node,0);return node;
    }
    static Object value(AnnotationNode node,String key) {
        if(node.values==null)return null;for(int i=0;i<node.values.size();i+=2)if(key.equals(node.values.get(i)))return node.values.get(i+1);return null;
    }
    static List<?> list(Object value){return value instanceof List<?> l?l:value==null?List.of():List.of(value);}
    static List<AnnotationNode> annotations(MethodNode m){var a=new ArrayList<AnnotationNode>();if(m.visibleAnnotations!=null)a.addAll(m.visibleAnnotations);if(m.invisibleAnnotations!=null)a.addAll(m.invisibleAnnotations);return a;}
    static List<AnnotationNode> ats(AnnotationNode a){var out=new ArrayList<AnnotationNode>();if(a.desc.endsWith("/At;"))out.add(a);if(a.values!=null)for(var v:a.values)for(var x:list(v))if(x instanceof AnnotationNode n)out.addAll(ats(n));return out;}
    public static void main(String[] args)throws Exception {
        minecraft=new ZipFile(args[0]);Path classes=Path.of(args[1]);
        var config=JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();int failures=0,checks=0;
        for(var item:config.getAsJsonArray("client")) {
            String name=config.get("package").getAsString().replace('.','/')+"/"+item.getAsString();
            var mixin=new ClassNode();new ClassReader(Files.readAllBytes(classes.resolve(name+".class"))).accept(mixin,0);
            var annotations=new ArrayList<AnnotationNode>();if(mixin.invisibleAnnotations!=null)annotations.addAll(mixin.invisibleAnnotations);if(mixin.visibleAnnotations!=null)annotations.addAll(mixin.visibleAnnotations);
            for(var a:annotations)if(a.desc.endsWith("/Mixin;"))for(var type:list(value(a,"value"))) {
                var target=read(((Type)type).getInternalName());if(target==null)continue;
                for(var method:mixin.methods)for(var injection:annotations(method)) {
                    // Loader 0.19.3 поставляет MixinExtras 0.5.4: Redirect.at должен быть одиночной аннотацией.
                    // Компиляция против Mixin 0.17.4 незаметно меняет этот элемент на массив.
                    if (injection.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) {
                        checks++;
                        if (!(value(injection,"at") instanceof AnnotationNode)) {
                            System.out.println(item.getAsString()+" REDIRECT_AT incompatible with MixinExtras 0.5.4");
                            failures++;
                        }
                    }
                    Object selectors=value(injection,"method");if(selectors==null)continue;
                    var selected=new ArrayList<MethodNode>();
                    for(var selector:list(selectors)) {
                        String s=selector.toString();int paren=s.indexOf('(');String n=paren<0?s:s.substring(0,paren);String d=paren<0?null:s.substring(paren);
                        var matches=target.methods.stream().filter(m->m.name.equals(n)&&(d==null||m.desc.equals(d))).toList();checks++;
                        if(matches.isEmpty()){System.out.println(item.getAsString()+" METHOD "+s);failures++;}
                        if(d==null && matches.size()>1 && injection.desc.endsWith("/Inject;")) {
                            var parameters=Type.getArgumentTypes(method.desc);
                            int callback=0;while(callback<parameters.length && !parameters[callback].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo"))callback++;
                            if(callback>0) for(var match:matches) {
                                checks++;
                                if(!Arrays.equals(Arrays.copyOf(parameters,callback),Type.getArgumentTypes(match.desc))) {
                                    System.out.println(item.getAsString()+" OVERLOAD_SIGNATURE "+s+" requires an explicit descriptor");failures++;
                                }
                            }
                        }
                        selected.addAll(matches);
                    }
                    if(method.name.equals("orion$basketballHands") && selected.stream().anyMatch(targetMethod -> {
                        for(var insn:targetMethod.instructions) if(insn instanceof MethodInsnNode call && call.name.equals("renderAllFeatures")) return true;
                        return false;
                    })) {
                        int flush=-1,batch=-1,cancel=-1,index=0;
                        for(var insn:method.instructions) {
                            if(insn instanceof MethodInsnNode call) {
                                if(call.name.equals("renderAllFeatures"))flush=index;
                                if(call.name.equals("endBatch"))batch=index;
                                if(call.name.equals("cancel"))cancel=index;
                            }
                            index++;
                        }
                        checks++;
                        if(flush<0 || batch<flush || cancel<batch) {
                            System.out.println(item.getAsString()+" HANDS_FLUSH cancellation skips the vanilla render queue flush");failures++;
                        }
                    }
                    for(var at:ats(injection)) {
                        String ref=(String)value(at,"target");if(ref==null||!"INVOKE".equals(value(at,"value")))continue;
                        boolean found=false;for(var m:selected)for(var insn:m.instructions)if(insn instanceof MethodInsnNode call && ref.equals("L"+call.owner+";"+call.name+call.desc))found=true;
                        checks++;if(!found){System.out.println(item.getAsString()+" INVOKE "+ref);failures++;}
                        if (method.name.equals("orion$applyMirageBeforeHands")) for (var m:selected) {
                            int hook=-1,clear=-1,index=0;
                            for(var insn:m.instructions) {
                                if(insn instanceof MethodInsnNode call) {
                                    if(ref.equals("L"+call.owner+";"+call.name+call.desc))hook=index;
                                    if(clear<0 && call.name.equals("clearDepthTexture"))clear=index;
                                }
                                index++;
                            }
                            if(clear>=0) {
                                checks++;
                                boolean shiftedAfter=value(at,"shift") instanceof String[] shift && shift[1].equals("AFTER");
                                if(hook>clear || hook==clear && shiftedAfter) {
                                    System.out.println(item.getAsString()+" MIRAGE_DEPTH_CLEAR runs after the world depth was cleared");failures++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // Короткий submitModel принимает outlineColor, а не цвет текстуры: -1 даёт белую маску.
        var keeperPath=classes.resolve("ru/orion/client/football/OrionFootballRenderer.class");
        if(Files.isRegularFile(keeperPath)) {
            var keeper=new ClassNode();new ClassReader(Files.readAllBytes(keeperPath)).accept(keeper,0);
            for(var method:keeper.methods)if(method.name.equals("goal"))for(var insn:method.instructions)
                if(insn instanceof MethodInsnNode call && call.name.equals("submitModel")) {
                    var outline=insn.getPrevious();while(outline!=null && outline.getOpcode()<0)outline=outline.getPrevious();
                    if(outline!=null && outline.getOpcode()==Opcodes.ACONST_NULL) {
                        outline=outline.getPrevious();while(outline!=null && outline.getOpcode()<0)outline=outline.getPrevious();
                    }
                    checks++;
                    if(outline==null || outline.getOpcode()!=Opcodes.ICONST_0) {
                        System.out.println("OrionFootballRenderer KEEPER_OUTLINE must be zero, not a white silhouette");failures++;
                    }
                }
        }
        System.out.println("MIXIN_AUDIT "+checks+" checks, "+failures+" unresolved targets");
        if(failures>0)System.exit(1);
    }
}
