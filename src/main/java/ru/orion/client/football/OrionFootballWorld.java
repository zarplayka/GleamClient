package ru.orion.client.football;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.basketball.OrionBasketballBox;
import ru.orion.client.basketball.OrionBasketballVector;
import java.util.ArrayList;
import java.util.List;

/** Коллизии и сопротивление разных поверхностей из клиентского мира. */
public final class OrionFootballWorld implements OrionFootballPhysics.World {
    private final ClientLevel level;
    public OrionFootballWorld(ClientLevel level){this.level=level;}
    static OrionFootballPhysics.PlayerBody playerBody(Player player, OrionBasketballVector origin) {
        var box = player.getBoundingBox();
        var bounds = new OrionBasketballBox(box.minX - origin.x(), box.minY - origin.y(), box.minZ - origin.z(),
                box.maxX - origin.x(), box.maxY - origin.y(), box.maxZ - origin.z());
        var step = new OrionBasketballVector(player.getX() - player.xOld, player.getY() - player.yOld, player.getZ() - player.zOld);
        double distance = step.length();
        var velocity = distance > 1.25 || distance < .001 ? OrionBasketballVector.ZERO
                : step.scale(Math.min(20, 9 / distance));
        return new OrionFootballPhysics.PlayerBody(bounds, velocity);
    }
    @Override public List<OrionBasketballBox> obstacles(OrionBasketballVector center,double radius,OrionBasketballVector delta){
        AABB bounds=new AABB(center.x()-radius,center.y()-radius,center.z()-radius,center.x()+radius,center.y()+radius,center.z()+radius)
                .expandTowards(new Vec3(delta.x(),delta.y(),delta.z())).inflate(.002);
        List<OrionBasketballBox> boxes=new ArrayList<>();
        for(var shape:level.getBlockCollisions(null,bounds))for(AABB b:shape.toAabbs())boxes.add(new OrionBasketballBox(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        return boxes;
    }
    @Override public double groundFriction(OrionBasketballVector position){
        var state=level.getBlockState(BlockPos.containing(position.x(),position.y()-OrionFootballPhysics.RADIUS-.03,position.z()));
        float friction=state.getBlock().getFriction();
        return Math.max(.015,Math.min(.22,(1.0-friction)*.23));
    }
}
