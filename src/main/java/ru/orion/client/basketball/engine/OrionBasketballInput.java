package ru.orion.client.basketball.engine;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

/** Клавиши Orion; алгоритм обработки кликов соответствует исходной мини-игре. */
public final class OrionBasketballInput {
    private static KeyMapping take, hoop;
    private OrionBasketballInput() { }
    public static void register() {
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"basketball"));
        take=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orion-client.basketball.ball",InputConstants.Type.KEYBOARD,InputConstants.KEY_B,category));
        hoop=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orion-client.basketball.place",InputConstants.Type.KEYBOARD,InputConstants.KEY_N,category));
    }
    public static KeyMapping ballKey() { return take; }
    public static KeyMapping hoopKey() { return hoop; }
    public static boolean consumeTake() { return consume(take); }
    public static boolean consumeHoop() { return consume(hoop); }
    private static boolean consume(KeyMapping key) {
        if(key==null)return false;
        boolean clicked=false;while(key.consumeClick())clicked=true;return clicked;
    }
}
