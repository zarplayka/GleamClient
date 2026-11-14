package ru.orion.client.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.orion.client.gui.OrionFriendWorlds;

import java.io.IOException;
import java.net.InetAddress;

@Mixin(IntegratedServer.class)
abstract class OrionWorldPublishMixin {
    @Redirect(method = "publishServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void gleam$bindPrivateLan(ServerConnectionListener connections, InetAddress address, int port) throws IOException {
        connections.startTcpServerListener(OrionFriendWorlds.lanBindAddress(address), port);
    }
}
