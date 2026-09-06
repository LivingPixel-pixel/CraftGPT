package dev.craftgpt.client.ui;
import dev.craftgpt.client.codex.CodexGenerationStage;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Progress is observable without a checklist, diagnostics wall or automatic screen takeover. */
public final class CodexGenerationScreen extends FocusedScreen {
    private final Screen parent;
    private final PlanningController c;
    private CodexGenerationStage rendered;
    public CodexGenerationScreen(Screen parent,PlanningController c){
        super(Component.translatable("craftgpt.codex.screen.title"));this.parent=parent;this.c=c;
    }
    @Override protected void init(){
        rendered=c.codexStage();
        String primary=rendered.active()?"craftgpt.codex.screen.play":
            c.activeBuild().isPresent()?"craftgpt.codex.screen.review":"craftgpt.codex.screen.back";
        action(primary,132,1,0,b->{
            if(c.codexStage().active())minecraft.setScreen(null);
            else if(c.activeBuild().isPresent())minecraft.setScreen(new PreviewReviewScreen(parent,c));
            else minecraft.setScreen(parent);
        });
        action("craftgpt.codex.screen.chat",160,2,0,b->minecraft.setScreen(new CodexChatScreen(this,c)));
        action("craftgpt.ui.activity",160,2,1,b->minecraft.setScreen(UiMenus.activity(this,c)));
        action(Component.translatable(rendered.active()?"craftgpt.codex.screen.cancel":"gui.back"),
            frame().footer(1,0),b->{
                if(c.codexStage().active())UiMenus.confirm(this,"craftgpt.codex.screen.cancel",
                    "craftgpt.ui.cancel_hint",()->{c.cancelCodexGeneration();rebuildWidgets();});
                else minecraft.setScreen(parent);
            });
    }
    @Override public void tick(){if(rendered!=c.codexStage())rebuildWidgets();}
    @Override public void onClose(){minecraft.setScreen(null);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);
        Component headline=rendered==CodexGenerationStage.REVIEWING&&c.visualRefinementStep()>0
            ?Component.translatable("craftgpt.codex.status.visual_reviewing_step",c.visualRefinementStep(),c.visualRefinementSteps())
            :Component.translatable(stageKey(rendered));
        wrapped(g,headline,35,2,rendered==CodexGenerationStage.FAILED?0xFFFF8585:TEXT);
        if(rendered.active()){
            var f=frame();int gap=4,segment=(f.width()-gap*4)/5;
            for(int i=0;i<5;i++){
                int color=i<rendered.completedSteps()?0xFF72C6A2:0xFF394454;
                if(i+1==rendered.currentStep())color=(System.currentTimeMillis()/500)%2==0?ACCENT:0xFF968564;
                g.fill(f.left()+i*(segment+gap),f.top()+63,f.left()+i*(segment+gap)+segment,f.top()+66,color);
            }
            line(g,Component.translatable("craftgpt.ui.elapsed",duration(c.codexElapsedSeconds())),76,MUTED);
            wrapped(g,Component.translatable(c.codexSecondsSinceActivity()>=120?"craftgpt.ui.quiet":"craftgpt.ui.background"),
                94,2,c.codexSecondsSinceActivity()>=120?0xFFFFAA55:MUTED);
        } else {
            wrapped(g,rendered==CodexGenerationStage.FAILED?c.status():
                Component.translatable(rendered==CodexGenerationStage.READY?"craftgpt.ui.ready_hint":"craftgpt.ui.stopped_hint"),
                67,4,rendered==CodexGenerationStage.FAILED?0xFFFFAA55:MUTED);
        }
    }
    private static String duration(long seconds){return "%02d:%02d".formatted(seconds/60,seconds%60);}
    private static String stageKey(CodexGenerationStage s){
        return switch(s){
            case IDLE,STARTING->"craftgpt.codex.status.starting";
            case REFRESHING->"craftgpt.codex.status.refreshing_context";
            case THINKING->"craftgpt.codex.status.thinking";
            case WRITING->"craftgpt.codex.status.writing";
            case REVIEWING->"craftgpt.codex.status.visual_reviewing";
            case REPAIRING->"craftgpt.codex.status.repairing";
            case IMPORTING->"craftgpt.codex.status.importing";
            case VALIDATING->"craftgpt.codex.status.validating";
            case READY->"craftgpt.codex.status.ready";
            case FAILED->"craftgpt.codex.status.failed";
            case CANCELLED->"craftgpt.codex.status.cancelled";
        };
    }
}
