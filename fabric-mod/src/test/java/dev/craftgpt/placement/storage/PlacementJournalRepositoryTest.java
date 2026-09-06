package dev.craftgpt.placement.storage;

import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementChange;
import dev.craftgpt.placement.model.PlacementSnapshot;
import dev.craftgpt.placement.model.PlacementState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.TagParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacementJournalRepositoryTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLACEMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUILD = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesImmutableSnapshotAndCrashSafeMutableProgress() throws IOException {
        PlacementJournalRepository repository = new PlacementJournalRepository(temporaryDirectory);
        PlacementSnapshot snapshot = snapshot();

        var created = repository.create(snapshot);
        assertEquals(PlacementStatusCodes.PREPARED, created.state().status());
        assertEquals(snapshot, repository.loadLatest(OWNER).orElseThrow().snapshot());

        PlacementState placing = new PlacementState(
            PlacementLimits.SCHEMA_VERSION,
            PLACEMENT.toString(),
            created.state().snapshotHash(),
            PlacementStatusCodes.PLACING,
            1,
            0,
            0,
            Instant.now().toString()
        );
        var updated = repository.update(snapshot, placing);
        assertEquals(1, repository.load(OWNER, PLACEMENT).state().processedChanges());

        var interrupted = repository.markInterrupted(updated);
        assertEquals(PlacementStatusCodes.INTERRUPTED, interrupted.state().status());
        assertEquals(1, interrupted.state().processedChanges());
        assertTrue(Files.exists(temporaryDirectory.resolve(OWNER.toString())
            .resolve(PLACEMENT + ".snapshot.json")));
        assertFalse(Files.readString(temporaryDirectory.resolve(OWNER.toString())
            .resolve(PLACEMENT + ".snapshot.json")).contains("beforeBlockEntityNbt"));
    }

    @Test
    void rejectsDuplicateCoordinatesAndNonReversibleChanges() {
        PlacementChange duplicate = new PlacementChange(1, 2, 3, "minecraft:stone", "minecraft:dirt");
        PlacementSnapshot duplicates = new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            PLACEMENT.toString(),
            BUILD.toString(),
            OWNER.toString(),
            "44444444-4444-4444-4444-444444444444",
            "minecraft:overworld",
            Instant.now().toString(),
            List.of(duplicate, duplicate)
        );
        PlacementSnapshot noOp = new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            PLACEMENT.toString(),
            BUILD.toString(),
            OWNER.toString(),
            "44444444-4444-4444-4444-444444444444",
            "minecraft:overworld",
            Instant.now().toString(),
            List.of(new PlacementChange(0, 0, 0, "minecraft:stone", "minecraft:stone"))
        );

        assertThrows(IOException.class, () -> PlacementJournalRepository.validateSnapshot(duplicates));
        assertThrows(IOException.class, () -> PlacementJournalRepository.validateSnapshot(noOp));
    }

    @Test
    void detectsSnapshotTamperingThroughStateHash() throws IOException {
        PlacementJournalRepository repository = new PlacementJournalRepository(temporaryDirectory);
        repository.create(snapshot());
        Path snapshotFile = temporaryDirectory.resolve(OWNER.toString())
            .resolve(PLACEMENT + ".snapshot.json");
        String tampered = Files.readString(snapshotFile, StandardCharsets.UTF_8)
            .replace("minecraft:stone", "minecraft:gold_block");
        Files.writeString(snapshotFile, tampered, StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> repository.load(OWNER, PLACEMENT));
        assertFalse(repository.loadLatest(OWNER).isPresent());
    }

    @Test
    void listsNewestValidHistoryFirstAndKeepsOlderSnapshots() throws IOException {
        PlacementJournalRepository repository = new PlacementJournalRepository(temporaryDirectory);
        repository.create(snapshot());
        UUID newerPlacement = UUID.fromString("66666666-6666-6666-6666-666666666666");
        repository.create(new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            newerPlacement.toString(),
            "77777777-7777-7777-7777-777777777777",
            OWNER.toString(),
            "88888888-8888-8888-8888-888888888888",
            "minecraft:overworld",
            "2026-07-15T11:00:00Z",
            List.of(new PlacementChange(4, 5, 6, "minecraft:dirt", "minecraft:stone"))
        ));

        var history = repository.listHistory(OWNER);
        assertEquals(2, history.size());
        assertEquals(newerPlacement.toString(), history.getFirst().snapshot().placementId());
        assertEquals(PLACEMENT.toString(), history.getLast().snapshot().placementId());
    }

    @Test
    void acceptsCanonicalBoundedBlockEntityRecoveryDataAndRejectsMalformedData() throws Exception {
        String chestNbt = TagParser.parseCompoundFully(
            "{id:\"minecraft:chest\",x:1,y:2,z:3,Items:[]}"
        ).toString();
        PlacementSnapshot protectedSnapshot = new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            PLACEMENT.toString(),
            BUILD.toString(),
            OWNER.toString(),
            "44444444-4444-4444-4444-444444444444",
            "minecraft:overworld",
            "2026-07-15T10:00:00Z",
            List.of(new PlacementChange(
                1, 2, 3, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                "minecraft:stone", chestNbt
            ))
        );
        PlacementSnapshot malformed = new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            PLACEMENT.toString(),
            BUILD.toString(),
            OWNER.toString(),
            "44444444-4444-4444-4444-444444444444",
            "minecraft:overworld",
            "2026-07-15T10:00:00Z",
            List.of(new PlacementChange(1, 2, 3, "minecraft:chest", "minecraft:stone", "not-nbt"))
        );

        PlacementJournalRepository.validateSnapshot(protectedSnapshot);
        assertThrows(IOException.class, () -> PlacementJournalRepository.validateSnapshot(malformed));
    }

    private PlacementSnapshot snapshot() {
        return new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            PLACEMENT.toString(),
            BUILD.toString(),
            OWNER.toString(),
            "44444444-4444-4444-4444-444444444444",
            "minecraft:overworld",
            "2026-07-15T10:00:00Z",
            List.of(
                new PlacementChange(1, 2, 3, "minecraft:stone", "minecraft:oak_planks"),
                new PlacementChange(2, 2, 3, "minecraft:air", "minecraft:glass")
            )
        );
    }
}
