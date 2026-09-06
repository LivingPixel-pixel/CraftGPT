package dev.craftgpt.client.build.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.build.model.CompiledBuildArtifact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class BuildArtifactHasher {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final byte[] DOMAIN = "craftgpt:compiled-build:v1\n".getBytes(StandardCharsets.UTF_8);

    private BuildArtifactHasher() {
    }

    public static String sha256(CompiledBuildArtifact artifact) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            digest.update(GSON.toJson(artifact).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
