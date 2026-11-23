package ru.orion.client.mixin;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
//? if >=26.3 {
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
//?} else {
/*import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
//? if >=26.2 {
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
//?}
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import ru.orion.client.gui.OrionMinecraftSession;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
abstract class OrionMinecraftSessionMixin implements OrionMinecraftSession {
    @Shadow @Final @Mutable private User user;
    @Shadow @Final @Mutable private UserApiService userApiService;
    @Shadow @Final @Mutable private CompletableFuture<?> profileFuture;
    @Shadow @Final @Mutable private CompletableFuture<UserApiService.UserProperties> userPropertiesFuture;
    @Shadow @Final @Mutable private ProfileKeyPairManager profileKeyPairManager;
    @Shadow @Final @Mutable private ClientTelemetryManager telemetryManager;
    @Shadow @Final @Mutable private PlayerSocialManager playerSocialManager;
    @Shadow private ReportingContext reportingContext;
    //? if >=26.2 {
    @Shadow @Final @Mutable private RemoteFriendListUpdateHandler remoteFriendListUpdateHandler;
    //?}

    @Override
    public boolean gleam$useSession(User replacement, boolean offline) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.level != null || client.getConnection() != null) return false;

        String token = replacement.getAccessToken();
        //? if >=26.3 {
        var authentication = offline ? MinecraftServicesDiscoveryService.createOffline(client.getProxy())
                : MinecraftServicesDiscoveryService.create(client.getProxy());
        //?} else {
        /*var authentication = new YggdrasilAuthenticationService(client.getProxy());
        *///?}
        var api = offline ? UserApiService.OFFLINE : authentication.createUserApiService(token);
        var keys = offline ? ProfileKeyPairManager.EMPTY_KEY_MANAGER
                : ProfileKeyPairManager.create(api, replacement, client.gameDirectory.toPath());
        //? if >=26.2 {
        var friends = authentication.createFriendsService(token);
        var remote = new RemoteFriendListUpdateHandler(friends, client);
        var social = new PlayerSocialManager(client, api, friends, remote);
        //?} else {
        /*var social = new PlayerSocialManager(client, api);
        *///?}
        var reporting = ReportingContext.create(ReportEnvironment.local(), api);
        var telemetry = new ClientTelemetryManager(client, api, replacement);
        var profile = offline ? CompletableFuture.completedFuture(null) : CompletableFuture.supplyAsync(
                () -> client.services().sessionService().fetchProfile(replacement.getProfileId(), true), Util.nonCriticalIoPool());
        var properties = offline ? CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES)
                : CompletableFuture.supplyAsync(() -> {
                    try {
                        return api.fetchProperties();
                    } catch (AuthenticationException failure) {
                        return UserApiService.OFFLINE_PROPERTIES;
                    }
                }, Util.nonCriticalIoPool());

        telemetryManager.close();
        //? if >=26.2 {
        remoteFriendListUpdateHandler.close();
        remoteFriendListUpdateHandler = remote;
        //?}
        user = replacement;
        userApiService = api;
        profileFuture = profile;
        userPropertiesFuture = properties;
        profileKeyPairManager = keys;
        playerSocialManager = social;
        reportingContext = reporting;
        telemetryManager = telemetry;
        //? if >=26.2 {
        if (!offline && social.isFriendListEnabled()) remote.start();
        //?}
        return true;
    }
}
