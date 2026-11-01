package ru.orion.client.basketball.engine;

import ru.orion.client.config.OrionConfigManager;

/** Адаптер параметров восстановленного движка к конфигурации Orion. */
public final class OrionBasketballSettings {
    private static OrionConfigManager manager;
    private static final OrionBasketballSettings VALUE = new OrionBasketballSettings();
    public boolean basket, basketWalk=true, basketCatch=true, basketHands=true, basketLines=true, basketPreview=true;
    public float basketPower=13.5F, basketCharge=1F, basketPass=13F, basketDribble=2.9F;
    public float basketGravity=9.81F, basketSpin=1F, basketBounce=.72F, basketVolume=.75F;
    public float basketBallScale=1F;
    public static void bind(OrionConfigManager configManager) { manager=configManager; }
    public static OrionBasketballSettings get() {
        if(manager!=null) {
            var config=manager.getConfig();
            VALUE.basket=config.isBasketballEnabled();
            VALUE.basketPower=13.5F*config.getBasketballThrowPower();
            VALUE.basketCharge=1F/config.getBasketballChargeSpeed();
            VALUE.basketDribble=2.9F*config.getBasketballDribbleSpeed();
            VALUE.basketGravity=config.getBasketballGravity();
            VALUE.basketBounce=config.getBasketballBounce();
            VALUE.basketVolume=config.getBasketballVolume();
            VALUE.basketBallScale=config.getBasketballBallScale();
            VALUE.basketPass=config.getBasketballPassPower();
            VALUE.basketSpin=config.getBasketballSpin();
            VALUE.basketCatch=config.isBasketballAutoCatch();
            VALUE.basketWalk=config.isBasketballAutoDribble();
            VALUE.basketHands=config.isBasketballHands();
            VALUE.basketLines=config.isBasketballCourtLines();
            VALUE.basketPreview=config.isBasketballTrajectory();
        }
        return VALUE;
    }
}
