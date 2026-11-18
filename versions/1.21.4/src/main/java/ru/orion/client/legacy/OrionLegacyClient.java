package ru.orion.client.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionLegacyBlockHighlight;
import ru.orion.client.effect.OrionLegacyBreadcrumbs;
import ru.orion.client.effect.OrionLegacyJumpCircleEffect;
import ru.orion.client.effect.OrionLegacyProjectileTrail;
import ru.orion.client.effect.OrionLegacyTargetEsp;
import ru.orion.client.effect.OrionLegacyWorldParticles;
import ru.orion.client.effect.OrionLegacyZoom;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class OrionLegacyClient implements ClientModInitializer {
    public static final String MOD_ID = "orion-client";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("orion-client-1.21.4.json");
    private static OrionLegacyClient instance;
    private OrionConfig config = OrionConfig.createDefault();

    @Override
    public void onInitializeClient() {
        load();
        instance = this;
        OrionLegacyZoom.register();
        OrionHudRenderer hud = new OrionHudRenderer(config);
        hud.register();

        OrionLegacyTargetEsp targetEsp = new OrionLegacyTargetEsp(config);
        targetEsp.register();
        new OrionLegacyJumpCircleEffect(config).register();
        new OrionLegacyBreadcrumbs(config).register();
        new OrionLegacyBlockHighlight(config).register();
        new OrionLegacyProjectileTrail(config).register();
        new OrionLegacyWorldParticles(config).register();
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClientSide() && !player.isSpectator()) {
                targetEsp.recordHit(target);
            }
            return InteractionResult.PASS;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hud.releaseTextures());

        KeyMapping openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.orion-client.menu", InputConstants.KEY_RSHIFT, "key.categories.orion-client"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new OrionLegacyScreen(config, this::save));
                }
            }
        });
    }

    public static OrionConfig getConfigOrNull() {
        return instance == null ? null : instance.config;
    }

    private void load() {
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        try (var reader = Files.newBufferedReader(configPath)) {
            OrionConfig loaded = GSON.fromJson(reader, OrionConfig.class);
            if (loaded != null) {
                loaded.sanitize();
                config = loaded;
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to load Orion Client 1.21.4 config", exception);
        }
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Path temporary = Files.createTempFile(configPath.getParent(), "orion-client-1.21.4-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(config));
                try {
                    Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save Orion Client 1.21.4 config", exception);
        }
    }
}
