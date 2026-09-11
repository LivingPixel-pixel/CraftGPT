package dev.craftgpt.client.area;

import dev.craftgpt.area.AreaBounds;
import dev.craftgpt.network.AreaSelectionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import dev.craftgpt.client.ui.CraftGptSoundFeedback;
import dev.craftgpt.client.build.preview.GhostPreviewManager;

public final class ClientAreaState {
    public static final ClientAreaState INSTANCE = new ClientAreaState();

    private int state = AreaSelectionPayload.CLEARED;
    private BlockPos start = BlockPos.ZERO;
    private BlockPos end = BlockPos.ZERO;
    private String dimension = "";
    private String selectionId = "";
    private int tickCounter;
    private boolean previewVisualizationSuppressed;

    private ClientAreaState() {
    }

    public void update(AreaSelectionPayload payload) {
        int previous = state;
        state = payload.state();
        start = payload.start();
        end = payload.end();
        dimension = payload.dimension();
        selectionId = payload.selectionId();
        if (state == AreaSelectionPayload.CLEARED) {
            clear();
        } else if (state != previous) {
            if (state == AreaSelectionPayload.COMPLETE) {
                CraftGptSoundFeedback.complete(Minecraft.getInstance());
            } else {
                CraftGptSoundFeedback.select(Minecraft.getInstance());
            }
        }
    }

    public int state() { return state; }
    public boolean started() { return state == AreaSelectionPayload.STARTED; }
    public boolean complete() { return state == AreaSelectionPayload.COMPLETE; }
    public BlockPos start() { return start; }
    public BlockPos end() { return end; }

    public void clear() {
        state = AreaSelectionPayload.CLEARED;
        start = BlockPos.ZERO;
        end = BlockPos.ZERO;
        dimension = "";
        selectionId = "";
        tickCounter = 0;
    }

    public void tick(Minecraft minecraft) {
        if (state == AreaSelectionPayload.CLEARED || minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean suppressForPreview = GhostPreviewManager.INSTANCE.hasPreview()
            && GhostPreviewManager.INSTANCE.isVisible();
        if (suppressForPreview) {
            if (!previewVisualizationSuppressed) {
                dev.craftgpt.client.platform.ClientPlatform.clearParticles(minecraft);
                previewVisualizationSuppressed = true;
            }
            return;
        }
        previewVisualizationSuppressed = false;
        if (++tickCounter % 10 != 0) {
            return;
        }
        if (!minecraft.level.dimension().identifier().toString().equals(dimension)) {
            return;
        }

        if (state == AreaSelectionPayload.STARTED) {
            renderStartMarker(minecraft, start);
        } else if (state == AreaSelectionPayload.COMPLETE) {
            renderBounds(minecraft, AreaBounds.between(start, end));
        }
    }

    private void renderStartMarker(Minecraft minecraft, BlockPos position) {
        double minX = position.getX();
        double minY = position.getY();
        double minZ = position.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        particle(minecraft, minX, minY, minZ);
        particle(minecraft, maxX, minY, minZ);
        particle(minecraft, minX, maxY, minZ);
        particle(minecraft, maxX, maxY, minZ);
        particle(minecraft, minX, minY, maxZ);
        particle(minecraft, maxX, minY, maxZ);
        particle(minecraft, minX, maxY, maxZ);
        particle(minecraft, maxX, maxY, maxZ);
    }

    private void renderBounds(Minecraft minecraft, AreaBounds bounds) {
        double minX = bounds.min().getX();
        double minY = bounds.min().getY();
        double minZ = bounds.min().getZ();
        double maxX = bounds.max().getX() + 1.0;
        double maxY = bounds.max().getY() + 1.0;
        double maxZ = bounds.max().getZ() + 1.0;

        int longestEdge = Math.max(bounds.width(), Math.max(bounds.height(), bounds.depth()));
        double spacing = Math.max(1.0, longestEdge / 16.0);

        lineX(minecraft, minX, maxX, minY, minZ, spacing);
        lineX(minecraft, minX, maxX, maxY, minZ, spacing);
        lineX(minecraft, minX, maxX, minY, maxZ, spacing);
        lineX(minecraft, minX, maxX, maxY, maxZ, spacing);

        lineY(minecraft, minY, maxY, minX, minZ, spacing);
        lineY(minecraft, minY, maxY, maxX, minZ, spacing);
        lineY(minecraft, minY, maxY, minX, maxZ, spacing);
        lineY(minecraft, minY, maxY, maxX, maxZ, spacing);

        lineZ(minecraft, minZ, maxZ, minX, minY, spacing);
        lineZ(minecraft, minZ, maxZ, maxX, minY, spacing);
        lineZ(minecraft, minZ, maxZ, minX, maxY, spacing);
        lineZ(minecraft, minZ, maxZ, maxX, maxY, spacing);
    }

    private void lineX(Minecraft minecraft, double from, double to, double y, double z, double spacing) {
        for (double x = from; x < to; x += spacing) particle(minecraft, x, y, z);
        particle(minecraft, to, y, z);
    }

    private void lineY(Minecraft minecraft, double from, double to, double x, double z, double spacing) {
        for (double y = from; y < to; y += spacing) particle(minecraft, x, y, z);
        particle(minecraft, x, to, z);
    }

    private void lineZ(Minecraft minecraft, double from, double to, double x, double y, double spacing) {
        for (double z = from; z < to; z += spacing) particle(minecraft, x, y, z);
        particle(minecraft, x, y, to);
    }

    private void particle(Minecraft minecraft, double x, double y, double z) {
        minecraft.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.0, 0.0);
    }
}
