package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;
import static dev.craftgpt.client.ui.ActionMenuScreen.Entry.of;

final class UiMenus {
    private UiMenus(){}
    static Screen activity(Screen parent,PlanningController c) {
        Minecraft mc=Minecraft.getInstance();
        return new ActionMenuScreen(parent,"craftgpt.ui.activity","craftgpt.ui.activity_hint",List.of(
            of("craftgpt.ui.details",()->true,s->ClientPlatform.setScreen(mc, new StatusDetailsScreen(s,c::status))),
            of("craftgpt.codex.screen.log",()->true,s->ClientPlatform.setScreen(mc, new CodexLogScreen(s,c))),
            of("craftgpt.codex.screen.app",c::codexAppSessionAvailable,s->{if(!c.openCodexSessionInApp())CraftGptSoundFeedback.error(mc);}),
            of("craftgpt.codex.screen.folder",()->true,s->c.openPortableFolder())));
    }
    static void confirm(Screen parent,String title,String message,Runnable confirmed) {
        Minecraft mc=Minecraft.getInstance();
        ClientPlatform.setScreen(mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes->{
            ClientPlatform.setScreen(mc, parent);if(yes)confirmed.run();
        },net.minecraft.network.chat.Component.translatable(title),
            net.minecraft.network.chat.Component.translatable(message)){
                @Override public boolean isPauseScreen(){return false;}
        });
    }
}
