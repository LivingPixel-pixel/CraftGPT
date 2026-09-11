package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
/** Secondary actions: at most four per page. */
final class ActionMenuScreen extends FocusedScreen {
    record Entry(Supplier<Component> label,BooleanSupplier enabled,Consumer<Screen> run) {
        static Entry of(String key,BooleanSupplier enabled,Consumer<Screen> run) {
            return new Entry(()->Component.translatable(key),enabled,run);
        }
    }
    private final Screen parent;
    private final Component description;
    private final List<Entry> entries;
    private final List<Button> buttons=new ArrayList<>();
    private int page;
    ActionMenuScreen(Screen parent,String title,String description,List<Entry> entries) {
        super(Component.translatable(title));this.parent=parent;
        this.description=Component.translatable(description);this.entries=List.copyOf(entries);
    }
    @Override protected void init() {
        buttons.clear();
        for(int i=0;i<4&&page*4+i<entries.size();i++) {
            Entry entry=entries.get(page*4+i);
            buttons.add(action(entry.label().get(),frame().row(62+i*25,1,0),b->entry.run().accept(this)));
        }
        if(entries.size()>4) {
            action(Component.literal("<"),frame().row(166,2,0),b->{page--;rebuildWidgets();}).active=page>0;
            action(Component.literal(">"),frame().row(166,2,1),b->{page++;rebuildWidgets();}).active=(page+1)*4<entries.size();
        }
        action(Component.translatable("gui.back"),frame().footer(1,0),b->onClose());tick();
    }
    @Override public void tick() {
        for(int i=0;i<buttons.size();i++) {
            Entry entry=entries.get(page*4+i);buttons.get(i).active=entry.enabled().getAsBoolean();
            buttons.get(i).setMessage(entry.label().get());
            buttons.get(i).setTooltip(net.minecraft.client.gui.components.Tooltip.create(entry.label().get()));
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d) {
        super.extractRenderState(g,x,y,d);heading(g);wrapped(g,description,34,2,MUTED);
    }
    @Override public void onClose() { ClientPlatform.setScreen(minecraft, parent); }
}
