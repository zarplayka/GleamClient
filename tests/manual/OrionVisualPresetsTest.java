package ru.orion.client.config;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.CloudStatus;
import net.minecraft.server.level.ParticleStatus;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Optional;

/** Проверяет применение настоящих OptionInstance, возврат и изоляцию игровых настроек. */
public final class OrionVisualPresetsTest {
    private static Field field(Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static final class TestOptions extends Options {
        int saves;
        private TestOptions() { super(null, new java.io.File(".")); }
        @Override public void save() { saves++; }
    }
    private static Object option(Unsafe unsafe, Object value) throws Exception {
        Object option = unsafe.allocateInstance(OptionInstance.class);
        field(OptionInstance.class, "value").set(option, value);
        field(OptionInstance.class, "initialValue").set(option, value);
        Object validator = Proxy.newProxyInstance(OptionInstance.class.getClassLoader(), new Class<?>[]{OptionInstance.ValueSet.class},
                (proxy, method, args) -> method.getName().equals("validateValue") ? Optional.ofNullable(args[0]) : null);
        field(OptionInstance.class, "values").set(option, validator);
        return option;
    }
    public static void main(String[] args) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        field(Minecraft.class, "instance").set(null, client);
        var options = (TestOptions) unsafe.allocateInstance(TestOptions.class);
        Object[][] settings = {{"renderDistance", 9}, {"simulationDistance", 5}, {"entityDistanceScaling", .85}, {"ambientOcclusion", true},
                {"cloudStatus", CloudStatus.FAST}, {"particles", ParticleStatus.DECREASED}, {"biomeBlendRadius", 2}, {"gamma", .85},
                {"mipmapLevels", 2}, {"cutoutLeaves", true}, {"improvedTransparency", false}, {"vignette", false}};
        for (Object[] setting : settings) field(Options.class, (String) setting[0]).set(options, option(unsafe, setting[1]));
        field(Minecraft.class, "options").set(client, options);
        var manager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        var config = OrionConfig.createDefault(); config.setZoomEnabled(true); config.setFullBrightEnabled(true); config.setClickGuiLastSection("COMBAT");
        field(OrionConfigManager.class, "config").set(manager, config);
        field(OrionConfigManager.class, "configPath").set(manager, Path.of(System.getProperty("java.io.tmpdir"), "orion-preset-test.json"));
        config.setHudElementGlass("hud_watermark", false); config.setHudElementGlass("custom_user_panel", true);
        config.setHaloEnabled(true); config.setWingsEnabled(true); config.setWingsStyle(1);
        config.setHitParticlesColor("#123456"); config.setKillEffectsVolume(17);
        config.setProtectName("Не менять"); config.setWaypointsEnabled(true);
        var presets = manager.getVisualPresets();
        var baseline = new Gson().toJsonTree(config).getAsJsonObject();
        baseline.remove("visualPresetSelection"); baseline.remove("appliedVisualPreset");
        for (var profile : OrionVisualPresets.Profile.values()) {
            presets.apply(profile, client);
            check(options.renderDistance().get() == profile.renderDistance, "render distance applied");
            check(options.simulationDistance().get() == profile.simulationDistance, "simulation distance applied");
            check(options.cutoutLeaves().get() == (profile != OrionVisualPresets.Profile.PERFORMANCE), "leaf quality follows graphics mode");
            check(config.getMirageQuality() == profile.mirageQuality && config.getMirageBloom() == profile.bloom, "effects applied");
            check(config.isMirageEnabled() == (profile != OrionVisualPresets.Profile.PERFORMANCE), "performance disables reflection pass");
            check(config.isWorldParticlesEnabled() == (profile == OrionVisualPresets.Profile.BEAUTY), "beauty ambient particles");
            check(config.isNoParticlesEnabled() == (profile == OrionVisualPresets.Profile.PERFORMANCE), "performance particle filter");
            boolean fast = profile == OrionVisualPresets.Profile.PERFORMANCE;
            boolean beauty = profile == OrionVisualPresets.Profile.BEAUTY;
            boolean detailed = beauty || profile == OrionVisualPresets.Profile.QUALITY;
            check(config.isHitParticlesEnabled() == !fast && config.isBlockHighlightEnabled() == !fast, "hit particles and block outlines configured");
            check(config.isTargetEspEnabled() == !fast && config.isProjectileTrailEnabled() == !fast, "target styles and projectile trails configured");
            check(config.isKillEffectsEnabled() == detailed && config.isKillEffectsEnabled() == config.isHudElementGlass("hud_watermark"), "detailed profiles enable kill effects and glass");
            check(config.isHaloEnabled() == beauty && config.isWingsEnabled() == beauty && config.isBreadcrumbsEnabled() == beauty, "beauty cosmetics and trail");
            check(config.isMotionBlurEnabled() == beauty && config.getMotionBlurStrength() <= .12F, "subtle beauty motion blur");
            check(config.isAnimationsEnabled() == !fast && config.isItemPhysicsEnabled() == !fast && config.isEnchantGlintEnabled() == !fast, "animation and item styles configured");
            check(config.isCrosshairEnabled() == !fast && config.isHealthTagsEnabled() == !fast && config.isArmorIndicatorEnabled() == !fast, "visual information configured");
            check(config.getKillEffectsVolume() == 17 && config.getProtectName().equals("Не менять") && config.isWaypointsEnabled(), "audio privacy and waypoint data isolated");
            check(config.isHudElementGlass("custom_user_panel"), "unrelated HUD style preserved");
            var configured = new Gson().toJsonTree(config); config.sanitize();
            check(configured.equals(new Gson().toJsonTree(config)), "preset values stay within sanitizer bounds");
            check(config.isZoomEnabled() && config.getClickGuiLastSection().equals("COMBAT"), "controls and navigation untouched");
        }
        var gson = new Gson();
        check(config.getVisualPresetSelection().equals("BEAUTY") && config.getAppliedVisualPreset().equals("BEAUTY"), "applied profile stored");
        presets.select(OrionVisualPresets.Profile.QUALITY);
        check(config.getAppliedVisualPreset().equals("BEAUTY"), "preview does not apply another profile");
        var reopenedConfig = gson.fromJson(gson.toJson(config), OrionConfig.class); reopenedConfig.sanitize();
        var reopenedManager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        field(OrionConfigManager.class, "config").set(reopenedManager, reopenedConfig);
        var reopened = reopenedManager.getVisualPresets();
        check(reopened.selectedProfile() == OrionVisualPresets.Profile.QUALITY && reopened.wasApplied(OrionVisualPresets.Profile.BEAUTY), "selection and applied marker survive restart independently");
        check(reopened.status().contains("Красота"), "applied status restored");
        var legacy = OrionConfig.createDefault(); OrionVisualPresets.configure(legacy, OrionVisualPresets.Profile.BEAUTY);
        var legacyManager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        field(OrionConfigManager.class, "config").set(legacyManager, legacy);
        check(legacyManager.getVisualPresets().selectedProfile() == OrionVisualPresets.Profile.BEAUTY, "legacy applied profile inferred");
        presets.restore(client);
        check(options.renderDistance().get() == 9 && options.gamma().get() == .85 && options.mipmapLevels().get() == 2, "original video state restored");
        check(config.isFullBrightEnabled() && !config.isMirageEnabled() && config.isZoomEnabled(), "original visual state restored");
        var rolledBack = gson.toJsonTree(config).getAsJsonObject();
        rolledBack.remove("visualPresetSelection"); rolledBack.remove("appliedVisualPreset");
        check(baseline.equals(rolledBack), "complete rollback restores every modified config field and HUD map");
        check(!presets.canRestore() && options.saves == 5, "one save per application and restore");
        check(config.getAppliedVisualPreset().isEmpty(), "restore clears applied profile marker");
        var restored = gson.fromJson(gson.toJson(config), OrionConfig.class); restored.sanitize();
        check(restored.getClickGuiLastSection().equals("COMBAT"), "last category survives restart");
        config.setVisualPresetSelection("invalid"); config.setAppliedVisualPreset(null); config.sanitize();
        check(config.getVisualPresetSelection().isEmpty() && config.getAppliedVisualPreset().isEmpty(), "invalid profile ids sanitized");
        config.setClickGuiLastSection("invalid"); check(config.getClickGuiLastSection().equals("ALL"), "invalid section fallback");
        config.setMirageStrength(Float.NaN); config.setMirageBloom(99); config.setMirageQuality(-2); config.sanitize();
        check(config.getMirageStrength() == .16F && config.getMirageBloom() == .35F && config.getMirageQuality() == 0, "invalid reflection values sanitized");
        System.out.println("Visual presets: all four profiles, rollback, persistence and setting isolation passed");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
