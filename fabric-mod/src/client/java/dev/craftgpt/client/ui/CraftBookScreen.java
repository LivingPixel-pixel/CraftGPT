package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;
import dev.craftgpt.client.area.ClientAreaState;
import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.network.CraftBookActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.util.List;
import static dev.craftgpt.client.ui.ActionMenuScreen.Entry.of;

/** One contextual next step. The complete command workflow remains available under Tools. */
public final class CraftBookScreen extends FocusedScreen {
    public static final URI MODEL_GUIDE=URI.create("https://developers.openai.com/api/docs/models");
    private final Screen parent;
    private final PlanningController c;
    private final CraftGptConfig config;
    private Button primary;
    public CraftBookScreen(Screen parent,PlanningController c,CraftGptConfig config){
        super(Component.translatable("craftgpt.book.title"));this.parent=parent;this.c=c;this.config=config;
    }
    @Override protected void init(){
        primary=action(primaryLabel(),frame().row(82,1,0),b->runPrimary());
        action("craftgpt.ui.tools",116,2,0,b->openTools());
        action("craftgpt.book.settings",116,2,1,b->ClientPlatform.setScreen(minecraft, new SettingsHomeScreen(this,config)));
        action("craftgpt.book.help",150,1,0,b->ClientPlatform.setScreen(minecraft, new ActionMenuScreen(this,
            "craftgpt.book.help","craftgpt.ui.help_hint",List.of(
                of("craftgpt.ui.workflow",()->true,s->ClientPlatform.setScreen(minecraft, new StatusDetailsScreen(s,()->Component.translatable("craftgpt.ui.workflow_help")))),
                of("craftgpt.book.models_link",()->true,s->ConfirmLinkScreen.confirmLinkNow(s,MODEL_GUIDE))))));
        action(Component.translatable("gui.close"),frame().footer(1,0),b->onClose());tick();
    }
    @Override public void tick(){
        if(primary!=null){primary.setMessage(primaryLabel());primary.active=c.codexStage().active()||
            (!c.requestInFlight()&&ClientPlayNetworking.canSend(CraftBookActionPayload.TYPE));}
    }
    private void openTools(){
        ClientPlatform.setScreen(minecraft, new ActionMenuScreen(this,"craftgpt.ui.tools","craftgpt.ui.book_tools_hint",List.of(
            new ActionMenuScreen.Entry(()->Component.translatable(ClientAreaState.INSTANCE.started()?"craftgpt.book.area.finish":
                ClientAreaState.INSTANCE.complete()?"craftgpt.book.area.clear":"craftgpt.book.area.start"),
                ()->!c.requestInFlight()&&ClientPlayNetworking.canSend(CraftBookActionPayload.TYPE),s->runAreaAction(s)),
            of("craftgpt.book.plan",()->ClientAreaState.INSTANCE.complete()&&!c.requestInFlight(),s->send(CraftBookActionPayload.PLAN)),
            of("craftgpt.book.versions",()->c.activeForCurrentArea().isPresent()&&!c.requestInFlight(),s->send(CraftBookActionPayload.VERSIONS)),
            of("craftgpt.book.history",()->!c.requestInFlight(),s->send(CraftBookActionPayload.HISTORY)))));
    }
    private void runPrimary(){
        if(c.codexStage().active())ClientPlatform.setScreen(minecraft, new CodexGenerationScreen(this,c));
        else if(!ClientAreaState.INSTANCE.complete())runAreaAction(this);
        else if(c.activeForCurrentArea().isEmpty())send(CraftBookActionPayload.PLAN);
        else if(c.activeBuild().isEmpty())ClientPlatform.setScreen(minecraft, new PlanReviewScreen(this,c));
        else if(!c.activeBuildPlaced())send(CraftBookActionPayload.PREVIEW);
        else send(CraftBookActionPayload.HISTORY);
    }
    private void runAreaAction(Screen returnTo){
        if(ClientAreaState.INSTANCE.started())send(CraftBookActionPayload.AREA_STOP);
        else if(ClientAreaState.INSTANCE.complete())UiMenus.confirm(returnTo,"craftgpt.book.area.clear",
            "craftgpt.ui.area_reset_hint",()->send(CraftBookActionPayload.AREA_CLEAR));
        else send(CraftBookActionPayload.AREA_START);
    }
    private void send(String action){
        if(!ClientPlayNetworking.canSend(CraftBookActionPayload.TYPE)){CraftGptSoundFeedback.error(minecraft);return;}
        try{ClientPlayNetworking.send(new CraftBookActionPayload(action));ClientPlatform.setScreen(minecraft, null);CraftGptSoundFeedback.page(minecraft);}
        catch(RuntimeException e){CraftGptSoundFeedback.error(minecraft);}
    }
    private Component primaryLabel(){
        return Component.translatable(c.codexStage().active()?"craftgpt.book.primary.codex_progress":
            !ClientAreaState.INSTANCE.complete()?(ClientAreaState.INSTANCE.started()?"craftgpt.book.primary.finish_area":"craftgpt.book.primary.start_area"):
            c.activeForCurrentArea().isEmpty()?"craftgpt.book.primary.describe":
            c.activeBuild().isEmpty()?"craftgpt.book.primary.review_plan":
            !c.activeBuildPlaced()?"craftgpt.book.primary.review":"craftgpt.book.primary.recovery");
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);
        String key=c.codexStage().active()?"craftgpt.ui.background":
            !ClientAreaState.INSTANCE.complete()?(ClientAreaState.INSTANCE.started()?"craftgpt.ui.area_finish":"craftgpt.ui.area_start"):
            c.activeForCurrentArea().isEmpty()?"craftgpt.ui.describe_hint":
            c.activeBuild().isEmpty()?"craftgpt.ui.plan_hint":
            !c.activeBuildPlaced()?"craftgpt.ui.ready_hint":"craftgpt.ui.placed_hint";
        wrapped(g,Component.translatable(key),36,3,TEXT);
    }
    @Override public void onClose(){ClientPlatform.setScreen(minecraft, parent);}
}
