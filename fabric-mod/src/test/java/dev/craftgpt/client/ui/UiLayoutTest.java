package dev.craftgpt.client.ui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class UiLayoutTest {
    @Test void primaryScreensFitAcrossSupportedGuiSizesWithoutOverlappingActions(){
        for(int[] size:List.of(new int[]{320,240},new int[]{426,240},new int[]{640,360},new int[]{854,480},new int[]{1280,720})){
            var f=UiLayout.focused(size[0],size[1]);
            for(var rows:List.of(
                List.of(f.row(82,1,0),f.row(116,2,0),f.row(116,2,1),f.row(150,1,0),f.footer(1,0)),
                List.of(f.row(96,1,0),f.row(124,2,0),f.row(124,2,1),f.row(154,2,0),f.row(154,2,1),f.footer(1,0)),
                List.of(f.row(132,1,0),f.row(160,2,0),f.row(160,2,1),f.footer(1,0)),
                List.of(new UiLayout.Box(f.left(),f.top()+70,f.width(),62),f.row(142,1,0),f.footer(2,0),f.footer(2,1)),
                List.of(f.row(54,1,0),f.row(82,1,0),f.row(112,1,0),f.row(140,1,0),f.footer(2,0),f.footer(2,1)))){
                assertLayout(rows,size[0],size[1]);
            }
        }
    }
    @Test void fourActionMenusHaveSpaceForNavigationAndBack(){
        for(int width:List.of(320,426,640,854)){
            var f=UiLayout.focused(width,240);var boxes=new ArrayList<UiLayout.Box>();
            for(int i=0;i<4;i++)boxes.add(f.row(62+i*25,1,0));
            boxes.add(f.row(166,2,0));boxes.add(f.row(166,2,1));boxes.add(f.footer(1,0));
            assertLayout(boxes,width,240);
        }
    }
    @Test void settingsTabsAndLongestPageFit(){
        for(int width:List.of(320,426,640)){
            var f=UiLayout.focused(width,240);var boxes=new ArrayList<UiLayout.Box>();
            for(int i=0;i<4;i++)boxes.add(f.row(32,4,i));
            for(int i=0;i<4;i++)boxes.add(f.row(64+i*26,1,0));
            boxes.add(f.footer(2,0));boxes.add(f.footer(2,1));
            assertLayout(boxes,width,240);
        }
    }
    @Test void columnRemainderDoesNotLoseRightEdge(){
        var f=UiLayout.focused(321,240);
        for(int columns=1;columns<=4;columns++){
            var last=f.row(64,columns,columns-1);
            assertEquals(f.left()+f.width(),last.x()+last.width());
        }
    }
    @Test void invalidColumnsFailEarly(){
        var f=UiLayout.focused(320,240);
        assertThrows(IllegalArgumentException.class,()->f.row(0,0,0));
        assertThrows(IllegalArgumentException.class,()->f.row(0,2,2));
    }
    private void assertLayout(List<UiLayout.Box> boxes,int width,int height){
        for(var b:boxes){
            assertTrue(b.width()>0&&b.height()>=20);
            assertTrue(b.x()>=0&&b.y()>=0&&b.x()+b.width()<=width&&b.y()+b.height()<=height,b.toString());
        }
        for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();j++){
            var a=boxes.get(i);var b=boxes.get(j);
            assertFalse(a.x()<b.x()+b.width()&&b.x()<a.x()+a.width()&&a.y()<b.y()+b.height()&&b.y()<a.y()+a.height(),
                "Overlapping controls: "+a+" and "+b);
        }
    }
}
