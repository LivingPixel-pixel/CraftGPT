package dev.craftgpt.client.ui;
import dev.craftgpt.client.build.preview.GhostPreviewStats;
import dev.craftgpt.client.build.storage.BuildArtifactSnapshot;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Optional;

/** Inspect, place or revise. Destructive and advanced actions live in the tools menu. */
public final class PreviewReviewScreen extends FocusedScreen {
    private final Screen parent;
    private final PlanningController controller;
    private Optional<BuildArtifactSnapshot> build=Optional.empty();
    private Button place,inspect,iterate,more,detailsButton;
    private boolean incompleteCoverageConfirmed,placementStatusRequested;
    public PreviewReviewScreen(Screen parent,PlanningController controller) {
        super(Component.translatable("craftgpt.preview.title"));this.parent=parent;this.controller=controller;
    }
    @Override protected void init() {
        build=controller.activeBuild();
        if(!placementStatusRequested){placementStatusRequested=true;controller.refreshPlacementStatus();}
        place=action(primaryLabel(),frame().row(96,1,0),b->primaryAction());
        inspect=action("craftgpt.ui.inspect",124,2,0,b->minecraft.setScreen(new BuildInspectionScreen(this,controller)));
        iterate=action("craftgpt.preview.iterate_simple",124,2,1,b->minecraft.setScreen(
            new IntentionPlanningScreen(this,controller,Component.translatable("craftgpt.preview.iteration_prompt").getString())));
        more=action("craftgpt.ui.tools",154,2,0,b->minecraft.setScreen(new PreviewOptionsScreen(this,controller)));
        detailsButton=action("craftgpt.ui.details",154,2,1,b->minecraft.setScreen(new StatusDetailsScreen(this,this::details)));
        action(Component.translatable("craftgpt.preview.continue_playing"),frame().footer(1,0),b->minecraft.setScreen(null));
        tick();
    }
    public void onBuildUpdated() {
        build=controller.activeBuild();incompleteCoverageConfirmed=false;
        if(minecraft!=null&&minecraft.screen==this) rebuildWidgets();
    }
    @Override public void tick() {
        var current=controller.activeBuild();
        if(!current.equals(build)){build=current;incompleteCoverageConfirmed=false;}
        if(place==null)return;
        boolean busy=controller.requestInFlight(),present=build.isPresent(),placed=controller.activeBuildPlaced();
        var stats=controller.previewStats();
        place.active=present&&!busy&&(build.get().accepted()?controller.canPlaceAccepted():visible(stats));
        place.setMessage(primaryLabel());
        inspect.active=present&&!busy;
        iterate.active=present&&!busy&&!placed;
        more.active=present; // Activity and recovery remain accessible while work is running.
        detailsButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(details()));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d) {
        super.extractRenderState(g,x,y,d);heading(g);
        wrapped(g,build.map(s->Component.literal(s.artifact().summary()))
            .orElse(Component.translatable("craftgpt.preview.none")),34,2,TEXT);
        if(build.isPresent()) {
            line(g,Component.translatable("craftgpt.preview.simple_changes",build.get().actualChanges()),60,MUTED);
            boolean error=controller.statusColor()==0xFFFF5555&&!controller.status().getString().isBlank();
            Component notice=error?controller.status():
                controller.requestInFlight()?Component.translatable("craftgpt.ui.busy"):
                controller.activeBuildPlaced()?Component.translatable("craftgpt.preview.placed"):
                !build.get().accepted()&&!complete(controller.previewStats())?coverageMessage(controller.previewStats()):
                Component.translatable("craftgpt.ui.preview_safe");
            line(g,notice,77,error?0xFFFF8585:
                !build.get().accepted()&&!complete(controller.previewStats())?0xFFFFAA55:MUTED);
        }
    }
    private Component details() {
        return Component.empty().append(build.map(b->Component.literal(b.artifact().summary())).orElse(Component.empty()))
            .append("\n\n").append(coverageMessage(controller.previewStats()))
            .append("\n\n").append(controller.status()).append("\n\n")
            .append(Component.translatable("craftgpt.preview.simple_safety_notice"));
    }
    private void primaryAction() {
        if(build.isEmpty())return;
        if(build.get().accepted()){controller.placeAccepted(minecraft);tick();return;}
        var stats=controller.previewStats();
        if(!visible(stats))return;
        if(!complete(stats)&&!incompleteCoverageConfirmed){incompleteCoverageConfirmed=true;tick();return;}
        if(controller.acceptPreview(minecraft)){
            incompleteCoverageConfirmed=false;onBuildUpdated();controller.placeAccepted(minecraft);
        }
    }
    private Component primaryLabel() {
        return Component.translatable(controller.activeBuildPlaced()?"craftgpt.placement.placed_button":
            incompleteCoverageConfirmed?"craftgpt.preview.accept_confirm":"craftgpt.placement.place");
    }
    private Component coverageMessage(GhostPreviewStats stats) {
        return Component.translatable(!stats.visible()?"craftgpt.preview.coverage_hidden":
            !stats.dimensionMatches()?"craftgpt.preview.coverage_dimension":
            complete(stats)?"craftgpt.preview.coverage_complete_simple":
            incompleteCoverageConfirmed?"craftgpt.preview.coverage_confirmed":"craftgpt.preview.coverage_partial");
    }
    private boolean visible(GhostPreviewStats s){return s.hasPreview()&&s.visible()&&s.dimensionMatches();}
    private boolean complete(GhostPreviewStats s){return visible(s)&&s.validOperations()>0&&s.renderCapOmittedOperations()==0;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
