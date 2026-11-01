package ru.orion.client.football;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class OrionFootballStorage {
    private static final Logger LOG=LoggerFactory.getLogger("orion-client");
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path path=FabricLoader.getInstance().getConfigDir().resolve("orion-football-goals.json");
    private final java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"orion-football-storage");t.setDaemon(true);return t;});
    private Map<String,List<OrionFootballGoal>> data=new HashMap<>();
    public void load(Consumer<Map<String,List<OrionFootballGoal>>> callback){executor.execute(()->{
        try{if(Files.exists(path)){Map<String,List<OrionFootballGoal>> loaded=GSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),new TypeToken<Map<String,List<OrionFootballGoal>>>(){}.getType());if(loaded!=null)data=loaded;}}
        catch(Exception e){LOG.warn("Не удалось загрузить футбольные ворота",e);}
        Map<String,List<OrionFootballGoal>> snapshot=new HashMap<>();data.forEach((k,v)->{if(k!=null&&v!=null)snapshot.put(k,new ArrayList<>(v));});
        Minecraft.getInstance().execute(()->callback.accept(snapshot));
    });}
    public void save(String key,List<OrionFootballGoal> goals){List<OrionFootballGoal> copy=List.copyOf(goals);executor.execute(()->{
        data.put(key,copy);try{Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,GSON.toJson(data),StandardCharsets.UTF_8);
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException ignored){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}
        catch(Exception e){LOG.warn("Не удалось сохранить футбольные ворота",e);}
    });}
}
