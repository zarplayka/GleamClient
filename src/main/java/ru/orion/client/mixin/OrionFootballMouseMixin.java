package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(MouseHandler.class)
public abstract class OrionFootballMouseMixin {
    @Inject(method="onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",at=@At("HEAD"),cancellable=true)
    private void orion$football(long window,MouseButtonInfo info,int action,CallbackInfo callback){
        Minecraft client=Minecraft.getInstance();
        if(window!=client.getWindow().handle()||client.gui.screen()!=null||(action!=InputConstants.PRESS&&action!=InputConstants.RELEASE))return;
        var football=OrionClient.getFootballOrNull();
        if(football!=null&&football.button(info.button(),action==InputConstants.PRESS,info.hasShiftDown()))callback.cancel();
    }
}
