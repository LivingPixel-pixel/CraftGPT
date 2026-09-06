package dev.craftgpt.client.ui;
import dev.craftgpt.client.config.CraftGptConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;

/** Separate subscription generation from optional API credentials and world safety. */
class SettingsHomeScreen extends FocusedScreen {
    private final Screen parent;
    private final CraftGptConfig config;
    SettingsHomeScreen(Screen parent,CraftGptConfig config){
        super(Component.translatable("craftgpt.settings.title"));this.parent=parent;this.config=config;
    }
    @Override protected void init(){
        action("craftgpt.settings.codex",68,1,0,b->minecraft.setScreen(new CodexSettingsScreen(this,config)));
        action("craftgpt.ui.api_settings",100,1,0,b->minecraft.setScreen(new AdvancedSettingsScreen(this,config,0)));
        action("craftgpt.ui.world_settings",132,1,0,b->minecraft.setScreen(new AdvancedSettingsScreen(this,config,2)));
        action("craftgpt.settings.models_link",162,1,0,b->ConfirmLinkScreen.confirmLinkNow(this,CraftBookScreen.MODEL_GUIDE));
        action(Component.translatable("gui.back"),frame().footer(1,0),b->onClose());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);
        wrapped(g,Component.translatable("craftgpt.ui.settings_hint"),35,2,MUTED);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
