package ru.orion.client.football;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Обычная модель игрока с футбольными позами поверх стандартной анимации. */
public final class OrionGoalkeeperModel extends PlayerModel {
    public OrionGoalkeeperModel(ModelPart root){super(root,false);}
    @Override public void setupAnim(AvatarRenderState state){
        super.setupAnim(state);
        if(!(state instanceof OrionGoalkeeperRenderState keeper))return;
        head.yRot=keeper.keeperHeadYaw;
        hat.yRot=head.yRot;
        float gait=keeper.keeperGait;
        if(keeper.goalkeeperAction==OrionGoalkeeperAI.Action.RUN){rightLeg.xRot=gait;leftLeg.xRot=-gait;}
        else {rightLeg.xRot=.08F;leftLeg.xRot=-.08F;}
        if(keeper.keeperHandsHigh){rightArm.xRot=-2.45F;leftArm.xRot=-2.45F;rightArm.zRot=.26F;leftArm.zRot=-.26F;}
        else if(keeper.goalkeeperAction==OrionGoalkeeperAI.Action.DIVE_LEFT||keeper.goalkeeperAction==OrionGoalkeeperAI.Action.DIVE_RIGHT){
            rightArm.xRot=-1.18F;leftArm.xRot=-1.18F;rightArm.zRot=.55F;leftArm.zRot=-.55F;
        }else if(keeper.goalkeeperAction==OrionGoalkeeperAI.Action.LOW_SAVE){
            rightArm.xRot=-.75F;leftArm.xRot=-.75F;rightLeg.xRot=-.45F;leftLeg.xRot=-.45F;
        }else{rightArm.xRot=-.38F;leftArm.xRot=-.38F;rightArm.zRot=.16F;leftArm.zRot=-.16F;}
        rightSleeve.xRot=rightArm.xRot;rightSleeve.yRot=rightArm.yRot;rightSleeve.zRot=rightArm.zRot;
        leftSleeve.xRot=leftArm.xRot;leftSleeve.yRot=leftArm.yRot;leftSleeve.zRot=leftArm.zRot;
        rightPants.xRot=rightLeg.xRot;rightPants.yRot=rightLeg.yRot;rightPants.zRot=rightLeg.zRot;
        leftPants.xRot=leftLeg.xRot;leftPants.yRot=leftLeg.yRot;leftPants.zRot=leftLeg.zRot;
    }
}
