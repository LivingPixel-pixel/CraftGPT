package dev.craftgpt.area;

import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextExtractor;
import dev.craftgpt.network.AreaContextPayload;
import dev.craftgpt.network.AreaContextRefreshRequestPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;

/** Serves bounded read-only area refreshes for UI-driven iterations. */
public final class AreaContextRefreshServer {
    private AreaContextRefreshServer() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(AreaContextRefreshRequestPayload.TYPE, (payload, context) -> {
            AreaSelection selection = AreaSelectionManager.INSTANCE
                .get(context.player().getUUID())
                .filter(AreaSelection::isComplete)
                .orElse(null);
            if (selection == null
                || !selection.dimension().equals(context.player().level().dimension().identifier().toString())) {
                return;
            }
            AreaContext area = AreaContextExtractor.extract((ServerLevel) context.player().level(), selection);
            if (area.unloadedBlocks() == 0
                && ServerPlayNetworking.canSend(context.player(), AreaContextPayload.TYPE)) {
                ServerPlayNetworking.send(context.player(), AreaContextPayload.from(area));
            }
        });
    }
}
