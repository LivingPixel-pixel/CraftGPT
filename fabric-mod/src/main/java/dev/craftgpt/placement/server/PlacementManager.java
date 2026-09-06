package dev.craftgpt.placement.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.craftgpt.CraftGptMod;
import dev.craftgpt.area.AreaBounds;
import dev.craftgpt.area.AreaSelection;
import dev.craftgpt.area.AreaSelectionManager;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.BuildValidationRequest;
import dev.craftgpt.build.server.BuildPreviewValidationResult;
import dev.craftgpt.build.server.BuildPreviewValidator;
import dev.craftgpt.network.PlacementStatusPayload;
import dev.craftgpt.network.PlacementHistoryPayload;
import dev.craftgpt.network.PlacementInspectPayload;
import dev.craftgpt.network.PlacementMaintenanceRequestPayload;
import dev.craftgpt.network.PlacementRecoveryRequestPayload;
import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementChange;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import dev.craftgpt.placement.model.PlacementSnapshot;
import dev.craftgpt.placement.model.PlacementState;
import dev.craftgpt.placement.storage.PlacementJournalRepository;
import dev.craftgpt.placement.storage.PlacementJournalRepository.LoadedPlacement;
import dev.craftgpt.placement.storage.PlacementArchiveRepository;
import dev.craftgpt.placement.storage.PlacementArchiveRepository.RecoveryArchive;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Server-authoritative placement scheduler. Every mutation is journaled first and guarded by
 * before/after state comparisons so unrelated player changes are never silently overwritten.
 */
public enum PlacementManager {
    INSTANCE;

    private static final long MINIMUM_MUTATION_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS
        | Block.UPDATE_KNOWN_SHAPE
        | Block.UPDATE_SUPPRESS_DROPS
        | Block.UPDATE_SKIP_ON_PLACE
        | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private final BuildPreviewValidator previewValidator = new BuildPreviewValidator();
    private final Map<UUID, ActiveJob> activeJobs = new HashMap<>();
    private final Set<WorldPosition> reservedPositions = new HashSet<>();
    private final Map<UUID, Long> lastMutationRequestNanos = new HashMap<>();

    private MinecraftServer server;
    private PlacementJournalRepository repository;
    private PlacementArchiveRepository archiveRepository;

    public synchronized void startServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
        activeJobs.clear();
        reservedPositions.clear();
        lastMutationRequestNanos.clear();
        var craftGptRoot = minecraftServer.getWorldPath(LevelResource.ROOT).resolve("craftgpt");
        repository = new PlacementJournalRepository(craftGptRoot.resolve("placements"));
        archiveRepository = new PlacementArchiveRepository(craftGptRoot.resolve("recovery-archives"));
    }

    public synchronized void stopServer(MinecraftServer minecraftServer) {
        for (ActiveJob job : List.copyOf(activeJobs.values())) {
            interrupt(job, PlacementStatusCodes.INTERRUPTED);
        }
        activeJobs.clear();
        reservedPositions.clear();
        lastMutationRequestNanos.clear();
        repository = null;
        archiveRepository = null;
        server = null;
    }

    public synchronized void requestPlacement(
        ServerPlayer player,
        String requestId,
        String buildId,
        boolean allowBlockEntityReplacement,
        BuildValidationRequest build
    ) {
        if (!authorized(player)) {
            sendError(player, requestId, PlacementStatusCodes.FORBIDDEN);
            return;
        }
        if (!tryAcquire(player.getUUID(), System.nanoTime())) {
            sendError(player, requestId, PlacementStatusCodes.RATE_LIMITED);
            return;
        }
        if (repository == null || server == null) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
            return;
        }
        if (activeJobs.containsKey(player.getUUID())) {
            sendError(player, requestId, PlacementStatusCodes.BUSY);
            return;
        }

        BuildPreviewValidationResult validation = previewValidator.validate(player, build);
        if (!validation.accepted()) {
            sendError(player, requestId, mapValidationFailure(validation.code()));
            return;
        }

        try {
            PreparedPlacement prepared = prepare(
                player,
                buildId,
                allowBlockEntityReplacement,
                build,
                validation.actualChanges()
            );
            if (!reserve(prepared.dimension(), prepared.runtimeChanges())) {
                sendError(player, requestId, PlacementStatusCodes.BUSY);
                return;
            }

            LoadedPlacement loaded;
            try {
                loaded = repository.create(prepared.snapshot());
                PlacementState placing = state(
                    loaded,
                    PlacementStatusCodes.PLACING,
                    0,
                    0,
                    0
                );
                loaded = repository.update(loaded.snapshot(), placing);
            } catch (IOException exception) {
                release(prepared.dimension(), prepared.runtimeChanges());
                throw exception;
            }

            ActiveJob job = new ActiveJob(
                player.getUUID(),
                requestId,
                prepared.dimension(),
                loaded,
                prepared.runtimeChanges(),
                JobMode.PLACE,
                0,
                0,
                0,
                0
            );
            activeJobs.put(player.getUUID(), job);
            send(player, statusPayload(job, PlacementStatusCodes.PLACING));
        } catch (UnsafePlacementException exception) {
            sendError(player, requestId, exception.code());
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not prepare CraftGPT placement", exception);
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
        }
    }

    public synchronized void requestUndo(ServerPlayer player, String requestId) {
        requestRecovery(player, requestId, null, PlacementRecoveryRequestPayload.UNDO);
    }

    public synchronized void requestRecovery(
        ServerPlayer player,
        String requestId,
        String placementId,
        String action
    ) {
        if (!authorized(player)) {
            sendError(player, requestId, PlacementStatusCodes.FORBIDDEN);
            return;
        }
        if (repository == null || server == null) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
            return;
        }

        boolean redoRequested = PlacementRecoveryRequestPayload.REDO.equals(action);
        if (!redoRequested && !PlacementRecoveryRequestPayload.UNDO.equals(action)) {
            sendError(player, requestId, PlacementStatusCodes.INVALID_REQUEST);
            return;
        }

        ActiveJob existing = activeJobs.get(player.getUUID());
        if (existing == null && !tryAcquire(player.getUUID(), System.nanoTime())) {
            sendError(player, requestId, PlacementStatusCodes.RATE_LIMITED);
            return;
        }
        if (existing != null) {
            boolean emergencyUndo = !redoRequested
                && existing.mode() == JobMode.PLACE
                && (placementId == null
                    || placementId.equals(existing.loaded().snapshot().placementId()));
            if (!emergencyUndo) {
                sendError(player, requestId, PlacementStatusCodes.BUSY);
                return;
            }
            interrupt(existing, PlacementStatusCodes.INTERRUPTED);
        }

        LoadedPlacement loaded;
        try {
            loaded = placementId == null
                ? repository.loadLatest(player.getUUID()).orElse(null)
                : repository.load(player.getUUID(), UUID.fromString(placementId));
        } catch (IOException | IllegalArgumentException exception) {
            loaded = null;
        }
        if (loaded == null) {
            sendError(player, requestId, PlacementStatusCodes.NO_PLACEMENT);
            return;
        }
        if (!redoRequested && PlacementStatusCodes.UNDONE.equals(loaded.state().status())) {
            sendError(player, requestId, PlacementStatusCodes.ALREADY_UNDONE);
            return;
        }
        if (redoRequested && !PlacementStatusCodes.isUndone(loaded.state().status())) {
            sendError(player, requestId, PlacementStatusCodes.NOT_REDOABLE);
            return;
        }

        try {
            if (hasBlockingNewerOverlap(player.getUUID(), loaded)) {
                sendError(player, requestId, PlacementStatusCodes.NEWER_OVERLAP);
                return;
            }
            ServerLevel level = levelFor(loaded.snapshot().dimension());
            List<RuntimeChange> changes = runtimeChanges(level, loaded.snapshot());
            JobMode mode = redoRequested ? JobMode.REDO : JobMode.UNDO;
            if (mode == JobMode.UNDO) {
                Collections.reverse(changes);
            }
            if (!reserve(loaded.snapshot().dimension(), changes)) {
                sendError(player, requestId, PlacementStatusCodes.BUSY);
                return;
            }
            int baseConflicts = 0;
            try {
                PlacementState recovering = state(
                    loaded,
                    mode == JobMode.UNDO
                        ? PlacementStatusCodes.UNDOING
                        : PlacementStatusCodes.REDOING,
                    mode == JobMode.UNDO ? loaded.state().processedChanges() : 0,
                    0,
                    0
                );
                loaded = repository.update(loaded.snapshot(), recovering);
            } catch (IOException exception) {
                release(loaded.snapshot().dimension(), changes);
                throw exception;
            }
            ActiveJob recovery = new ActiveJob(
                player.getUUID(),
                requestId,
                loaded.snapshot().dimension(),
                loaded,
                changes,
                mode,
                0,
                baseConflicts,
                mode == JobMode.UNDO ? loaded.state().processedChanges() : 0,
                baseConflicts
            );
            activeJobs.put(player.getUUID(), recovery);
            send(player, statusPayload(
                recovery,
                mode == JobMode.UNDO ? PlacementStatusCodes.UNDOING : PlacementStatusCodes.REDOING
            ));
        } catch (UnsafePlacementException exception) {
            sendError(player, requestId, exception.code());
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not start CraftGPT recovery", exception);
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
        }
    }

    public synchronized void requestHistory(ServerPlayer player, String requestId) {
        if (player == null || repository == null || archiveRepository == null) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
            return;
        }
        try {
            Map<String, PlacementHistoryEntry> entries = new LinkedHashMap<>();
            for (LoadedPlacement original : repository.listHistory(player.getUUID())) {
                LoadedPlacement loaded = original;
                if (PlacementStatusCodes.isBusy(loaded.state().status())
                    && !activeJobs.containsKey(player.getUUID())) {
                    loaded = repository.markInterrupted(loaded);
                }
                boolean archived = archiveRepository.load(
                    player.getUUID(), UUID.fromString(loaded.snapshot().placementId())).isPresent();
                entries.put(loaded.snapshot().placementId(), historyEntry(loaded, true, archived));
            }
            for (RecoveryArchive archive : archiveRepository.list(player.getUUID())) {
                entries.putIfAbsent(
                    archive.snapshot().placementId(),
                    historyEntry(new LoadedPlacement(archive.snapshot(), archive.state()), false, true)
                );
            }
            if (ServerPlayNetworking.canSend(player, PlacementHistoryPayload.TYPE)) {
                List<PlacementHistoryEntry> sorted = entries.values().stream()
                    .sorted(Comparator.comparing(
                        (PlacementHistoryEntry entry) -> Instant.parse(entry.createdAt())).reversed())
                    .limit(PlacementLimits.MAX_HISTORY_ENTRIES)
                    .toList();
                ServerPlayNetworking.send(player, PlacementHistoryPayload.create(requestId, sorted));
            }
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not read CraftGPT placement history", exception);
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
        }
    }

    public synchronized void requestMaintenance(
        ServerPlayer player,
        String requestId,
        String placementId,
        String action,
        int retentionCount
    ) {
        if (!authorized(player)) {
            sendError(player, requestId, PlacementStatusCodes.FORBIDDEN);
            return;
        }
        if (repository == null || archiveRepository == null) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
            return;
        }
        if (activeJobs.containsKey(player.getUUID())) {
            sendError(player, requestId, PlacementStatusCodes.BUSY);
            return;
        }
        try {
            if (PlacementMaintenanceRequestPayload.EXPORT.equals(action)) {
                LoadedPlacement loaded = repository.load(player.getUUID(), UUID.fromString(placementId));
                archiveRepository.export(loaded);
                sendMaintenanceStatus(player, requestId, loaded, PlacementStatusCodes.EXPORTED, 1);
            } else if (PlacementMaintenanceRequestPayload.IMPORT.equals(action)) {
                UUID id = UUID.fromString(placementId);
                if (repository.exists(player.getUUID(), id)) {
                    sendError(player, requestId, PlacementStatusCodes.JOURNAL_EXISTS);
                    return;
                }
                RecoveryArchive archive = archiveRepository.load(player.getUUID(), id).orElse(null);
                if (archive == null) {
                    sendError(player, requestId, PlacementStatusCodes.ARCHIVE_MISSING);
                    return;
                }
                LoadedPlacement restored = repository.restore(archive.snapshot(), archive.state());
                sendMaintenanceStatus(player, requestId, restored, PlacementStatusCodes.IMPORTED, 1);
            } else if (PlacementMaintenanceRequestPayload.PRUNE.equals(action)) {
                if (retentionCount < PlacementLimits.MIN_RETENTION_COUNT
                    || retentionCount > PlacementLimits.MAX_RETENTION_COUNT) {
                    sendError(player, requestId, PlacementStatusCodes.INVALID_REQUEST);
                    return;
                }
                List<LoadedPlacement> history = repository.listHistory(player.getUUID());
                int pruned = 0;
                for (int index = retentionCount; index < history.size(); index++) {
                    LoadedPlacement loaded = history.get(index);
                    if (PlacementStatusCodes.isBusy(loaded.state().status())) continue;
                    archiveRepository.export(loaded);
                    repository.remove(player.getUUID(), UUID.fromString(loaded.snapshot().placementId()));
                    pruned++;
                }
                send(player, new PlacementStatusPayload(
                    requestId, "", "", PlacementStatusCodes.PRUNED, pruned, pruned, 0));
            } else {
                sendError(player, requestId, PlacementStatusCodes.INVALID_REQUEST);
            }
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not maintain CraftGPT recovery data", exception);
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
        }
    }

    public synchronized void requestInspect(ServerPlayer player, String requestId, String placementId) {
        if (player == null || repository == null || archiveRepository == null) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
            return;
        }
        try {
            UUID id = UUID.fromString(placementId);
            PlacementSnapshot snapshot;
            if (repository.exists(player.getUUID(), id)) {
                snapshot = repository.load(player.getUUID(), id).snapshot();
            } else {
                RecoveryArchive archive = archiveRepository.load(player.getUUID(), id).orElse(null);
                if (archive == null) {
                    sendError(player, requestId, PlacementStatusCodes.NO_PLACEMENT);
                    return;
                }
                snapshot = archive.snapshot();
            }
            int minX = snapshot.changes().stream().mapToInt(PlacementChange::x).min().orElseThrow();
            int minY = snapshot.changes().stream().mapToInt(PlacementChange::y).min().orElseThrow();
            int minZ = snapshot.changes().stream().mapToInt(PlacementChange::z).min().orElseThrow();
            int maxX = snapshot.changes().stream().mapToInt(PlacementChange::x).max().orElseThrow();
            int maxY = snapshot.changes().stream().mapToInt(PlacementChange::y).max().orElseThrow();
            int maxZ = snapshot.changes().stream().mapToInt(PlacementChange::z).max().orElseThrow();
            if (ServerPlayNetworking.canSend(player, PlacementInspectPayload.TYPE)) {
                ServerPlayNetworking.send(player, new PlacementInspectPayload(
                    requestId, placementId, snapshot.dimension(), minX, minY, minZ, maxX, maxY, maxZ));
            }
        } catch (IOException | RuntimeException exception) {
            sendError(player, requestId, PlacementStatusCodes.NO_PLACEMENT);
        }
    }

    public synchronized void requestStatus(ServerPlayer player, String requestId) {
        ActiveJob active = activeJobs.get(player.getUUID());
        if (active != null) {
            send(player, statusPayload(active, statusFor(active.mode())));
            return;
        }
        if (repository == null) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
            return;
        }
        LoadedPlacement loaded = repository.loadLatest(player.getUUID()).orElse(null);
        if (loaded == null) {
            sendError(player, requestId, PlacementStatusCodes.NO_PLACEMENT);
            return;
        }
        try {
            if (PlacementStatusCodes.isBusy(loaded.state().status())) {
                loaded = repository.markInterrupted(loaded);
            }
            send(player, new PlacementStatusPayload(
                requestId,
                loaded.snapshot().placementId(),
                loaded.snapshot().buildId(),
                loaded.state().status(),
                progressFor(loaded.state()),
                loaded.snapshot().changes().size(),
                loaded.state().conflicts()
            ));
        } catch (IOException exception) {
            sendError(player, requestId, PlacementStatusCodes.STORAGE_ERROR);
        }
    }

    public synchronized void tick(MinecraftServer minecraftServer) {
        if (server != minecraftServer || repository == null || activeJobs.isEmpty()) {
            return;
        }
        int globalBudget = PlacementLimits.OPERATIONS_PER_SERVER_TICK;
        for (ActiveJob original : List.copyOf(activeJobs.values())) {
            if (globalBudget <= 0 || activeJobs.get(original.ownerId()) != original) {
                continue;
            }
            int budget = Math.min(PlacementLimits.OPERATIONS_PER_JOB_TICK, globalBudget);
            globalBudget -= processBatch(original, budget);
        }
    }

    public synchronized void clearPlayer(UUID playerId) {
        lastMutationRequestNanos.remove(playerId);
    }

    public synchronized void sendError(ServerPlayer player, String requestId, String code) {
        send(player, new PlacementStatusPayload(
            requestId == null ? "" : requestId,
            "",
            "",
            code,
            0,
            0,
            0
        ));
    }

    private int processBatch(ActiveJob job, int budget) {
        ServerLevel level;
        try {
            level = levelFor(job.dimension());
        } catch (UnsafePlacementException exception) {
            interrupt(job, exception.code());
            return 0;
        }

        int cursor = job.cursor();
        int conflicts = job.conflicts();
        int processed = 0;
        while (processed < budget && cursor < job.changes().size()) {
            RuntimeChange change = job.changes().get(cursor);
            if (!level.hasChunk(change.position().getX() >> 4, change.position().getZ() >> 4)) {
                interrupt(job, PlacementStatusCodes.UNLOADED_AREA);
                return processed;
            }

            BlockState current = level.getBlockState(change.position());
            boolean applying = job.mode() != JobMode.UNDO;
            BlockState expected = applying ? change.before() : change.after();
            BlockState replacement = applying ? change.after() : change.before();
            String expectedNbt = applying ? change.beforeBlockEntityNbt() : null;
            String replacementNbt = applying ? null : change.beforeBlockEntityNbt();
            String currentNbt = currentBlockEntityNbt(level, change.position());
            switch (PlacementConflictPolicy.decide(
                current,
                currentNbt,
                expected,
                expectedNbt,
                replacement,
                replacementNbt
            )) {
                case APPLY -> {
                    if (!applyReplacement(level, change.position(), replacement, replacementNbt)) {
                        conflicts++;
                    }
                }
                case ALREADY_APPLIED -> {
                }
                case CONFLICT -> conflicts++;
            }
            cursor++;
            processed++;
        }

        ActiveJob advanced = job.withProgress(cursor, conflicts);
        try {
            PlacementState state = job.mode() == JobMode.UNDO
                ? state(
                    job.loaded(),
                    PlacementStatusCodes.UNDOING,
                    job.placementProcessed(),
                    cursor,
                    conflicts
                )
                : state(
                    job.loaded(),
                    job.mode() == JobMode.PLACE
                        ? PlacementStatusCodes.PLACING
                        : PlacementStatusCodes.REDOING,
                    cursor,
                    0,
                    conflicts
                );
            LoadedPlacement saved = repository.update(job.loaded().snapshot(), state);
            advanced = advanced.withLoaded(saved);
        } catch (IOException exception) {
            CraftGptMod.LOGGER.error("Could not persist CraftGPT placement progress", exception);
            interrupt(advanced, PlacementStatusCodes.STORAGE_ERROR);
            return processed;
        }

        if (cursor >= job.changes().size()) {
            complete(advanced);
        } else {
            activeJobs.put(job.ownerId(), advanced);
            sendToOwner(advanced, statusPayload(
                advanced,
                statusFor(job.mode())
            ));
        }
        return processed;
    }

    private void complete(ActiveJob job) {
        String status;
        if (job.mode() == JobMode.PLACE) {
            status = job.conflicts() == 0
                ? PlacementStatusCodes.PLACED
                : PlacementStatusCodes.PLACED_WITH_CONFLICTS;
        } else if (job.mode() == JobMode.UNDO) {
            int undoConflicts = job.conflicts() - job.baseConflicts();
            status = undoConflicts <= 0
                ? PlacementStatusCodes.UNDONE
                : PlacementStatusCodes.UNDONE_WITH_CONFLICTS;
        } else {
            status = job.conflicts() == 0
                ? PlacementStatusCodes.REDONE
                : PlacementStatusCodes.REDONE_WITH_CONFLICTS;
        }

        try {
            PlacementState finished = job.mode() == JobMode.UNDO
                ? state(
                    job.loaded(),
                    status,
                    job.placementProcessed(),
                    job.changes().size(),
                    job.conflicts()
                )
                : state(job.loaded(), status, job.changes().size(), 0, job.conflicts());
            LoadedPlacement saved = repository.update(job.loaded().snapshot(), finished);
            ActiveJob completed = job.withLoaded(saved);
            activeJobs.remove(job.ownerId());
            release(job.dimension(), job.changes());
            sendToOwner(completed, statusPayload(completed, status));
        } catch (IOException exception) {
            CraftGptMod.LOGGER.error("Could not finalize CraftGPT placement", exception);
            interrupt(job, PlacementStatusCodes.STORAGE_ERROR);
        }
    }

    private PreparedPlacement prepare(
        ServerPlayer player,
        String buildId,
        boolean allowBlockEntityReplacement,
        BuildValidationRequest build,
        int expectedChanges
    ) throws UnsafePlacementException {
        AreaSelection selection = AreaSelectionManager.INSTANCE.get(player.getUUID()).orElse(null);
        if (selection == null || !selection.isComplete()) {
            throw new UnsafePlacementException(PlacementStatusCodes.INVALID_BUILD);
        }
        AreaBounds bounds = selection.bounds().orElseThrow();
        ServerLevel level = player.level();
        Registry<Block> registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);

        List<BlockState> palette = new ArrayList<>(build.palette().size());
        for (String serialized : build.palette()) {
            try {
                BlockState target = BuildPreviewValidator.parseCanonicalState(registry, serialized);
                if (target.getBlock() instanceof EntityBlock) {
                    throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_UNSAFE);
                }
                if (target.getBlock() instanceof FallingBlock) {
                    throw new UnsafePlacementException(PlacementStatusCodes.PHYSICS_UNSAFE);
                }
                palette.add(target);
            } catch (CommandSyntaxException | IllegalArgumentException exception) {
                throw new UnsafePlacementException(PlacementStatusCodes.INVALID_BUILD);
            }
        }

        List<RuntimeChange> runtime = new ArrayList<>(expectedChanges);
        List<PlacementChange> journal = new ArrayList<>(expectedChanges);
        int blockEntityChanges = 0;
        int totalBlockEntityBytes = 0;
        for (BuildOperation operation : build.operations()) {
            BlockPos position = bounds.min().offset(
                operation.relativeX(),
                operation.relativeY(),
                operation.relativeZ()
            );
            BlockState before = level.getBlockState(position);
            BlockState after = palette.get(operation.paletteIndex());
            if (before.equals(after)) {
                continue;
            }
            BlockEntity existingBlockEntity = level.getBlockEntity(position);
            String beforeBlockEntityNbt = null;
            if (before.getBlock() instanceof EntityBlock || existingBlockEntity != null) {
                if (!allowBlockEntityReplacement || existingBlockEntity == null) {
                    throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_UNSAFE);
                }
                blockEntityChanges++;
                if (blockEntityChanges > PlacementLimits.MAX_BLOCK_ENTITY_CHANGES) {
                    throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_LIMIT);
                }
                CompoundTag saved = existingBlockEntity.saveWithFullMetadata(level.registryAccess());
                beforeBlockEntityNbt = saved.toString();
                int serializedBytes = beforeBlockEntityNbt
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                totalBlockEntityBytes += serializedBytes;
                if (saved.sizeInBytes() > PlacementLimits.MAX_BLOCK_ENTITY_NBT_BYTES
                    || serializedBytes > PlacementLimits.MAX_BLOCK_ENTITY_NBT_BYTES
                    || totalBlockEntityBytes > PlacementLimits.MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES) {
                    throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_LIMIT);
                }
            }
            if (before.getBlock() instanceof FallingBlock) {
                throw new UnsafePlacementException(PlacementStatusCodes.PHYSICS_UNSAFE);
            }
            PlacementChange change = new PlacementChange(
                position.getX(),
                position.getY(),
                position.getZ(),
                BlockStateParser.serialize(before),
                BlockStateParser.serialize(after),
                beforeBlockEntityNbt
            );
            journal.add(change);
            runtime.add(new RuntimeChange(position.immutable(), before, after, beforeBlockEntityNbt));
        }
        if (runtime.isEmpty() || runtime.size() != expectedChanges) {
            throw new UnsafePlacementException(PlacementStatusCodes.STALE_CONTEXT);
        }

        PlacementSnapshot snapshot = new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            UUID.randomUUID().toString(),
            buildId,
            player.getUUID().toString(),
            selection.selectionId(),
            selection.dimension(),
            Instant.now().toString(),
            journal
        );
        return new PreparedPlacement(snapshot, selection.dimension(), List.copyOf(runtime));
    }

    private List<RuntimeChange> runtimeChanges(
        ServerLevel level,
        PlacementSnapshot snapshot
    ) throws UnsafePlacementException {
        Registry<Block> registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<RuntimeChange> runtime = new ArrayList<>(snapshot.changes().size());
        for (PlacementChange change : snapshot.changes()) {
            try {
                BlockState before = BuildPreviewValidator.parseCanonicalState(registry, change.beforeState());
                BlockState after = BuildPreviewValidator.parseCanonicalState(registry, change.afterState());
                if (after.getBlock() instanceof EntityBlock) {
                    throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_UNSAFE);
                }
                if (before.getBlock() instanceof FallingBlock || after.getBlock() instanceof FallingBlock) {
                    throw new UnsafePlacementException(PlacementStatusCodes.PHYSICS_UNSAFE);
                }
                if (change.hasBlockEntityData()) {
                    CompoundTag tag = TagParser.parseCompoundFully(change.beforeBlockEntityNbt());
                    BlockEntity restored = BlockEntity.loadStatic(
                        new BlockPos(change.x(), change.y(), change.z()),
                        before,
                        tag,
                        level.registryAccess()
                    );
                    if (restored == null || !restored.isValidBlockState(before)) {
                        throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_UNSAFE);
                    }
                } else if (before.getBlock() instanceof EntityBlock) {
                    throw new UnsafePlacementException(PlacementStatusCodes.BLOCK_ENTITY_UNSAFE);
                }
                runtime.add(new RuntimeChange(
                    new BlockPos(change.x(), change.y(), change.z()),
                    before,
                    after,
                    change.beforeBlockEntityNbt()
                ));
            } catch (CommandSyntaxException | IllegalArgumentException exception) {
                throw new UnsafePlacementException(PlacementStatusCodes.INVALID_BUILD);
            }
        }
        return runtime;
    }

    private ServerLevel levelFor(String dimension) throws UnsafePlacementException {
        Identifier id = Identifier.tryParse(dimension);
        if (server == null || id == null) {
            throw new UnsafePlacementException(PlacementStatusCodes.DIMENSION_UNAVAILABLE);
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            throw new UnsafePlacementException(PlacementStatusCodes.DIMENSION_UNAVAILABLE);
        }
        return level;
    }

    private String currentBlockEntityNbt(ServerLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity == null
            ? null
            : blockEntity.saveWithFullMetadata(level.registryAccess()).toString();
    }

    private boolean applyReplacement(
        ServerLevel level,
        BlockPos position,
        BlockState replacement,
        String replacementBlockEntityNbt
    ) {
        try {
            BlockEntity restored = null;
            if (replacementBlockEntityNbt != null) {
                CompoundTag tag = TagParser.parseCompoundFully(replacementBlockEntityNbt);
                restored = BlockEntity.loadStatic(position, replacement, tag, level.registryAccess());
                if (restored == null || !restored.isValidBlockState(replacement)) {
                    return false;
                }
            } else if (replacement.getBlock() instanceof EntityBlock) {
                return false;
            }

            if (!level.setBlock(position, replacement, UPDATE_FLAGS)) {
                return false;
            }
            if (restored != null) {
                level.setBlockEntity(restored);
                restored.setChanged();
                level.sendBlockUpdated(position, replacement, replacement, Block.UPDATE_CLIENTS);
            } else {
                level.removeBlockEntity(position);
            }
            return true;
        } catch (CommandSyntaxException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not restore CraftGPT block-entity data", exception);
            return false;
        }
    }

    private boolean hasBlockingNewerOverlap(UUID ownerId, LoadedPlacement target) throws IOException {
        return PlacementHistorySafety.hasBlockingNewerOverlap(
            repository.listHistory(ownerId),
            target
        );
    }

    private PlacementHistoryEntry historyEntry(LoadedPlacement loaded, boolean live, boolean archived) {
        return new PlacementHistoryEntry(
            loaded.snapshot().placementId(),
            loaded.snapshot().buildId(),
            loaded.snapshot().dimension(),
            loaded.snapshot().createdAt(),
            loaded.state().status(),
            progressFor(loaded.state()),
            loaded.snapshot().changes().size(),
            loaded.state().conflicts(),
            loaded.snapshot().changes().stream().anyMatch(PlacementChange::hasBlockEntityData),
            live,
            archived
        );
    }

    private void sendMaintenanceStatus(
        ServerPlayer player, String requestId, LoadedPlacement loaded, String status, int count
    ) {
        send(player, new PlacementStatusPayload(
            requestId,
            loaded.snapshot().placementId(),
            loaded.snapshot().buildId(),
            status,
            count,
            count,
            0
        ));
    }

    private PlacementState state(
        LoadedPlacement loaded,
        String status,
        int processed,
        int reverted,
        int conflicts
    ) {
        return new PlacementState(
            PlacementLimits.SCHEMA_VERSION,
            loaded.snapshot().placementId(),
            loaded.state().snapshotHash(),
            status,
            processed,
            reverted,
            conflicts,
            Instant.now().toString()
        );
    }

    private void interrupt(ActiveJob job, String status) {
        if (job == null) {
            return;
        }
        activeJobs.remove(job.ownerId());
        release(job.dimension(), job.changes());
        try {
            PlacementState interrupted = state(
                job.loaded(),
                PlacementStatusCodes.INTERRUPTED,
                job.mode() == JobMode.UNDO ? job.placementProcessed() : job.cursor(),
                job.mode() == JobMode.UNDO ? job.cursor() : job.loaded().state().revertedChanges(),
                job.conflicts()
            );
            repository.update(job.loaded().snapshot(), interrupted);
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not persist interrupted CraftGPT placement", exception);
        }
        sendToOwner(job, new PlacementStatusPayload(
            job.requestId(),
            job.loaded().snapshot().placementId(),
            job.loaded().snapshot().buildId(),
            status,
            job.cursor(),
            job.changes().size(),
            job.conflicts()
        ));
    }

    private boolean reserve(String dimension, List<RuntimeChange> changes) {
        for (RuntimeChange change : changes) {
            if (reservedPositions.contains(WorldPosition.of(dimension, change.position()))) {
                return false;
            }
        }
        for (RuntimeChange change : changes) {
            reservedPositions.add(WorldPosition.of(dimension, change.position()));
        }
        return true;
    }

    private void release(String dimension, List<RuntimeChange> changes) {
        for (RuntimeChange change : changes) {
            reservedPositions.remove(WorldPosition.of(dimension, change.position()));
        }
    }

    private boolean tryAcquire(UUID playerId, long nowNanos) {
        Long previous = lastMutationRequestNanos.get(playerId);
        if (previous != null && nowNanos - previous < MINIMUM_MUTATION_INTERVAL_NANOS) {
            return false;
        }
        lastMutationRequestNanos.put(playerId, nowNanos);
        return true;
    }

    private boolean authorized(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer minecraftServer = server;
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
            || (minecraftServer != null
                && minecraftServer.isSingleplayer()
                && minecraftServer.isSingleplayerOwner(player.nameAndId()));
    }

    private String mapValidationFailure(String code) {
        return switch (code) {
            case BuildPreviewValidationResult.STALE_CONTEXT -> PlacementStatusCodes.STALE_CONTEXT;
            case BuildPreviewValidationResult.UNLOADED_AREA -> PlacementStatusCodes.UNLOADED_AREA;
            default -> PlacementStatusCodes.INVALID_BUILD;
        };
    }

    private PlacementStatusPayload statusPayload(ActiveJob job, String status) {
        return new PlacementStatusPayload(
            job.requestId(),
            job.loaded().snapshot().placementId(),
            job.loaded().snapshot().buildId(),
            status,
            job.cursor(),
            job.changes().size(),
            job.conflicts()
        );
    }

    private String statusFor(JobMode mode) {
        return switch (mode) {
            case PLACE -> PlacementStatusCodes.PLACING;
            case UNDO -> PlacementStatusCodes.UNDOING;
            case REDO -> PlacementStatusCodes.REDOING;
        };
    }

    private int progressFor(PlacementState state) {
        return PlacementStatusCodes.isUndone(state.status()) || PlacementStatusCodes.UNDOING.equals(state.status())
            ? state.revertedChanges()
            : state.processedChanges();
    }

    private void sendToOwner(ActiveJob job, PlacementStatusPayload payload) {
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(job.ownerId());
        if (player != null) {
            send(player, payload);
        }
    }

    private void send(ServerPlayer player, PlacementStatusPayload payload) {
        if (player != null && ServerPlayNetworking.canSend(player, PlacementStatusPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private enum JobMode {
        PLACE,
        UNDO,
        REDO
    }

    private record RuntimeChange(
        BlockPos position,
        BlockState before,
        BlockState after,
        String beforeBlockEntityNbt
    ) {
    }

    private record PreparedPlacement(
        PlacementSnapshot snapshot,
        String dimension,
        List<RuntimeChange> runtimeChanges
    ) {
    }

    private record WorldPosition(String dimension, int x, int y, int z) {
        static WorldPosition of(String dimension, BlockPos position) {
            return new WorldPosition(dimension, position.getX(), position.getY(), position.getZ());
        }
    }

    private record ActiveJob(
        UUID ownerId,
        String requestId,
        String dimension,
        LoadedPlacement loaded,
        List<RuntimeChange> changes,
        JobMode mode,
        int cursor,
        int conflicts,
        int placementProcessed,
        int baseConflicts
    ) {
        ActiveJob withProgress(int newCursor, int newConflicts) {
            return new ActiveJob(
                ownerId,
                requestId,
                dimension,
                loaded,
                changes,
                mode,
                newCursor,
                newConflicts,
                placementProcessed,
                baseConflicts
            );
        }

        ActiveJob withLoaded(LoadedPlacement newLoaded) {
            return new ActiveJob(
                ownerId,
                requestId,
                dimension,
                newLoaded,
                changes,
                mode,
                cursor,
                conflicts,
                placementProcessed,
                baseConflicts
            );
        }
    }

    private static final class UnsafePlacementException extends Exception {
        private final String code;

        UnsafePlacementException(String code) {
            super(code);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
