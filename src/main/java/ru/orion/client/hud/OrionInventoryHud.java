package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;


/** Фон Lumen и реальные модели предметов из основного инвентаря. */
public final class OrionInventoryHud {
    private static final int COLS = 9, ROWS = 3;
    private static final int SLOT = 18, GAP = 1, STEP = SLOT + GAP, PAD = 4, ITEM_INSET = 1;
    private static final int DESIGN_W = COLS * STEP - GAP + PAD * 2;
    private static final int DESIGN_H = ROWS * STEP - GAP + PAD * 2;
    private final OrionConfigManager configManager;
    private final OrionHudDrag drag=new OrionHudDrag("inventory");
    private final OrionHudView view = new OrionHudView("inventory_hud");
    private final OrionHudView.Scene scene = scene();
    public OrionInventoryHud(OrionConfigManager configManager){this.configManager=configManager;}
    public void register(){HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"inventory_hud_element"),this::render);}
    private void render(GuiGraphicsExtractor context,DeltaTracker ticks) {
        Minecraft client=Minecraft.getInstance();var config=configManager.getConfig();
        if(!config.isInventoryHudEnabled()||client.player==null||client.level==null){release();return;}
        var inventory = client.player.getInventory();
        if (!(GleamMinecraft.screen(client) instanceof ChatScreen)) {
            boolean hasItems = false;
            for (int i = 0; i < COLS * ROWS && !hasItems; i++) {
                hasItems = !inventory.getItem(i + 9).isEmpty();
            }
            if (!hasItems) {
                release();
                return;
            }
        }
        float elementScale = config.getHudScale() * config.getHudElementScale("inventory");
        float fit = OrionHudView.fit(elementScale, DESIGN_W, DESIGN_H, context.guiWidth(), context.guiHeight());
        int guiW = Math.max(1, Math.round(DESIGN_W * fit));
        int guiH = Math.max(1, Math.round(DESIGN_H * fit));
        int x=config.getInventoryHudX()<0?context.guiWidth()-guiW-8:config.getInventoryHudX();
        int y=config.getInventoryHudY()<0?(context.guiHeight()-guiH)/2:config.getInventoryHudY();
        x=Math.max(0,Math.min(x,context.guiWidth()-guiW));y=Math.max(0,Math.min(y,context.guiHeight()-guiH));
        var position=drag.update(context,x,y,guiW,guiH,configManager,(nx,ny)->{config.setInventoryHudX(nx);config.setInventoryHudY(ny);});
        view.draw(context, scene, position.x(), position.y(), guiW, guiH);
        context.nextStratum();
        var pose=context.pose();pose.pushMatrix();
        pose.translate(position.x(),position.y());pose.scale(guiW/(float)DESIGN_W,guiH/(float)DESIGN_H);
        try {
            for (int i = 0; i < COLS * ROWS; i++) {
                ItemStack item = inventory.getItem(i + 9);
                if (item.isEmpty()) continue;
                int xItem = PAD + (i % COLS) * STEP + ITEM_INSET;
                int yItem = PAD + (i / COLS) * STEP + ITEM_INSET;
                context.item(item, xItem, yItem);
                context.itemDecorations(client.font, item, xItem, yItem);
            }
        } finally {pose.popMatrix();}
    }
    private static OrionHudView.Scene scene() {
        var scene = new OrionHudView.Scene(DESIGN_W, DESIGN_H);
        scene.panel(0, 0, DESIGN_W, DESIGN_H, 6);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                scene.box(PAD + col * STEP, PAD + row * STEP, SLOT, SLOT, 2, "#80809a18", null);
            }
        }
        return scene;
    }

    private void release() {
        drag.cancel(configManager);
        view.dispose();
    }
}
