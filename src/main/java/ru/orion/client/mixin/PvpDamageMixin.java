package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionPvpSafe;

@Mixin(ClientPacketListener.class)
public abstract class PvpDamageMixin {
    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At("TAIL"))
    private void orion$pvpDamage(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        OrionPvpSafe.onDamage(packet);
    }
}
