package dev.craftgpt.client.placement;

import dev.craftgpt.area.AreaBounds;
import dev.craftgpt.network.PlacementInspectPayload;
import dev.craftgpt.network.PlacementInspectRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

import java.util.UUID;

/** Client-only, non-mutating particle outline for a selected recovery entry. */
public final class RecoveryVisualizationState {
    public static final RecoveryVisualizationState INSTANCE = new RecoveryVisualizationState();

    private String requestId = "";
    private String placementId = "";
    private String dimension = "";
    private AreaBounds bounds;
    private int tickCounter;

    private RecoveryVisualizationState() {}

    public boolean request(String selectedPlacementId) {
        if (selectedPlacementId == null || !ClientPlayNetworking.canSend(PlacementInspectRequestPayload.TYPE)) {
            return false;
        }
        String nextRequestId = UUID.randomUUID().toString();
        try {
            ClientPlayNetworking.send(new PlacementInspectRequestPayload(nextRequestId, selectedPlacementId));
            requestId = nextRequestId;
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean handle(PlacementInspectPayload payload) {
        if (payload == null || !payload.requestId().equals(requestId)) return false;
        placementId = payload.placementId();
        dimension = payload.dimension();
        bounds = AreaBounds.between(
            new BlockPos(payload.minX(), payload.minY(), payload.minZ()),
            new BlockPos(payload.maxX(), payload.maxY(), payload.maxZ())
        );
        requestId = "";
        tickCounter = 0;
        return true;
    }

    public boolean toggle(String selectedPlacementId) {
        if (selectedPlacementId != null && selectedPlacementId.equals(placementId) && bounds != null) {
            clear();
            return false;
        }
        request(selectedPlacementId);
        return true;
    }

    public boolean visibleFor(String selectedPlacementId) {
        return bounds != null && placementId.equals(selectedPlacementId);
    }

    public void clear() {
        requestId = "";
        placementId = "";
        dimension = "";
        bounds = null;
        tickCounter = 0;
    }

    public void tick(Minecraft minecraft) {
        if (bounds == null || minecraft.level == null || minecraft.player == null || ++tickCounter % 10 != 0) return;
        if (!minecraft.level.dimension().identifier().toString().equals(dimension)) return;
        double minX = bounds.min().getX();
        double minY = bounds.min().getY();
        double minZ = bounds.min().getZ();
        double maxX = bounds.max().getX() + 1.0;
        double maxY = bounds.max().getY() + 1.0;
        double maxZ = bounds.max().getZ() + 1.0;
        int longest = Math.max(bounds.width(), Math.max(bounds.height(), bounds.depth()));
        double spacing = Math.max(1.0, longest / 16.0);
        lineX(minecraft, minX, maxX, minY, minZ, spacing); lineX(minecraft, minX, maxX, maxY, minZ, spacing);
        lineX(minecraft, minX, maxX, minY, maxZ, spacing); lineX(minecraft, minX, maxX, maxY, maxZ, spacing);
        lineY(minecraft, minY, maxY, minX, minZ, spacing); lineY(minecraft, minY, maxY, maxX, minZ, spacing);
        lineY(minecraft, minY, maxY, minX, maxZ, spacing); lineY(minecraft, minY, maxY, maxX, maxZ, spacing);
        lineZ(minecraft, minZ, maxZ, minX, minY, spacing); lineZ(minecraft, minZ, maxZ, maxX, minY, spacing);
        lineZ(minecraft, minZ, maxZ, minX, maxY, spacing); lineZ(minecraft, minZ, maxZ, maxX, maxY, spacing);
    }

    private void lineX(Minecraft m, double from, double to, double y, double z, double s) {
        for (double x = from; x < to; x += s) particle(m, x, y, z); particle(m, to, y, z);
    }
    private void lineY(Minecraft m, double from, double to, double x, double z, double s) {
        for (double y = from; y < to; y += s) particle(m, x, y, z); particle(m, x, to, z);
    }
    private void lineZ(Minecraft m, double from, double to, double x, double y, double s) {
        for (double z = from; z < to; z += s) particle(m, x, y, z); particle(m, x, y, to);
    }
    private void particle(Minecraft m, double x, double y, double z) {
        m.level.addParticle(ParticleTypes.WAX_ON, x, y, z, 0.0, 0.0, 0.0);
    }
}
