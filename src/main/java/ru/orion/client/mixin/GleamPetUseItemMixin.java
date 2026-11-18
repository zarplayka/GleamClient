package ru.orion.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.orion.client.cosmetic.GleamPetPlacement;

@Mixin(MultiPlayerGameMode.class)
abstract class GleamPetUseItemMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult gleam$checkPlacement(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                                    Operation<InteractionResult> original) {
        GleamPetPlacement.begin();
        try {
            return original.call(player, hand, hit);
        } finally {
            GleamPetPlacement.end();
        }
    }

    @WrapOperation(method = "startPrediction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void gleam$skipRejectedPlacement(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
        if (!(packet instanceof ServerboundUseItemOnPacket) || !GleamPetPlacement.blocked()) {
            original.call(connection, packet);
        }
    }
}
