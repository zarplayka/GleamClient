package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Мягкая золотистая подсветка по форме ванильных значков голода. */
public final class OrionSaturationOverlay {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_saturation");
    private static final Identifier FOOD_SPRITE = Identifier.withDefaultNamespace("textures/gui/sprites/hud/food_full.png");
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static volatile long resourceGeneration;
    private DynamicTexture texture;
    private long loadedGeneration = -1;
    private BufferedImage goldFood;
    private Object resourceManager;
    private boolean attemptedLoad;
    private int textureScale, saturationHalf = -1;

    public static void registerResourceListener() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "saturation_resources"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { resourceGeneration++; }
        });
    }

    /** Чтение ресурсов происходит на клиентском тике, не в рендере. */
    public void tick() {
        var config = OrionClient.getConfigOrNull();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.isSaturationEnabled() || client.player == null) { release(); return; }
        var current = client.getResourceManager();
        if (current != resourceManager || loadedGeneration != resourceGeneration) {
            resourceManager = current; loadedGeneration = resourceGeneration; attemptedLoad = false; goldFood = null; saturationHalf = -1;
        }
        if (attemptedLoad) return;
        attemptedLoad = true;
        try (var stream = current.open(FOOD_SPRITE)) {
            BufferedImage original = ImageIO.read(stream);
            if (original != null && original.getWidth() > 0 && original.getHeight() > 0) goldFood = tintFood(original);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Не удалось загрузить значок голода для Saturation", exception);
        }
    }
    public void render(GuiGraphicsExtractor context, Player player, int y, int right) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isSaturationEnabled() || player == null) { release(); return; }
        if (goldFood == null) return;
        int half = Math.max(0, Math.min(40, Math.round(player.getFoodData().getSaturationLevel() * 2F)));
        if (half == 0) return;
        int scale = Math.max(1, Math.min(6, Minecraft.getInstance().getWindow().getGuiScale()));
        if (texture == null || half != saturationHalf || scale != textureScale) update(half, scale);
        if (texture != null) context.blit(TEXTURE, right - 81, y, right, y + 9, 0, 1, 0, 1);
    }
    static BufferedImage tintFood(BufferedImage original) {
        BufferedImage result = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<original.getHeight();y++) for (int x=0;x<original.getWidth();x++) {
            int source=original.getRGB(x,y), alpha=source>>>24;
            if (alpha == 0) continue;
            int red=(source>>>16)&255, green=(source>>>8)&255, blue=source&255;
            int luminance=(red*3+green*6+blue)/10;
            int r=Math.min(255,90+luminance), g=Math.min(255,50+luminance*3/4), b=Math.min(255,10+luminance/3);
            result.setRGB(x,y,((alpha*125/255)<<24)|(r<<16)|(g<<8)|b);
        }
        return result;
    }
    static BufferedImage compose(BufferedImage food, int half, int scale) {
        BufferedImage image = new BufferedImage(81 * scale, 9 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale, scale);
            for (int i=0;i<10;i++) {
                float fill=Math.max(0,Math.min(1,(half-i*4)/4F));
                if(fill<=0)continue;
                float x=72-i*8;
                Shape clip=g.getClip();
                g.clip(new Rectangle2D.Float(x+9-9*fill,0,9*fill,9));
                g.drawImage(food,(int)x,0,9,9,null);
                g.setClip(clip);
            }
        } finally { g.dispose(); }
        return image;
    }
    private void update(int half, int scale) {
        BufferedImage image = compose(goldFood, half, scale);
        Minecraft client=Minecraft.getInstance();int w=image.getWidth(),h=image.getHeight();
        if(texture==null || textureScale!=scale) {
            client.getTextureManager().release(TEXTURE);NativeImage pixels=new NativeImage(w,h,true);OrionHudRenderer.copyPixels(image,pixels);
            texture=new DynamicTexture(()->"orion_saturation",pixels);client.getTextureManager().register(TEXTURE,texture);textureScale=scale;
        } else if(texture.getPixels()!=null&&!texture.getPixels().isClosed()) { OrionHudRenderer.copyPixels(image,texture.getPixels());texture.upload(); }
        saturationHalf=half;
    }
    public void release() { if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);texture=null;textureScale=0;saturationHalf=-1; }
}
