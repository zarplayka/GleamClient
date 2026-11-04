package ru.orion.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.language.LanguageManager;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionEmotes;
import sun.misc.Unsafe;
import javax.imageio.ImageIO;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.Map;

/** Проверка секторов колеса, локализации и поз обеих моделей скина без GPU. */
public final class OrionEmotesTest {
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    public static void main(String[] args) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Minecraft client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        field(Minecraft.class, "instance").set(null, client);
        LanguageManager languages = new LanguageManager(client, "ru_ru", language -> {});
        field(Minecraft.class, "languageManager").set(client, languages);
        var manager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        var config = OrionConfig.createDefault();
        field(OrionConfigManager.class, "config").set(manager, config);
        OrionClient orion = (OrionClient) unsafe.allocateInstance(OrionClient.class);
        field(OrionClient.class, "configManager").set(orion, manager);
        field(OrionClient.class, "instance").set(null, orion);
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60);
            if (OrionEmoteWheelScreen.sector(Math.sin(angle) * 100, -Math.cos(angle) * 100) != i) {
                throw new AssertionError("Wrong wheel sector " + i);
            }
        }
        if (OrionEmoteWheelScreen.sector(0, 0) != -1 || OrionEmoteWheelScreen.sector(200, 0) != -1) {
            throw new AssertionError("Wheel dead zones");
        }
        if (!OrionGuiLanguage.translate("Games").equals("Игры")) throw new AssertionError("Russian games label");
        for (String code : new String[]{"ru_ru", "en_us"}) {
            languages.setSelected(code);
            for (boolean light : new boolean[]{false, true}) {
                ImageIO.write(OrionEmoteWheelScreen.renderImage(660, light, 2), "png",
                        Path.of(args[0], "wheel-" + code + "-" + light + ".png").toFile());
            }
        }
        if (!OrionGuiLanguage.translate("Танцы").equals("Emotes")) throw new AssertionError("English dance label");
        @SuppressWarnings("unchecked")
        Map<Integer,Object> visible = (Map<Integer,Object>) field(OrionEmotes.class, "VISIBLE").get(null);
        Class<?> danceType = Class.forName(OrionEmotes.class.getName() + "$Dance");
        Constructor<?> dance = danceType.getDeclaredConstructor(int.class, long.class);
        dance.setAccessible(true);
        var state = new AvatarRenderState();
        state.id = 55;
        for (boolean slim : new boolean[]{false, true}) {
            var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            for (int id = 1; id <= 6; id++) {
                model.resetPose();
                visible.put(state.id, dance.newInstance(id, System.nanoTime() - 900_000_000L));
                OrionEmotes.apply(model, state);
                if (!Float.isFinite(model.rightArm.xRot) || !Float.isFinite(model.leftArm.zRot)) throw new AssertionError("Invalid pose");
                if (Math.abs(model.rightArm.xRot) + Math.abs(model.rightArm.zRot) < .01F) throw new AssertionError("No pose " + id);
                if (model.rightSleeve.xRot != 0F) throw new AssertionError("Double-transformed skin layer");
            }
        }
        System.out.println("Wheel selection, RU/EN rendering and all six poses for both skin models passed");
    }
}
