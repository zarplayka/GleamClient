package ru.orion.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionScreenMotion;

@Mixin(Screen.class)
public abstract class InventoryOpenAnimationMixin implements OrionScreenMotion {
    @Unique private long orion$openedAt;
    @Unique private float orion$currentFrameScale = 1F;
    @Unique private boolean orion$insideAnimatedBackground;
    @Inject(method="added()V",at=@At("HEAD"))
    private void orion$startOpening(CallbackInfo callback) { orion$openedAt=System.nanoTime(); }
    @Override public float orion$inventoryScale() {
        var config=OrionClient.getConfigOrNull();
        if(config==null || !config.isAnimationsEnabled() || !((Object)this instanceof AbstractContainerScreen<?>) || orion$openedAt==0)return 1F;
        float t=Math.max(0F,Math.min(1F,(System.nanoTime()-orion$openedAt)/(config.getAnimationsDuration()*1_000_000F)));
        float eased=1F-(1F-t)*(1F-t)*(1F-t);
        return .88F+.12F*eased;
    }
    @Override public float orion$frameScale() { return orion$currentFrameScale; }
    @Override public float orion$inventoryCenterX() {
        if (!((Object)this instanceof AbstractContainerScreen<?>)) return ((Screen)(Object)this).width/2F;
        var geometry=(OrionContainerGeometryAccessor)(Object)this;
        return geometry.orion$getLeftPos()+geometry.orion$getImageWidth()/2F;
    }
    @Override public float orion$inventoryCenterY() {
        if (!((Object)this instanceof AbstractContainerScreen<?>)) return ((Screen)(Object)this).height/2F;
        var geometry=(OrionContainerGeometryAccessor)(Object)this;
        return geometry.orion$getTopPos()+geometry.orion$getImageHeight()/2F;
    }
    @Redirect(method="extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void orion$inventoryBackground(Screen screen,GuiGraphicsExtractor context,int mx,int my,float delta) {
        float scale=orion$inventoryScale();orion$currentFrameScale=scale;
        if(scale>=.999F){screen.extractBackground(context,mx,my,delta);return;}
        var pose=context.pose();pose.pushMatrix();orion$applyScale(context,scale);
        orion$insideAnimatedBackground=true;
        try { screen.extractBackground(context,orion$unscaleX(mx,scale),orion$unscaleY(my,scale),delta); }
        finally { orion$insideAnimatedBackground=false;pose.popMatrix(); }
    }
    @Inject(method="extractTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",at=@At("HEAD"))
    private void orion$fixedDarknessStart(GuiGraphicsExtractor context,CallbackInfo callback){orion$unscaleFullScreenStart(context);}
    @Inject(method="extractTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",at=@At("RETURN"))
    private void orion$fixedDarknessEnd(GuiGraphicsExtractor context,CallbackInfo callback){orion$unscaleFullScreenEnd(context);}
    @Inject(method="extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",at=@At("HEAD"))
    private void orion$fixedMenuStart(GuiGraphicsExtractor context,CallbackInfo callback){orion$unscaleFullScreenStart(context);}
    @Inject(method="extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",at=@At("RETURN"))
    private void orion$fixedMenuEnd(GuiGraphicsExtractor context,CallbackInfo callback){orion$unscaleFullScreenEnd(context);}
    @Inject(method="extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V",at=@At("HEAD"))
    private void orion$fixedPanoramaStart(GuiGraphicsExtractor context,float delta,CallbackInfo callback){orion$unscaleFullScreenStart(context);}
    @Inject(method="extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V",at=@At("RETURN"))
    private void orion$fixedPanoramaEnd(GuiGraphicsExtractor context,float delta,CallbackInfo callback){orion$unscaleFullScreenEnd(context);}
    @Unique private void orion$unscaleFullScreenStart(GuiGraphicsExtractor context){
        if(orion$insideAnimatedBackground){context.pose().pushMatrix();orion$applyScale(context,1F/orion$currentFrameScale);}
    }
    @Unique private void orion$unscaleFullScreenEnd(GuiGraphicsExtractor context){if(orion$insideAnimatedBackground)context.pose().popMatrix();}
    @Unique private void orion$applyScale(GuiGraphicsExtractor context,float scale){
        var pose=context.pose();float x=orion$inventoryCenterX(),y=orion$inventoryCenterY();
        pose.translate(x,y);pose.scale(scale,scale);pose.translate(-x,-y);
    }
    @Unique private int orion$unscaleX(int x,float scale){float center=orion$inventoryCenterX();return Math.round(center+(x-center)/scale);}
    @Unique private int orion$unscaleY(int y,float scale){float center=orion$inventoryCenterY();return Math.round(center+(y-center)/scale);}
}
