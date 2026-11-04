package ru.orion.client.config;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/** Меняет только графику и визуальные эффекты, сохраняя исходные значения для возврата. */
public final class OrionVisualPresets {
    public enum Profile {
        QUALITY("Качество", "Чёткая графика и эффекты клиента", "Контуры, частицы, кристаллы и анимации", 16, 8, 1.25, 5, .55, 1, .16F, .06F, .16F),
        PERFORMANCE("Производительность", "Больше кадров и минимум декора", "Дорогие эффекты и стеклянный HUD отключены", 8, 5, .75, 1, .65, 0, 0F, 0F, 0F),
        BALANCE("Баланс", "Лёгкие эффекты и чистый интерфейс", "Частицы ударов, круги и простая подсветка", 12, 6, 1.0, 3, .55, 0, .10F, .025F, .06F),
        BEAUTY("Красота", "Полный визуальный образ клиента", "Крылья, ореол, следы, сияние и закат", 16, 8, 1.25, 5, .45, 2, .22F, .18F, .38F);
        public final String title, description, detail;
        public final int renderDistance, simulationDistance, biomeBlend, mirageQuality;
        public final double entityScale, gamma;
        public final float reflection, bloom, grade;
        public String effectsSummary() {
            return switch (this) {
                case QUALITY -> "Частицы ударов · Кристаллы цели · Эффекты убийств · Анимации";
                case PERFORMANCE -> "Отключены эффекты мира, следы, свечение, косметика и Glass";
                case BALANCE -> "Лёгкие частицы · Маркер цели · Круги прыжка · Короткие следы";
                case BEAUTY -> "Частицы мира и ударов · Следы · Ореол · Ангельские крылья";
            };
        }
        Profile(String title, String description, String detail, int render, int simulation, double entity, int blend, double gamma,
                int quality, float reflection, float bloom, float grade) {
            this.title = title; this.description = description; this.detail = detail; this.renderDistance = render;
            this.simulationDistance = simulation; this.entityScale = entity; this.biomeBlend = blend; this.gamma = gamma;
            this.mirageQuality = quality; this.reflection = reflection; this.bloom = bloom; this.grade = grade;
        }
    }
    private final OrionConfigManager manager;
    private VideoState originalVideo;
    private VisualState originalVisual;
    private String originalAppliedPreset;
    private String status = "После применения можно вернуть настройки этого сеанса";
    public OrionVisualPresets(OrionConfigManager manager) {
        this.manager = manager;
        var config = manager.getConfig();
        if (config.getAppliedVisualPreset().isEmpty()) {
            Profile inferred = inferApplied(config);
            if (inferred != null) config.setAppliedVisualPreset(inferred.name());
        }
        if (config.getVisualPresetSelection().isEmpty()) config.setVisualPresetSelection(
                config.getAppliedVisualPreset().isEmpty() ? Profile.BALANCE.name() : config.getAppliedVisualPreset());
        if (!config.getAppliedVisualPreset().isEmpty()) status = "Последний применённый: «" + Profile.valueOf(config.getAppliedVisualPreset()).title + "»";
    }
    public Profile selectedProfile() { return Profile.valueOf(manager.getConfig().getVisualPresetSelection()); }
    public boolean wasApplied(Profile profile) { return profile.name().equals(manager.getConfig().getAppliedVisualPreset()); }
    public void select(Profile profile) { manager.getConfig().setVisualPresetSelection(profile.name()); manager.save(); }
    private static Profile inferApplied(OrionConfig c) {
        for (Profile p : Profile.values()) {
            boolean fast = p == Profile.PERFORMANCE, beauty = p == Profile.BEAUTY;
            if (c.isMirageEnabled() == !fast && c.getMirageQuality() == p.mirageQuality
                    && Math.abs(c.getMirageStrength() - p.reflection) < .0001F && Math.abs(c.getMirageBloom() - p.bloom) < .0001F
                    && Math.abs(c.getMirageColorGrade() - p.grade) < .0001F && Math.abs(c.getMirageRoughness() - .6F) < .0001F
                    && !c.isFullBrightEnabled() && (!c.isWorldChangerEnabled() || beauty) && !c.isRainbowEnabled()
                    && c.isCustomTimeEnabled() == beauty && (!beauty || c.getCustomTimeTicks() == 11000)
                    && c.isWorldParticlesEnabled() == beauty && c.isNoParticlesEnabled() == fast) return p;
        }
        return null;
    }
    public String status() { return status; }
    public boolean canRestore() { return originalVideo != null; }
    public void apply(Profile profile, Minecraft client) {
        if (client == null || client.options == null) { status = "Настройки игры ещё не готовы"; return; }
        if (originalVideo == null) {
            originalVideo = VideoState.read(client.options); originalVisual = VisualState.read(manager.getConfig());
            originalAppliedPreset = manager.getConfig().getAppliedVisualPreset();
        }
        boolean oldHalo = manager.getConfig().isHaloEnabled(), oldWings = manager.getConfig().isWingsEnabled();
        int oldWingsStyle = manager.getConfig().getWingsStyle();
        configure(manager.getConfig(), profile);
        Options o = client.options;
        boolean fast = profile == Profile.PERFORMANCE;
        o.renderDistance().set(profile.renderDistance); o.simulationDistance().set(profile.simulationDistance);
        o.entityDistanceScaling().set(profile.entityScale); o.ambientOcclusion().set(!fast);
        o.cloudStatus().set(fast ? CloudStatus.OFF : CloudStatus.FANCY);
        o.particles().set(fast ? ParticleStatus.MINIMAL : profile == Profile.BALANCE ? ParticleStatus.DECREASED : ParticleStatus.ALL);
        o.biomeBlendRadius().set(profile.biomeBlend); o.gamma().set(profile.gamma); o.mipmapLevels().set(4);
        o.cutoutLeaves().set(!fast); o.improvedTransparency().set(!fast); o.vignette().set(!fast);
        manager.getConfig().setVisualPresetSelection(profile.name());
        manager.getConfig().setAppliedVisualPreset(profile.name());
        o.save(); manager.save();
        refreshCosmetics(oldHalo, oldWings, oldWingsStyle);
        status = "Применён пресет «" + profile.title + "»";
    }
    public void restore(Minecraft client) {
        if (originalVideo == null || client == null || client.options == null) { status = "Сначала примените один из пресетов"; return; }
        boolean oldHalo = manager.getConfig().isHaloEnabled(), oldWings = manager.getConfig().isWingsEnabled();
        int oldWingsStyle = manager.getConfig().getWingsStyle();
        originalVideo.apply(client.options); originalVisual.apply(manager.getConfig());
        manager.getConfig().setAppliedVisualPreset(originalAppliedPreset);
        if (originalAppliedPreset != null && !originalAppliedPreset.isEmpty()) manager.getConfig().setVisualPresetSelection(originalAppliedPreset);
        client.options.save(); manager.save(); originalVideo = null; originalVisual = null; originalAppliedPreset = null;
        refreshCosmetics(oldHalo, oldWings, oldWingsStyle);
        status = "Ваши исходные настройки восстановлены";
    }
    private void refreshCosmetics(boolean oldHalo, boolean oldWings, int oldStyle) {
        var config = manager.getConfig();
        if (oldHalo != config.isHaloEnabled() || oldWings != config.isWingsEnabled() || oldStyle != config.getWingsStyle())
            ru.orion.client.effect.OrionBadgePresence.requestRefresh();
    }

    static void configure(OrionConfig config, Profile profile) {
        boolean fast = profile == Profile.PERFORMANCE, balanced = profile == Profile.BALANCE, beauty = profile == Profile.BEAUTY;
        boolean detailed = profile == Profile.QUALITY || beauty;
        String accent = beauty ? "#F2CDA0" : profile == Profile.QUALITY ? "#8FCFEC" : balanced ? "#FF9B38" : "#B3BCC8";
        config.setAccentColor(accent);
        config.setMirageEnabled(!fast); config.setMirageStrength(profile.reflection);
        config.setMirageQuality(profile.mirageQuality); config.setMirageRoughness(.6F);
        config.setMirageBloom(profile.bloom); config.setMirageColorGrade(profile.grade);
        config.setFullBrightEnabled(false); config.setWorldChangerEnabled(beauty); config.setRainbowEnabled(false);
        config.setWorldChangerSkyColor("#9CAFD0"); config.setWorldChangerFogColor("#C8B5A3");
        config.setCustomTimeEnabled(beauty); config.setCustomTimeTicks(beauty ? 11000 : 6000);
        config.setNoWeatherEnabled(fast); config.setNoFireEnabled(!beauty);
        config.setNoParticlesEnabled(fast); config.setNoParticlesExplosions(true);
        config.setNoParticlesAmbient(true); config.setNoParticlesCombat(false);
        config.setWorldParticlesEnabled(beauty); config.setWorldParticlesCount(beauty ? 72 : 24);
        config.setWorldParticlesColor(accent);
        config.setMotionBlurEnabled(beauty); config.setMotionBlurStrength(beauty ? .12F : .08F);
        config.setBlockHighlightEnabled(!fast); config.setBlockHighlightColor(accent);
        config.setBlockHighlightMoveSpeed(beauty ? 12F : 18F); config.setBlockHighlightFadeSpeed(beauty ? 9F : 15F);
        config.setBlockHighlightLineWidth(beauty ? 1.8F : balanced ? 1.2F : 1.5F);
        config.setBlockHighlightOpacity(beauty ? 82 : 68); config.setBlockHighlightGlow(detailed);
        config.setBlockHighlightGlowWidth(beauty ? 7F : 4F); config.setBlockHighlightGlowOpacity(beauty ? 18 : 10);
        config.setTargetEspEnabled(!fast); config.setTargetEspTexture(beauty ? 1 : profile == Profile.QUALITY ? 3 : 4);
        config.setTargetEspChainColor(accent);
        config.setHitColorEnabled(!fast); config.setHitColorColor(accent); config.setHitColorOpacity(beauty ? 22 : 16);
        config.setHitParticlesEnabled(!fast); config.setHitParticlesColor(accent);
        config.setHitParticlesCount(beauty ? 22 : balanced ? 8 : fast ? 4 : 12);
        config.setHitParticlesSize(beauty ? .085F : balanced ? .055F : .07F); config.setHitParticlesShape(0);
        config.setKillEffectsEnabled(detailed); config.setKillEffectsStyle(beauty ? 5 : 3);
        config.setKillEffectsQuality(beauty ? 2 : balanced || fast ? 0 : 1); config.setKillEffectsLimit(detailed ? 2 : 1);
        config.setKillEffectsSize(beauty ? 1.05F : .85F); config.setKillEffectsSpeed(beauty ? .9F : 1.15F);
        config.setKillEffectsParticles(beauty ? .75F : fast ? 0F : .4F); config.setKillEffectsDistance(beauty ? 64 : 48);
        config.setJumpCircleEnabled(!fast); config.setJumpCircleColor(accent); config.setJumpCircleSize(beauty ? 1.1F : balanced ? .7F : .9F);
        config.setBreadcrumbsEnabled(beauty); config.setBreadcrumbsUseAccent(true); config.setBreadcrumbsColor(accent);
        config.setBreadcrumbsDuration(beauty ? 1.8F : 1F); config.setBreadcrumbsWidth(beauty ? .055F : .035F);
        config.setBreadcrumbsOpacity(beauty ? 52 : 36); config.setBreadcrumbsGlow(detailed); config.setBreadcrumbsThroughWalls(false);
        config.setProjectileTrailEnabled(!fast); config.setProjectileTrailColor(accent);
        config.setProjectileTrailDuration(beauty ? 1.6F : balanced ? .7F : 1.1F); config.setProjectileTrailGlow(detailed);
        config.setDamageIndicatorsEnabled(!fast); config.setDamageIndicatorsLowColor("#EEE6D6");
        config.setDamageIndicatorsHighColor(accent); config.setDamageIndicatorsThreshold(10F);
        config.setAnimationsEnabled(!fast); config.setAnimationsDuration(beauty ? 240 : balanced ? 130 : fast ? 80 : 180);
        config.setEnchantGlintEnabled(!fast); config.setEnchantGlintMode(beauty ? 4 : profile == Profile.QUALITY ? 2 : fast ? 1 : 0);
        config.setItemPhysicsEnabled(!fast); config.setItemScalingEnabled(!fast); config.setItemScaling(beauty ? .8F : .9F);
        config.setSmallShieldEnabled(!fast); config.setSmallShieldScale(beauty ? .55F : .65F);
        config.setViewModelEnabled(!fast); config.setViewModelX(beauty ? .05F : 0F);
        config.setViewModelY(beauty ? -.12F : -.05F); config.setViewModelZ(beauty ? -.15F : -.08F);
        config.setHaloEnabled(beauty); config.setWingsEnabled(beauty); config.setWingsStyle(0); config.setChinaHeadEnabled(false);
        config.setHealthTagsEnabled(!fast); config.setArmorIndicatorEnabled(!fast); config.setSelfTagEnabled(beauty);
        config.setCrosshairEnabled(!fast); config.setCrosshairColor(accent);
        config.setScoreboardGlassEnabled(detailed);
        for (String id : HUD_STYLES) config.setHudElementGlass(id, detailed);
    }

    private static final String[] HUD_STYLES = {"hud_watermark", "hud_status_panels", "inventory_hud", "hud_better_near", "media_player_hud", "basketball_hud", "football_hud"};

    private record VideoState(int render, int simulation, double entity, boolean ao, CloudStatus clouds, ParticleStatus particles,
                              int blend, double gamma, int mipmap, boolean cutout, boolean transparency, boolean vignette) {
        static VideoState read(Options o) { return new VideoState(o.renderDistance().get(), o.simulationDistance().get(), o.entityDistanceScaling().get(),
                o.ambientOcclusion().get(), o.cloudStatus().get(), o.particles().get(), o.biomeBlendRadius().get(), o.gamma().get(), o.mipmapLevels().get(),
                o.cutoutLeaves().get(), o.improvedTransparency().get(), o.vignette().get()); }
        void apply(Options o) {
            o.renderDistance().set(render); o.simulationDistance().set(simulation); o.entityDistanceScaling().set(entity);
            o.ambientOcclusion().set(ao); o.cloudStatus().set(clouds); o.particles().set(particles); o.biomeBlendRadius().set(blend);
            o.gamma().set(gamma); o.mipmapLevels().set(mipmap); o.cutoutLeaves().set(cutout); o.improvedTransparency().set(transparency); o.vignette().set(vignette);
        }
    }
    /** Типизированные снимки только затронутых настроек; остальные функции не перезаписываются. */
    private record VisualState(java.util.List<java.util.function.Consumer<OrionConfig>> values) {
        private static <T> java.util.function.Consumer<OrionConfig> saved(T value, java.util.function.BiConsumer<OrionConfig, T> setter) {
            return config -> setter.accept(config, value);
        }
        static VisualState read(OrionConfig c) {
            return new VisualState(java.util.List.of(
                    saved(c.getAccentColor(), OrionConfig::setAccentColor),
                    saved(c.isMirageEnabled(), OrionConfig::setMirageEnabled),
                    saved(c.getMirageStrength(), OrionConfig::setMirageStrength),
                    saved(c.getMirageQuality(), OrionConfig::setMirageQuality),
                    saved(c.getMirageRoughness(), OrionConfig::setMirageRoughness),
                    saved(c.getMirageBloom(), OrionConfig::setMirageBloom),
                    saved(c.getMirageColorGrade(), OrionConfig::setMirageColorGrade),
                    saved(c.isFullBrightEnabled(), OrionConfig::setFullBrightEnabled),
                    saved(c.isWorldChangerEnabled(), OrionConfig::setWorldChangerEnabled),
                    saved(c.isRainbowEnabled(), OrionConfig::setRainbowEnabled),
                    saved(c.getWorldChangerSkyColor(), OrionConfig::setWorldChangerSkyColor),
                    saved(c.getWorldChangerFogColor(), OrionConfig::setWorldChangerFogColor),
                    saved(c.isCustomTimeEnabled(), OrionConfig::setCustomTimeEnabled),
                    saved(c.getCustomTimeTicks(), OrionConfig::setCustomTimeTicks),
                    saved(c.isNoWeatherEnabled(), OrionConfig::setNoWeatherEnabled),
                    saved(c.isNoFireEnabled(), OrionConfig::setNoFireEnabled),
                    saved(c.isNoParticlesEnabled(), OrionConfig::setNoParticlesEnabled),
                    saved(c.isNoParticlesExplosions(), OrionConfig::setNoParticlesExplosions),
                    saved(c.isNoParticlesAmbient(), OrionConfig::setNoParticlesAmbient),
                    saved(c.isNoParticlesCombat(), OrionConfig::setNoParticlesCombat),
                    saved(c.isWorldParticlesEnabled(), OrionConfig::setWorldParticlesEnabled),
                    saved(c.getWorldParticlesCount(), OrionConfig::setWorldParticlesCount),
                    saved(c.getWorldParticlesColor(), OrionConfig::setWorldParticlesColor),
                    saved(c.isMotionBlurEnabled(), OrionConfig::setMotionBlurEnabled),
                    saved(c.getMotionBlurStrength(), OrionConfig::setMotionBlurStrength),
                    saved(c.isBlockHighlightEnabled(), OrionConfig::setBlockHighlightEnabled),
                    saved(c.getBlockHighlightColor(), OrionConfig::setBlockHighlightColor),
                    saved(c.getBlockHighlightMoveSpeed(), OrionConfig::setBlockHighlightMoveSpeed),
                    saved(c.getBlockHighlightFadeSpeed(), OrionConfig::setBlockHighlightFadeSpeed),
                    saved(c.getBlockHighlightLineWidth(), OrionConfig::setBlockHighlightLineWidth),
                    saved(c.getBlockHighlightOpacity(), OrionConfig::setBlockHighlightOpacity),
                    saved(c.isBlockHighlightGlow(), OrionConfig::setBlockHighlightGlow),
                    saved(c.getBlockHighlightGlowWidth(), OrionConfig::setBlockHighlightGlowWidth),
                    saved(c.getBlockHighlightGlowOpacity(), OrionConfig::setBlockHighlightGlowOpacity),
                    saved(c.isTargetEspEnabled(), OrionConfig::setTargetEspEnabled),
                    saved(c.getTargetEspTexture(), OrionConfig::setTargetEspTexture),
                    saved(c.getTargetEspChainColor(), OrionConfig::setTargetEspChainColor),
                    saved(c.isHitColorEnabled(), OrionConfig::setHitColorEnabled),
                    saved(c.getHitColorColor(), OrionConfig::setHitColorColor),
                    saved(c.getHitColorOpacity(), OrionConfig::setHitColorOpacity),
                    saved(c.isHitParticlesEnabled(), OrionConfig::setHitParticlesEnabled),
                    saved(c.getHitParticlesColor(), OrionConfig::setHitParticlesColor),
                    saved(c.getHitParticlesCount(), OrionConfig::setHitParticlesCount),
                    saved(c.getHitParticlesSize(), OrionConfig::setHitParticlesSize),
                    saved(c.getHitParticlesShape(), OrionConfig::setHitParticlesShape),
                    saved(c.isKillEffectsEnabled(), OrionConfig::setKillEffectsEnabled),
                    saved(c.getKillEffectsStyle(), OrionConfig::setKillEffectsStyle),
                    saved(c.getKillEffectsQuality(), OrionConfig::setKillEffectsQuality),
                    saved(c.getKillEffectsLimit(), OrionConfig::setKillEffectsLimit),
                    saved(c.getKillEffectsSize(), OrionConfig::setKillEffectsSize),
                    saved(c.getKillEffectsSpeed(), OrionConfig::setKillEffectsSpeed),
                    saved(c.getKillEffectsParticles(), OrionConfig::setKillEffectsParticles),
                    saved(c.getKillEffectsDistance(), OrionConfig::setKillEffectsDistance),
                    saved(c.isJumpCircleEnabled(), OrionConfig::setJumpCircleEnabled),
                    saved(c.getJumpCircleColor(), OrionConfig::setJumpCircleColor),
                    saved(c.getJumpCircleSize(), OrionConfig::setJumpCircleSize),
                    saved(c.isBreadcrumbsEnabled(), OrionConfig::setBreadcrumbsEnabled),
                    saved(c.isBreadcrumbsUseAccent(), OrionConfig::setBreadcrumbsUseAccent),
                    saved(c.getBreadcrumbsColor(), OrionConfig::setBreadcrumbsColor),
                    saved(c.getBreadcrumbsDuration(), OrionConfig::setBreadcrumbsDuration),
                    saved(c.getBreadcrumbsWidth(), OrionConfig::setBreadcrumbsWidth),
                    saved(c.getBreadcrumbsOpacity(), OrionConfig::setBreadcrumbsOpacity),
                    saved(c.isBreadcrumbsGlow(), OrionConfig::setBreadcrumbsGlow),
                    saved(c.isBreadcrumbsThroughWalls(), OrionConfig::setBreadcrumbsThroughWalls),
                    saved(c.isProjectileTrailEnabled(), OrionConfig::setProjectileTrailEnabled),
                    saved(c.getProjectileTrailColor(), OrionConfig::setProjectileTrailColor),
                    saved(c.getProjectileTrailDuration(), OrionConfig::setProjectileTrailDuration),
                    saved(c.isProjectileTrailGlow(), OrionConfig::setProjectileTrailGlow),
                    saved(c.isDamageIndicatorsEnabled(), OrionConfig::setDamageIndicatorsEnabled),
                    saved(c.getDamageIndicatorsLowColor(), OrionConfig::setDamageIndicatorsLowColor),
                    saved(c.getDamageIndicatorsHighColor(), OrionConfig::setDamageIndicatorsHighColor),
                    saved(c.getDamageIndicatorsThreshold(), OrionConfig::setDamageIndicatorsThreshold),
                    saved(c.isAnimationsEnabled(), OrionConfig::setAnimationsEnabled),
                    saved(c.getAnimationsDuration(), OrionConfig::setAnimationsDuration),
                    saved(c.isEnchantGlintEnabled(), OrionConfig::setEnchantGlintEnabled),
                    saved(c.getEnchantGlintMode(), OrionConfig::setEnchantGlintMode),
                    saved(c.isItemPhysicsEnabled(), OrionConfig::setItemPhysicsEnabled),
                    saved(c.isItemScalingEnabled(), OrionConfig::setItemScalingEnabled),
                    saved(c.getItemScaling(), OrionConfig::setItemScaling),
                    saved(c.isSmallShieldEnabled(), OrionConfig::setSmallShieldEnabled),
                    saved(c.getSmallShieldScale(), OrionConfig::setSmallShieldScale),
                    saved(c.isViewModelEnabled(), OrionConfig::setViewModelEnabled),
                    saved(c.getViewModelX(), OrionConfig::setViewModelX),
                    saved(c.getViewModelY(), OrionConfig::setViewModelY),
                    saved(c.getViewModelZ(), OrionConfig::setViewModelZ),
                    saved(c.isHaloEnabled(), OrionConfig::setHaloEnabled),
                    saved(c.isWingsEnabled(), OrionConfig::setWingsEnabled),
                    saved(c.getWingsStyle(), OrionConfig::setWingsStyle),
                    saved(c.isChinaHeadEnabled(), OrionConfig::setChinaHeadEnabled),
                    saved(c.isHealthTagsEnabled(), OrionConfig::setHealthTagsEnabled),
                    saved(c.isArmorIndicatorEnabled(), OrionConfig::setArmorIndicatorEnabled),
                    saved(c.isSelfTagEnabled(), OrionConfig::setSelfTagEnabled),
                    saved(c.isCrosshairEnabled(), OrionConfig::setCrosshairEnabled),
                    saved(c.getCrosshairColor(), OrionConfig::setCrosshairColor),
                    saved(c.isScoreboardGlassEnabled(), OrionConfig::setScoreboardGlassEnabled),
                    saved(c.getHudElementGlassOverrides(), OrionConfig::setHudElementGlassOverrides)));
        }
        void apply(OrionConfig config) { values.forEach(value -> value.accept(config)); }
    }
}
