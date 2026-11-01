package ru.orion.client.config;

public final class OrionConfig {
    private boolean zoomEnabled;
    private float zoomFactor = 4.0F;
    private boolean chatFilterEnabled;
    private String chatFilterWords = "";
    private boolean chatHighlightEnabled;
    private String chatHighlightWords = "";
    private String chatHighlightColor = "#FFB34D";
    private boolean waypointsEnabled;
    private boolean deathPointEnabled;
    private java.util.List<Waypoint> waypoints = new java.util.ArrayList<>();
    private Waypoint deathPoint;

    public static final class Waypoint {
        public String name;
        public String world;
        public String color = "#FF9B38";
        public int x, y, z;
        public Waypoint(String name, String world, int x, int y, int z) {
            this.name = name; this.world = world; this.x = x; this.y = y; this.z = z;
        }
    }
    public boolean isZoomEnabled() { return zoomEnabled; }
    public void setZoomEnabled(boolean value) { zoomEnabled = value; }
    public float getZoomFactor() { return zoomFactor; }
    public void setZoomFactor(float value) { zoomFactor = finiteClamp(value, 1.5F, 12F, 4F); }
    public boolean isChatFilterEnabled() { return chatFilterEnabled; }
    public void setChatFilterEnabled(boolean value) { chatFilterEnabled = value; }
    public String getChatFilterWords() { return chatFilterWords; }
    public void setChatFilterWords(String value) { chatFilterWords = value == null ? "" : value; }
    public boolean isChatHighlightEnabled() { return chatHighlightEnabled; }
    public void setChatHighlightEnabled(boolean value) { chatHighlightEnabled = value; }
    public String getChatHighlightWords() { return chatHighlightWords; }
    public void setChatHighlightWords(String value) { chatHighlightWords = value == null ? "" : value; }
    public String getChatHighlightColor() { return chatHighlightColor; }
    public void setChatHighlightColor(String value) { chatHighlightColor = value; }
    public boolean isWaypointsEnabled() { return waypointsEnabled; }
    public void setWaypointsEnabled(boolean value) { waypointsEnabled = value; }
    public boolean isDeathPointEnabled() { return deathPointEnabled; }
    public void setDeathPointEnabled(boolean value) { deathPointEnabled = value; }
    public java.util.List<Waypoint> getWaypoints() { return waypoints; }
    public Waypoint getDeathPoint() { return deathPoint; }
    public void setDeathPoint(Waypoint value) { deathPoint = value; }
    private static final String DEFAULT_DISCORD_APPLICATION_ID = "1520703983163932832";

    private boolean footballEnabled = false;
    private float footballGoalScale = 1.0F;
    private int footballGoalkeeperDifficulty = 1;
    private boolean footballGoalkeeperEnabled = true;
    private boolean footballPenaltyMode = false;
    private float footballKickPower = 1.0F;
    private float footballChargeSpeed = 1.0F;
    private boolean basketballEnabled = false;
    private float basketballThrowPower = 1.0F;
    private float basketballChargeSpeed = 1.0F;
    private float basketballBounce = 1.0F;
    private float basketballGravity = 9.81F;
    private float basketballDribbleSpeed = 1.0F;
    private float basketballBallScale = 1.0F;
    private float basketballVolume = 0.7F;
    private boolean basketballHud = true;
    private float basketballPassPower = 13F;
    private float basketballSpin = 1F;
    private boolean basketballAutoCatch = true;
    private boolean basketballAutoDribble = true;
    private boolean basketballHands = true;
    private boolean basketballCourtLines = true;
    private boolean basketballTrajectory = true;
    public float getBasketballPassPower(){return basketballPassPower;}
    public void setBasketballPassPower(float value){basketballPassPower=value;}
    public float getBasketballSpin(){return basketballSpin;}
    public void setBasketballSpin(float value){basketballSpin=value;}
    public boolean isBasketballAutoCatch(){return basketballAutoCatch;}
    public void setBasketballAutoCatch(boolean value){basketballAutoCatch=value;}
    public boolean isBasketballAutoDribble(){return basketballAutoDribble;}
    public void setBasketballAutoDribble(boolean value){basketballAutoDribble=value;}
    public boolean isBasketballHands(){return basketballHands;}
    public void setBasketballHands(boolean value){basketballHands=value;}
    public boolean isBasketballCourtLines(){return basketballCourtLines;}
    public void setBasketballCourtLines(boolean value){basketballCourtLines=value;}
    public boolean isBasketballTrajectory(){return basketballTrajectory;}
    public void setBasketballTrajectory(boolean value){basketballTrajectory=value;}
    private boolean animationsEnabled = false;
    private int animationsDuration = 180;
    private boolean enchantGlintEnabled = false;
    private int enchantGlintMode = 0;
    private boolean inventoryHudEnabled = false;
    private boolean selfTagEnabled = false;
    private boolean armorIndicatorEnabled = false;
    private boolean lockSlotEnabled = false;
    private long lockSlotMask = 0L;
    private boolean pvpSafeEnabled = false;
    private int pvpSafeSeconds = 30;
    private int keystrokesX = -1;
    private int keystrokesY = -1;
    private int inventoryHudX = -1;
    private int inventoryHudY = -1;
    private boolean mediaPlayerPositionCustom = false;
    private boolean keystrokesEnabled = false;
    private boolean saturationEnabled = false;
    private boolean chatHistoryEnabled = false;
    private boolean chatTimeEnabled = false;
    private boolean customTimeEnabled = false;
    private int customTimeTicks = 6000;
    private boolean itemScalingEnabled = false;
    private float itemScaling = 1.0F;
    private boolean smallShieldEnabled = false;
    private float smallShieldScale = 0.65F;
    private boolean viewModelEnabled = false;
    private float viewModelX = 0.0F;
    private float viewModelY = 0.0F;
    private float viewModelZ = 0.0F;
    private boolean hudEnabled = true;
    private boolean scoreboardGlassEnabled = true;
    private int hudX = 8;
    private int hudY = 8;
    private float hudScale = 1.0F;
    private java.util.Map<String, Float> hudElementScales = new java.util.HashMap<>();
    public float getHudElementScale(String id) {
        if (hudElementScales == null) return 1F;
        Float value = hudElementScales.get(id);
        return value == null || !Float.isFinite(value) ? 1F : clamp(value, .35F, 4F);
    }
    public void setHudElementScale(String id, float value) {
        if (hudElementScales == null) hudElementScales = new java.util.HashMap<>();
        hudElementScales.put(id, Float.isFinite(value) ? clamp(value, .35F, 4F) : 1F);
    }
    private String accentColor = "#FF7C00";
    private boolean discordRpcEnabled = true;
    private String discordApplicationId = DEFAULT_DISCORD_APPLICATION_ID;
    private String discordLargeImageKey = "orion_icon";
    private String discordLargeImageText = "Orion Client";
    private boolean jumpCircleEnabled = true;
    private String jumpCircleColor = "#FF7C00";
    private float jumpCircleSize = 1.0F;
    private boolean mediaPlayerEnabled = false;
    private int mediaPlayerX = 8;
    private int mediaPlayerY = 40;
    private String mediaPlayerCorner = "TOP_LEFT";
    private boolean mediaPlayerAnimated = true;
    private boolean aspectEnabled = false;
    private float aspectStretch = 1.25F;
    private boolean targetEspEnabled = false;
    private int targetEspTexture = 0;
    private String targetEspChainColor = "#F5F5FF";
    private boolean chinaHeadEnabled = false;
    private boolean motionBlurEnabled = false;
    private float motionBlurStrength = 0.5F;
    private boolean blockHighlightEnabled = false;
    private String blockHighlightColor = "#FF7C00";
    private float blockHighlightMoveSpeed = 18.0F;
    private float blockHighlightFadeSpeed = 12.0F;
    private float blockHighlightLineWidth = 1.6F;
    private int blockHighlightOpacity = 92;
    private boolean blockHighlightGlow = true;
    private float blockHighlightGlowWidth = 6.0F;
    private int blockHighlightGlowOpacity = 14;
    private float blockHighlightOffset = 0.003F;
    private boolean itemPhysicsEnabled = false;
    private boolean hitParticlesEnabled = false;
    private String hitParticlesColor = "#FF7C00";
    private int hitParticlesCount = 16;
    private float hitParticlesSize = 0.10F;
    private int hitParticlesShape = 0;
    private boolean projectileTrailEnabled = false;
    private String projectileTrailColor = "#FF7C00";
    private float projectileTrailDuration = 2.0F;
    private boolean projectileTrailGlow = true;
    private boolean damageIndicatorsEnabled = false;
    private String damageIndicatorsLowColor = "#FFFFFF";
    private String damageIndicatorsHighColor = "#FF4936";
    private float damageIndicatorsThreshold = 10.0F;
    private boolean breadcrumbsEnabled = false;
    private boolean breadcrumbsUseAccent = true;
    private String breadcrumbsColor = "#FF7C00";
    private float breadcrumbsDuration = 3.0F;
    private float breadcrumbsWidth = 0.08F;
    private int breadcrumbsOpacity = 80;
    private boolean breadcrumbsGlow = true;
    private boolean breadcrumbsThroughWalls = false;
    private boolean worldParticlesEnabled = false;
    private String worldParticlesColor = "#FF7C00";
    private int worldParticlesCount = 180;
    private boolean noParticlesEnabled = false;
    private boolean noParticlesExplosions = true;
    private boolean noParticlesAmbient = true;
    private boolean noParticlesCombat = false;
    private boolean noWeatherEnabled = false;
    private boolean noFireEnabled = false;
    private boolean fullBrightEnabled = false;
    private boolean worldChangerEnabled = false;
    private String worldChangerSkyColor = "#64D9FF";
    private String worldChangerFogColor = "#14111F";
    private int clickGuiTheme = 0;
    private java.util.List<String> favoriteModules = new java.util.ArrayList<>();

    public java.util.List<String> getFavoriteModules() {
        return java.util.List.copyOf(favoriteModules);
    }
    public void setFavoriteModules(java.util.List<String> modules) {
        favoriteModules = new java.util.ArrayList<>(modules);
    }
    private boolean healthTagsEnabled = true;
    private boolean protectNameEnabled = false;
    private String protectName = "Player";
    private boolean fakePrefixEnabled = false;
    private String fakePrefix = "{\"text\":\"ORION \",\"color\":\"#FF7C00\",\"bold\":true}";
    private String fakePrefixAdditionalNames = "";
    private boolean hitBoxEnabled = false;
    private String hitBoxColor = "#FF7C00";
    private boolean hitColorEnabled = false;
    private String hitColorColor = "#EF4444";
    private int hitColorOpacity = 45;
    private boolean crosshairEnabled = false;
    private String crosshairColor = "#F5F5FF";
    private String crosshairPixels = defaultCrosshairPixels();
    private boolean betterNearEnabled = false;
    private int betterNearRadius = 50;
    private int betterNearX = -1; // -1 — позиция по умолчанию (правый верхний угол)
    private int betterNearY = -1;
    private boolean rainbowEnabled = false;
    private int rainbowSpeed = 3; // 1..10, период цикла = 12с / скорость
    private boolean hitSoundEnabled = false;
    private int hitSoundIndex = 0; // 0..5 — встроенные, 6 — свои из папки
    private int hitSoundVolume = 70; // 0..100%

    public static final int CROSSHAIR_GRID = 15;

    /** Классический плюс 9x9 по центру сетки 15x15. */
    public static String defaultCrosshairPixels() {
        StringBuilder pixels = new StringBuilder(CROSSHAIR_GRID * CROSSHAIR_GRID);
        for (int y = 0; y < CROSSHAIR_GRID; y++) {
            for (int x = 0; x < CROSSHAIR_GRID; x++) {
                boolean on = (x == 7 && y >= 3 && y <= 11) || (y == 7 && x >= 3 && x <= 11);
                pixels.append(on ? '1' : '0');
            }
        }
        return pixels.toString();
    }

    public static OrionConfig createDefault() {
        return new OrionConfig();
    }

    public boolean isFootballEnabled() { return footballEnabled; }
    public void setFootballEnabled(boolean value) { footballEnabled = value; }
    public float getFootballGoalScale() { return footballGoalScale; }
    public void setFootballGoalScale(float value) { footballGoalScale = value; }
    public int getFootballGoalkeeperDifficulty() { return footballGoalkeeperDifficulty; }
    public void setFootballGoalkeeperDifficulty(int value) { footballGoalkeeperDifficulty = value; }
    public boolean isFootballGoalkeeperEnabled() { return footballGoalkeeperEnabled; }
    public void setFootballGoalkeeperEnabled(boolean value) { footballGoalkeeperEnabled = value; }
    public boolean isFootballPenaltyMode() { return footballPenaltyMode; }
    public void setFootballPenaltyMode(boolean value) { footballPenaltyMode = value; }
    public float getFootballKickPower() { return footballKickPower; }
    public void setFootballKickPower(float value) { footballKickPower = value; }
    public float getFootballChargeSpeed() { return footballChargeSpeed; }
    public void setFootballChargeSpeed(float value) { footballChargeSpeed = value; }
    public boolean isBasketballEnabled() { return basketballEnabled; }
    public void setBasketballEnabled(boolean value) { basketballEnabled = value; }
    public float getBasketballThrowPower() { return basketballThrowPower; }
    public void setBasketballThrowPower(float value) { basketballThrowPower = value; }
    public float getBasketballChargeSpeed() { return basketballChargeSpeed; }
    public void setBasketballChargeSpeed(float value) { basketballChargeSpeed = value; }
    public float getBasketballBounce() { return basketballBounce; }
    public void setBasketballBounce(float value) { basketballBounce = value; }
    public float getBasketballGravity() { return basketballGravity; }
    public void setBasketballGravity(float value) { basketballGravity = value; }
    public float getBasketballDribbleSpeed() { return basketballDribbleSpeed; }
    public void setBasketballDribbleSpeed(float value) { basketballDribbleSpeed = value; }
    public float getBasketballBallScale() { return basketballBallScale; }
    public void setBasketballBallScale(float value) { basketballBallScale = value; }
    public float getBasketballVolume() { return basketballVolume; }
    public void setBasketballVolume(float value) { basketballVolume = value; }
    public boolean isBasketballHud() { return basketballHud; }
    public void setBasketballHud(boolean value) { basketballHud = value; }
    public boolean isAnimationsEnabled() { return animationsEnabled; }
    public void setAnimationsEnabled(boolean value) { animationsEnabled = value; }
    public int getAnimationsDuration() { return animationsDuration; }
    public void setAnimationsDuration(int value) { animationsDuration = value; }
    public boolean isEnchantGlintEnabled() { return enchantGlintEnabled; }
    public void setEnchantGlintEnabled(boolean value) { enchantGlintEnabled = value; }
    public int getEnchantGlintMode() { return enchantGlintMode; }
    public void setEnchantGlintMode(int value) { enchantGlintMode = value; }
    public boolean isInventoryHudEnabled() { return inventoryHudEnabled; }
    public void setInventoryHudEnabled(boolean value) { inventoryHudEnabled = value; }
    public boolean isSelfTagEnabled() { return selfTagEnabled; }
    public void setSelfTagEnabled(boolean value) { selfTagEnabled = value; }
    public boolean isArmorIndicatorEnabled() { return armorIndicatorEnabled; }
    public void setArmorIndicatorEnabled(boolean value) { armorIndicatorEnabled = value; }
    public boolean isLockSlotEnabled() { return lockSlotEnabled; }
    public void setLockSlotEnabled(boolean value) { lockSlotEnabled = value; }
    public long getLockSlotMask() { return lockSlotMask; }
    public void setLockSlotMask(long value) { lockSlotMask = value; }
    public boolean isPvpSafeEnabled() { return pvpSafeEnabled; }
    public void setPvpSafeEnabled(boolean value) { pvpSafeEnabled = value; }
    public int getPvpSafeSeconds() { return pvpSafeSeconds; }
    public void setPvpSafeSeconds(int value) { pvpSafeSeconds = value; }
    public int getKeystrokesX() { return keystrokesX; }
    public void setKeystrokesX(int value) { keystrokesX = value; }
    public int getKeystrokesY() { return keystrokesY; }
    public void setKeystrokesY(int value) { keystrokesY = value; }
    public int getInventoryHudX() { return inventoryHudX; }
    public void setInventoryHudX(int value) { inventoryHudX = value; }
    public int getInventoryHudY() { return inventoryHudY; }
    public void setInventoryHudY(int value) { inventoryHudY = value; }
    public boolean isMediaPlayerPositionCustom() { return mediaPlayerPositionCustom; }
    public void setMediaPlayerPositionCustom(boolean value) { mediaPlayerPositionCustom = value; }
    public boolean isKeystrokesEnabled() { return keystrokesEnabled; }
    public void setKeystrokesEnabled(boolean value) { keystrokesEnabled = value; }
    public boolean isSaturationEnabled() { return saturationEnabled; }
    public void setSaturationEnabled(boolean value) { saturationEnabled = value; }
    public boolean isChatHistoryEnabled() { return chatHistoryEnabled; }
    public void setChatHistoryEnabled(boolean value) { chatHistoryEnabled = value; }
    public boolean isChatTimeEnabled() { return chatTimeEnabled; }
    public void setChatTimeEnabled(boolean value) { chatTimeEnabled = value; }
    public boolean isCustomTimeEnabled() { return customTimeEnabled; }
    public void setCustomTimeEnabled(boolean value) { customTimeEnabled = value; }
    public int getCustomTimeTicks() { return customTimeTicks; }
    public void setCustomTimeTicks(int value) { customTimeTicks = value; }
    public boolean isItemScalingEnabled() { return itemScalingEnabled; }
    public void setItemScalingEnabled(boolean value) { itemScalingEnabled = value; }
    public float getItemScaling() { return itemScaling; }
    public void setItemScaling(float value) { itemScaling = value; }
    public boolean isSmallShieldEnabled() { return smallShieldEnabled; }
    public void setSmallShieldEnabled(boolean value) { smallShieldEnabled = value; }
    public float getSmallShieldScale() { return smallShieldScale; }
    public void setSmallShieldScale(float value) { smallShieldScale = value; }
    public boolean isViewModelEnabled() { return viewModelEnabled; }
    public void setViewModelEnabled(boolean value) { viewModelEnabled = value; }
    public float getViewModelX() { return viewModelX; }
    public void setViewModelX(float value) { viewModelX = value; }
    public float getViewModelY() { return viewModelY; }
    public void setViewModelY(float value) { viewModelY = value; }
    public float getViewModelZ() { return viewModelZ; }
    public void setViewModelZ(float value) { viewModelZ = value; }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public boolean isScoreboardGlassEnabled() {
        return scoreboardGlassEnabled;
    }

    public void setScoreboardGlassEnabled(boolean enabled) {
        scoreboardGlassEnabled = enabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    public int getHudX() {
        return hudX;
    }

    public void setHudX(int hudX) {
        this.hudX = hudX;
    }

    public int getHudY() {
        return hudY;
    }

    public void setHudY(int hudY) {
        this.hudY = hudY;
    }

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = hudScale;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public boolean isDiscordRpcEnabled() {
        return discordRpcEnabled;
    }

    public void setDiscordRpcEnabled(boolean discordRpcEnabled) {
        this.discordRpcEnabled = discordRpcEnabled;
    }

    public String getDiscordApplicationId() {
        return discordApplicationId;
    }

    public void setDiscordApplicationId(String discordApplicationId) {
        this.discordApplicationId = discordApplicationId;
    }

    public String getDiscordLargeImageKey() {
        return discordLargeImageKey;
    }

    public void setDiscordLargeImageKey(String discordLargeImageKey) {
        this.discordLargeImageKey = discordLargeImageKey;
    }

    public String getDiscordLargeImageText() {
        return discordLargeImageText;
    }

    public void setDiscordLargeImageText(String discordLargeImageText) {
        this.discordLargeImageText = discordLargeImageText;
    }

    public boolean isJumpCircleEnabled() {
        return jumpCircleEnabled;
    }

    public void setJumpCircleEnabled(boolean jumpCircleEnabled) {
        this.jumpCircleEnabled = jumpCircleEnabled;
    }

    public String getJumpCircleColor() {
        return jumpCircleColor;
    }

    public void setJumpCircleColor(String jumpCircleColor) {
        this.jumpCircleColor = jumpCircleColor;
    }

    public float getJumpCircleSize() {
        return jumpCircleSize;
    }

    public void setJumpCircleSize(float jumpCircleSize) {
        this.jumpCircleSize = jumpCircleSize;
    }

    public boolean isMediaPlayerEnabled() {
        return mediaPlayerEnabled;
    }

    public void setMediaPlayerEnabled(boolean mediaPlayerEnabled) {
        this.mediaPlayerEnabled = mediaPlayerEnabled;
    }

    public int getMediaPlayerX() {
        return mediaPlayerX;
    }

    public void setMediaPlayerX(int mediaPlayerX) {
        this.mediaPlayerX = mediaPlayerX;
    }

    public int getMediaPlayerY() {
        return mediaPlayerY;
    }

    public void setMediaPlayerY(int mediaPlayerY) {
        this.mediaPlayerY = mediaPlayerY;
    }

    public String getMediaPlayerCorner() {
        return mediaPlayerCorner;
    }

    public void setMediaPlayerCorner(String mediaPlayerCorner) {
        this.mediaPlayerCorner = mediaPlayerCorner;
    }

    public boolean isMediaPlayerAnimated() {
        return mediaPlayerAnimated;
    }

    public void setMediaPlayerAnimated(boolean mediaPlayerAnimated) {
        this.mediaPlayerAnimated = mediaPlayerAnimated;
    }

    public boolean isAspectEnabled() {
        return aspectEnabled;
    }

    public void setAspectEnabled(boolean aspectEnabled) {
        this.aspectEnabled = aspectEnabled;
    }

    public float getAspectStretch() {
        return aspectStretch;
    }

    public void setAspectStretch(float aspectStretch) {
        this.aspectStretch = aspectStretch;
    }

    public boolean isTargetEspEnabled() {
        return targetEspEnabled;
    }

    public void setTargetEspEnabled(boolean targetEspEnabled) {
        this.targetEspEnabled = targetEspEnabled;
    }

    public int getTargetEspTexture() {
        return targetEspTexture;
    }

    public void setTargetEspTexture(int targetEspTexture) {
        this.targetEspTexture = targetEspTexture;
    }

    public String getTargetEspChainColor() {
        return targetEspChainColor;
    }

    public void setTargetEspChainColor(String targetEspChainColor) {
        this.targetEspChainColor = targetEspChainColor;
    }

    public boolean isChinaHeadEnabled() {
        return chinaHeadEnabled;
    }

    public void setChinaHeadEnabled(boolean chinaHeadEnabled) {
        this.chinaHeadEnabled = chinaHeadEnabled;
    }

    public boolean isMotionBlurEnabled() { return motionBlurEnabled; }
    public void setMotionBlurEnabled(boolean value) { motionBlurEnabled = value; }
    public float getMotionBlurStrength() { return motionBlurStrength; }
    public void setMotionBlurStrength(float value) { motionBlurStrength = value; }
    public boolean isBlockHighlightEnabled() { return blockHighlightEnabled; }
    public void setBlockHighlightEnabled(boolean value) { blockHighlightEnabled = value; }
    public String getBlockHighlightColor() { return blockHighlightColor; }
    public void setBlockHighlightColor(String value) { blockHighlightColor = value; }
    public float getBlockHighlightMoveSpeed() { return blockHighlightMoveSpeed; }
    public void setBlockHighlightMoveSpeed(float value) { blockHighlightMoveSpeed = value; }
    public float getBlockHighlightFadeSpeed() { return blockHighlightFadeSpeed; }
    public void setBlockHighlightFadeSpeed(float value) { blockHighlightFadeSpeed = value; }
    public float getBlockHighlightLineWidth() { return blockHighlightLineWidth; }
    public void setBlockHighlightLineWidth(float value) { blockHighlightLineWidth = value; }
    public int getBlockHighlightOpacity() { return blockHighlightOpacity; }
    public void setBlockHighlightOpacity(int value) { blockHighlightOpacity = value; }
    public boolean isBlockHighlightGlow() { return blockHighlightGlow; }
    public void setBlockHighlightGlow(boolean value) { blockHighlightGlow = value; }
    public float getBlockHighlightGlowWidth() { return blockHighlightGlowWidth; }
    public void setBlockHighlightGlowWidth(float value) { blockHighlightGlowWidth = value; }
    public int getBlockHighlightGlowOpacity() { return blockHighlightGlowOpacity; }
    public void setBlockHighlightGlowOpacity(int value) { blockHighlightGlowOpacity = value; }
    public float getBlockHighlightOffset() { return blockHighlightOffset; }
    public void setBlockHighlightOffset(float value) { blockHighlightOffset = value; }
    public boolean isItemPhysicsEnabled() { return itemPhysicsEnabled; }
    public void setItemPhysicsEnabled(boolean value) { itemPhysicsEnabled = value; }
    public boolean isHitParticlesEnabled() { return hitParticlesEnabled; }
    public void setHitParticlesEnabled(boolean value) { hitParticlesEnabled = value; }
    public String getHitParticlesColor() { return hitParticlesColor; }
    public void setHitParticlesColor(String value) { hitParticlesColor = value; }
    public int getHitParticlesCount() { return hitParticlesCount; }
    public void setHitParticlesCount(int value) { hitParticlesCount = value; }
    public float getHitParticlesSize() { return hitParticlesSize; }
    public void setHitParticlesSize(float value) { hitParticlesSize = value; }
    public int getHitParticlesShape() { return hitParticlesShape; }
    public void setHitParticlesShape(int value) { hitParticlesShape = value; }

    public boolean isProjectileTrailEnabled() { return projectileTrailEnabled; }
    public void setProjectileTrailEnabled(boolean value) { projectileTrailEnabled = value; }
    public String getProjectileTrailColor() { return projectileTrailColor; }
    public void setProjectileTrailColor(String value) { projectileTrailColor = value; }
    public float getProjectileTrailDuration() { return projectileTrailDuration; }
    public void setProjectileTrailDuration(float value) { projectileTrailDuration = value; }
    public boolean isProjectileTrailGlow() { return projectileTrailGlow; }
    public void setProjectileTrailGlow(boolean value) { projectileTrailGlow = value; }
    public boolean isDamageIndicatorsEnabled() { return damageIndicatorsEnabled; }
    public void setDamageIndicatorsEnabled(boolean value) { damageIndicatorsEnabled = value; }
    public String getDamageIndicatorsLowColor() { return damageIndicatorsLowColor; }
    public void setDamageIndicatorsLowColor(String value) { damageIndicatorsLowColor = value; }
    public String getDamageIndicatorsHighColor() { return damageIndicatorsHighColor; }
    public void setDamageIndicatorsHighColor(String value) { damageIndicatorsHighColor = value; }
    public float getDamageIndicatorsThreshold() { return damageIndicatorsThreshold; }
    public void setDamageIndicatorsThreshold(float value) { damageIndicatorsThreshold = value; }

    public boolean isBreadcrumbsEnabled() { return breadcrumbsEnabled; }
    public void setBreadcrumbsEnabled(boolean value) { breadcrumbsEnabled = value; }
    public boolean isBreadcrumbsUseAccent() { return breadcrumbsUseAccent; }
    public void setBreadcrumbsUseAccent(boolean value) { breadcrumbsUseAccent = value; }
    public String getBreadcrumbsColor() { return breadcrumbsColor; }
    public void setBreadcrumbsColor(String value) { breadcrumbsColor = value; }
    public float getBreadcrumbsDuration() { return breadcrumbsDuration; }
    public void setBreadcrumbsDuration(float value) { breadcrumbsDuration = value; }
    public float getBreadcrumbsWidth() { return breadcrumbsWidth; }
    public void setBreadcrumbsWidth(float value) { breadcrumbsWidth = value; }
    public int getBreadcrumbsOpacity() { return breadcrumbsOpacity; }
    public void setBreadcrumbsOpacity(int value) { breadcrumbsOpacity = value; }
    public boolean isBreadcrumbsGlow() { return breadcrumbsGlow; }
    public void setBreadcrumbsGlow(boolean value) { breadcrumbsGlow = value; }
    public boolean isBreadcrumbsThroughWalls() { return breadcrumbsThroughWalls; }
    public void setBreadcrumbsThroughWalls(boolean value) { breadcrumbsThroughWalls = value; }

    public boolean isWorldParticlesEnabled() {
        return worldParticlesEnabled;
    }

    public void setWorldParticlesEnabled(boolean worldParticlesEnabled) {
        this.worldParticlesEnabled = worldParticlesEnabled;
    }

    public String getWorldParticlesColor() {
        return worldParticlesColor;
    }

    public void setWorldParticlesColor(String worldParticlesColor) {
        this.worldParticlesColor = worldParticlesColor;
    }

    public int getWorldParticlesCount() {
        return worldParticlesCount;
    }

    public void setWorldParticlesCount(int worldParticlesCount) {
        this.worldParticlesCount = worldParticlesCount;
    }

    public boolean isNoParticlesEnabled() { return noParticlesEnabled; }
    public void setNoParticlesEnabled(boolean value) { noParticlesEnabled = value; }
    public boolean isNoParticlesExplosions() { return noParticlesExplosions; }
    public void setNoParticlesExplosions(boolean value) { noParticlesExplosions = value; }
    public boolean isNoParticlesAmbient() { return noParticlesAmbient; }
    public void setNoParticlesAmbient(boolean value) { noParticlesAmbient = value; }
    public boolean isNoParticlesCombat() { return noParticlesCombat; }
    public void setNoParticlesCombat(boolean value) { noParticlesCombat = value; }

    public boolean isNoWeatherEnabled() {
        return noWeatherEnabled;
    }

    public void setNoWeatherEnabled(boolean noWeatherEnabled) {
        this.noWeatherEnabled = noWeatherEnabled;
    }

    public boolean isNoFireEnabled() {
        return noFireEnabled;
    }

    public void setNoFireEnabled(boolean noFireEnabled) {
        this.noFireEnabled = noFireEnabled;
    }

    public boolean isFullBrightEnabled() {
        return fullBrightEnabled;
    }

    public void setFullBrightEnabled(boolean fullBrightEnabled) {
        this.fullBrightEnabled = fullBrightEnabled;
    }

    public boolean isWorldChangerEnabled() {
        return worldChangerEnabled;
    }

    public void setWorldChangerEnabled(boolean worldChangerEnabled) {
        this.worldChangerEnabled = worldChangerEnabled;
    }

    public String getWorldChangerSkyColor() {
        return worldChangerSkyColor;
    }

    public void setWorldChangerSkyColor(String worldChangerSkyColor) {
        this.worldChangerSkyColor = worldChangerSkyColor;
    }

    public String getWorldChangerFogColor() {
        return worldChangerFogColor;
    }

    public void setWorldChangerFogColor(String worldChangerFogColor) {
        this.worldChangerFogColor = worldChangerFogColor;
    }

    public int getClickGuiTheme() {
        return clickGuiTheme;
    }

    public void setClickGuiTheme(int clickGuiTheme) {
        this.clickGuiTheme = clickGuiTheme;
    }

    public boolean isHealthTagsEnabled() {
        return healthTagsEnabled;
    }

    public void setHealthTagsEnabled(boolean healthTagsEnabled) {
        this.healthTagsEnabled = healthTagsEnabled;
    }

    public boolean isProtectNameEnabled() {
        return protectNameEnabled;
    }

    public void setProtectNameEnabled(boolean protectNameEnabled) {
        this.protectNameEnabled = protectNameEnabled;
    }

    public String getProtectName() {
        return protectName;
    }

    public void setProtectName(String protectName) {
        this.protectName = protectName;
    }

    public boolean isFakePrefixEnabled() {
        return fakePrefixEnabled;
    }

    public void setFakePrefixEnabled(boolean fakePrefixEnabled) {
        this.fakePrefixEnabled = fakePrefixEnabled;
    }

    public String getFakePrefix() {
        return fakePrefix;
    }

    public void setFakePrefix(String fakePrefix) {
        this.fakePrefix = fakePrefix;
    }

    public String getFakePrefixAdditionalNames() {
        return fakePrefixAdditionalNames;
    }

    public void setFakePrefixAdditionalNames(String fakePrefixAdditionalNames) {
        this.fakePrefixAdditionalNames = fakePrefixAdditionalNames;
    }

    public boolean isHitBoxEnabled() {
        return hitBoxEnabled;
    }

    public void setHitBoxEnabled(boolean hitBoxEnabled) {
        this.hitBoxEnabled = hitBoxEnabled;
    }

    public String getHitBoxColor() {
        return hitBoxColor;
    }

    public void setHitBoxColor(String hitBoxColor) {
        this.hitBoxColor = hitBoxColor;
    }

    public boolean isHitColorEnabled() {
        return hitColorEnabled;
    }

    public void setHitColorEnabled(boolean hitColorEnabled) {
        this.hitColorEnabled = hitColorEnabled;
    }

    public String getHitColorColor() {
        return hitColorColor;
    }

    public void setHitColorColor(String hitColorColor) {
        this.hitColorColor = hitColorColor;
    }

    public int getHitColorOpacity() {
        return hitColorOpacity;
    }

    public void setHitColorOpacity(int hitColorOpacity) {
        this.hitColorOpacity = hitColorOpacity;
    }

    public boolean isCrosshairEnabled() {
        return crosshairEnabled;
    }

    public void setCrosshairEnabled(boolean crosshairEnabled) {
        this.crosshairEnabled = crosshairEnabled;
    }

    public String getCrosshairColor() {
        return crosshairColor;
    }

    public void setCrosshairColor(String crosshairColor) {
        this.crosshairColor = crosshairColor;
    }

    public String getCrosshairPixels() {
        return crosshairPixels;
    }

    public void setCrosshairPixels(String crosshairPixels) {
        this.crosshairPixels = crosshairPixels;
    }

    public boolean isBetterNearEnabled() {
        return betterNearEnabled;
    }

    public void setBetterNearEnabled(boolean betterNearEnabled) {
        this.betterNearEnabled = betterNearEnabled;
    }

    public int getBetterNearRadius() {
        return betterNearRadius;
    }

    public void setBetterNearRadius(int betterNearRadius) {
        this.betterNearRadius = betterNearRadius;
    }

    public int getBetterNearX() {
        return betterNearX;
    }

    public void setBetterNearX(int betterNearX) {
        this.betterNearX = betterNearX;
    }

    public int getBetterNearY() {
        return betterNearY;
    }

    public void setBetterNearY(int betterNearY) {
        this.betterNearY = betterNearY;
    }

    public boolean isRainbowEnabled() {
        return rainbowEnabled;
    }

    public void setRainbowEnabled(boolean rainbowEnabled) {
        this.rainbowEnabled = rainbowEnabled;
    }

    public int getRainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(int rainbowSpeed) {
        this.rainbowSpeed = rainbowSpeed;
    }

    public boolean isHitSoundEnabled() {
        return hitSoundEnabled;
    }

    public void setHitSoundEnabled(boolean hitSoundEnabled) {
        this.hitSoundEnabled = hitSoundEnabled;
    }

    public int getHitSoundIndex() {
        return hitSoundIndex;
    }

    public void setHitSoundIndex(int hitSoundIndex) {
        this.hitSoundIndex = hitSoundIndex;
    }

    public int getHitSoundVolume() {
        return hitSoundVolume;
    }

    public void setHitSoundVolume(int hitSoundVolume) {
        this.hitSoundVolume = hitSoundVolume;
    }

    public void sanitize() {
        zoomFactor = finiteClamp(zoomFactor, 1.5F, 12F, 4F);
        chatFilterWords = boundedText(chatFilterWords, 512);
        chatHighlightWords = boundedText(chatHighlightWords, 512);
        if (chatHighlightColor == null || !chatHighlightColor.matches("^#[0-9a-fA-F]{6}$")) chatHighlightColor = "#FFB34D";
        if (waypoints == null) waypoints = new java.util.ArrayList<>();
        waypoints.removeIf(point -> !validWaypoint(point));
        for (Waypoint point : waypoints) if (point.color == null || !point.color.matches("^#[0-9a-fA-F]{6}$")) point.color = "#FF9B38";
        if (waypoints.size() > 512) waypoints = new java.util.ArrayList<>(waypoints.subList(0, 512));
        if (!validWaypoint(deathPoint)) deathPoint = null;
        footballGoalScale = finiteClamp(footballGoalScale, .5F, 2F, 1F);
        footballGoalkeeperDifficulty = Math.max(0, Math.min(2, footballGoalkeeperDifficulty));
        footballKickPower = finiteClamp(footballKickPower, .3F, 2F, 1F);
        footballChargeSpeed = finiteClamp(footballChargeSpeed, .3F, 3F, 1F);
        basketballThrowPower = finiteClamp(basketballThrowPower, 0.3F, 2F, 1F);
        basketballChargeSpeed = finiteClamp(basketballChargeSpeed, 0.3F, 3F, 1F);
        basketballBounce = finiteClamp(basketballBounce, 0.1F, 2F, 1F);
        basketballGravity = finiteClamp(basketballGravity, 2F, 25F, 9.81F);
        basketballDribbleSpeed = finiteClamp(basketballDribbleSpeed, 0.3F, 2F, 1F);
        basketballBallScale = finiteClamp(basketballBallScale, 0.5F, 1.5F, 1F);
        basketballVolume = finiteClamp(basketballVolume, 0F, 1F, 0.7F);
        basketballPassPower=finiteClamp(basketballPassPower,6F,30F,13F);
        basketballSpin=finiteClamp(basketballSpin,0F,3F,1F);
        animationsDuration = Math.max(50, Math.min(600, animationsDuration));
        enchantGlintMode = Math.max(0, Math.min(4, enchantGlintMode));
        lockSlotMask &= (1L << 41) - 1L;
        keystrokesX = Math.max(-1, keystrokesX); keystrokesY = Math.max(-1, keystrokesY);
        inventoryHudX = Math.max(-1, inventoryHudX); inventoryHudY = Math.max(-1, inventoryHudY);
        pvpSafeSeconds = Math.max(1, Math.min(300, pvpSafeSeconds));
        customTimeTicks = Math.max(0, Math.min(23999, customTimeTicks));
        itemScaling = Float.isFinite(itemScaling) ? clamp(itemScaling, 0.2F, 2.0F) : 1.0F;
        smallShieldScale = Float.isFinite(smallShieldScale) ? clamp(smallShieldScale, 0.2F, 1.0F) : 0.65F;
        viewModelX = Float.isFinite(viewModelX) ? clamp(viewModelX, -1F, 1F) : 0F;
        viewModelY = Float.isFinite(viewModelY) ? clamp(viewModelY, -1F, 1F) : 0F;
        viewModelZ = Float.isFinite(viewModelZ) ? clamp(viewModelZ, -1F, 1F) : 0F;
        hudScale = clamp(hudScale, 0.5F, 4.0F);
        if (hudElementScales == null) hudElementScales = new java.util.HashMap<>();
        hudElementScales.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        hudElementScales.replaceAll((key, value) -> Float.isFinite(value) ? clamp(value, .35F, 4F) : 1F);
        clickGuiTheme = Math.max(0, Math.min(4, clickGuiTheme));
        if (favoriteModules == null) favoriteModules = new java.util.ArrayList<>();
        favoriteModules.removeIf(value -> value == null || value.length() > 64);

        if (accentColor == null || !accentColor.matches("^#[0-9a-fA-F]{6}$")) {
            accentColor = "#FF7C00";
        }

        if (discordApplicationId == null || discordApplicationId.isBlank()) {
            discordApplicationId = DEFAULT_DISCORD_APPLICATION_ID;
        }

        if (discordLargeImageKey == null || discordLargeImageKey.isBlank()) {
            discordLargeImageKey = "orion_icon";
        }

        if (discordLargeImageText == null || discordLargeImageText.isBlank()) {
            discordLargeImageText = "Orion Client";
        }

        if (jumpCircleColor == null || !jumpCircleColor.matches("^#[0-9a-fA-F]{6}$")) {
            jumpCircleColor = "#FF7C00";
        }

        jumpCircleSize = clamp(jumpCircleSize, 0.25F, 3.0F);

        mediaPlayerX = Math.max(0, mediaPlayerX);
        mediaPlayerY = Math.max(0, mediaPlayerY);
        if (!"TOP_LEFT".equals(mediaPlayerCorner) && !"TOP_RIGHT".equals(mediaPlayerCorner)) {
            mediaPlayerCorner = "TOP_LEFT";
        }
        aspectStretch = clamp(aspectStretch, 0.05F, 2.0F);
        targetEspTexture = Math.max(0, targetEspTexture);
        if (targetEspChainColor == null || !targetEspChainColor.matches("^#[0-9a-fA-F]{6}$")) {
            targetEspChainColor = "#F5F5FF";
        }

        if (worldParticlesColor == null || !worldParticlesColor.matches("^#[0-9a-fA-F]{6}$")) {
            worldParticlesColor = "#FF7C00";
        }

        motionBlurStrength = Float.isFinite(motionBlurStrength) ? clamp(motionBlurStrength, 0.0F, 1.0F) : 0.5F;
        if (blockHighlightColor == null || !blockHighlightColor.matches("^#[0-9a-fA-F]{6}$")) blockHighlightColor = "#FF7C00";
        blockHighlightMoveSpeed = Float.isFinite(blockHighlightMoveSpeed) ? clamp(blockHighlightMoveSpeed, 1.0F, 40.0F) : 18.0F;
        blockHighlightFadeSpeed = Float.isFinite(blockHighlightFadeSpeed) ? clamp(blockHighlightFadeSpeed, 1.0F, 40.0F) : 12.0F;
        blockHighlightLineWidth = Float.isFinite(blockHighlightLineWidth) ? clamp(blockHighlightLineWidth, 0.5F, 5.0F) : 1.6F;
        blockHighlightOpacity = Math.max(5, Math.min(100, blockHighlightOpacity));
        blockHighlightGlowWidth = Float.isFinite(blockHighlightGlowWidth) ? clamp(blockHighlightGlowWidth, 1.0F, 16.0F) : 6.0F;
        blockHighlightGlowOpacity = Math.max(0, Math.min(100, blockHighlightGlowOpacity));
        blockHighlightOffset = Float.isFinite(blockHighlightOffset) ? clamp(blockHighlightOffset, 0.0F, 0.1F) : 0.003F;
        if (hitParticlesColor == null || !hitParticlesColor.matches("^#[0-9a-fA-F]{6}$")) hitParticlesColor = "#FF7C00";
        hitParticlesCount = Math.max(1, Math.min(64, hitParticlesCount));
        hitParticlesSize = Float.isFinite(hitParticlesSize) ? clamp(hitParticlesSize, 0.03F, 0.3F) : 0.10F;
        hitParticlesShape = Math.max(0, Math.min(2, hitParticlesShape));
        if (projectileTrailColor == null || !projectileTrailColor.matches("^#[0-9a-fA-F]{6}$")) projectileTrailColor = "#FF7C00";
        projectileTrailDuration = Float.isFinite(projectileTrailDuration) ? clamp(projectileTrailDuration, 0.25F, 5.0F) : 2.0F;
        if (damageIndicatorsLowColor == null || !damageIndicatorsLowColor.matches("^#[0-9a-fA-F]{6}$")) damageIndicatorsLowColor = "#FFFFFF";
        if (damageIndicatorsHighColor == null || !damageIndicatorsHighColor.matches("^#[0-9a-fA-F]{6}$")) damageIndicatorsHighColor = "#FF4936";
        damageIndicatorsThreshold = Float.isFinite(damageIndicatorsThreshold) ? clamp(damageIndicatorsThreshold, 1.0F, 40.0F) : 10.0F;
        if (breadcrumbsColor == null || !breadcrumbsColor.matches("^#[0-9a-fA-F]{6}$")) {
            breadcrumbsColor = "#FF7C00";
        }
        breadcrumbsDuration = Float.isFinite(breadcrumbsDuration) ? clamp(breadcrumbsDuration, 0.5F, 8.0F) : 3.0F;
        breadcrumbsWidth = Float.isFinite(breadcrumbsWidth) ? clamp(breadcrumbsWidth, 0.02F, 0.3F) : 0.08F;
        breadcrumbsOpacity = Math.max(5, Math.min(100, breadcrumbsOpacity));

        worldParticlesCount = Math.max(1, Math.min(2500, worldParticlesCount));

        if (worldChangerSkyColor == null || !worldChangerSkyColor.matches("^#[0-9a-fA-F]{6}$")) {
            worldChangerSkyColor = "#64D9FF";
        }

        if (worldChangerFogColor == null || !worldChangerFogColor.matches("^#[0-9a-fA-F]{6}$")) {
            worldChangerFogColor = "#14111F";
        }

        if (hitBoxColor == null || !hitBoxColor.matches("^#[0-9a-fA-F]{6}$")) {
            hitBoxColor = "#FF7C00";
        }

        if (hitColorColor == null || !hitColorColor.matches("^#[0-9a-fA-F]{6}$")) {
            hitColorColor = "#EF4444";
        }
        hitColorOpacity = Math.max(5, Math.min(100, hitColorOpacity));

        if (crosshairColor == null || !crosshairColor.matches("^#[0-9a-fA-F]{6}$")) {
            crosshairColor = "#F5F5FF";
        }

        if (crosshairPixels == null || crosshairPixels.length() != CROSSHAIR_GRID * CROSSHAIR_GRID
                || !crosshairPixels.matches("^[01]+$")) {
            crosshairPixels = defaultCrosshairPixels();
        }

        betterNearRadius = Math.max(10, Math.min(200, betterNearRadius));
        betterNearX = Math.max(-1, betterNearX);
        betterNearY = Math.max(-1, betterNearY);
        rainbowSpeed = Math.max(1, Math.min(10, rainbowSpeed));
        hitSoundIndex = Math.max(0, Math.min(6, hitSoundIndex));
        hitSoundVolume = Math.max(0, Math.min(100, hitSoundVolume));

        if (protectName == null) {
            protectName = "Player";
        }
        protectName = protectName.trim();
        if (protectName.length() > 16) {
            protectName = protectName.substring(0, 16);
        }
        if (protectName.isEmpty()) {
            protectName = "Player";
        }

        if (fakePrefix == null || fakePrefix.isBlank()) {
            fakePrefix = "{\"text\":\"ORION \",\"color\":\"#FF7C00\",\"bold\":true}";
        } else if (fakePrefix.length() > 2048) {
            fakePrefix = fakePrefix.substring(0, 2048);
        }

        if (fakePrefixAdditionalNames == null) {
            fakePrefixAdditionalNames = "";
        } else if (fakePrefixAdditionalNames.length() > 512) {
            fakePrefixAdditionalNames = fakePrefixAdditionalNames.substring(0, 512);
        }
    }

    private static boolean validWaypoint(Waypoint point) {
        return point != null && point.name != null && !point.name.isBlank() && point.name.length() <= 32
                && point.world != null && !point.world.isBlank() && point.world.length() <= 1024
                && Math.abs((long) point.x) <= 30000000L && Math.abs((long) point.z) <= 30000000L
                && point.y >= -2048 && point.y <= 2048;
    }
    private static String boundedText(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? clamp(value, min, max) : fallback;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}
