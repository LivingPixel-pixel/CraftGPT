package dev.craftgpt.client.ui;
/** Shared logical-pixel geometry. Supported minimum: Minecraft's 320 x 240 GUI. */
public record UiLayout(int left,int top,int width,int height) {
    public static UiLayout focused(int screenWidth,int screenHeight) {
        int w=Math.min(380,Math.max(1,screenWidth-32)), h=Math.min(224,Math.max(1,screenHeight-16));
        return new UiLayout((screenWidth-w)/2,(screenHeight-h)/2,w,h);
    }
    public Box row(int offset,int columns,int column) {
        if(columns<1||column<0||column>=columns) throw new IllegalArgumentException("Invalid column");
        int available=width-(columns-1)*8, x=left+column*(available/columns+8);
        return new Box(x,top+offset,column==columns-1?left+width-x:available/columns,20);
    }
    public Box footer(int columns,int column) { return row(height-28,columns,column); }
    public record Box(int x,int y,int width,int height) { }
}
