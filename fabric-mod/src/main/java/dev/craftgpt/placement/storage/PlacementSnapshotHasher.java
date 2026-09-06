package dev.craftgpt.placement.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.placement.model.PlacementSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PlacementSnapshotHasher {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private PlacementSnapshotHasher() {
    }

    public static String sha256(PlacementSnapshot snapshot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
