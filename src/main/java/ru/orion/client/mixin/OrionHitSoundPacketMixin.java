package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(ClientPacketListener.class)
public abstract class OrionHitSoundPacketMixin {
    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V",
            at = @At("TAIL"))
    private void orion$confirmedHit(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        var sound = OrionClient.getHitSoundOrNull();
        if (sound != null) sound.onDamage(packet);
    }
}
