package ru.orion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import ru.orion.client.command.OrionFakeBanCommand;
import ru.orion.client.basketball.OrionBasketball;
import ru.orion.client.football.OrionFootball;
import ru.orion.client.football.OrionFootballHud;
import ru.orion.client.basketball.OrionBasketballHud;
import ru.orion.client.command.OrionChatClearCommand;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.discord.OrionDiscordPresenceService;
import ru.orion.client.effect.OrionBreadcrumbs;
import ru.orion.client.effect.OrionChatHistory;
import ru.orion.client.effect.OrionSelfTag;
import ru.orion.client.effect.OrionArmorIndicator;
import ru.orion.client.effect.OrionProjectileTrail;
import ru.orion.client.effect.OrionDamageIndicators;
import ru.orion.client.effect.OrionBlockHighlight;
import ru.orion.client.effect.OrionHitParticles;
import ru.orion.client.effect.OrionMotionBlur;
import ru.orion.client.effect.OrionChinaHeadRenderer;
import ru.orion.client.effect.OrionFullBrightModule;
import ru.orion.client.effect.OrionHealthTags;
import ru.orion.client.effect.OrionHitColorFlash;
import ru.orion.client.effect.OrionHitSound;
import ru.orion.client.effect.OrionJumpCircleEffect;
import ru.orion.client.effect.OrionNoWeatherModule;
import ru.orion.client.effect.OrionTargetEsp;
import ru.orion.client.effect.OrionWorldParticles;
import ru.orion.client.hud.OrionBetterNearHud;
import ru.orion.client.hud.OrionCustomCrosshair;
import ru.orion.client.hud.OrionHudRenderer;
import ru.orion.client.hud.OrionStatusHud;
import ru.orion.client.hud.OrionSaturationOverlay;
import ru.orion.client.hud.OrionHotbarLocks;
import ru.orion.client.hud.OrionInventoryHud;
import ru.orion.client.gui.OrionClickGuiScreen;
import ru.orion.client.keybind.OrionKeybinds;
import ru.orion.client.media.OrionMediaController;
import ru.orion.client.media.OrionMediaPlayerHud;

public final class OrionClient implements ClientModInitializer {
    public static final String MOD_ID = "orion-client";
    public static final String VERSION_TEXT = "Orion Client v0.1";

    private static OrionClient instance;

    private final OrionConfigManager configManager = new OrionConfigManager();
    private final ru.orion.client.effect.OrionWaypoints waypoints = new ru.orion.client.effect.OrionWaypoints(configManager);
    private final OrionFootball football = new OrionFootball(configManager);
    private final OrionFootballHud footballHud = new OrionFootballHud(football, configManager);
    private final OrionBasketball basketball = new OrionBasketball(configManager);
    private final OrionBasketballHud basketballHud = new OrionBasketballHud(basketball, configManager);
    private final OrionHotbarLocks hotbarLocks = new OrionHotbarLocks(configManager);
    private final OrionInventoryHud inventoryHud = new OrionInventoryHud(configManager);
    private final OrionStatusHud statusHud = new OrionStatusHud(configManager);
    private final OrionHudRenderer hudRenderer = new OrionHudRenderer(configManager);
    private final OrionKeybinds keybinds = new OrionKeybinds(configManager);
    private final OrionDiscordPresenceService discordPresenceService = new OrionDiscordPresenceService(configManager);
    private final OrionJumpCircleEffect jumpCircleEffect = new OrionJumpCircleEffect(configManager);
    private final OrionTargetEsp targetEsp = new OrionTargetEsp(configManager);
    private final OrionChinaHeadRenderer chinaHeadRenderer = new OrionChinaHeadRenderer(configManager);
    private final OrionHealthTags healthTags = new OrionHealthTags(configManager);
    private final OrionBlockHighlight blockHighlight = new OrionBlockHighlight(configManager);
    private final OrionHitParticles hitParticles = new OrionHitParticles(configManager);
    private final OrionMotionBlur motionBlur = new OrionMotionBlur(configManager);
    private final OrionSelfTag selfTag = new OrionSelfTag(configManager);
    private final OrionArmorIndicator armorIndicator = new OrionArmorIndicator(configManager);
    private final OrionProjectileTrail projectileTrail = new OrionProjectileTrail(configManager);
    private final OrionDamageIndicators damageIndicators = new OrionDamageIndicators(configManager);
    private final OrionBreadcrumbs breadcrumbs = new OrionBreadcrumbs(configManager);
    private final OrionWorldParticles worldParticles = new OrionWorldParticles(configManager);
    private final OrionNoWeatherModule noWeatherModule = new OrionNoWeatherModule(configManager);
    private final OrionFullBrightModule fullBrightModule = new OrionFullBrightModule(configManager);
    private final OrionMediaController mediaController = new OrionMediaController(configManager);
    private final OrionMediaPlayerHud mediaPlayerHud = new OrionMediaPlayerHud(configManager, mediaController);
    private final OrionHitColorFlash hitColorFlash = new OrionHitColorFlash(configManager);
    private final OrionHitSound hitSound = new OrionHitSound(configManager);
    private final OrionCustomCrosshair customCrosshair = new OrionCustomCrosshair(configManager);
    private final OrionBetterNearHud betterNearHud = new OrionBetterNearHud(configManager);
    private final OrionChatClearCommand chatClearCommand = new OrionChatClearCommand();
    private final OrionFakeBanCommand fakeBanCommand = new OrionFakeBanCommand();

    @Override
    public void onInitializeClient() {
        instance = this;

        OrionClickGuiScreen.prepareInBackground();
        configManager.load();
        basketball.register();
        football.register();
        footballHud.register();
        basketballHud.register();
        OrionSaturationOverlay.registerResourceListener();
        ClientTickEvents.END_CLIENT_TICK.register(client -> OrionChatHistory.ensureLoaded());
        waypoints.register();
        ru.orion.client.effect.OrionBadgePresence.register();
        hudRenderer.register();
        statusHud.register();
        hotbarLocks.register();
        inventoryHud.register();
        keybinds.register();
        discordPresenceService.register();
        jumpCircleEffect.register();
        targetEsp.register();
        chinaHeadRenderer.register();
        healthTags.register();
        worldParticles.register();
        breadcrumbs.register();
        projectileTrail.register();
        damageIndicators.register();
        armorIndicator.register();
        selfTag.register();
        blockHighlight.register();
        hitParticles.register();
        motionBlur.register();
        noWeatherModule.register();
        fullBrightModule.register();
        mediaController.register();
        mediaPlayerHud.register();
        hitColorFlash.register();
        hitSound.register();
        customCrosshair.register();
        betterNearHud.register();
        fakeBanCommand.register();
        chatClearCommand.register();
        ru.orion.client.hud.OrionHudDrag.registerOverlay();
    }

    public static OrionClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Orion Client is not initialized yet.");
        }

        return instance;
    }

    /** Safe accessor for mixins/early callers; returns null before init instead of throwing. */
    public static OrionConfig getConfigOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.configManager.getConfig();
    }

    public OrionConfigManager getConfigManager() {
        return configManager;
    }

    public OrionDiscordPresenceService getDiscordPresenceService() {
        return discordPresenceService;
    }

    public OrionTargetEsp getTargetEsp() {
        return targetEsp;
    }

    public static OrionFootball getFootballOrNull() { return instance == null ? null : instance.football; }

    public static OrionBasketball getBasketballOrNull() { return instance == null ? null : instance.basketball; }

    public static OrionTargetEsp getTargetEspOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.targetEsp;
    }

    public static OrionHitColorFlash getHitColorFlashOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.hitColorFlash;
    }

    public static OrionHitParticles getHitParticlesOrNull() {
        return instance == null ? null : instance.hitParticles;
    }

    public static OrionDamageIndicators getDamageIndicatorsOrNull() {
        return instance == null ? null : instance.damageIndicators;
    }

    public static OrionMotionBlur getMotionBlurOrNull() {
        return instance == null ? null : instance.motionBlur;
    }

    public OrionHitSound getHitSound() {
        return hitSound;
    }

    public static OrionHitSound getHitSoundOrNull() {
        OrionClient current = instance;
        return current == null ? null : current.hitSound;
    }

    public OrionMediaController getMediaController() {
        return mediaController;
    }
}
