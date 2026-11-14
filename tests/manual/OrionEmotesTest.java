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
import com.google.gson.Gson;
import java.lang.reflect.*;
import java.util.Arrays;
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
        languages.setSelected("en_us");
        if (!OrionGuiLanguage.translate("Танцы").equals("Emotes")
                || !OrionGuiLanguage.translate("Изменить круг").equals("Edit wheel")) {
            throw new AssertionError("English wheel labels");
        }
        int[] defaults = {1, 2, 3, 4, 5, 6};
        if (!Arrays.equals(config.getEmoteSlots(), defaults)) throw new AssertionError("Default order");
        config.setEmoteSlot(0, 6);
        config.setEmoteSlot(1, 0);
        int[] edited = {6, 0, 3, 4, 5, 1};
        Gson gson = new Gson();
        var restored = gson.fromJson(gson.toJson(config), OrionConfig.class);
        restored.sanitize();
        if (!Arrays.equals(restored.getEmoteSlots(), edited)) throw new AssertionError("Wheel order after reload");
        restored.setEmoteSlot(-1, 6);
        restored.setEmoteSlot(0, 7);
        if (!Arrays.equals(restored.getEmoteSlots(), edited)) throw new AssertionError("Invalid slot update");
        restored.resetEmoteSlots();
        var corrupt = gson.fromJson("{\"emoteSlots\":[1,1,3,4,5,6]}", OrionConfig.class);
        corrupt.sanitize();
        if (!Arrays.equals(restored.getEmoteSlots(), defaults) || !Arrays.equals(corrupt.getEmoteSlots(), defaults)) {
            throw new AssertionError("Default order after reset / migration");
        }
        @SuppressWarnings("unchecked")
        Map<Integer,Object> visible = (Map<Integer,Object>) field(OrionEmotes.class, "VISIBLE").get(null);
        Class<?> danceType = Class.forName(OrionEmotes.class.getName() + "$Dance");
        Constructor<?> dance = danceType.getDeclaredConstructor(int.class, long.class);
        dance.setAccessible(true);
        var state = new AvatarRenderState();
        state.id = 55;
        var pose = OrionEmotes.class.getDeclaredMethod("applyPose", net.minecraft.client.model.HumanoidModel.class,
                int.class, double.class, float.class);
        pose.setAccessible(true);
        for (boolean slim : new boolean[]{false, true}) {
            var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            for (int id = 1; id <= 6; id++) {
                model.resetPose();
                visible.put(state.id, dance.newInstance(id, System.nanoTime() - 900_000_000L));
                OrionEmotes.apply(model, state);
                if (Math.abs(model.rightArm.xRot) + Math.abs(model.rightArm.zRot) < .01F) throw new AssertionError("No pose " + id);
                if (model.rightSleeve.xRot != 0F) throw new AssertionError("Double-transformed skin layer");
                double period = new double[]{1.36F, 2F, 1.92F, 1.48F, 2.4F, 2F}[id - 1];
                model.resetPose();
                pose.invoke(null, model, id, 0D, 1F);
                float origin = model.rightArm.xRot, base = model.body.x;
                model.resetPose();
                pose.invoke(null, model, id, period - .00001D, 1F);
                if (Math.abs(origin - model.rightArm.xRot) > .001F || Math.abs(base - model.body.x) > .001F) {
                    throw new AssertionError("Loop seam " + id);
                }
                for (int frame = 0; frame < 120; frame++) {
                    model.resetPose();
                    pose.invoke(null, model, id, frame * period / 120, 1F);
                    for (var part : model.root().getAllParts()) {
                        if (!Float.isFinite(part.x + part.y + part.z + part.xRot + part.yRot + part.zRot)) {
                            throw new AssertionError("Invalid pose " + id + " frame " + frame);
                        }
                    }
                }
            }
        }
        System.out.println("Wheel selection, editable slot persistence, localization and six cyclic dances for both skin models passed");
    }
}
