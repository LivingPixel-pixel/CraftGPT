package dev.craftgpt.client.planning.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.client.planning.model.IntentionSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Produces the stable content identity stored alongside every immutable plan version. */
public final class PlanContentHasher {
    private static final Gson CANONICAL_GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .create();
    private static final byte[] DOMAIN = "craftgpt:intention:v1\n".getBytes(StandardCharsets.UTF_8);

    private PlanContentHasher() {
    }

    public static String sha256(IntentionSpec intention) {
        Objects.requireNonNull(intention, "intention");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            digest.update(CANONICAL_GSON.toJson(intention).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
