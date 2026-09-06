package dev.craftgpt.client.ui;
import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.client.build.preview.GhostPreviewManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import static dev.craftgpt.client.ui.ActionMenuScreen.Entry.of;

/** Grouped tools replace an unstructured wall of preview buttons. */
final class PreviewOptionsScreen extends FocusedScreen {
    private final PreviewReviewScreen parent;
    private final PlanningController c;
    PreviewOptionsScreen(PreviewReviewScreen parent,PlanningController c){
        super(Component.translatable("craftgpt.preview.options_title"));this.parent=parent;this.c=c;
    }
    private boolean editable(){return c.activeBuild().isPresent()&&!c.requestInFlight()&&!c.activeBuildPlaced();}
    @Override protected void init(){
        action("craftgpt.ui.display",58,1,0,b->minecraft.setScreen(new ActionMenuScreen(this,
            "craftgpt.ui.display","craftgpt.ui.display_hint",List.of(
                new ActionMenuScreen.Entry(()->Component.translatable(c.previewVisible()?"craftgpt.preview.hide":"craftgpt.preview.show"),
                    this::editable,s->c.togglePreviewVisible()),
                new ActionMenuScreen.Entry(()->Component.translatable(GhostPreviewManager.INSTANCE.solid()?"craftgpt.preview.mode.solid":"craftgpt.preview.mode.wire"),
                    this::editable,s->GhostPreviewManager.INSTANCE.toggleSolid()),
                of("craftgpt.ui.inspect",()->c.activeBuild().isPresent()&&!c.requestInFlight(),s->minecraft.setScreen(new BuildInspectionScreen(s,c)))))));
        action("craftgpt.ui.improve",86,1,0,b->minecraft.setScreen(new ActionMenuScreen(this,
            "craftgpt.ui.improve","craftgpt.ui.improve_hint",List.of(
                of("craftgpt.part.title",c::canVisualReview,s->minecraft.setScreen(new PartImprovementScreen(s,c))),
                of("craftgpt.preview.visual_review",c::canVisualReview,s->{if(!c.startVisualReview(minecraft,parent))CraftGptSoundFeedback.error(minecraft);}),
                of("craftgpt.findings.title",()->true,s->minecraft.setScreen(new ReviewFindingsScreen(s,c))),
                of("craftgpt.build.regenerate",()->editable()&&c.currentPlanMatchesContext(),s->UiMenus.confirm(s,
                    "craftgpt.build.regenerate","craftgpt.ui.api_charge",()->{c.compilePreview(minecraft);minecraft.setScreen(parent);}))))));
        action("craftgpt.ui.history",114,1,0,b->minecraft.setScreen(new ActionMenuScreen(this,
            "craftgpt.ui.history","craftgpt.ui.history_hint",List.of(
                of("craftgpt.planning.versions",()->!c.requestInFlight(),s->c.openVersions(minecraft,s,null)),
                of("craftgpt.history.open",()->!c.requestInFlight(),s->c.openPlacementHistory(minecraft,s)),
                of("craftgpt.placement.undo",()->c.undoAvailable()&&!c.requestInFlight(),s->c.undoPlacement(minecraft)),
                of("craftgpt.preview.abandon",this::editable,s->UiMenus.confirm(s,"craftgpt.preview.abandon",
                    "craftgpt.ui.discard_hint",()->{if(editable()&&c.abandonPreview()){parent.onBuildUpdated();minecraft.setScreen(parent);}}))))));
        action("craftgpt.ui.activity",142,1,0,b->minecraft.setScreen(new ActionMenuScreen(this,
            "craftgpt.ui.activity","craftgpt.ui.activity_hint",List.of(
                of("craftgpt.codex.screen.chat",()->true,s->minecraft.setScreen(new CodexChatScreen(s,c))),
                of("craftgpt.ui.diagnostics",()->true,s->minecraft.setScreen(UiMenus.activity(s,c)))))));
        action(Component.translatable("gui.back"),frame().footer(1,0),b->onClose());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);line(g,Component.translatable("craftgpt.ui.tools_hint"),35,MUTED);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
