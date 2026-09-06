package dev.craftgpt.build.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BuildModelsTest {
    @Test
    void draftAndArtifactDefensivelyCopyLists() {
        List<String> palette = new ArrayList<>(List.of("minecraft:stone"));
        List<String> compact = new ArrayList<>(List.of("0,0,0,0"));
        BuildDraft draft = new BuildDraft(1, "Stone", palette, compact);
        palette.add("minecraft:dirt");
        compact.add("1,0,0,0");

        assertEquals(List.of("minecraft:stone"), draft.palette());
        assertEquals(List.of("0,0,0,0"), draft.operations());
        assertThrows(UnsupportedOperationException.class, () -> draft.palette().add("minecraft:dirt"));

        List<BuildOperation> operations = new ArrayList<>(List.of(new BuildOperation(0, 0, 0, 0)));
        CompiledBuildArtifact artifact = new CompiledBuildArtifact(
            1, "build", "selection", "project", "v1", "plan", "context", "now",
            "model", "low", "Stone", draft.palette(), operations
        );
        operations.add(new BuildOperation(1, 0, 0, 0));

        assertEquals(1, artifact.operations().size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> artifact.operations().add(new BuildOperation(2, 0, 0, 0))
        );
    }
}
