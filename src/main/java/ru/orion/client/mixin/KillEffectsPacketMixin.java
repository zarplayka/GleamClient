package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

/** TAIL выполняется после ванильного переноса обработки пакета на игровой поток. */
@Mixin(ClientPacketListener.class)
public abstract class KillEffectsPacketMixin {
    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At("TAIL"))
    private void orion$killDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        var effects = OrionClient.getKillEffects();
        if (effects != null) effects.onDamage(packet);
    }

    @Inject(method = "handleEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;)V", at = @At("TAIL"))
    private void orion$killDeath(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        var effects = OrionClient.getKillEffects();
        if (effects != null) effects.onDeath(packet);
    }

    @Inject(method = "handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V", at = @At("TAIL"))
    private void orion$killConfirmation(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        var effects = OrionClient.getKillEffects();
        if (effects != null && !packet.overlay()) effects.onDeathMessage(packet.content());
    }
}
