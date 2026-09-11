package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import static dev.craftgpt.client.ui.ActionMenuScreen.Entry.of;

/** Advanced workflows are explicit destinations, not competing primary actions. */
final class PlanningToolsScreen extends FocusedScreen {
    private final IntentionPlanningScreen parent;
    private final PlanningController c;
    PlanningToolsScreen(IntentionPlanningScreen parent,PlanningController c){
        super(Component.translatable("craftgpt.planning.tools_title"));this.parent=parent;this.c=c;
    }
    @Override protected void init(){
        action("craftgpt.settings.codex",58,1,0,b->ClientPlatform.setScreen(minecraft,
            new CodexSettingsScreen(this,dev.craftgpt.client.CraftGptClient.config())));
        action("craftgpt.ui.plan_tools",86,1,0,b->ClientPlatform.setScreen(minecraft, new ActionMenuScreen(this,
            "craftgpt.ui.plan_tools","craftgpt.ui.api_hint",List.of(
                of("craftgpt.ui.plan_api",()->!c.requestInFlight()&&!parent.draftText().isBlank()&&c.currentContext().isPresent(),
                    s->UiMenus.confirm(s,"craftgpt.ui.plan_api","craftgpt.ui.api_charge",parent::submitApi)),
                of("craftgpt.planning.review",()->c.activeForCurrentArea().isPresent()&&!c.requestInFlight(),s->c.openPlanReview(minecraft,s)),
                of("craftgpt.planning.versions",()->c.activeForCurrentArea().isPresent()&&!c.requestInFlight(),s->c.openVersions(minecraft,s,null)),
                of("craftgpt.ui.details",()->true,s->ClientPlatform.setScreen(minecraft, new StatusDetailsScreen(s,parent::details)))))));
        action("craftgpt.ui.exchange",114,1,0,b->ClientPlatform.setScreen(minecraft, new ActionMenuScreen(this,
            "craftgpt.ui.exchange","craftgpt.ui.exchange_hint",List.of(
                of("craftgpt.portable.import",()->!c.requestInFlight()&&c.currentContext().isPresent(),s->{ClientPlatform.setScreen(minecraft, parent);c.importPortable(minecraft);}),
                of("craftgpt.portable.viewer",()->true,s->c.openPortableViewer()),
                of("craftgpt.codex.screen.folder",()->true,s->c.openPortableFolder())))));
        action("craftgpt.ui.activity",142,1,0,b->ClientPlatform.setScreen(minecraft, new ActionMenuScreen(this,
            "craftgpt.ui.activity","craftgpt.ui.activity_hint",List.of(
                of("craftgpt.book.primary.codex_progress",()->true,s->ClientPlatform.setScreen(minecraft, new CodexGenerationScreen(s,c))),
                of("craftgpt.codex.screen.chat",()->true,s->ClientPlatform.setScreen(minecraft, new CodexChatScreen(s,c))),
                of("craftgpt.ui.details",()->true,s->ClientPlatform.setScreen(minecraft, new StatusDetailsScreen(s,parent::details))),
                of("craftgpt.generation.cancel",c::generationRequestInFlight,s->UiMenus.confirm(s,"craftgpt.generation.cancel",
                    "craftgpt.ui.cancel_hint",()->c.cancelGeneration()))))));
        action(Component.translatable("gui.back"),frame().footer(1,0),b->onClose());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);line(g,Component.translatable("craftgpt.ui.tools_hint"),35,MUTED);
    }
    @Override public void onClose(){ClientPlatform.setScreen(minecraft, parent);}
}
