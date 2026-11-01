package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(PlayerModel.class)
public abstract class OrionFootballKickPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void orion$kick(AvatarRenderState state,CallbackInfo callback){
        Minecraft client=Minecraft.getInstance();var football=OrionClient.getFootballOrNull();
        if(client.player==null||state.id!=client.player.getId()||football==null||football.kickAt()==0)return;
        float t=(System.nanoTime()-football.kickAt())/350_000_000F;if(t<0||t>=1)return;
        PlayerModel model=(PlayerModel)(Object)this;float kick=(float)Math.sin(Math.PI*t);
        model.rightLeg.xRot-=kick*1.15F;model.rightPants.xRot=model.rightLeg.xRot;
    }
}
