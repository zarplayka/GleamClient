package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class GleamWingModel {
    public static final List<String> IDS = List.of("angel", "dragon", "midnight", "evergreen", "first_salvo", "ash_phoenix", "lunar_moth");
    private static final List<Identifier> TEXTURES = IDS.subList(2, IDS.size()).stream()
            .map(id -> Identifier.fromNamespaceAndPath("orion-client", "textures/cosmetics/wings/wing_" + id + ".png"))
            .toList();

    private GleamWingModel() {}

    static void submit(PoseStack pose, SubmitNodeCollector collector, int style) {
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURES.get(style - 3)), (matrix, vertices) -> {
            for (int side : SIDES) {
                for (int i = 0; i < 4; i++) {
                    int corner = side > 0 ? 3 - i : i;
                    boolean right = corner == 1 || corner == 2;
                    boolean top = corner < 2;
                    vertices.addVertex(matrix, right ? 2F : -2F, ((top ? 36.5F : -3.5F) - 20.8F) / 16F, .44F / 16F)
                            .setColor(-1).setUv(right ? 1F : 0F, top ? 0F : 1F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(matrix, 0F, 0F, side);
                }
            }
        });
    }

    private static final int[] SIDES = {-1, 1};
}