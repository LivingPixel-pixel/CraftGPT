package dev.craftgpt.build.model;

import java.util.List;

/**
 * Compact, untrusted structured output returned by the builder model.
 */
public record BuildDraft(
    int schemaVersion,
    String summary,
    List<String> palette,
    List<String> operations,
    List<BuildComponent> components
) {
    public BuildDraft(int schemaVersion, String summary, List<String> palette, List<String> operations) {
        this(schemaVersion, summary, palette, operations, List.of());
    }
    public BuildDraft {
        ComponentCompiler.Expanded expanded = ComponentCompiler.expand(palette, operations, components);
        palette = expanded.palette();
        operations = expanded.operations();
        palette = List.copyOf(palette);
        operations = List.copyOf(operations);
        // Persist the final canonical block list, never re-expand on reload.
        components = List.of();
    }
}
