package dev.craftgpt.client.build.preview;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ModelRasterizerTest {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("craftgpt","test/pattern");
    static List<MinecraftModelSnapshot.Quad> box(float x0,float y0,float z0,float x1,float y1,float z1) {
        float[][][] faces={
            {{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}},
            {{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},
            {{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}},
            {{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}},
            {{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},
            {{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1}}
        };
        List<MinecraftModelSnapshot.Quad> out=new ArrayList<>();
        for(int f=0;f<faces.length;f++) {
            var vs=new ArrayList<MinecraftModelSnapshot.Vertex>();
            for(int i=0;i<4;i++){var p=faces[f][i];vs.add(new MinecraftModelSnapshot.Vertex(p[0],p[1],p[2],i==1||i==2?1:0,i>=2?0:1));}
            out.add(new MinecraftModelSnapshot.Quad(vs,TEXTURE,0xffffff,f==0?1:.8f));
        }
        return out;
    }
    static BufferedImage pattern(){
        var t=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)t.setRGB(x,y,(x/4+y/4)%2==0?0xffb87b44:0xff5c3820);
        return t;
    }
    static MinecraftModelSnapshot models(){
        var stairs=new ArrayList<>(box(0,0,0,1,.5f,1)); stairs.addAll(box(.5f,.5f,0,1,1,1));
        return MinecraftModelSnapshot.fixture(Map.of("cube",box(0,0,0,1,1,1),"slab",box(0,0,0,1,.5f,1),"stairs",stairs,"door",box(0,0,0,1,2,.1875f)),Map.of(TEXTURE,pattern()));
    }
    @Test void geometryAndTextureSurviveAllFourAngles() throws Exception {
        var sheet=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        g.setColor(new Color(24,31,43));g.fillRect(0,0,1024,1024);
        String[] states={"cube","slab","stairs","door"};
        var hashes=new HashSet<Integer>();
        for(int r=0;r<4;r++)for(int i=0;i<4;i++){
            var im=ModelRasterizer.render(240,225,List.of(new ModelRasterizer.Block(0,0,0,states[i])),models(),r);
            int count=0,hash=1;Set<Integer> colors=new HashSet<>();
            for(int y=0;y<225;y++)for(int x=0;x<240;x++){int c=im.getRGB(x,y);if((c>>>24)>0){count++;colors.add(c);}hash=31*hash+c;}
            assertTrue(count>1000);assertTrue(colors.size()>=2);hashes.add(hash);
            g.drawImage(im,i*256+8,r*256+22,null);g.setColor(Color.WHITE);g.drawString(states[i]+" / rotation "+r,i*256+12,r*256+16);
        }
        g.dispose();assertTrue(hashes.size()>=8,"Different geometry and angles must not collapse to cubes");
        Path path=Path.of("build/reports/visual-qa/model-geometry.png");Files.createDirectories(path.getParent());ImageIO.write(sheet,"png",path.toFile());
    }
    @Test void emptySceneAndCutoutPixelsRemainTransparent(){
        var blank=ModelRasterizer.render(120,120,List.of(),models(),0);
        assertEquals(0,blank.getRGB(60,60));
        var clear=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        var m=MinecraftModelSnapshot.fixture(Map.of("cube",box(0,0,0,1,1,1)),Map.of(TEXTURE,clear));
        var invisible=ModelRasterizer.render(120,120,List.of(new ModelRasterizer.Block(0,0,0,"cube")),m,0);
        for(int y=0;y<120;y++)for(int x=0;x<120;x++)assertEquals(0,invisible.getRGB(x,y));
    }
}
