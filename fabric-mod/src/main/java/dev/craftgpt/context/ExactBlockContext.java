package dev.craftgpt.context;

import java.util.List;

/**
 * Exact non-air block states using a palette and relative x,y,z,paletteIndex entries.
 * Every unlisted coordinate inside the area is air when {@code complete} is true.
 */
public record ExactBlockContext(
    boolean complete,
    int blockCount,
    List<String> palette,
    String blocks
) {
    public ExactBlockContext {
        palette = List.copyOf(palette);
    }

    public static ExactBlockContext unavailable() {
        return new ExactBlockContext(false, 0, List.of(), "");
    }
}
