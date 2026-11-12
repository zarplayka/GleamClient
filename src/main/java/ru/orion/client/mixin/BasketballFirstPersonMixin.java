package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
//?} else {
/*import net.minecraft.client.renderer.ItemInHandRenderer;
*///?}

import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=26.3 {
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
//?}

//? if >=26.3 {
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.basketball.engine.OrionBasketballHands;

/** Две кисти и мяч в общей системе координат восстановленной мини-игры. */
//? if >=26.3 {
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//?} else {
/*@Mixin(ItemInHandRenderer.class)
*///?}

public abstract class BasketballFirstPersonMixin {
    //? if >=26.3 {
    @Inject(method="submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",at=@At("HEAD"),cancellable=true)
    private void orion$basketballHands(float partial,PoseStack pose,SubmitNodeCollector collector,PlayerRenderState player,
                                     FirstPersonHandsAndItemsRenderState hands,CallbackInfo callback) {
        int light=player.avatarRenderState==null?0:player.avatarRenderState.lightCoords;
        if(OrionBasketballHands.renderHands(partial,pose,collector,Minecraft.getInstance().player,light))callback.cancel();
    }
    //?} else {
        /*@Inject(method="submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",at=@At("HEAD"),cancellable=true)
    private void orion$basketballHands(float partial,PoseStack pose,SubmitNodeCollector collector,net.minecraft.client.player.LocalPlayer player, int light,CallbackInfo callback) {
        if(OrionBasketballHands.renderHands(partial,pose,collector,Minecraft.getInstance().player,light))callback.cancel();
    }
    *///?}
}
