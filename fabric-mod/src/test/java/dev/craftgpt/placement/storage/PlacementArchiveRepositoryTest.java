package dev.craftgpt.placement.storage;

import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.model.PlacementChange;
import dev.craftgpt.placement.model.PlacementSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacementArchiveRepositoryTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLACEMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir Path temporaryDirectory;

    @Test
    void exportsIntegrityCheckedArchiveAndRestoresWithoutOverwrite() throws Exception {
        PlacementJournalRepository journals = new PlacementJournalRepository(temporaryDirectory.resolve("journals"));
        PlacementArchiveRepository archives = new PlacementArchiveRepository(temporaryDirectory.resolve("archives"));
        var loaded = journals.create(snapshot());

        archives.export(loaded);
        var archive = archives.load(OWNER, PLACEMENT).orElseThrow();
        assertEquals(snapshot(), archive.snapshot());
        assertTrue(journals.exists(OWNER, PLACEMENT));
        assertThrows(Exception.class, () -> journals.restore(archive.snapshot(), archive.state()));

        UUID newerId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        journals.create(snapshot(newerId, "2026-07-15T11:00:00Z"));
        journals.remove(OWNER, PLACEMENT);
        assertFalse(journals.exists(OWNER, PLACEMENT));
        journals.restore(archive.snapshot(), archive.state());
        assertTrue(journals.exists(OWNER, PLACEMENT));
    }

    @Test
    void rejectsTamperedArchiveContent() throws Exception {
        PlacementJournalRepository journals = new PlacementJournalRepository(temporaryDirectory.resolve("journals"));
        PlacementArchiveRepository archives = new PlacementArchiveRepository(temporaryDirectory.resolve("archives"));
        archives.export(journals.create(snapshot()));
        Path file = temporaryDirectory.resolve("archives").resolve(OWNER.toString())
            .resolve(PLACEMENT + ".recovery.json");
        String tampered = Files.readString(file, StandardCharsets.UTF_8)
            .replace("minecraft:stone", "minecraft:gold_block");
        Files.writeString(file, tampered, StandardCharsets.UTF_8);
        assertTrue(archives.load(OWNER, PLACEMENT).isEmpty());
    }

    private PlacementSnapshot snapshot() {
        return snapshot(PLACEMENT, "2026-07-15T10:00:00Z");
    }

    private PlacementSnapshot snapshot(UUID id, String createdAt) {
        return new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            id.toString(),
            "33333333-3333-3333-3333-333333333333",
            OWNER.toString(),
            "44444444-4444-4444-4444-444444444444",
            "minecraft:overworld",
            createdAt,
            List.of(new PlacementChange(1, 2, 3, "minecraft:stone", "minecraft:oak_planks"))
        );
    }
}
