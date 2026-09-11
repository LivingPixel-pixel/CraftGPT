package dev.craftgpt.client.build.preview;

import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.context.AreaContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import dev.craftgpt.client.platform.PreviewPlatform;
import net.minecraft.world.phys.AABB;

/**
 * Owns a client-only, immutable snapshot of the current build preview.
 *
 * <p>The manager deliberately has no reference to {@code ClientLevel} and never
 * writes blocks. The renderer consumes only the precomputed absolute positions.
 */
public final class GhostPreviewManager {
    public static final GhostPreviewManager INSTANCE = new GhostPreviewManager();

    /** Safety bound for data retained by the client preview. */
    public static final int MAX_PREVIEW_OPERATIONS = 32_768;

    private final AtomicReference<PreviewSnapshot> snapshot =
        new AtomicReference<>(PreviewSnapshot.empty());
    private final AtomicReference<GhostPreviewStats> stats =
        new AtomicReference<>(GhostPreviewStats.empty(true));
    private final AtomicBoolean rendererRegistered = new AtomicBoolean();
    private volatile boolean visible = true;
    private volatile boolean solid = true;
    public boolean solid() { return solid; }
    public void toggleSolid() { solid = !solid; }

    private GhostPreviewManager() {
    }

    /**
     * Registers the renderer exactly once. Call from the Fabric client entrypoint.
     */
    public void register() {
        if (rendererRegistered.compareAndSet(false, true)) {
            PreviewPlatform.register(GhostPreviewRenderer::render);
        }
    }

    /**
     * Replaces the current preview with an immutable projection of the artifact.
     * Relative artifact coordinates use the selected area's minimum corner as
     * their absolute origin.
     *
     * @return initial statistics, including any invalid operations that were skipped
     */
    public GhostPreviewStats show(CompiledBuildArtifact artifact, AreaContext context) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(artifact.palette(), "artifact.palette");
        Objects.requireNonNull(artifact.operations(), "artifact.operations");

        if (artifact.operations().size() > MAX_PREVIEW_OPERATIONS) {
            throw new IllegalArgumentException(
                "Preview exceeds the client safety limit of " + MAX_PREVIEW_OPERATIONS + " operations"
            );
        }

        Map<SectionKey, BatchBuilder> batchBuilders = new LinkedHashMap<>();
        int placementCount = 0;
        int removalCount = 0;
        int invalidCount = 0;

        for (BuildOperation operation : artifact.operations()) {
            if (!isValid(operation, artifact.palette(), context)) {
                invalidCount++;
                continue;
            }

            int x;
            int y;
            int z;
            try {
                x = Math.addExact(context.min().x(), operation.relativeX());
                y = Math.addExact(context.min().y(), operation.relativeY());
                z = Math.addExact(context.min().z(), operation.relativeZ());
            } catch (ArithmeticException ignored) {
                // Treat a wrapped absolute position as malformed preview data.
                invalidCount++;
                continue;
            }
            boolean removal = "minecraft:air".equals(artifact.palette().get(operation.paletteIndex()));
            if (removal) {
                removalCount++;
            } else {
                placementCount++;
            }

            SectionKey key = new SectionKey(
                Math.floorDiv(x, 16),
                Math.floorDiv(y, 16),
                Math.floorDiv(z, 16)
            );
            batchBuilders.computeIfAbsent(key, ignored -> new BatchBuilder())
                .add(new PreviewBlock(x, y, z, removal, artifact.palette().get(operation.paletteIndex())));
        }

        List<PreviewBatch> batches = batchBuilders.values().stream()
            .map(BatchBuilder::build)
            .toList();
        int validCount = placementCount + removalCount;
        PreviewSnapshot next = new PreviewSnapshot(
            context.dimension(),
            List.copyOf(batches),
            artifact.operations().size(),
            validCount,
            placementCount,
            removalCount,
            invalidCount
        );
        snapshot.set(next);

        GhostPreviewStats initial = next.frameStats(visible, false, 0, 0, 0, 0);
        stats.set(initial);
        return initial;
    }

    public void clear() {
        snapshot.set(PreviewSnapshot.empty());
        stats.set(GhostPreviewStats.empty(visible));
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        PreviewSnapshot current = snapshot.get();
        GhostPreviewStats previous = stats.get();
        stats.set(current.frameStats(
            visible,
            previous.dimensionMatches(),
            visible ? previous.renderedOperations() : 0,
            visible ? previous.distanceCulledOperations() : 0,
            visible ? previous.frustumCulledOperations() : 0,
            visible ? previous.renderCapOmittedOperations() : 0
        ));
    }

    public boolean toggleVisible() {
        setVisible(!visible);
        return visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean hasPreview() {
        return snapshot.get().validOperations() > 0;
    }

    public GhostPreviewStats stats() {
        return stats.get();
    }

    PreviewSnapshot snapshot() {
        return snapshot.get();
    }

    void updateFrameStats(
        PreviewSnapshot renderedSnapshot,
        boolean dimensionMatches,
        int rendered,
        int distanceCulled,
        int frustumCulled,
        int capOmitted
    ) {
        // Do not publish counters from a frame whose preview was replaced midway.
        if (snapshot.get() == renderedSnapshot) {
            stats.set(renderedSnapshot.frameStats(
                visible,
                dimensionMatches,
                rendered,
                distanceCulled,
                frustumCulled,
                capOmitted
            ));
        }
    }

    private static boolean isValid(BuildOperation operation, List<String> palette, AreaContext context) {
        if (operation == null
            || operation.paletteIndex() < 0
            || operation.paletteIndex() >= palette.size()
            || palette.get(operation.paletteIndex()) == null
            || palette.get(operation.paletteIndex()).isBlank()) {
            return false;
        }
        return operation.relativeX() >= 0 && operation.relativeX() < context.width()
            && operation.relativeY() >= 0 && operation.relativeY() < context.height()
            && operation.relativeZ() >= 0 && operation.relativeZ() < context.depth();
    }

    private record SectionKey(int x, int y, int z) {
    }

    private static final class BatchBuilder {
        private final List<PreviewBlock> blocks = new ArrayList<>();
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void add(PreviewBlock block) {
            blocks.add(block);
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }

        PreviewBatch build() {
            return new PreviewBatch(
                List.copyOf(blocks),
                new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0)
            );
        }
    }
}

record PreviewBlock(int x, int y, int z, boolean removal, String state) {
    PreviewBlock(int x,int y,int z,boolean removal) { this(x,y,z,removal,removal?"minecraft:air":"minecraft:stone"); }
}

record PreviewBatch(List<PreviewBlock> blocks, AABB bounds) {
}

record PreviewSnapshot(
    String dimension,
    List<PreviewBatch> batches,
    int totalOperations,
    int validOperations,
    int placementOperations,
    int removalOperations,
    int invalidOperations
) {
    static PreviewSnapshot empty() {
        return new PreviewSnapshot("", List.of(), 0, 0, 0, 0, 0);
    }

    GhostPreviewStats frameStats(
        boolean visible,
        boolean dimensionMatches,
        int rendered,
        int distanceCulled,
        int frustumCulled,
        int capOmitted
    ) {
        return new GhostPreviewStats(
            totalOperations,
            validOperations,
            placementOperations,
            removalOperations,
            invalidOperations,
            rendered,
            distanceCulled,
            frustumCulled,
            capOmitted,
            validOperations > 0,
            visible,
            dimensionMatches
        );
    }
}
