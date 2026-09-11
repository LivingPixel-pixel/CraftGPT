package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A visible, bounded edit request; unchanged coordinates remain protected by the importer. */
final class PartImprovementScreen extends Screen {
    private final Screen parent;
    private final PlanningController controller;
    private EditBox prompt;
    private int radius=2;
    private String value="";
    private java.util.Optional<dev.craftgpt.client.portable.BuildEditScope> target=java.util.Optional.empty();
    private Button submit;
    PartImprovementScreen(Screen parent,PlanningController controller) {
        super(Component.translatable("craftgpt.part.title"));this.parent=parent;this.controller=controller;
    }
    @Override protected void init() {
        if(prompt!=null)value=prompt.getValue();
        target=controller.aimedEditScope(minecraft,radius,"preview");
        int w=Math.min(390,width-24),x=(width-w)/2,y=Math.max(26,(height-180)/2);
        prompt=new EditBox(font,x,y+58,w,20,Component.translatable("craftgpt.part.prompt"));prompt.setMaxLength(1000);prompt.setValue(value);addRenderableWidget(prompt);
        addRenderableWidget(Button.builder(Component.translatable("craftgpt.part.radius",radius),b->{radius=radius==5?0:radius+1;target=controller.aimedEditScope(minecraft,radius,"preview");b.setMessage(Component.translatable("craftgpt.part.radius",radius));}).bounds(x,y+84,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x,y+138,(w-6)/2,20).build());
        submit=addRenderableWidget(Button.builder(Component.translatable("craftgpt.part.improve"),b->{
            if(prompt.getValue().isBlank())return;
            target.ifPresent(scope->controller.startVisualReview(minecraft,parent,new dev.craftgpt.client.portable.BuildEditScope(scope.from(),scope.to(),prompt.getValue())));
        }).bounds(x+(w+6)/2,y+138,(w-6)/2,20).build());
        submit.active=controller.canVisualReview();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        int w=Math.min(390,width-24),y=Math.max(26,(height-180)/2);
        g.centeredText(font,title,width/2,y-14,0xffffd966);
        int row=y+8;for(var line:font.split(Component.translatable("craftgpt.part.help"),w)){
            g.centeredText(font,line,width/2,row,0xffcccccc);row+=10;if(row>y+48)break;
        }
        var scope=target;
        String bounds=scope.map(s->s.from()+" to "+s.to()).orElse(Component.translatable("craftgpt.part.no_target").getString());
        g.centeredText(font,font.plainSubstrByWidth(bounds,w),width/2,y+116,scope.isPresent()?0xff55ffff:0xffffaa00);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(submit!=null)submit.active=target.isPresent()&&!prompt.getValue().isBlank()&&controller.canVisualReview();}
    @Override public void onClose(){ClientPlatform.setScreen(minecraft, parent);}
}
