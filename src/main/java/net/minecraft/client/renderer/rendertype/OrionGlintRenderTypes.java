package net.minecraft.client.renderer.rendertype;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Типы отрисовки с отдельной текстурой переливания Orion. */
public final class OrionGlintRenderTypes {
    private static Identifier texture(int mode) {
        String name = switch (mode) { case 2 -> "azure"; case 3 -> "rose"; case 4 -> "gold"; default -> "orion"; };
        return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/misc/" + name + "_glint.png");
    }
    private static int mode() {
        var config=OrionClient.getConfigOrNull();
        return config==null?0:config.getEnchantGlintMode();
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
        var setup = RenderSetup.builder(pipeline).withTexture("Sampler0", key.atlas).withTexture("GlintSampler", texture(key.mode))
                .setTextureTransform(TextureTransform.GLINT_TEXTURING).useLightmap().useOverlay().affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
        if (key.translucent) setup.setOitPipelines(key.special ? RenderPipelines.OIT_ITEM_GLINT_SPECIAL : RenderPipelines.OIT_ITEM_GLINT).sortOnUpload();
        return RenderType.create("orion_item_glint", setup.createRenderSetup());
    }
    public static RenderType armor(Identifier texture) {
        return ARMOR.computeIfAbsent(new KeyTexture(texture,mode()), key -> RenderType.create("orion_armor_glint",
                RenderSetup.builder(RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT).withTexture("Sampler0", key.texture)
                        .withTexture("GlintSampler", texture(key.mode)).setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                        .useLightmap().useOverlay().setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .affectsCrumbling().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
    }
    public static RenderType entity(Identifier texture) {
        return ENTITIES.computeIfAbsent(new KeyTexture(texture,mode()), key -> RenderType.create("orion_entity_glint",
                RenderSetup.builder(RenderPipelines.ENTITY_SOLID_GLINT).withTexture("Sampler0", key.texture)
                        .withTexture("GlintSampler", texture(key.mode)).setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING)
                        .useLightmap().useOverlay().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
    }
    private static final Map<Integer, RenderType> TRIMS = new ConcurrentHashMap<>();
    private static final Map<Integer, RenderType> SHIELDS = new ConcurrentHashMap<>();
    public static RenderType trim() { return TRIMS.computeIfAbsent(mode(), preset -> RenderType.create("orion_trim_glint_"+preset,
            RenderSetup.builder(RenderPipelines.GLINT).withTexture("Sampler0", texture(preset))
                    .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).withForcedSolidModelPhase().createRenderSetup())); }
    public static RenderType shield() { return SHIELDS.computeIfAbsent(mode(), preset -> RenderType.create("orion_shield_glint_"+preset,
            RenderSetup.builder(RenderPipelines.GLINT).withTexture("Sampler0", texture(preset))
                    .setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING).withForcedSolidModelPhase().createRenderSetup())); }
    private record KeyTexture(Identifier texture, int mode) { }
    private record Key(Identifier atlas, boolean translucent, boolean special, int mode) { }
}
