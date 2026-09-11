package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The intention is the focus; paid API planning and exchange tools are secondary. */
public final class IntentionPlanningScreen extends FocusedScreen {
    private final Screen parent;
    private final PlanningController c;
    private String draft;
    private MultiLineEditBox promptBox;
    private Button build;
    public IntentionPlanningScreen(Screen parent,PlanningController c,String draft){
        super(Component.translatable("craftgpt.planning.title"));this.parent=parent;this.c=c;this.draft=draft==null?"":draft;
    }
    @Override protected void init(){
        var f=frame();
        promptBox=ClientPlatform.promptBox(font, f.left(), f.top()+70, f.width(), 62,
            Component.translatable("craftgpt.planning.placeholder"), Component.translatable("craftgpt.planning.input"));
        promptBox.setCharacterLimit(8000);promptBox.setValue(draft);
        promptBox.setValueListener(value->{draft=value;updateState();});
        addRenderableWidget(promptBox);
        build=action("craftgpt.ui.build_codex",142,1,0,b->{
            if(c.startCodexBuild(minecraft,draft))ClientPlatform.setScreen(minecraft, new CodexGenerationScreen(parent,c));
            updateState();
        });
        build.setTooltip(Tooltip.create(Component.translatable("craftgpt.codex.build_with_model",
            c.codexModel(),c.codexReasoningLevel(),c.codexGenerationEffort())));
        action(Component.translatable("craftgpt.ui.tools"),f.footer(2,0),b->ClientPlatform.setScreen(minecraft, new PlanningToolsScreen(this,c)));
        action(Component.translatable("gui.back"),f.footer(2,1),b->onClose());
        setInitialFocus(promptBox);updateState();
    }
    private void updateState(){if(build!=null)build.active=!draft.isBlank()&&c.currentContext().isPresent()&&!c.requestInFlight();}
    String draftText(){return draft;}
    void submitApi(){c.submit(minecraft,draft);ClientPlatform.setScreen(minecraft, this);updateState();}
    Component details(){
        return Component.empty().append(c.status()).append("\n\n")
            .append(Component.translatable("craftgpt.ui.api_estimate"))
            .append("\n").append(ApiUiText.estimate(c.estimatePlanningCost(draft)));
    }
    @Override public void tick(){updateState();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);
        line(g,Component.translatable(c.activeBuild().isPresent()?"craftgpt.ui.revise_hint":"craftgpt.ui.describe_hint"),36,TEXT);
        line(g,c.currentContext().map(a->Component.translatable("craftgpt.ui.area_size",a.width(),a.height(),a.depth()))
            .orElse(Component.translatable("craftgpt.planning.no_area")),53,MUTED);
        Component status=c.status();
        line(g,status.getString().isBlank()?Component.translatable("craftgpt.ui.preview_safe"):status,174,
            status.getString().isBlank()?MUTED:c.statusColor());
    }
    public void onPlanSaved(){draft="";if(minecraft!=null&&ClientPlatform.screen(minecraft)==this)ClientPlatform.setScreen(minecraft, new PlanReviewScreen(parent,c));}
    public void onVersionChanged(){if(minecraft!=null&&ClientPlatform.screen(minecraft)==this)rebuildWidgets();}
    @Override public void onClose(){ClientPlatform.setScreen(minecraft, parent);}
}
