package dev.craftgpt.client.ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;
/** Complete error text remains available beyond the primary screen's short summary. */
final class StatusDetailsScreen extends FocusedScreen {
    private final Screen parent;
    private final Supplier<Component> content;
    private int page;
    StatusDetailsScreen(Screen parent,Supplier<Component> content) {
        super(Component.translatable("craftgpt.ui.details"));this.parent=parent;this.content=content;
    }
    @Override protected void init() {
        int count=font.split(content.get(),frame().width()).size();
        page=Math.min(page,Math.max(0,(count-1)/11));
        action(Component.literal("<"),frame().row(166,2,0),b->{page--;rebuildWidgets();}).active=page>0;
        action(Component.literal(">"),frame().row(166,2,1),b->{page++;rebuildWidgets();}).active=(page+1)*11<count;
        action(Component.translatable("gui.back"),frame().footer(1,0),b->onClose());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d) {
        super.extractRenderState(g,x,y,d);heading(g);
        var lines=font.split(content.get(),frame().width());
        for(int i=0;i<11&&page*11+i<lines.size();i++)
            g.text(font,lines.get(page*11+i),frame().left(),frame().top()+34+i*11,TEXT,false);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
