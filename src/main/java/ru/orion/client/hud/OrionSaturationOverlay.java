package ru.orion.client.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import ru.orion.client.OrionClient;

import java.util.Random;

/** Спрайты и поведение полосок еды основаны на AppleSkin (squeek502, Unlicense). */
public final class OrionSaturationOverlay {
    static final Identifier ICONS = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/gui/appleskin_icons.png");
    static final Identifier[] FOOD = foodSprites("");
    static final Identifier[] ROTTEN = foodSprites("_hunger");
    private static final Identifier[] HEART = heartSprites("");
    private static final Identifier[] HARDCORE = heartSprites("hardcore_");
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HARDCORE_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container_hardcore");
    private static final boolean EXTERNAL_APPLESKIN = FabricLoader.getInstance().isModLoaded("appleskin");
    private final int[] foodY = new int[10];
    private final Random heartRandom = new Random();
    private float flash;
    private float flashStep = .125F;

    public static boolean enabled() {
        var config = OrionClient.getConfigOrNull();
        return !EXTERNAL_APPLESKIN && config != null && config.isSaturationEnabled();
    }

    public void tick() {
        flash += flashStep;
        if (flash >= 1.5F || flash <= -.5F) flashStep = -flashStep;
    }

    public void beginFood(GuiGraphicsExtractor context, Player player, int top, int right) {
        java.util.Arrays.fill(foodY, top);
        int width = (int) (Math.clamp(OrionFoodSync.exhaustion(player) / 4F, 0F, 1F) * 81);
        if (width > 0) context.blit(RenderPipelines.GUI_TEXTURED, ICONS, right - width, top,
                81 - width, 18, width, 9, 256, 256, tint(.75F));
    }

    public void positionFoodIcon(int right, int x, int y) {
        int index = (right - 9 - x) / 8;
        if (index >= 0 && index < foodY.length) foodY[index] = y;
    }

    public void renderFood(GuiGraphicsExtractor context, Player player, int right) {
        var stats = player.getFoodData();
        float saturation = OrionFoodSync.saturation(player);
        drawSaturation(context, right, 0, saturation, 1F);
        ItemStack stack = heldFood(player);
        if (stack.isEmpty()) {
            flash = 0F;
            flashStep = .125F;
            return;
        }
        float alpha = alpha();
        if (alpha <= 0F) return;
        FoodProperties food = stack.get(DataComponents.FOOD);
        int hunger = stats.getFoodLevel();
        int futureHunger = Math.clamp(hunger + food.nutrition(), 0, 20);
        if (futureHunger > hunger) {
            Identifier[] sprites = isRotten(stack.get(DataComponents.CONSUMABLE)) ? ROTTEN : FOOD;
            for (int i = hunger / 2; i < (futureHunger + 1) / 2; i++) {
                int x = right - 9 - i * 8;
                context.blitSprite(RenderPipelines.GUI_TEXTURED, sprites[0], x, foodY[i], 9, 9, tint(alpha * .25F));
                context.blitSprite(RenderPipelines.GUI_TEXTURED, sprites[futureHunger == i * 2 + 1 ? 1 : 2],
                        x, foodY[i], 9, 9, tint(alpha));
            }
        }
        float futureSaturation = Math.min(futureHunger, saturation + food.saturation());
        if (futureSaturation > saturation) drawSaturation(context, right, (int) (saturation / 2F), futureSaturation, alpha);
    }

    public void renderHealth(GuiGraphicsExtractor context, Player player, int left, int top, int lineHeight,
                             int regenerating, float maxHealth, int absorption, int ticks) {
        float alpha = alpha();
        if (alpha <= 0F || !player.isHurt() || player.getFoodData().getFoodLevel() >= 18
                || player.level().getDifficulty() == Difficulty.PEACEFUL || player.hasEffect(MobEffects.POISON)
                || player.hasEffect(MobEffects.WITHER) || player.hasEffect(MobEffects.REGENERATION)) return;
        ItemStack stack = heldFood(player);
        if (stack.isEmpty()) return;
        FoodProperties food = stack.get(DataComponents.FOOD);
        int hunger = Math.min(20, player.getFoodData().getFoodLevel() + food.nutrition());
        float sat = Math.min(hunger, OrionFoodSync.saturation(player) + food.saturation());
        float restored = OrionFoodSync.naturalRegeneration() ? naturalHealing(hunger, sat, OrionFoodSync.exhaustion(player)) : 0F;
        for (var effect : stack.get(DataComponents.CONSUMABLE).onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect statuses)) continue;
            for (var status : statuses.effects()) {
                if (status.getEffect().equals(MobEffects.REGENERATION)) {
                    restored += status.getDuration() / Math.max(50 >> Math.min(5, status.getAmplifier()), 1);
                    break;
                }
            }
        }
        int future = (int) Math.ceil(Math.min(player.getHealth() + restored, maxHealth));
        int health = (int) Math.ceil(player.getHealth());
        int hearts = (int) Math.ceil((maxHealth + absorption) / 2F);
        if (future <= health || hearts > 1000) return;
        boolean hardcore = player.level().getLevelData().isHardcore();
        Identifier[] sprites = hardcore ? HARDCORE : HEART;
        Identifier container = hardcore ? HARDCORE_CONTAINER : HEART_CONTAINER;
        heartRandom.setSeed((long) (ticks * 312871));
        for (int i = hearts - 1; i >= 0; i--) {
            int y = top - (i / 10) * lineHeight;
            if (health <= 4) y += heartRandom.nextInt(2);
            if (i == regenerating) y -= 2;
            if (i < health / 2 || i >= (future + 1) / 2) continue;
            int x = left + (i % 10) * 8;
            context.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, y, 9, 9, tint(alpha * .25F));
            context.blitSprite(RenderPipelines.GUI_TEXTURED, sprites[future == i * 2 + 1 ? 0 : 1],
                    x, y, 9, 9, tint(alpha));
        }
    }

    private static float naturalHealing(int hunger, float saturation, float exhaustion) {
        if (!Float.isFinite(saturation) || !Float.isFinite(exhaustion)) return 0F;
        double fatigue = Math.clamp(exhaustion, 0F, 40F);
        float restored = 0F;
        while (hunger >= 18) {
            if (fatigue > 4D) {
                fatigue -= 4D;
                if (saturation > 0F) saturation = Math.max(0F, saturation - 1F);
                else hunger--;
            } else if (hunger == 20 && saturation >= Float.MIN_NORMAL) {
                double cost = Math.min(6F, saturation);
                double healedCost = cost * (1D + Math.floor((4D - fatigue) / cost));
                restored += (float) (healedCost / 6D);
                fatigue = Math.max(Math.nextUp(4D), fatigue + healedCost);
            } else {
                restored += 1F;
                fatigue += 6D;
            }
        }
        return restored;
    }

    private void drawSaturation(GuiGraphicsExtractor context, int right, int start, float saturation, float alpha) {
        float icons = Math.clamp(saturation / 2F, 0F, 10F);
        for (int i = Math.max(0, start); i < (int) Math.ceil(icons); i++) {
            float fill = icons - i;
            int u = 9 * (fill >= 1F ? 3 : fill > .5F ? 2 : fill > .25F ? 1 : 0);
            context.blit(RenderPipelines.GUI_TEXTURED, ICONS, right - 9 - i * 8, foodY[i],
                    u, 0, 9, 9, 256, 256, tint(alpha));
        }
    }

    public static ItemStack heldFood(Player player) {
        ItemStack main = player.getMainHandItem();
        if (canEat(player, main)) return main;
        ItemStack off = player.getOffhandItem();
        return canEat(player, off) ? off : ItemStack.EMPTY;
    }

    private static boolean canEat(Player player, ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && stack.has(DataComponents.CONSUMABLE) && player.canEat(food.canAlwaysEat());
    }

    static boolean isRotten(Consumable consumable) {
        for (var effect : consumable.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect statuses)) continue;
            for (var status : statuses.effects()) {
                if (status.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
            }
        }
        return false;
    }

    private float alpha() { return Math.clamp(flash, 0F, 1F) * .65F; }
    private static int tint(float alpha) { return ((int) (alpha * 255F) << 24) | 0xFFFFFF; }
    private static Identifier[] foodSprites(String suffix) {
        return new Identifier[]{Identifier.withDefaultNamespace("hud/food_empty" + suffix),
                Identifier.withDefaultNamespace("hud/food_half" + suffix), Identifier.withDefaultNamespace("hud/food_full" + suffix)};
    }

    private static Identifier[] heartSprites(String prefix) {
        return new Identifier[]{Identifier.withDefaultNamespace("hud/heart/" + prefix + "half"),
                Identifier.withDefaultNamespace("hud/heart/" + prefix + "full")};
    }
}
