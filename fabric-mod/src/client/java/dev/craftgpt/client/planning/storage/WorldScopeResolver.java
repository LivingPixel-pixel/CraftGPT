package dev.craftgpt.client.planning.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class WorldScopeResolver {
    private WorldScopeResolver() {
    }

    public static String resolve(Minecraft minecraft) {
        MinecraftServer integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            String localPath = integratedServer.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize().toString();
            return hash("singleplayer:" + localPath);
        }

        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return hash("multiplayer:" + server.ip.trim().toLowerCase(Locale.ROOT));
        }
        throw new IllegalStateException("No active world scope");
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
