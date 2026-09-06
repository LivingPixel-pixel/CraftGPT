package dev.craftgpt.client.ui;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

final class ReviewFindingsScreen extends Screen {
    private final Screen parent;
    private final PlanningController controller;
    private int page;
    ReviewFindingsScreen(Screen parent,PlanningController controller){
        super(Component.translatable("craftgpt.findings.title"));this.parent=parent;this.controller=controller;
    }
    private List<FormattedCharSequence> lines(){
        String s=controller.reviewNotes();
        return font.split(s.isBlank()?Component.translatable("craftgpt.findings.none"):Component.literal(s),Math.max(40,width-32));
    }
    private int count(){return Math.max(1,(height-90)/12);}
    @Override protected void init(){
        int w=Math.min(100,(width-40)/3),x=(width-3*w-12)/2;
        addRenderableWidget(Button.builder(Component.literal("<"),b->page=Math.max(0,page-1)).bounds(x,height-30,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x+w+6,height-30,w,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->page=Math.min(Math.max(0,(lines().size()-1)/count()),page+1)).bounds(x+2*(w+6),height-30,w,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);g.centeredText(font,title,width/2,12,0xffffd966);
        var lines=lines();page=Math.min(page,Math.max(0,(lines.size()-1)/count()));
        for(int i=page*count();i<Math.min(lines.size(),(page+1)*count());i++)g.text(font,lines.get(i),16,40+(i-page*count())*12,0xffeeeeee);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
