package ru.orion.client.basketball;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Локальные площадки, записываемые последовательно вне рендера. */
public final class OrionBasketballStorage {
    private static final Logger LOGGER=LoggerFactory.getLogger("orion-client");
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE=new TypeToken<Map<String,List<OrionBasketballHoop>>>(){}.getType();
    private final Path path=FabricLoader.getInstance().getConfigDir().resolve("orion-basketball-hoops.json");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"orion-basketball-storage");t.setDaemon(true);return t;});
    private Map<String,List<OrionBasketballHoop>> data=new HashMap<>();
    private boolean loaded;
    public void load(Consumer<Map<String,List<OrionBasketballHoop>>> done) {
        worker.execute(()->{
            if(!loaded){
                try { if(Files.exists(path)){Map<String,List<OrionBasketballHoop>> parsed=GSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),TYPE);if(parsed!=null)data=parsed;} }
                catch(Exception e){LOGGER.warn("Не удалось загрузить баскетбольные кольца",e);}
                loaded=true;
            }
            Map<String,List<OrionBasketballHoop>> snapshot=new HashMap<>();
            data.forEach((key,value)->{if(key!=null&&value!=null)snapshot.put(key,new ArrayList<>(value));});
            done.accept(snapshot);
        });
    }
    public void save(String key,List<OrionBasketballHoop> hoops) {
        List<OrionBasketballHoop> snapshot=List.copyOf(hoops);
        worker.execute(()->{
            data.put(key,snapshot);
            try { Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");
                Files.writeString(temp,GSON.toJson(data),StandardCharsets.UTF_8);
                try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException ignored){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
            } catch(IOException e){LOGGER.warn("Не удалось сохранить баскетбольные кольца",e);}
        });
    }
}
