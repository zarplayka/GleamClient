package ru.orion.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionScreenMotion;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryAnimatedContentsMixin {
    @Unique private boolean orion$scaledContents;
    @Inject(method="extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",at=@At("HEAD"))
    private void orion$animateSlotsStart(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta,CallbackInfo callback){
        OrionScreenMotion motion=(OrionScreenMotion)(Object)this;float scale=motion.orion$frameScale();
        orion$scaledContents=scale<.999F;
        if(!orion$scaledContents)return;
        var pose=context.pose();pose.pushMatrix();float x=motion.orion$inventoryCenterX(),y=motion.orion$inventoryCenterY();
        pose.translate(x,y);pose.scale(scale,scale);pose.translate(-x,-y);
    }
    @Inject(method="extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",at=@At("RETURN"))
    private void orion$animateSlotsEnd(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta,CallbackInfo callback){
        if(orion$scaledContents){context.pose().popMatrix();orion$scaledContents=false;}
    }
    @ModifyVariable(method="extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private int orion$slotMouseX(int x){OrionScreenMotion motion=(OrionScreenMotion)(Object)this;float center=motion.orion$inventoryCenterX();return Math.round(center+(x-center)/motion.orion$frameScale());}
    @ModifyVariable(method="extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private int orion$slotMouseY(int y){OrionScreenMotion motion=(OrionScreenMotion)(Object)this;float center=motion.orion$inventoryCenterY();return Math.round(center+(y-center)/motion.orion$frameScale());}
}
