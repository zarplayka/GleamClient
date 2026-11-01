package ru.orion.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OrionConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "orion-client.json";

    private final Path configPath;
    private OrionConfig config = OrionConfig.createDefault();

    public OrionConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            config = OrionConfig.createDefault();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            OrionConfig loadedConfig = GSON.fromJson(reader, OrionConfig.class);

            if (loadedConfig == null) {
                throw new IllegalStateException("Config file is empty.");
            }

            loadedConfig.sanitize();
            config = loadedConfig;
            save();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load Orion Client config. Restoring default config.", exception);
            config = OrionConfig.createDefault();
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());

            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save Orion Client config.", exception);
        }
    }

    public OrionConfig getConfig() {
        return config;
    }

    public void setZoomEnabled(boolean value) { config.setZoomEnabled(value); save(); }
    public void setHaloEnabled(boolean value) {
        config.setHaloEnabled(value);
        save();
        ru.orion.client.effect.OrionBadgePresence.requestRefresh();
    }
    public void setWingsEnabled(boolean value) {
        config.setWingsEnabled(value); save(); ru.orion.client.effect.OrionBadgePresence.requestRefresh();
    }
    public void setWingsStyle(int value) {
        config.setWingsStyle(value); save(); ru.orion.client.effect.OrionBadgePresence.requestRefresh();
    }
    public void setZoomFactor(float value) { config.setZoomFactor(value); save(); }
    public void setChatFilterEnabled(boolean value) { config.setChatFilterEnabled(value); save(); }
    public void setChatFilterWords(String value) { config.setChatFilterWords(value); save(); }
    public void setChatHighlightEnabled(boolean value) { config.setChatHighlightEnabled(value); save(); }
    public void setChatHighlightWords(String value) { config.setChatHighlightWords(value); save(); }
    public void setChatHighlightColor(String value) { config.setChatHighlightColor(value); save(); }
    public void setWaypointsEnabled(boolean value) { config.setWaypointsEnabled(value); save(); }
    public void setDeathPointEnabled(boolean value) { config.setDeathPointEnabled(value); save(); }

    public void setBasketballPassPower(float value) { config.setBasketballPassPower(value); save(); }
    public void setBasketballSpin(float value) { config.setBasketballSpin(value); save(); }
    public void setBasketballAutoCatch(boolean value) { config.setBasketballAutoCatch(value); save(); }
    public void setBasketballAutoDribble(boolean value) { config.setBasketballAutoDribble(value); save(); }
    public void setBasketballHands(boolean value) { config.setBasketballHands(value); save(); }
    public void setBasketballCourtLines(boolean value) { config.setBasketballCourtLines(value); save(); }
    public void setBasketballTrajectory(boolean value) { config.setBasketballTrajectory(value); save(); }

    public void setHudEnabled(boolean hudEnabled) {
        config.setHudEnabled(hudEnabled);
        save();
    }

    public void setScoreboardGlassEnabled(boolean enabled) {
        config.setScoreboardGlassEnabled(enabled);
        save();
    }

    public void setDiscordRpcEnabled(boolean discordRpcEnabled) {
        config.setDiscordRpcEnabled(discordRpcEnabled);
        save();
    }

    public void setJumpCircleEnabled(boolean jumpCircleEnabled) {
        config.setJumpCircleEnabled(jumpCircleEnabled);
        save();
    }

    public void setJumpCircleColor(String jumpCircleColor) {
        config.setJumpCircleColor(jumpCircleColor);
        save();
    }

    public void setMediaPlayerEnabled(boolean mediaPlayerEnabled) {
        config.setMediaPlayerEnabled(mediaPlayerEnabled);
        save();
    }

    public void setMediaPlayerPosition(int x, int y) {
        config.setMediaPlayerX(x);
        config.setMediaPlayerY(y);
        save();
    }

    public void setAspectEnabled(boolean aspectEnabled) {
        config.setAspectEnabled(aspectEnabled);
        save();
    }

    public void setTargetEspEnabled(boolean targetEspEnabled) {
        config.setTargetEspEnabled(targetEspEnabled);
        save();
    }

    public void setTargetEspTexture(int targetEspTexture) {
        config.setTargetEspTexture(targetEspTexture);
        save();
    }

    public void setTargetEspChainColor(String targetEspChainColor) {
        config.setTargetEspChainColor(targetEspChainColor);
        save();
    }

    public void setChinaHeadEnabled(boolean chinaHeadEnabled) {
        config.setChinaHeadEnabled(chinaHeadEnabled);
        save();
    }

    public void setMotionBlurEnabled(boolean value) { config.setMotionBlurEnabled(value); save(); }
    public void setBlockHighlightEnabled(boolean value) { config.setBlockHighlightEnabled(value); save(); }
    public void setBlockHighlightColor(String value) { config.setBlockHighlightColor(value); save(); }
    public void setBlockHighlightMoveSpeed(float value) { config.setBlockHighlightMoveSpeed(value); save(); }
    public void setBlockHighlightFadeSpeed(float value) { config.setBlockHighlightFadeSpeed(value); save(); }
    public void setBlockHighlightLineWidth(float value) { config.setBlockHighlightLineWidth(value); save(); }
    public void setBlockHighlightOpacity(int value) { config.setBlockHighlightOpacity(value); save(); }
    public void setBlockHighlightGlow(boolean value) { config.setBlockHighlightGlow(value); save(); }
    public void setBlockHighlightGlowWidth(float value) { config.setBlockHighlightGlowWidth(value); save(); }
    public void setBlockHighlightGlowOpacity(int value) { config.setBlockHighlightGlowOpacity(value); save(); }
    public void setBlockHighlightOffset(float value) { config.setBlockHighlightOffset(value); save(); }
    public void setItemPhysicsEnabled(boolean value) { config.setItemPhysicsEnabled(value); save(); }
    public void setHitParticlesEnabled(boolean value) { config.setHitParticlesEnabled(value); save(); }

    public void setProjectileTrailEnabled(boolean value) { config.setProjectileTrailEnabled(value); save(); }
    public void setProjectileTrailColor(String value) { config.setProjectileTrailColor(value); save(); }
    public void setProjectileTrailDuration(float value) { config.setProjectileTrailDuration(value); save(); }
    public void setProjectileTrailGlow(boolean value) { config.setProjectileTrailGlow(value); save(); }
    public void setDamageIndicatorsEnabled(boolean value) { config.setDamageIndicatorsEnabled(value); save(); }
    public void setDamageIndicatorsLowColor(String value) { config.setDamageIndicatorsLowColor(value); save(); }
    public void setDamageIndicatorsHighColor(String value) { config.setDamageIndicatorsHighColor(value); save(); }
    public void setDamageIndicatorsThreshold(float value) { config.setDamageIndicatorsThreshold(value); save(); }

    public void setBreadcrumbsEnabled(boolean value) {
        config.setBreadcrumbsEnabled(value);
        save();
    }

    public void setWorldParticlesEnabled(boolean worldParticlesEnabled) {
        config.setWorldParticlesEnabled(worldParticlesEnabled);
        save();
    }

    public void setWorldParticlesColor(String worldParticlesColor) {
        config.setWorldParticlesColor(worldParticlesColor);
        save();
    }

    public void setFootballEnabled(boolean value) { config.setFootballEnabled(value); save(); }
    public void setFootballGoalkeeperDifficulty(int value) { config.setFootballGoalkeeperDifficulty(value); save(); }
    public void setFootballGoalkeeperEnabled(boolean value) { config.setFootballGoalkeeperEnabled(value); save(); }
    public void setFootballPenaltyMode(boolean value) { config.setFootballPenaltyMode(value); save(); }
    public void setBasketballEnabled(boolean value) { config.setBasketballEnabled(value); save(); }
    public void setBasketballHud(boolean value) { config.setBasketballHud(value); save(); }
    public void setAnimationsEnabled(boolean value) { config.setAnimationsEnabled(value); save(); }
    public void setAnimationsDuration(int value) { config.setAnimationsDuration(value); save(); }
    public void setEnchantGlintEnabled(boolean value) { config.setEnchantGlintEnabled(value); save(); }
    public void setEnchantGlintMode(int value) { config.setEnchantGlintMode(value); save(); }
    public void setInventoryHudEnabled(boolean value) { config.setInventoryHudEnabled(value); save(); }
    public void setSelfTagEnabled(boolean value) { config.setSelfTagEnabled(value); save(); }
    public void setArmorIndicatorEnabled(boolean value) { config.setArmorIndicatorEnabled(value); save(); }
    public void setLockSlotEnabled(boolean value) { config.setLockSlotEnabled(value); save(); }
    public void setLockSlotMask(long value) { config.setLockSlotMask(value); save(); }
    public void setPvpSafeEnabled(boolean value) { config.setPvpSafeEnabled(value); save(); }
    public void setPvpSafeSeconds(int value) { config.setPvpSafeSeconds(value); save(); }
    public void setKeystrokesEnabled(boolean value) { config.setKeystrokesEnabled(value); save(); }
    public void setSaturationEnabled(boolean value) { config.setSaturationEnabled(value); save(); }
    public void setChatHistoryEnabled(boolean value) { config.setChatHistoryEnabled(value); if (value) ru.orion.client.effect.OrionChatHistory.ensureLoaded(); save(); }
    public void setChatTimeEnabled(boolean value) { config.setChatTimeEnabled(value); save(); }
    public void setCustomTimeEnabled(boolean value) { config.setCustomTimeEnabled(value); save(); }
    public void setCustomTimeTicks(int value) { config.setCustomTimeTicks(value); save(); }
    public void setItemScalingEnabled(boolean value) { config.setItemScalingEnabled(value); save(); }
    public void setItemScaling(float value) { config.setItemScaling(value); save(); }
    public void setSmallShieldEnabled(boolean value) { config.setSmallShieldEnabled(value); save(); }
    public void setSmallShieldScale(float value) { config.setSmallShieldScale(value); save(); }
    public void setViewModelEnabled(boolean value) { config.setViewModelEnabled(value); save(); }
    public void setViewModelX(float value) { config.setViewModelX(value); save(); }
    public void setViewModelY(float value) { config.setViewModelY(value); save(); }
    public void setViewModelZ(float value) { config.setViewModelZ(value); save(); }

    public void setNoParticlesEnabled(boolean value) { config.setNoParticlesEnabled(value); save(); }
    public void setNoParticlesExplosions(boolean value) { config.setNoParticlesExplosions(value); save(); }
    public void setNoParticlesAmbient(boolean value) { config.setNoParticlesAmbient(value); save(); }
    public void setNoParticlesCombat(boolean value) { config.setNoParticlesCombat(value); save(); }

    public void setNoWeatherEnabled(boolean noWeatherEnabled) {
        config.setNoWeatherEnabled(noWeatherEnabled);
        save();
    }

    public void setNoFireEnabled(boolean noFireEnabled) {
        config.setNoFireEnabled(noFireEnabled);
        save();
    }

    public void setFullBrightEnabled(boolean fullBrightEnabled) {
        config.setFullBrightEnabled(fullBrightEnabled);
        save();
    }

    public void setWorldChangerEnabled(boolean worldChangerEnabled) {
        config.setWorldChangerEnabled(worldChangerEnabled);
        save();
    }

    public void setWorldChangerSkyColor(String worldChangerSkyColor) {
        config.setWorldChangerSkyColor(worldChangerSkyColor);
        save();
    }

    public void setWorldChangerFogColor(String worldChangerFogColor) {
        config.setWorldChangerFogColor(worldChangerFogColor);
        save();
    }

    public void setMediaPlayerCorner(String mediaPlayerCorner) {
        config.setMediaPlayerCorner(mediaPlayerCorner);
        config.setMediaPlayerPositionCustom(false);
        config.sanitize();
        save();
    }

    public void setMediaPlayerAnimated(boolean mediaPlayerAnimated) {
        config.setMediaPlayerAnimated(mediaPlayerAnimated);
        save();
    }

    public void setClickGuiTheme(int clickGuiTheme) {
        config.setClickGuiTheme(clickGuiTheme);
        config.sanitize();
        save();
    }

    public void setHealthTagsEnabled(boolean healthTagsEnabled) {
        config.setHealthTagsEnabled(healthTagsEnabled);
        save();
    }

    public void setProtectNameEnabled(boolean protectNameEnabled) {
        config.setProtectNameEnabled(protectNameEnabled);
        save();
    }

    public void setProtectName(String protectName) {
        config.setProtectName(protectName);
        save();
    }

    public void setFakePrefixEnabled(boolean fakePrefixEnabled) {
        config.setFakePrefixEnabled(fakePrefixEnabled);
        save();
    }

    public void setFakePrefix(String fakePrefix) {
        config.setFakePrefix(fakePrefix);
        save();
    }

    public void setFakePrefixAdditionalNames(String additionalNames) {
        config.setFakePrefixAdditionalNames(additionalNames);
        save();
    }

    public void setHitBoxEnabled(boolean hitBoxEnabled) {
        config.setHitBoxEnabled(hitBoxEnabled);
        save();
    }

    public void setHitColorEnabled(boolean hitColorEnabled) {
        config.setHitColorEnabled(hitColorEnabled);
        save();
    }

    public void setCrosshairEnabled(boolean crosshairEnabled) {
        config.setCrosshairEnabled(crosshairEnabled);
        save();
    }

    public void setBetterNearEnabled(boolean betterNearEnabled) {
        config.setBetterNearEnabled(betterNearEnabled);
        save();
    }

    public void setRainbowEnabled(boolean rainbowEnabled) {
        config.setRainbowEnabled(rainbowEnabled);
        save();
    }

    public void setHitSoundEnabled(boolean hitSoundEnabled) {
        config.setHitSoundEnabled(hitSoundEnabled);
        save();
    }

    public void setHitSoundIndex(int hitSoundIndex) {
        config.setHitSoundIndex(hitSoundIndex);
        config.sanitize();
        save();
    }
}
