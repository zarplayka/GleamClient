package ru.orion.client.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface OrionLegacyLanAccess {
    @Accessor("publishedPort") void gleam$publishedPort(int port);
    @Accessor("lanPinger") LanServerPinger gleam$lanPinger();
    @Accessor("lanPinger") void gleam$lanPinger(LanServerPinger pinger);
}
