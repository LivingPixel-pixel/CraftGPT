package dev.craftgpt.client.ui;

import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Solid block-model inspection without placing or making a model call. */
public final class BuildInspectionScreen extends Screen {
    private final Screen parent;
    private final PlanningController controller;
    private final Identifier textureId=Identifier.fromNamespaceAndPath("craftgpt","inspection/"+UUID.randomUUID());
    private List<Path> pages=List.of();
    private int page, imageWidth, imageHeight, generation;
    private boolean started, loaded, closed;
    private Component message=Component.translatable("craftgpt.inspection.loading");
    public BuildInspectionScreen(Screen parent,PlanningController controller) {
        super(Component.translatable("craftgpt.inspection.title"));this.parent=parent;this.controller=controller;
    }
    @Override protected void init() {
        int w=Math.min(120,(width-40)/3),left=(width-(w*3+12))/2;
        addRenderableWidget(Button.builder(Component.literal("<"),b->changePage(-1)).bounds(left,height-28,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(left+w+6,height-28,w,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->changePage(1)).bounds(left+2*(w+6),height-28,w,20).build());
        if(!started) {
            started=true;
            controller.inspectionImages(minecraft).whenComplete((paths,error)->minecraft.execute(()->{
                if(closed)return;
                if(error!=null){message=Component.translatable("craftgpt.inspection.failed");return;}
                pages=paths;loadPage();
            }));
        }
    }
    private void changePage(int delta) {if(!pages.isEmpty()){page=Math.floorMod(page+delta,pages.size());loadPage();}}
    private void loadPage() {
        int token=++generation;Path file=pages.get(page);loaded=false;
        CompletableFuture.supplyAsync(()->{
            try{return Files.readAllBytes(file);}catch(Exception e){throw new CompletionException(e);}
        }).whenComplete((bytes,error)->minecraft.execute(()->{
            if(closed||generation!=token)return;
            if(error!=null){message=Component.translatable("craftgpt.inspection.failed");return;}
            try {
                NativeImage image=NativeImage.read(bytes);
                imageWidth=image.getWidth();imageHeight=image.getHeight();
                minecraft.getTextureManager().register(textureId,new DynamicTexture(()->"CraftGPT inspection",image));
                loaded=true;message=Component.translatable("craftgpt.inspection.page",page+1,pages.size());
            }catch(Exception e){message=Component.translatable("craftgpt.inspection.failed");}
        }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        super.extractRenderState(g,mouseX,mouseY,delta);
        g.centeredText(font,title,width/2,8,0xFFFFD966);
        if(loaded) {
            float scale=Math.min((width-20f)/imageWidth,(height-78f)/imageHeight);
            int w=(int)(imageWidth*scale),h=(int)(imageHeight*scale);
            g.blit(RenderPipelines.GUI_TEXTURED,textureId,(width-w)/2,25,0f,0f,w,h,imageWidth,imageHeight,imageWidth,imageHeight);
        }
        g.centeredText(font,message,width/2,height-43,0xFFCCCCCC);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){closed=true;generation++;minecraft.getTextureManager().release(textureId);}
}
