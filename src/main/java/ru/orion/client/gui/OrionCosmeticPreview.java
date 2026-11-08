package ru.orion.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Set;
import ru.orion.client.effect.OrionChinaHeadRenderer;
import ru.orion.client.effect.OrionHalo;
import ru.orion.client.effect.OrionWings;

/** Состояние модели для примерочной, независимое от экипировки игрока в мире. */
public final class OrionCosmeticPreview extends AvatarRenderState {
    private final Set<String> items;
    private final int hatColor;

    private OrionCosmeticPreview(LocalPlayer player, Set<String> items, float yaw, int hatColor) {
        this.items = items;
        this.hatColor = hatColor;
        entityType = player.getType();
        skin = player.getSkin();
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

    static void draw(GuiGraphicsExtractor graphics, LocalPlayer player, Set<String> items, float yaw, int hatColor,
                     int left, int top, int right, int bottom, float scale, float verticalOffset) {
        var state = new OrionCosmeticPreview(player, items, yaw, hatColor);
        graphics.entity(state, scale, new Vector3f(0, verticalOffset, 0),
                new Quaternionf().rotateZ((float) Math.PI).rotateX(-.10F), null, left, top, right, bottom);
    }

    public boolean dances() { return items.contains("dance"); }

    public void submitItem(PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        try {
            pose.rotate(Axis.YP.rotationDegrees(180F - bodyRot));
            for (String item : items) {
                pose.pushPose();
                try {
                    switch (item) {
                        case "angel", "dragon" -> OrionWings.submitPreview(pose, collector, "angel".equals(item) ? 1 : 2);
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
