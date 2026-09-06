package dev.craftgpt.client.placement;

import dev.craftgpt.client.build.storage.BuildArtifactSnapshot;
import dev.craftgpt.network.PlacementRequestPayload;
import dev.craftgpt.network.PlacementHistoryPayload;
import dev.craftgpt.network.PlacementHistoryRequestPayload;
import dev.craftgpt.network.PlacementRecoveryRequestPayload;
import dev.craftgpt.network.PlacementStatusPayload;
import dev.craftgpt.network.PlacementStatusRequestPayload;
import dev.craftgpt.network.PlacementUndoRequestPayload;
import dev.craftgpt.network.PlacementMaintenanceRequestPayload;
import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.List;

/** Client-side UX state only; all authority and world mutation remain on the server. */
public final class PlacementWorkflowController {
    private boolean busy;
    private boolean placed;
    private boolean undoAvailable;
    private String activeRequestId;
    private String lastStatusRequestId;
    private String placementId = "";
    private String placementBuildId = "";
    private int processed;
    private int total;
    private int conflicts;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private List<PlacementHistoryEntry> history = List.of();
    private String historyRequestId;
    private int historyRevision;

    public boolean place(Minecraft minecraft, BuildArtifactSnapshot build) {
        return place(minecraft, build, false);
    }

    public boolean place(
        Minecraft minecraft,
        BuildArtifactSnapshot build,
        boolean allowBlockEntityReplacement
    ) {
        if (busy) {
            setError("craftgpt.placement.error.busy");
            return false;
        }
        if (build == null || !build.accepted()) {
            setError("craftgpt.placement.error.not_accepted");
            return false;
        }
        if (!ClientPlayNetworking.canSend(PlacementRequestPayload.TYPE)) {
            setError("craftgpt.placement.error.server_unavailable");
            return false;
        }
        String requestId = UUID.randomUUID().toString();
        try {
            ClientPlayNetworking.send(PlacementRequestPayload.create(
                requestId,
                build.artifact(),
                allowBlockEntityReplacement
            ));
            activeRequestId = requestId;
            busy = true;
            status = Component.translatable("craftgpt.placement.status.requesting");
            statusColor = 0xFFFFFF55;
            return true;
        } catch (RuntimeException exception) {
            setError("craftgpt.placement.error.invalid_request");
            return false;
        }
    }

    public boolean undo(Minecraft minecraft) {
        if (busy) {
            setError("craftgpt.placement.error.busy");
            return false;
        }
        if (!ClientPlayNetworking.canSend(PlacementUndoRequestPayload.TYPE)) {
            setError("craftgpt.placement.error.server_unavailable");
            return false;
        }
        String requestId = UUID.randomUUID().toString();
        ClientPlayNetworking.send(new PlacementUndoRequestPayload(requestId));
        activeRequestId = requestId;
        busy = true;
        status = Component.translatable("craftgpt.placement.status.undo_requesting");
        statusColor = 0xFFFFFF55;
        return true;
    }

    public void refreshStatus() {
        if (!ClientPlayNetworking.canSend(PlacementStatusRequestPayload.TYPE)) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        lastStatusRequestId = requestId;
        ClientPlayNetworking.send(new PlacementStatusRequestPayload(requestId));
    }

    public void refreshHistory() {
        if (!ClientPlayNetworking.canSend(PlacementHistoryRequestPayload.TYPE)) {
            setError("craftgpt.placement.error.server_unavailable");
            return;
        }
        String requestId = UUID.randomUUID().toString();
        historyRequestId = requestId;
        ClientPlayNetworking.send(new PlacementHistoryRequestPayload(requestId));
    }

    public boolean recover(String placementId, String action) {
        if (busy) {
            setError("craftgpt.placement.error.busy");
            return false;
        }
        if (!ClientPlayNetworking.canSend(PlacementRecoveryRequestPayload.TYPE)) {
            setError("craftgpt.placement.error.server_unavailable");
            return false;
        }
        try {
            String requestId = UUID.randomUUID().toString();
            ClientPlayNetworking.send(new PlacementRecoveryRequestPayload(
                requestId,
                placementId,
                action
            ));
            activeRequestId = requestId;
            busy = true;
            status = Component.translatable(PlacementRecoveryRequestPayload.REDO.equals(action)
                ? "craftgpt.placement.status.redo_requesting"
                : "craftgpt.placement.status.undo_requesting");
            statusColor = 0xFFFFFF55;
            return true;
        } catch (RuntimeException exception) {
            setError("craftgpt.placement.error.invalid_request");
            return false;
        }
    }

    public boolean maintain(String placementId, String action, int retentionCount) {
        if (busy) {
            setError("craftgpt.placement.error.busy");
            return false;
        }
        if (!ClientPlayNetworking.canSend(PlacementMaintenanceRequestPayload.TYPE)) {
            setError("craftgpt.placement.error.server_unavailable");
            return false;
        }
        try {
            String requestId = UUID.randomUUID().toString();
            ClientPlayNetworking.send(new PlacementMaintenanceRequestPayload(
                requestId, placementId == null ? "" : placementId, action, retentionCount));
            activeRequestId = requestId;
            busy = true;
            status = Component.translatable("craftgpt.recovery.status.working");
            statusColor = 0xFFFFFF55;
            return true;
        } catch (RuntimeException exception) {
            setError("craftgpt.placement.error.invalid_request");
            return false;
        }
    }

    public boolean handleHistory(PlacementHistoryPayload payload) {
        if (payload == null || !payload.requestId().equals(historyRequestId)) {
            return false;
        }
        try {
            history = payload.decodeValidated();
            historyRevision++;
            return true;
        } catch (RuntimeException exception) {
            setError("craftgpt.placement.error.history_invalid");
            return false;
        }
    }

    public Outcome handle(PlacementStatusPayload payload) {
        if (payload == null) {
            return Outcome.IGNORED;
        }
        boolean statusQuery = payload.requestId().equals(lastStatusRequestId);
        if (!payload.placementId().isEmpty()) {
            placementId = payload.placementId();
        }
        if (!payload.buildId().isEmpty()) {
            placementBuildId = payload.buildId();
        }
        processed = payload.processed();
        total = payload.total();
        conflicts = payload.conflicts();

        return switch (payload.status()) {
            case PlacementStatusCodes.PLACING -> {
                busy = true;
                placed = true;
                undoAvailable = true;
                status = Component.translatable(
                    "craftgpt.placement.status.placing",
                    processed,
                    total
                );
                statusColor = 0xFFFFFF55;
                yield Outcome.PLACING;
            }
            case PlacementStatusCodes.PLACED -> {
                finishRequest();
                placed = true;
                undoAvailable = true;
                status = Component.translatable("craftgpt.placement.status.placed", total);
                statusColor = 0xFF55FF55;
                yield Outcome.PLACED;
            }
            case PlacementStatusCodes.PLACED_WITH_CONFLICTS -> {
                finishRequest();
                placed = true;
                undoAvailable = true;
                status = Component.translatable(
                    "craftgpt.placement.status.placed_conflicts",
                    Math.max(0, total - conflicts),
                    conflicts
                );
                statusColor = 0xFFFFAA55;
                yield Outcome.PLACED;
            }
            case PlacementStatusCodes.UNDOING -> {
                busy = true;
                placed = true;
                undoAvailable = false;
                status = Component.translatable(
                    "craftgpt.placement.status.undoing",
                    processed,
                    total
                );
                statusColor = 0xFFFFFF55;
                yield Outcome.UNDOING;
            }
            case PlacementStatusCodes.REDOING -> {
                busy = true;
                placed = true;
                undoAvailable = false;
                status = Component.translatable(
                    "craftgpt.placement.status.redoing",
                    processed,
                    total
                );
                statusColor = 0xFFFFFF55;
                yield Outcome.REDOING;
            }
            case PlacementStatusCodes.UNDONE -> {
                finishRequest();
                placed = false;
                undoAvailable = false;
                status = Component.translatable("craftgpt.placement.status.undone", total);
                statusColor = 0xFF55FF55;
                yield Outcome.UNDONE;
            }
            case PlacementStatusCodes.UNDONE_WITH_CONFLICTS -> {
                finishRequest();
                placed = false;
                undoAvailable = true;
                status = Component.translatable(
                    "craftgpt.placement.status.undone_conflicts",
                    Math.max(0, total - conflicts),
                    conflicts
                );
                statusColor = 0xFFFFAA55;
                yield Outcome.UNDONE_WITH_CONFLICTS;
            }
            case PlacementStatusCodes.REDONE -> {
                finishRequest();
                placed = true;
                undoAvailable = true;
                status = Component.translatable("craftgpt.placement.status.redone", total);
                statusColor = 0xFF55FF55;
                yield Outcome.REDONE;
            }
            case PlacementStatusCodes.REDONE_WITH_CONFLICTS -> {
                finishRequest();
                placed = true;
                undoAvailable = true;
                status = Component.translatable(
                    "craftgpt.placement.status.redone_conflicts",
                    Math.max(0, total - conflicts),
                    conflicts
                );
                statusColor = 0xFFFFAA55;
                yield Outcome.REDONE_WITH_CONFLICTS;
            }
            case PlacementStatusCodes.INTERRUPTED -> {
                finishRequest();
                placed = true;
                undoAvailable = true;
                status = Component.translatable(
                    "craftgpt.placement.status.interrupted",
                    processed,
                    total
                );
                statusColor = 0xFFFF5555;
                yield Outcome.INTERRUPTED;
            }
            case PlacementStatusCodes.EXPORTED -> {
                finishRequest();
                status = Component.translatable("craftgpt.recovery.status.exported");
                statusColor = 0xFF55FF55;
                yield Outcome.MAINTENANCE;
            }
            case PlacementStatusCodes.IMPORTED -> {
                finishRequest();
                status = Component.translatable("craftgpt.recovery.status.imported");
                statusColor = 0xFF55FF55;
                yield Outcome.MAINTENANCE;
            }
            case PlacementStatusCodes.PRUNED -> {
                finishRequest();
                status = Component.translatable("craftgpt.recovery.status.pruned", total);
                statusColor = 0xFF55FF55;
                yield Outcome.MAINTENANCE;
            }
            case PlacementStatusCodes.NO_PLACEMENT -> {
                if (statusQuery) {
                    finishRequest();
                    placementId = "";
                    placementBuildId = "";
                    placed = false;
                    undoAvailable = false;
                    status = Component.empty();
                    statusColor = 0xFFAAAAAA;
                    yield Outcome.NO_PLACEMENT;
                }
                yield reject(payload.status());
            }
            default -> reject(payload.status());
        };
    }

    private Outcome reject(String code) {
        finishRequest();
        status = rejectionMessage(code);
        statusColor = 0xFFFF5555;
        return Outcome.REJECTED;
    }

    private Component rejectionMessage(String code) {
        return switch (code) {
            case PlacementStatusCodes.FORBIDDEN ->
                Component.translatable("craftgpt.placement.error.forbidden");
            case PlacementStatusCodes.BUSY ->
                Component.translatable("craftgpt.placement.error.busy");
            case PlacementStatusCodes.RATE_LIMITED ->
                Component.translatable("craftgpt.placement.error.rate_limited");
            case PlacementStatusCodes.STALE_CONTEXT ->
                Component.translatable("craftgpt.placement.error.stale");
            case PlacementStatusCodes.UNLOADED_AREA ->
                Component.translatable("craftgpt.placement.error.unloaded");
            case PlacementStatusCodes.BLOCK_ENTITY_UNSAFE ->
                Component.translatable("craftgpt.placement.error.block_entity");
            case PlacementStatusCodes.PHYSICS_UNSAFE ->
                Component.translatable("craftgpt.placement.error.physics");
            case PlacementStatusCodes.ALREADY_UNDONE ->
                Component.translatable("craftgpt.placement.error.already_undone");
            case PlacementStatusCodes.NO_PLACEMENT ->
                Component.translatable("craftgpt.placement.error.no_placement");
            case PlacementStatusCodes.STORAGE_ERROR ->
                Component.translatable("craftgpt.placement.error.storage");
            case PlacementStatusCodes.NOT_REDOABLE ->
                Component.translatable("craftgpt.placement.error.not_redoable");
            case PlacementStatusCodes.NEWER_OVERLAP ->
                Component.translatable("craftgpt.placement.error.newer_overlap");
            case PlacementStatusCodes.BLOCK_ENTITY_LIMIT ->
                Component.translatable("craftgpt.placement.error.block_entity_limit");
            case PlacementStatusCodes.ARCHIVE_MISSING ->
                Component.translatable("craftgpt.recovery.error.archive_missing");
            case PlacementStatusCodes.JOURNAL_EXISTS ->
                Component.translatable("craftgpt.recovery.error.journal_exists");
            default -> Component.translatable("craftgpt.placement.error.generic", code);
        };
    }

    public void disconnect() {
        busy = false;
        placed = false;
        undoAvailable = false;
        activeRequestId = null;
        lastStatusRequestId = null;
        placementId = "";
        placementBuildId = "";
        processed = 0;
        total = 0;
        conflicts = 0;
        history = List.of();
        historyRequestId = null;
        historyRevision++;
        status = Component.empty();
        statusColor = 0xFFAAAAAA;
    }

    private void finishRequest() {
        busy = false;
        activeRequestId = null;
    }

    private void setError(String key) {
        finishRequest();
        status = Component.translatable(key);
        statusColor = 0xFFFF5555;
    }

    public boolean busy() {
        return busy;
    }

    public boolean placed() {
        return placed;
    }

    public boolean placedForBuild(String buildId) {
        return placed
            && buildId != null
            && buildId.equals(placementBuildId);
    }

    public boolean latestMatchesBuild(String buildId) {
        return buildId != null && buildId.equals(placementBuildId);
    }

    public boolean undoAvailable() {
        return undoAvailable;
    }

    public Component status() {
        return status;
    }

    public int statusColor() {
        return statusColor;
    }

    public boolean hasStatus() {
        return !status.getString().isEmpty();
    }

    public void clearStatus() {
        if (!busy) {
            status = Component.empty();
            statusColor = 0xFFAAAAAA;
        }
    }

    public int processed() {
        return processed;
    }

    public int total() {
        return total;
    }

    public int conflicts() {
        return conflicts;
    }

    public List<PlacementHistoryEntry> history() {
        return history;
    }

    public int historyRevision() {
        return historyRevision;
    }

    public enum Outcome {
        PLACING,
        PLACED,
        UNDOING,
        REDOING,
        UNDONE,
        UNDONE_WITH_CONFLICTS,
        REDONE,
        REDONE_WITH_CONFLICTS,
        INTERRUPTED,
        MAINTENANCE,
        NO_PLACEMENT,
        REJECTED,
        IGNORED
    }
}
