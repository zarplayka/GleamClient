package ru.orion.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.effect.OrionScreenMotion;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryAnimatedInputMixin {
    @ModifyVariable(method={"mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z","mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z","mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"},at=@At("HEAD"),argsOnly=true)
    private MouseButtonEvent orion$matchOpeningAnimation(MouseButtonEvent event){
        Screen screen=(Screen)(Object)this;OrionScreenMotion motion=(OrionScreenMotion)screen;float scale=motion.orion$frameScale();
        if(scale>=.999F)return event;
        float cx=motion.orion$inventoryCenterX(),cy=motion.orion$inventoryCenterY();
        return new MouseButtonEvent(cx+(event.x()-cx)/scale,cy+(event.y()-cy)/scale,event.buttonInfo());
    }
}
