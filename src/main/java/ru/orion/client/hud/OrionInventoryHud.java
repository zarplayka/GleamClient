package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;


/** Фон Lumen и реальные модели предметов из основного инвентаря. */
public final class OrionInventoryHud {
    private static final int COLS = 9, ROWS = 3;
    private static final int SLOT = 18, GAP = 1, STEP = SLOT + GAP, PAD = 4, ITEM_INSET = 1;
    private static final int DESIGN_W = COLS * STEP - GAP + PAD * 2;
    private static final int GRID_TOP = 20, DESIGN_H = GRID_TOP + ROWS * STEP - GAP + PAD;
    private final OrionConfigManager configManager;
    private final OrionHudDrag drag=new OrionHudDrag("inventory");
    private final OrionHudView view = new OrionHudView("inventory_hud");
    private OrionHudView.Scene scene;
    private String title;
    public OrionInventoryHud(OrionConfigManager configManager){this.configManager=configManager;}
    public void register(){HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"inventory_hud_element"),(context, ticks) -> drag.draw(context, () -> render(context, ticks)));}
    private void render(GuiGraphicsExtractor context,DeltaTracker ticks) {
        Minecraft client=Minecraft.getInstance();var config=configManager.getConfig();
        if((!config.isInventoryHudEnabled() && !drag.fadingOut())||client.player==null||client.level==null){release();return;}
        var inventory = client.player.getInventory();
        if (!OrionHudEditorScreen.editing(client)) {
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
        var position=drag.update(x,y,guiW,guiH,(nx,ny)->{config.setInventoryHudX(nx);config.setInventoryHudY(ny);});
        String translated = ru.orion.client.gui.OrionGuiLanguage.translate("Инвентарь");
        if (!translated.equals(title)) {
            title = translated;
            scene = scene(title);
        }
        view.draw(context, scene, position.x(), position.y(), guiW, guiH);
        context.nextStratum();
        var pose=context.pose();pose.pushMatrix();
        pose.translate(position.x(),position.y());pose.scale(guiW/(float)DESIGN_W,guiH/(float)DESIGN_H);
        try {
            for (int i = 0; i < COLS * ROWS; i++) {
                ItemStack item = inventory.getItem(i + 9);
                if (item.isEmpty()) continue;
                int xItem = PAD + (i % COLS) * STEP + ITEM_INSET;
                int yItem = GRID_TOP + (i / COLS) * STEP + ITEM_INSET;
                context.item(item, xItem, yItem);
                context.itemDecorations(client.font, item, xItem, yItem);
            }
        } finally {pose.popMatrix();}
    }
    private static OrionHudView.Scene scene(String title) {
        var scene = new OrionHudView.Scene(DESIGN_W, DESIGN_H);
        scene.panel(0, 0, DESIGN_W, DESIGN_H, 5);
        scene.icon("M4 8h12l1 8H3zm3 0V6a3 3 0 0 1 6 0v2", 6, 3, 11, "var(--accent)", false);
        scene.label(title, 21, 1, 105, 15, 8, "var(--text)", "left", true);
        scene.divider(5, 16, DESIGN_W - 10);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                scene.box(PAD + col * STEP, GRID_TOP + row * STEP, SLOT, SLOT, 2, "#ffffff10", null);
            }
        }
        return scene;
    }

    private void release() {
        drag.cancel();
        view.dispose();
    }
}
