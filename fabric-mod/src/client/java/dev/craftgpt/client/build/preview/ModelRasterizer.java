package dev.craftgpt.client.build.preview;

import java.awt.image.BufferedImage;
import java.util.*;

/** CPU z-buffer renderer. No OpenGL, camera movement, world mutation, or texture approximation. */
final class ModelRasterizer {
    record Block(double x,double y,double z,String state) { }
    private record Point(double x,double y,double depth,double u,double v) { }
    private record Triangle(Point a,Point b,Point c,BufferedImage texture,int tint,float shade) { }
    static BufferedImage render(int width,int height,List<Block> blocks,MinecraftModelSnapshot models,int rotation) {
        if(width<17||height<17||(long)width*height>8_000_000||rotation<0||rotation>3)
            throw new IllegalArgumentException("invalid_render_dimensions");
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        if(blocks.isEmpty()) return out;
        List<Triangle> triangles=new ArrayList<>();
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(Block block:blocks) for(var quad:models.quads(block.state())) {
            List<Point> points=new ArrayList<>();
            for(var v:quad.vertices()) {
                double x=block.x()+v.x(),y=block.y()+v.y(),z=block.z()+v.z();
                double rx=switch(rotation){case 1->z;case 2->-x;case 3->-z;default->x;};
                double rz=switch(rotation){case 1->-x;case 2->-z;case 3->x;default->z;};
                Point p=new Point(rx-rz,(rx+rz)*.5-y,rx+rz+y,v.u(),v.v());
                points.add(p); minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());
            }
            var tex=models.texture(quad.texture());
            triangles.add(new Triangle(points.get(0),points.get(1),points.get(2),tex,quad.tint(),quad.shade()));
            triangles.add(new Triangle(points.get(0),points.get(2),points.get(3),tex,quad.tint(),quad.shade()));
            if(triangles.size()>500_000)throw new IllegalArgumentException("visual_geometry_budget_exceeded");
        }
        if(triangles.isEmpty()) return out;
        double scale=Math.min((width-16)/Math.max(.01,maxX-minX),(height-16)/Math.max(.01,maxY-minY));
        double ox=(width-(maxX-minX)*scale)/2-minX*scale,oy=(height-(maxY-minY)*scale)/2-minY*scale;
        double[] depth=new double[width*height]; Arrays.fill(depth,Double.NEGATIVE_INFINITY);
        triangles.sort(Comparator.comparingDouble(t->t.a().depth()+t.b().depth()+t.c().depth()));
        for(Triangle t:triangles) draw(out,depth,transform(t.a(),scale,ox,oy),transform(t.b(),scale,ox,oy),transform(t.c(),scale,ox,oy),t);
        return out;
    }
    private static Point transform(Point p,double s,double x,double y) { return new Point(p.x()*s+x,p.y()*s+y,p.depth(),p.u(),p.v()); }
    private static double edge(Point a,Point b,double x,double y) {return (x-a.x())*(b.y()-a.y())-(y-a.y())*(b.x()-a.x());}
    private static void draw(BufferedImage out,double[] depth,Point a,Point b,Point c,Triangle t) {
        double area=edge(a,b,c.x(),c.y()); if(Math.abs(area)<.00001)return;
        int x0=Math.max(0,(int)Math.floor(Math.min(a.x(),Math.min(b.x(),c.x())))),x1=Math.min(out.getWidth()-1,(int)Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))));
        int y0=Math.max(0,(int)Math.floor(Math.min(a.y(),Math.min(b.y(),c.y())))),y1=Math.min(out.getHeight()-1,(int)Math.ceil(Math.max(a.y(),Math.max(b.y(),c.y()))));
        for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
            double w0=edge(b,c,x+.5,y+.5)/area,w1=edge(c,a,x+.5,y+.5)/area,w2=1-w0-w1;
            if(w0<0||w1<0||w2<0)continue;
            double z=w0*a.depth()+w1*b.depth()+w2*c.depth();int index=y*out.getWidth()+x;
            if(z<=depth[index]+.000001)continue;
            double u=w0*a.u()+w1*b.u()+w2*c.u(),v=w0*a.v()+w1*b.v()+w2*c.v();
            BufferedImage tex=t.texture();
            int color=tex==null?0xFFFF00FF:tex.getRGB(Math.clamp((int)(u*tex.getWidth()),0,tex.getWidth()-1),Math.clamp((int)(v*tex.getHeight()),0,tex.getHeight()-1));
            int alpha=color>>>24; if(alpha<30)continue;
            int r=(int)(((color>>16)&255)*((t.tint()>>16)&255)/255.0*t.shade());
            int g=(int)(((color>>8)&255)*((t.tint()>>8)&255)/255.0*t.shade());
            int bl=(int)((color&255)*(t.tint()&255)/255.0*t.shade());
            int old=out.getRGB(x,y),oldA=old>>>24;
            if(alpha<255 && oldA>0) {
                r=(r*alpha+((old>>16)&255)*(255-alpha))/255;
                g=(g*alpha+((old>>8)&255)*(255-alpha))/255;
                bl=(bl*alpha+(old&255)*(255-alpha))/255;
                alpha=alpha+oldA*(255-alpha)/255;
            }
            out.setRGB(x,y,(alpha<<24)|(r<<16)|(g<<8)|bl);depth[index]=z;
        }
    }
}
