package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.basketball.engine.OrionBasketballController;

/** Поза руки местного игрока на модели, а не обычная анимация удара. */
@Mixin(PlayerModel.class)
public abstract class BasketballPlayerPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void orion$basketballPose(AvatarRenderState state,CallbackInfo callback) {
        Minecraft client=Minecraft.getInstance();var game=OrionClient.getBasketballOrNull();
        if(client.player==null || state.id!=client.player.getId() || game==null || !game.renderBasketballHand())return;
        PlayerModel model=(PlayerModel)(Object)this;
        float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if(OrionBasketballController.mode()==OrionBasketballController.Mode.HELD) {
            float charge=OrionBasketballController.charge(partial);
            model.rightArm.xRot=-1.2F-charge*.75F;model.rightArm.yRot=-.3F+charge*.1F;model.rightArm.zRot=.2F;
            model.leftArm.xRot=-1.1F-charge*.65F;model.leftArm.yRot=.35F-charge*.1F;model.leftArm.zRot=-.25F;
        } else {
            float bounce=game.handMovement(partial);
            model.rightArm.xRot=-.95F+bounce*.65F;model.rightArm.yRot=-.18F;model.rightArm.zRot=.1F+bounce*.12F;
        }
        model.rightSleeve.xRot=model.rightArm.xRot;model.rightSleeve.yRot=model.rightArm.yRot;model.rightSleeve.zRot=model.rightArm.zRot;
        model.leftSleeve.xRot=model.leftArm.xRot;model.leftSleeve.yRot=model.leftArm.yRot;model.leftSleeve.zRot=model.leftArm.zRot;
    }
}
