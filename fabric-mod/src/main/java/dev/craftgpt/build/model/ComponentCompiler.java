package dev.craftgpt.build.model;

import dev.craftgpt.build.BuildLimits;
import java.util.*;

/** Deterministic geometry expansion with limits before allocation or iteration. */
public final class ComponentCompiler {
    private ComponentCompiler() { }
    public record Expanded(List<String> palette, List<String> operations) { }

    public static Expanded expand(List<String> palette, List<String> operations, List<BuildComponent> components) {
        if (components == null || components.isEmpty()) return new Expanded(palette, operations);
        if (components.size() > 128 || palette == null || operations == null)
            throw new IllegalArgumentException("invalid_components");
        List<String> materials = new ArrayList<>(palette);
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        long work = 0;
        for (BuildComponent c : components) {
            if (c == null || c.id() == null || !c.id().matches("[a-zA-Z0-9_-]{1,48}") || !ids.add(c.id())
                || c.paletteIndex() < 0 || c.paletteIndex() >= palette.size()) fail("invalid_component");
            checkPoint(c.from()); checkPoint(c.to());
            int x0=c.from().get(0), y0=c.from().get(1), z0=c.from().get(2);
            int x1=c.to().get(0), y1=c.to().get(1), z1=c.to().get(2);
            if (x1<x0 || y1<y0 || z1<z0) fail("inverted_component_bounds");
            work += (long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);
            if (work > BuildLimits.HARD_MAX_OPERATIONS * 8L) fail("component_work_limit");
            String state = palette.get(c.paletteIndex());
            switch (c.kind() == null ? "" : c.kind()) {
                case "fill", "shell" -> {
                    for(int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) for(int z=z0;z<=z1;z++) {
                        boolean edge=x==x0||x==x1||y==y0||y==y1||z==z0||z==z1;
                        if ("fill".equals(c.kind()) || edge) put(result,x,y,z,state);
                    }
                }
                case "gable" -> {
                    boolean alongX="x".equals(c.axis());
                    if (!alongX && !"z".equals(c.axis())) fail("invalid_roof_axis");
                    int span=alongX ? z1-z0 : x1-x0;
                    if (y1-y0 < span/2) fail("roof_too_tall_for_bounds");
                    for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) {
                        int offset=alongX ? z-z0 : x-x0;
                        int y=y0+Math.min(offset,span-offset);
                        String roof=state;
                        if (blockId(state).endsWith("_stairs")) {
                            String facing=alongX ? (offset*2<=span?"south":"north") : (offset*2<=span?"east":"west");
                            roof=properties(state,Map.of("facing",facing,"half","bottom","shape","straight"));
                        }
                        put(result,x,y,z,roof);
                    }
                }
                case "door" -> {
                    if (!blockId(state).endsWith("_door") || blockId(state).endsWith("_trapdoor")
                        || c.facing()==null || !List.of("north","south","east","west").contains(c.facing())
                        || y1!=y0+1 || x1!=x0 || z1!=z0) fail("invalid_door_component");
                    put(result,x0,y0,z0,properties(state,Map.of("facing",c.facing(),"half","lower","hinge","left","open","false","powered","false")));
                    put(result,x0,y1,z0,properties(state,Map.of("facing",c.facing(),"half","upper","hinge","left","open","false","powered","false")));
                }
                default -> fail("unknown_component_kind");
            }
        }
        // Explicit blocks are final overrides. This makes windows, openings and sculpted details cheap.
        Set<String> explicit = new HashSet<>();
        for(String op:operations) {
            String[] v=op.split(",",-1);
            if(v.length!=4) fail("invalid_component_override");
            try {
                int x=Integer.parseInt(v[0]),y=Integer.parseInt(v[1]),z=Integer.parseInt(v[2]),p=Integer.parseInt(v[3]);
                String key=x+","+y+","+z;
                if(!explicit.add(key)||p<0||p>=palette.size()||x<0||y<0||z<0) fail("invalid_component_override");
                put(result,x,y,z,palette.get(p));
            } catch(NumberFormatException e) { fail("invalid_component_override"); }
        }
        List<String> out=new ArrayList<>();
        for(var e:result.entrySet()) {
            int p=materials.indexOf(e.getValue());
            if(p<0) { p=materials.size(); materials.add(e.getValue()); }
            if(materials.size()>BuildLimits.MAX_PALETTE_ENTRIES) fail("component_palette_limit");
            out.add(e.getKey()+","+p);
        }
        return new Expanded(List.copyOf(materials),List.copyOf(out));
    }
    private static void checkPoint(List<Integer> p) {
        if(p==null||p.size()!=3||p.stream().anyMatch(v->v==null||v<0||v>255)) fail("invalid_component_coordinate");
    }
    private static void put(Map<String,String> result,int x,int y,int z,String state) {
        result.put(x+","+y+","+z,state);
        if(result.size()>BuildLimits.HARD_MAX_OPERATIONS) fail("component_operation_limit");
    }
    public static String blockId(String state) { int i=state.indexOf('['); return i<0?state:state.substring(0,i); }
    public static String properties(String state,Map<String,String> changes) {
        TreeMap<String,String> props=new TreeMap<>();
        int i=state.indexOf('[');
        if(i>=0) for(String p:state.substring(i+1,state.length()-1).split(",")) {
            String[] pair=p.split("=",2); if(pair.length==2) props.put(pair[0],pair[1]);
        }
        props.putAll(changes);
        return blockId(state)+"["+String.join(",",props.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]";
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
