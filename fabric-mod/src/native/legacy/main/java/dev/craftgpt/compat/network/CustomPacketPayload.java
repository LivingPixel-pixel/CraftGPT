package dev.craftgpt.compat.network;

import net.minecraft.resources.ResourceLocation;

/** CraftGPT's typed packets on releases predating Fabric's large-payload API. */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
