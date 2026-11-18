package ru.orion.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionfc;
public final class GleamPose {
    private GleamPose() { }
    public static void rotate(PoseStack pose, Quaternionfc rotation) {
        //? if >=26.3 {
        pose.rotate(rotation);
        //?} else {
                /*pose.mulPose(rotation);
        *///?}
    }
    public static void rotateDegrees(PoseStack pose, Axis axis, float degrees) {
        rotate(pose, axis.rotationDegrees(degrees));
    }
}
