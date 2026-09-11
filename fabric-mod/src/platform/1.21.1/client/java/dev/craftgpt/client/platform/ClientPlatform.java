package dev.craftgpt.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Native screen, message and texture APIs for Minecraft 1.21.1. */
public final class ClientPlatform {
    private ClientPlatform() {}
    public static void notifyPlayer(Minecraft client, net.minecraft.network.chat.Component message) { client.player.displayClientMessage(message, false); }

    public static Screen screen(Minecraft client) { return client.screen; }
    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }

    public static net.minecraft.client.gui.components.MultiLineEditBox promptBox(net.minecraft.client.gui.Font font,
            int x, int y, int width, int height, net.minecraft.network.chat.Component placeholder, net.minecraft.network.chat.Component label) {
        return new net.minecraft.client.gui.components.MultiLineEditBox(font, x, y, width, height, placeholder, label);
    }
    public static void registerImage(Minecraft client, net.minecraft.resources.Identifier id, com.mojang.blaze3d.platform.NativeImage image) {
        client.getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(image));
    }
    public static void drawImage(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier id,
            int x, int y, int width, int height, int imageWidth, int imageHeight) {
        graphics.blit(id, x, y, width, height, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight);
    }
    public static void clearParticles(Minecraft client) {
        ((dev.craftgpt.compat.mixin.ParticleEngineAccessor) client.particleEngine).craftgpt$clearParticles();
    }
}
