package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.basketball.engine.OrionBasketballController;

/** Настоящий предмет скрывается, когда руки игрока заняты локальным баскетбольным мячом. */
@Mixin(PlayerItemInHandLayer.class)
public abstract class BasketballHeldItemMixin {
    @Inject(method="submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",at=@At("HEAD"),cancellable=true)
    private void orion$basketballOccupiedHands(AvatarRenderState state,ItemStackRenderState rendered,ItemStack item,HumanoidArm arm,
                                              PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo callback) {
        var player=Minecraft.getInstance().player;
        if(player!=null && state.id==player.getId() && OrionBasketballController.localHolding())callback.cancel();
    }
}
