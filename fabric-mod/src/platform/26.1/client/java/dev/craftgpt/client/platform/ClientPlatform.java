package dev.craftgpt.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Native screen, message and texture APIs for Minecraft 26.1. */
public final class ClientPlatform {
    private ClientPlatform() {}
    public static void notifyPlayer(Minecraft client, net.minecraft.network.chat.Component message) { client.player.sendSystemMessage(message); }

    public static Screen screen(Minecraft client) { return client.screen; }
    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }

    public static net.minecraft.client.gui.components.MultiLineEditBox promptBox(net.minecraft.client.gui.Font font,
            int x, int y, int width, int height, net.minecraft.network.chat.Component placeholder, net.minecraft.network.chat.Component label) {
        return net.minecraft.client.gui.components.MultiLineEditBox.builder().setX(x).setY(y)
            .setPlaceholder(placeholder).setShowBackground(true).setShowDecorations(false).build(font, width, height, label);
    }
    public static void registerImage(Minecraft client, net.minecraft.resources.Identifier id, com.mojang.blaze3d.platform.NativeImage image) {
        client.getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "CraftGPT inspection", image));
    }
    public static void drawImage(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier id,
            int x, int y, int width, int height, int imageWidth, int imageHeight) {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f,
            width, height, imageWidth, imageHeight, imageWidth, imageHeight);
    }
    public static void clearParticles(Minecraft client) { client.particleEngine.clearParticles(); }
}
