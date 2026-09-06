package dev.craftgpt.client.ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Shared frame, bounded text, keyboard navigation and non-pausing menus. */
abstract class FocusedScreen extends Screen {
    protected static final int TEXT=0xFFE6E8EB,MUTED=0xFFABB3BF,ACCENT=0xFFFFD788;
    protected FocusedScreen(Component title) { super(title); }
    protected UiLayout frame() { return UiLayout.focused(width,height); }
    protected Button action(Component label,UiLayout.Box box,Button.OnPress press) {
        Button button=Button.builder(label,press).bounds(box.x(),box.y(),box.width(),box.height()).build();
        button.setTooltip(Tooltip.create(label));
        return addRenderableWidget(button);
    }
    protected Button action(String key,int offset,int columns,int column,Button.OnPress press) {
        return action(Component.translatable(key),frame().row(offset,columns,column),press);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta) {
        super.extractBackground(g,x,y,delta);
        UiLayout f=frame();
        g.fill(f.left()-10,f.top(),f.left()+f.width()+10,f.top()+f.height(),0xE8151C26);
        g.fill(f.left(),f.top()+26,f.left()+f.width(),f.top()+27,0xFF394454);
    }
    protected void heading(GuiGraphicsExtractor g) { line(g,title,10,ACCENT); }
    protected void line(GuiGraphicsExtractor g,Component text,int offset,int color) {
        UiLayout f=frame();String value=text.getString();
        String visible=font.width(value)>f.width()
            ?font.plainSubstrByWidth(value,Math.max(1,f.width()-font.width("...")))+"...":value;
        g.text(font,visible,f.left(),f.top()+offset,color,false);
    }
    protected void wrapped(GuiGraphicsExtractor g,Component text,int offset,int maxLines,int color) {
        var f=frame();var lines=font.split(text,f.width());int count=Math.min(lines.size(),maxLines);
        for(int i=0;i<count;i++) {
            if(i==count-1&&lines.size()>count) {
                StringBuilder plain=new StringBuilder();
                lines.get(i).accept((index,style,c)->{plain.appendCodePoint(c);return true;});
                g.text(font,font.plainSubstrByWidth(plain.toString(),Math.max(1,f.width()-font.width("...")))+"...",
                    f.left(),f.top()+offset+i*11,color,false);
            } else g.text(font,lines.get(i),f.left(),f.top()+offset+i*11,color,false);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
