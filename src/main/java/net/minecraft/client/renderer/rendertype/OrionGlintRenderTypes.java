package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfig.GlintPreset;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Типы отрисовки с отдельной текстурой переливания Orion. */
public final class OrionGlintRenderTypes {
    private static final Identifier CUSTOM_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "custom_enchant_glint");
    private static final TextureTransform CUSTOM_ITEM = animated("orion_item", 8F);
    private static final TextureTransform CUSTOM_ENTITY = animated("orion_entity", .5F);
    private static final TextureTransform CUSTOM_ARMOR = animated("orion_armor", .16F);
    private static DynamicTexture customTexture;
    private static GlintPreset loaded;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var config = OrionClient.getConfigOrNull();
            if (config == null || !config.isEnchantGlintEnabled() || config.getEnchantGlintMode() != OrionConfig.CUSTOM_GLINT) return;
            GlintPreset preset = config.getCustomGlint();
            if (customTexture == null || customTexture.getPixels() == null || customTexture.getPixels().isClosed()) {
                customTexture = new DynamicTexture("orion_custom_glint", 128, 128, true);
                client.getTextureManager().register(CUSTOM_TEXTURE, customTexture);
                loaded = null;
            }
            if (preset.equals(loaded)) return;
            if (loaded == null || !preset.first().equals(loaded.first()) || !preset.second().equals(loaded.second())
                    || preset.bands() != loaded.bands() || preset.intensity() != loaded.intensity()) {
                drawGlint(customTexture.getPixels(), preset);
                customTexture.upload();
            }
            loaded = preset;
        });
    }

    private static void drawGlint(NativeImage pixels, GlintPreset preset) {
        int first = ARGB.opaque(Integer.parseInt(preset.first().substring(1), 16));
        int second = ARGB.opaque(Integer.parseInt(preset.second().substring(1), 16));
        int size = pixels.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double wave = .5 + .5 * Math.cos(2 * Math.PI * (x + y) * preset.bands() / size);
                float mix = (float) (.5 + .5 * Math.sin(2 * Math.PI * (x - y) / size));
                float strength = (float) (wave * wave * preset.intensity() / 100);
                pixels.setPixel(x, y, ARGB.scaleRGB(ARGB.srgbLerp(mix, first, second), strength));
            }
        }
    }

    private static TextureTransform animated(String name, float scale) {
        return new TextureTransform(name, () -> {
            var config = OrionClient.getConfigOrNull();
            int speed = config == null ? 100 : config.getCustomGlint().speed();
            double vanillaSpeed = Minecraft.getInstance().gameRenderer.gameRenderState().optionsRenderState.glintSpeed;
            double phase = Util.getMillis() * 8D * vanillaSpeed * speed / 100D;
            return new Matrix4f().translation(-(float) (phase % 110000 / 110000),
                    (float) (phase % 30000 / 30000), 0).rotateZ(.17453292F).scale(scale);
        });
    }

    private static TextureTransform transform(int mode, TextureTransform vanilla, TextureTransform custom) {
        return mode == OrionConfig.CUSTOM_GLINT ? custom : vanilla;
    }

    private static RenderSetup.RenderSetupBuilder glint(RenderSetup.RenderSetupBuilder setup, String sampler, int mode) {
        return mode == OrionConfig.CUSTOM_GLINT ? setup.withTexture(sampler, CUSTOM_TEXTURE,
                () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
                : setup.withTexture(sampler, texture(mode));
    }

    private static Identifier texture(int mode) {
        String name = switch (mode) { case 2 -> "azure"; case 3 -> "rose"; case 4 -> "gold"; default -> "orion"; };
        return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/misc/" + name + "_glint.png");
    }
    private static int mode() {
        var config=OrionClient.getConfigOrNull();
        int mode = config == null ? 0 : config.getEnchantGlintMode();
        return mode == OrionConfig.CUSTOM_GLINT && loaded == null ? 0 : mode;
    }
    private static final Map<Key, RenderType> ITEMS = new ConcurrentHashMap<>();
    private static final Map<KeyTexture, RenderType> ARMOR = new ConcurrentHashMap<>();
    private static final Map<KeyTexture, RenderType> ENTITIES = new ConcurrentHashMap<>();
    private OrionGlintRenderTypes() { }

    public static RenderType item(Identifier atlas, boolean translucent, boolean special) {
        return ITEMS.computeIfAbsent(new Key(atlas, translucent, special, mode()), OrionGlintRenderTypes::createItem);
    }
    private static RenderType createItem(Key key) {
        var pipeline = key.translucent ? (key.special ? RenderPipelines.ITEM_TRANSLUCENT_GLINT_SPECIAL : RenderPipelines.ITEM_TRANSLUCENT_GLINT)
                : (key.special ? RenderPipelines.ITEM_CUTOUT_GLINT_SPECIAL : RenderPipelines.ITEM_CUTOUT_GLINT);
        var setup = glint(RenderSetup.builder(pipeline).withTexture("Sampler0", key.atlas), "GlintSampler", key.mode)
                .setTextureTransform(transform(key.mode, TextureTransform.GLINT_TEXTURING, CUSTOM_ITEM)).useLightmap().useOverlay().affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
        if (key.translucent) setup.setOitPipelines(key.special ? RenderPipelines.OIT_ITEM_GLINT_SPECIAL : RenderPipelines.OIT_ITEM_GLINT).sortOnUpload();
        return RenderType.create("orion_item_glint", setup.createRenderSetup());
    }
    public static RenderType armor(Identifier texture) {
        return ARMOR.computeIfAbsent(new KeyTexture(texture,mode()), key -> RenderType.create("orion_armor_glint",
                glint(RenderSetup.builder(RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT).withTexture("Sampler0", key.texture), "GlintSampler", key.mode)
                        .setTextureTransform(transform(key.mode, TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING, CUSTOM_ARMOR))
                        .useLightmap().useOverlay().setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .affectsCrumbling().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
    }
    public static RenderType entity(Identifier texture) {
        return ENTITIES.computeIfAbsent(new KeyTexture(texture,mode()), key -> RenderType.create("orion_entity_glint",
                glint(RenderSetup.builder(RenderPipelines.ENTITY_SOLID_GLINT).withTexture("Sampler0", key.texture), "GlintSampler", key.mode)
                        .setTextureTransform(transform(key.mode, TextureTransform.ENTITY_GLINT_TEXTURING, CUSTOM_ENTITY))
                        .useLightmap().useOverlay().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
    }
    private static final Map<Integer, RenderType> TRIMS = new ConcurrentHashMap<>();
    private static final Map<Integer, RenderType> SHIELDS = new ConcurrentHashMap<>();
    public static RenderType trim() { return TRIMS.computeIfAbsent(mode(), preset -> RenderType.create("orion_trim_glint_"+preset,
            glint(RenderSetup.builder(RenderPipelines.GLINT), "Sampler0", preset)
                    .setTextureTransform(transform(preset, TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING, CUSTOM_ARMOR))
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).withForcedSolidModelPhase().createRenderSetup())); }
    public static RenderType shield() { return SHIELDS.computeIfAbsent(mode(), preset -> RenderType.create("orion_shield_glint_"+preset,
            glint(RenderSetup.builder(RenderPipelines.GLINT), "Sampler0", preset)
                    .setTextureTransform(transform(preset, TextureTransform.ENTITY_GLINT_TEXTURING, CUSTOM_ENTITY)).withForcedSolidModelPhase().createRenderSetup())); }
    private record KeyTexture(Identifier texture, int mode) { }
    private record Key(Identifier atlas, boolean translucent, boolean special, int mode) { }
}
