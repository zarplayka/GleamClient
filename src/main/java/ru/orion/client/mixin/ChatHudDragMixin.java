package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.hud.OrionHudDrag;

/** Клик по HUD не должен одновременно активировать ссылку или поле чата под ним. */
@Mixin(ChatScreen.class)
public abstract class ChatHudDragMixin {
    @Inject(method="mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",at=@At("HEAD"),cancellable=true)
    private void orion$hudClick(MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> callback) {
        if(event.button()==InputConstants.MOUSE_BUTTON_LEFT && OrionHudDrag.covers(event.x(),event.y()))callback.setReturnValue(true);
    }
}
