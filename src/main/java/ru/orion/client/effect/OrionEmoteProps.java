package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static ru.orion.client.effect.OrionEmoteClips.*;
import static ru.orion.client.effect.OrionEmotes.radians;

/** Props submitted in the same coordinate system as the avatar model. */
public final class OrionEmoteProps {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("orion-client", "textures/basket_white.png");

    private record Piece(ModelPart model, int color) { }

    private static final Piece[] CHAIR = {
        piece(0x9C693C, new float[][] {{-5,19.25F,-3.3F,10,1,8}, {-5,13,4,10,2,1}, {-5,16,4,10,1.5F,1}}),
        piece(0x66432D, new float[][] {{-4.5F,20,-2.7F,1,4,1}, {3.5F,20,-2.7F,1,4,1},
                {-4.5F,12.5F,4,1,11.5F,1}, {3.5F,12.5F,4,1,11.5F,1}}),
    };
    private static final Piece[] MUG = {
        piece(0x78B5AD, new float[][] {{-1.6F,-2.8F,-1.6F,.4F,2.8F,3.2F}, {1.2F,-2.8F,-1.6F,.4F,2.8F,3.2F},
                {-1.2F,-2.8F,-1.6F,2.4F,2.8F,.4F}, {-1.2F,-2.8F,1.2F,2.4F,2.8F,.4F}, {-1.2F,-.45F,-1.2F,2.4F,.45F,2.4F},
                {1.6F,-2.35F,-.45F,1.05F,.35F,.9F}, {2.3F,-2F,-.45F,.35F,1.2F,.9F}, {1.6F,-.8F,-.45F,1.05F,.35F,.9F}}),
        piece(0x573725, new float[][] {{-1.2F,-2.35F,-1.2F,2.4F,.3F,2.4F}}),
    };
    private static final Piece[] CIGARETTE = {
        piece(0xE6DDC7, new float[][] {{-.22F,-.22F,-2.25F,.44F,.44F,1.8F}}),
        piece(0xB99166, new float[][] {{-.22F,-.22F,-.45F,.44F,.44F,.75F}}),
        piece(0xD06B48, new float[][] {{-.22F,-.22F,-2.5F,.44F,.44F,.25F}}),
    };

    private static Piece piece(int color, float[][] boxes) {
        var mesh = new MeshDefinition();
        var cubes = CubeListBuilder.create().texOffs(0, 0);
        for (float[] b : boxes) cubes.addBox(b[0], b[1], b[2], b[3], b[4], b[5]);
        mesh.getRoot().addOrReplaceChild("prop", cubes, PartPose.ZERO);
        return new Piece(LayerDefinition.create(mesh, 64, 64).bakeRoot(), color | 0xFF000000);
    }

    public static void submit(PlayerModel model, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        if (state.isInvisible || state.isSpectator) return;
        var dance = OrionEmotes.visibleDance(state);
        if (dance == null || dance.id() != OrionEmotes.SMOKING && dance.id() != OrionEmotes.COFFEE) return;
        double age = OrionEmotes.age(dance);
        float strength = OrionEmotes.blend(age);
        if (strength < .02F) return;
        boolean coffee = dance.id() == OrionEmotes.COFFEE;
        if (coffee) {
            pose.pushPose();
            // The renderer's model origin is at the top of the player, ground is at 24 model pixels.
            pose.translate(0F, (1F - strength) * .21F, 0F);
            draw(CHAIR, pose, collector, state.lightCoords);
            pose.popPose();
        }

        float[] frame = OrionEmoteClips.sample(dance.id(), age);
        Quaternionf torso = new Quaternionf().rotationZYX(radians(frame[BODY + 2]), radians(frame[BODY + 1]), radians(frame[BODY]));
        PartPose arm = model.rightArm.getInitialPose();
        Vector3f shoulder = new Vector3f(arm.x(), arm.y() - 12F, arm.z());
        Vector3f moved = torso.transform(new Vector3f(shoulder)).sub(shoulder).mul(strength).add(shoulder);
        Quaternionf reach = new Quaternionf().slerp(new Quaternionf(torso).rotateXYZ(radians(frame[RIGHT_ARM]),
                radians(frame[RIGHT_ARM + 1]), radians(frame[RIGHT_ARM + 2])), strength);
        Vector3f hand = reach.transform(new Vector3f(0F, 9.5F, -.2F)).add(moved);
        hand.add(frame[SHIFT] * strength, 12F - frame[RISE] * strength, 0F);
        pose.pushPose();
        pose.translate(hand.x / 16F, (hand.y + (coffee ? .35F : -.1F)) / 16F, (hand.z - (coffee ? 1.05F : .4F)) / 16F);
        float pitch = frame[RIGHT_ARM];
        ru.orion.client.render.GleamPose.rotate(pose, new Quaternionf().rotationX(radians(coffee
                ? Math.clamp((-pitch - 65F) * -.62F, -30F, 0F) : Math.clamp((pitch + 62F) * .62F, -18F, 34F))));
        pose.scale(strength, strength, strength);
        draw(coffee ? MUG : CIGARETTE, pose, collector, state.lightCoords);
        pose.popPose();
    }

    private static void draw(Piece[] pieces, PoseStack pose, SubmitNodeCollector collector, int light) {
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (transform, vertices) -> {
            var renderPose = new PoseStack();
            renderPose.last().set(transform);
            for (Piece piece : pieces) piece.model.render(renderPose, vertices, light, OverlayTexture.NO_OVERLAY, piece.color);
        });
    }

    private OrionEmoteProps() { }
}
