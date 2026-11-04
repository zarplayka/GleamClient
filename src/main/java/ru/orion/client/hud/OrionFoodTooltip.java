package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;

/** Графические строки питательности и насыщения в подсказке предмета. */
public record OrionFoodTooltip(int nutrition, float saturation, boolean rotten)
        implements Component, FormattedCharSequence, ClientTooltipComponent {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!OrionSaturationOverlay.enabled()
                    || (!flag.isCreative() && stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip())) return;
            var food = stack.get(DataComponents.FOOD);
            var consumable = stack.get(DataComponents.CONSUMABLE);
            if (food != null && food.nutrition() != 0 && consumable != null) {
                lines.add(new OrionFoodTooltip(food.nutrition(), food.saturation(), OrionSaturationOverlay.isRotten(consumable)));
            }
        });
    }

    @Override public Style getStyle() { return Style.EMPTY; }
    @Override public ComponentContents getContents() { return PlainTextContents.EMPTY; }
    @Override public List<Component> getSiblings() { return List.of(); }
    @Override public FormattedCharSequence getVisualOrderText() { return this; }
    @Override public boolean accept(FormattedCharSink visitor) { return true; }
    @Override public int getHeight(Font font) { return 20; }

    @Override public int getWidth(Font font) {
        return Math.max(rowWidth(font, hungerIcons(), 9), rowWidth(font, saturationIcons(), 7));
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor context) {
        int count = hungerIcons();
        int displayed = count > 10 ? 1 : count;
        var sprites = rotten ? OrionSaturationOverlay.ROTTEN : OrionSaturationOverlay.FOOD;
        for (int i = 0; i < displayed; i++) {
            int iconX = x + (displayed - 1 - i) * 9;
            context.blitSprite(RenderPipelines.GUI_TEXTURED, OrionSaturationOverlay.FOOD[0], iconX, y, 9, 9);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, sprites[Math.abs((long) nutrition) == i * 2L + 1 ? 1 : 2],
                    iconX, y, 9, 9);
        }
        if (count > 10) context.text(font, "x" + count, x + 11, y + 1, 0xFFAAAAAA);

        count = saturationIcons();
        displayed = count == 0 || count > 10 ? 1 : count;
        for (int i = 0; i < displayed; i++) {
            float fill = Math.abs(saturation) / 2F - i;
            int u = 7 * (fill >= 1F ? 3 : fill > .5F ? 2 : fill > .25F ? 1 : fill > 0F ? 0 : 4);
            int v = saturation < 0F ? 34 : 27;
            context.blit(RenderPipelines.GUI_TEXTURED, OrionSaturationOverlay.ICONS, x + (displayed - 1 - i) * 7, y + 10,
                    u, v, 7, 7, 256, 256, fill > 0F ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        if (count == 0 || count > 10) context.text(font, "x" + count, x + 9, y + 10, 0xFFAAAAAA);
    }

    private int hungerIcons() { return (int) Math.ceil(Math.abs(nutrition / 2D)); }
    private int saturationIcons() { return (int) Math.ceil(Math.abs(saturation / 2D)); }
    private static int rowWidth(Font font, int count, int size) {
        return count == 0 || count > 10 ? size + 2 + font.width("x" + count) : count * size;
    }
}
