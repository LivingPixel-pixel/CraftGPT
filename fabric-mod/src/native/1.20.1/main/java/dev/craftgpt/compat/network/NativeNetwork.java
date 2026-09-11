package dev.craftgpt.compat.network;

import net.minecraft.resources.ResourceLocation;

/** Fabric 1.20.1 uses identifier-and-buffer channels instead of typed native payloads. */
public final class NativeNetwork {
    public static final ResourceLocation CHANNEL = id("craftgpt", "transport_v1");
    private NativeNetwork() {}
    public static void initialize() {}
    public static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    public static ResourceLocation parse(String value) { return new ResourceLocation(value); }
}
