package ru.orion.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Pose;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Set;
import ru.orion.client.cosmetic.GleamPet;
import ru.orion.client.cosmetic.GleamPetRenderer;
import ru.orion.client.effect.OrionChinaHeadRenderer;
import ru.orion.client.effect.OrionHalo;
import ru.orion.client.effect.OrionWings;

/** Состояние модели для примерочной, независимое от экипировки игрока в мире. */
public final class OrionCosmeticPreview extends AvatarRenderState {
    private final Set<String> items;
    private final int hatColor;
    private final GleamPet pet;
    private final long petAge;

    private OrionCosmeticPreview(PlayerSkin skin, Set<String> items, float yaw, int hatColor, GleamPet pet, long petAge) {
        this.items = items;
        this.hatColor = hatColor;
        this.pet = pet;
        this.petAge = petAge;
        //? if >=26.2 {
        entityType = EntityTypes.PLAYER;
        //?} else {
        /*entityType = EntityType.PLAYER;
        *///?}
        this.skin = skin;
        id = -1;
        lightCoords = 0xF000F0;
        pose = Pose.STANDING;
        scale = ageScale = 1;
        bodyRot = 180F + yaw;
        boundingBoxWidth = .6F;
        boundingBoxHeight = 1.8F;
        eyeHeight = 1.62F;
        showHat = showJacket = showLeftSleeve = showRightSleeve = showLeftPants = showRightPants = true;
    }

    static void draw(GuiGraphicsExtractor graphics, PlayerSkin skin, Set<String> items, float yaw, int hatColor,
                     int left, int top, int right, int bottom, float scale, float verticalOffset, GleamPet pet, long petAge) {
        var state = new OrionCosmeticPreview(skin, items, yaw, hatColor, pet, petAge);
        graphics.entity(state, scale, new Vector3f(0, verticalOffset, 0),
                new Quaternionf().rotateZ((float) Math.PI).rotateX(-.10F), null, left, top, right, bottom);
    }

    public String selectedHat() {
        return ru.orion.client.cosmetic.GleamHatModel.IDS.stream().filter(items::contains).findFirst().orElse("");
    }

    public boolean dances() { return items.contains("dance"); }

    public void submitItem(PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        try {
            ru.orion.client.render.GleamPose.rotate(pose, Axis.YP.rotationDegrees(180F - bodyRot));
            if (pet != null) {
                pose.pushPose();
                GleamPetRenderer.submitPreview(pet, pose, collector, petAge);
                pose.popPose();
            }
            for (String item : items) {
                pose.pushPose();
                try {
                    switch (item) {
                        case "angel", "dragon", "evergreen", "ash_phoenix" -> OrionWings.submitPreview(pose, collector, ru.orion.client.effect.GleamWingModel.IDS.indexOf(item) + 1);
                        case "halo" -> OrionHalo.submitPreview(pose, collector, boundingBoxHeight);
                        case "hat" -> OrionChinaHeadRenderer.submitPreview(pose, collector, eyeHeight, hatColor);
                        default -> { }
                    }
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }
}
