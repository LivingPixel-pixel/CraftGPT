package dev.craftgpt.item;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.network.CraftBookActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class CraftBookServer {
    private CraftBookServer() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CraftBookActionPayload.TYPE, (payload, context) -> {
            try {
                context.server().getCommands().performPrefixedCommand(
                    context.player().createCommandSourceStack(), payload.command());
            } catch (RuntimeException exception) {
                CraftGptMod.LOGGER.warn("Rejected CraftBook action", exception);
            }
        });
    }
}
