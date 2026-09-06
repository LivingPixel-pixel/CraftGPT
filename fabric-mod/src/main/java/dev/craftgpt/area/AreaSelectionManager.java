package dev.craftgpt.area;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaSelectionManager {
    public static final AreaSelectionManager INSTANCE = new AreaSelectionManager();

    private final Map<UUID, AreaSelection> selections = new ConcurrentHashMap<>();

    private AreaSelectionManager() {
    }

    public AreaSelection start(UUID playerId, String dimension, net.minecraft.core.BlockPos position) {
        AreaSelection selection = AreaSelection.started(dimension, position);
        selections.put(playerId, selection);
        return selection;
    }

    public Optional<AreaSelection> get(UUID playerId) {
        return Optional.ofNullable(selections.get(playerId));
    }

    public void put(UUID playerId, AreaSelection selection) {
        selections.put(playerId, selection);
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
    }
}
