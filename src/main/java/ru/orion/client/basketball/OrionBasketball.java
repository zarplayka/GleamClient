package ru.orion.client.basketball;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.basketball.engine.*;
import ru.orion.client.config.OrionConfigManager;

/** Жизненный цикл восстановленной мини-игры и её интеграция с Orion. */
public final class OrionBasketball {
    public static final double BALL_RADIUS=.121*2.1;
    private final OrionConfigManager manager;
    private KeyMapping move, remove, pickup, reset, left, right;
    private long shotAt;
    private float lastCharge;
    private boolean wasCharging;
    private OrionHoop movingHoop;
    public OrionBasketball(OrionConfigManager manager) { this.manager=manager; }
    public void register() {
        OrionBasketballSettings.bind(manager);
        OrionBasketballInput.register();
        OrionHoopStore.load();migrateLegacyHoops();
        OrionBasketballController.register();OrionBasketballScene.register();
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"basketball_tools"));
        move=key("move",category);remove=key("remove",category);pickup=key("pickup",category);
        reset=key("reset",category);left=key("rotate_left",category);right=key("rotate_right",category);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static KeyMapping key(String name,KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orion-client.basketball."+name,
                InputConstants.Type.KEYBOARD,InputConstants.UNKNOWN.getValue(),category));
    }
    private void tick(Minecraft client) {
        if(!manager.getConfig().isBasketballEnabled() || client.player==null || client.level==null) {
            wasCharging=false;shotAt=0;movingHoop=null;drain();return;
        }
        boolean charging=OrionBasketballController.charging();
        if(wasCharging&&!charging&&OrionBasketballController.mode()==OrionBasketballController.Mode.LOOSE)shotAt=System.nanoTime();
        if(charging)lastCharge=OrionBasketballController.charge(1F);
        wasCharging=charging;
        if(client.gui.screen()!=null){drain();return;}
        while(reset.consumeClick())resetScore();
        while(pickup.consumeClick())OrionBasketballController.pickup();
        while(remove.consumeClick()){var hoop=nearest(client);if(hoop!=null)OrionHoopStore.remove(hoop);}
        while(move.consumeClick()) {
            if(movingHoop==null)movingHoop=nearest(client);
            else {if(OrionBasketballController.placeHoopAtLook(client))OrionHoopStore.remove(movingHoop);movingHoop=null;}
        }
        while(left.consumeClick())rotate(client,-15F);
        while(right.consumeClick())rotate(client,15F);
    }
    private void drain(){for(var key:new KeyMapping[]{move,remove,pickup,reset,left,right})while(key.consumeClick()){} }
    private static OrionHoop nearest(Minecraft client) {
        OrionHoop best=null;double distance=64;
        for(var hoop:OrionHoopStore.inWorld(client)) {
            double d=client.player.position().distanceToSqr(hoop.x,hoop.base,hoop.z);
            if(d<distance){best=hoop;distance=d;}
        }
        return best;
    }
    private static void rotate(Minecraft client,float degrees){var hoop=nearest(client);if(hoop!=null){hoop.yaw+=degrees;OrionHoopStore.put(hoop);}}
    private void migrateLegacyHoops() {
        if(OrionHoopStore.hasStoredFile()||OrionHoopStore.total()!=0)return;
        new OrionBasketballStorage().load(worlds->Minecraft.getInstance().execute(()->{
            if(OrionHoopStore.total()!=0)return;
            for(var hoop:OrionBasketballMigration.convert(worlds))OrionHoopStore.put(hoop);
        }));
    }
    public boolean holdingBall(){return OrionBasketballController.localHolding();}
    public boolean renderBasketballHand(){return holdingBall();}
    public float ballRadius(){return (float)OrionBasketballController.ball().radius;}
    public float handMovement(float partial){return OrionBasketballController.handBounce(Minecraft.getInstance().player,partial);}
    public float armPitch(float partial){return -1.05F+handMovement(partial)*.8F-charge()*.35F;}
    public float charge(){return OrionBasketballController.charge(1F);}
    public boolean isCharging(){return OrionBasketballController.charging();}
    public float indicatorAlpha(){return isCharging()?1F:shotAt==0?0F:Math.max(0,1F-(System.nanoTime()-shotAt)/250_000_000F);}
    public float indicatorCharge(){return isCharging()?charge():lastCharge;}
    public int score(){return OrionBasketballController.score();}
    public long scoreAt(){long time=OrionBasketballController.scoredAt();return time==0?0:System.nanoTime()-(System.currentTimeMillis()-time)*1_000_000L;}
    public int hoopCount(){var client=Minecraft.getInstance();return client.level==null?0:OrionHoopStore.inWorld(client).size();}
    public KeyMapping getBallKey(){return OrionBasketballInput.ballKey();}
    public void clearHoopsInCurrentWorld(){var c=Minecraft.getInstance();if(c.level!=null)OrionHoopStore.clearWorld(OrionBasketballController.courtKey(c),c.level.dimension().identifier().toString());}
    public void resetScore(){OrionBasketballController.resetScore();}
    public void removeBall(){OrionBasketballController.removeBall();}
}
