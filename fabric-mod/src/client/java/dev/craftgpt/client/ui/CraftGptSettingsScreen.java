package dev.craftgpt.client.ui;
import dev.craftgpt.client.config.CraftGptConfig;
import net.minecraft.client.gui.screens.Screen;
/** Existing commands and key bindings now open the focused settings hub. */
public final class CraftGptSettingsScreen extends SettingsHomeScreen {
    public CraftGptSettingsScreen(Screen parent,CraftGptConfig config){super(parent,config);}
}
