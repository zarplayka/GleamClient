package ru.orion.client.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.gui.OrionFriendWorlds;

@Mixin(ServerLoginPacketListenerImpl.class)
abstract class OrionWorldLoginMixin {
    @Shadow @Final private Connection connection;
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void gleam$checkWorldInvite(ServerboundHelloPacket packet, CallbackInfo ci) {
        if (OrionFriendWorlds.rejectLogin(server, connection.getRemoteAddress(), packet.name())) {
            connection.disconnect(Component.literal("Приглашение в мир больше не действует"));
            ci.cancel();
        }
    }

    @Redirect(method = "handleHello", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
    private boolean gleam$authenticateInvitedPlayer(MinecraftServer server) {
        return server.usesAuthentication() && !OrionFriendWorlds.allowsOffline(server, connection.getRemoteAddress());
    }

    @Redirect(method = "handleHello", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/UUIDUtil;createOfflineProfile(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;"))
    private GameProfile gleam$offlineProfile(String name) {
        var profile = OrionFriendWorlds.offlineProfile(server, connection.getRemoteAddress(), name);
        return profile != null ? profile : UUIDUtil.createOfflineProfile(name);
    }
}
