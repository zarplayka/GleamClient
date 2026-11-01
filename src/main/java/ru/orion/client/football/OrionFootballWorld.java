package ru.orion.client.football;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
