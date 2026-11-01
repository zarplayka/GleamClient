package ru.orion.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionBadge;
import ru.orion.client.effect.OrionBadgePresence;

@Mixin(EntityRenderer.class)
public abstract class OrionBadgeNameTagMixin {
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V", at = @At("RETURN"))
    private void orion$addBadge(Entity entity, EntityRenderState state, float tickDelta, double nameDistance, double scoreDistance, CallbackInfo ci) {
        if (entity instanceof Player && state.nameTag != null && OrionBadgePresence.isOrion(entity.getUUID())) {
            state.nameTag = OrionBadge.prepend(state.nameTag);
        }
    }
}
