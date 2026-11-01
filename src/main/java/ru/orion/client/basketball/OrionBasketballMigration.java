package ru.orion.client.basketball;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import ru.orion.client.basketball.engine.OrionHoop;

/** Перевод старого локального формата в геометрию нового движка, без изменения исходных данных. */
public final class OrionBasketballMigration {
    private OrionBasketballMigration() { }
    public static List<OrionHoop> convert(Map<String,List<OrionBasketballHoop>> worlds) {
        List<OrionHoop> result=new ArrayList<>();
        worlds.forEach((world,hoops)->{
            if(world==null||hoops==null)return;
            int separator=world.lastIndexOf('|');if(separator<0)return;
            for(var old:hoops) {
                if(old==null)continue;
                var rim=old.toWorld(new OrionBasketballVector(0,OrionBasketballHoop.RIM_HEIGHT,OrionBasketballHoop.RIM_FORWARD));
                result.add(new OrionHoop(UUID.randomUUID().toString(),world.substring(separator+1),world.toLowerCase(Locale.ROOT),
                        rim.x(),rim.y(),rim.z(),old.base().y(),(float)Math.toDegrees(old.yaw()),2.1));
            }
        });
        return result;
    }
}
