package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;


/** Красные метки и замочки поверх защищённых слотов хотбара. */
public final class OrionHotbarLocks {
    private final OrionConfigManager configManager;
    private final OrionHudView view = new OrionHudView("hotbar_locks");
    private OrionHudView.Scene scene;
    private long last;
    public OrionHotbarLocks(OrionConfigManager configManager){this.configManager=configManager;}
    public void register(){HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"hotbar_locks_element"),this::render);}
    private void render(GuiGraphicsExtractor context,DeltaTracker delta) {
        Minecraft client=Minecraft.getInstance();var config=configManager.getConfig();
        if(client.player==null || !config.isLockSlotEnabled() || client.player.isSpectator()){view.dispose();return;}
        long bits=config.getLockSlotMask() & 0x1FFL;
        if(bits==0){view.dispose();return;}
        if (bits != last) {
            scene = scene(bits);
            last = bits;
        }
        view.draw(context, scene, context.guiWidth() / 2 - 90, context.guiHeight() - 21, 180, 20);
    }

    private static OrionHudView.Scene scene(long bits) {
        var scene = new OrionHudView.Scene(180, 20);
        for (int i = 0; i < 9; i++) if ((bits & 1L << i) != 0) scene.lock(i * 20 + 2, 2);
        return scene;
    }
}
