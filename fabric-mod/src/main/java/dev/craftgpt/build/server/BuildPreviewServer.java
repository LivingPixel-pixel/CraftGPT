package dev.craftgpt.build.server;

import dev.craftgpt.network.BuildPreviewRequestPayload;
import dev.craftgpt.network.BuildPreviewResponsePayload;
import dev.craftgpt.validation.ValidationProblemJson;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/** Registers the serverbound preview-validation endpoint. */
public final class BuildPreviewServer {
    private static final BuildPreviewValidator VALIDATOR = new BuildPreviewValidator();
    private static final long MINIMUM_REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final ConcurrentMap<UUID, Long> LAST_REQUEST_NANOS = new ConcurrentHashMap<>();

    private BuildPreviewServer() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(BuildPreviewRequestPayload.TYPE, (payload, context) -> {
            String requestId = payload.requestIdOrEmpty();
            if (!tryAcquire(context.player().getUUID(), System.nanoTime())) {
                send(context, requestId, BuildPreviewValidationResult.rejected(
                    BuildPreviewValidationResult.RATE_LIMITED
                ));
                return;
            }
            BuildPreviewRequestPayload.RequestBody request;
            try {
                request = payload.decodeValidated();
                requestId = request.requestId();
            } catch (RuntimeException exception) {
                send(context, requestId, BuildPreviewValidationResult.rejected(
                    BuildPreviewValidationResult.INVALID_REQUEST
                ));
                return;
            }

            BuildPreviewValidationResult result;
            try {
                result = VALIDATOR.validate(context.player(), request.build());
            } catch (RuntimeException exception) {
                result = BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.INTERNAL_ERROR);
            }
            send(context, requestId, result);
        });
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            LAST_REQUEST_NANOS.remove(playerId);
        }
    }

    static boolean tryAcquire(UUID playerId, long nowNanos) {
        if (playerId == null) {
            return false;
        }
        boolean[] allowed = {false};
        LAST_REQUEST_NANOS.compute(playerId, (ignored, previous) -> {
            if (previous == null || nowNanos - previous >= MINIMUM_REQUEST_INTERVAL_NANOS) {
                allowed[0] = true;
                return nowNanos;
            }
            return previous;
        });
        return allowed[0];
    }

    private static void send(
        ServerPlayNetworking.Context context,
        String requestId,
        BuildPreviewValidationResult result
    ) {
        if (ServerPlayNetworking.canSend(context.player(), BuildPreviewResponsePayload.TYPE)) {
            ServerPlayNetworking.send(context.player(), new BuildPreviewResponsePayload(
                requestId,
                result.accepted(),
                result.code(),
                result.actualChanges(),
                ValidationProblemJson.encode(result.problems())
            ));
        }
    }
}
