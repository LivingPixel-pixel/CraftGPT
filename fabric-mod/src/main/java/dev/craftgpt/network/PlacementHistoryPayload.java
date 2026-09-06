package dev.craftgpt.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.craftgpt.CraftGptMod;
import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record PlacementHistoryPayload(String requestId, String json) implements CustomPacketPayload {
    public static final int MAX_JSON_BYTES = 64 * 1024;
    private static final Set<String> ENTRY_FIELDS = Set.of(
        "placementId", "buildId", "dimension", "createdAt", "status",
        "processed", "total", "conflicts", "hasBlockEntityData", "live", "archived"
    );
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final Type<PlacementHistoryPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_history")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementHistoryPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH),
            PlacementHistoryPayload::requestId,
            ByteBufCodecs.stringUtf8(MAX_JSON_BYTES), PlacementHistoryPayload::json,
            PlacementHistoryPayload::new
        );

    public PlacementHistoryPayload {
        PlacementRequestIds.validate(requestId);
        validateSize(json);
    }

    public static PlacementHistoryPayload create(String requestId, List<PlacementHistoryEntry> entries) {
        List<PlacementHistoryEntry> bounded = List.copyOf(entries).stream()
            .limit(PlacementLimits.MAX_HISTORY_ENTRIES)
            .toList();
        return new PlacementHistoryPayload(requestId, GSON.toJson(bounded));
    }

    public List<PlacementHistoryEntry> decodeValidated() {
        validateSize(json);
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray() || root.getAsJsonArray().size() > PlacementLimits.MAX_HISTORY_ENTRIES) {
            throw new IllegalArgumentException("Invalid placement history");
        }
        List<PlacementHistoryEntry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Invalid placement history entry");
            }
            JsonObject object = element.getAsJsonObject();
            if (!object.keySet().equals(ENTRY_FIELDS)) {
                throw new IllegalArgumentException("Invalid placement history fields");
            }
            PlacementHistoryEntry entry = GSON.fromJson(object, PlacementHistoryEntry.class);
            validateEntry(entry);
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    private static void validateEntry(PlacementHistoryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Missing placement history entry");
        }
        PlacementRequestIds.validateUuid(entry.placementId(), "placement ID");
        PlacementRequestIds.validateUuid(entry.buildId(), "build ID");
        try {
            Instant.parse(entry.createdAt());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid placement history timestamp", exception);
        }
        if (entry.dimension() == null || Identifier.tryParse(entry.dimension()) == null
            || entry.dimension().length() > PlacementLimits.MAX_DIMENSION_LENGTH
            || entry.status() == null || !entry.status().matches("[a-z_]{1,64}")
            || entry.processed() < 0 || entry.total() <= 0 || entry.processed() > entry.total()
            || entry.conflicts() < 0 || entry.conflicts() > (long) entry.total() * 2L) {
            throw new IllegalArgumentException("Invalid placement history entry");
        }
    }

    private static void validateSize(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Placement history is too large");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
