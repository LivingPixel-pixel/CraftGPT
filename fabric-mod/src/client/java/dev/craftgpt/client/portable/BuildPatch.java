package dev.craftgpt.client.portable;
import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.build.BuildLimits;
import java.util.*;

/** Merge by coordinate, not palette index. The base is never mutated. */
public final class BuildPatch {
    private BuildPatch() { }
    public static BuildDraft merge(BuildDraft base, BuildDraft patch) {
        if(base==null||patch==null) throw new PortableExchangeException("missing_patch_base");
        Map<String,String> states=new LinkedHashMap<>();
        apply(states,base); apply(states,patch);
        if(states.size()>BuildLimits.HARD_MAX_OPERATIONS) throw new PortableExchangeException("patch_too_large");
        List<String> palette=new ArrayList<>(),ops=new ArrayList<>();
        for(var e:states.entrySet()) {
            int p=palette.indexOf(e.getValue()); if(p<0){p=palette.size();palette.add(e.getValue());}
            ops.add(e.getKey()+","+p);
        }
        return new BuildDraft(1,patch.summary(),palette,ops);
    }
    private static void apply(Map<String,String> states,BuildDraft d) {
        Set<String> positions=new HashSet<>();
        for(String op:d.operations()) {
            int i=op.lastIndexOf(','); if(i<1) throw new PortableExchangeException("invalid_patch_operation");
            String key=op.substring(0,i);
            if(!key.matches("(?:0|[1-9][0-9]*),(?:0|[1-9][0-9]*),(?:0|[1-9][0-9]*)")||!positions.add(key))
                throw new PortableExchangeException("invalid_patch_operation");
            try {states.put(key,d.palette().get(Integer.parseInt(op.substring(i+1))));}
            catch(RuntimeException e){throw new PortableExchangeException("invalid_patch_palette",e);}
        }
    }
}
