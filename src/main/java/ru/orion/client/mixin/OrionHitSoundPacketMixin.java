package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
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

    @Inject(method = "handleHurtAnimation(Lnet/minecraft/network/protocol/game/ClientboundHurtAnimationPacket;)V",
            at = @At("TAIL"))
    private void orion$hurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo callback) {
        var sound = OrionClient.getHitSoundOrNull();
        if (sound != null) sound.onHurt(packet.id());
    }

    @Inject(method = "handleEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;)V",
            at = @At("TAIL"))
    private void orion$legacyHurt(ClientboundEntityEventPacket packet, CallbackInfo callback) {
        // Older server protocols use entity status 2 for a hurt animation.
        if (packet.getEventId() != 2) return;
        var sound = OrionClient.getHitSoundOrNull();
        var level = Minecraft.getInstance().level;
        if (sound == null || level == null) return;
        var target = packet.getEntity(level);
        if (target != null) sound.onHurt(target.getId());
    }
}
