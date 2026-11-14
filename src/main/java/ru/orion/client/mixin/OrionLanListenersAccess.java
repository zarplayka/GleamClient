package ru.orion.client.mixin;

import io.netty.channel.ChannelFuture;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerConnectionListener.class)
public interface OrionLanListenersAccess {
    @Accessor("channels") List<ChannelFuture> gleam$listeners();
}
