package dev.craftgpt;

import dev.craftgpt.area.AreaCommands;
import dev.craftgpt.area.AreaContextRefreshServer;
import dev.craftgpt.area.AreaSelectionManager;
import dev.craftgpt.build.server.BuildPreviewServer;
import dev.craftgpt.network.CraftGptNetworking;
import dev.craftgpt.item.CraftBookServer;
import dev.craftgpt.item.CraftGptItems;
import dev.craftgpt.placement.server.PlacementManager;
import dev.craftgpt.placement.server.PlacementServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftGptMod implements ModInitializer {
    public static final String MOD_ID = "craftgpt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CraftGptNetworking.registerPayloads();
        CraftGptItems.initialize();
        CraftBookServer.register();
        AreaContextRefreshServer.register();
        BuildPreviewServer.register();
        PlacementServer.register();
        AreaCommands.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            AreaSelectionManager.INSTANCE.clear(handler.player.getUUID());
            BuildPreviewServer.clear(handler.player.getUUID());
            PlacementManager.INSTANCE.clearPlayer(handler.player.getUUID());
        });
        LOGGER.info("CraftGPT initialized");
    }
}
