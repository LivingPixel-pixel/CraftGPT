package dev.craftgpt.client.portable;

import dev.craftgpt.build.model.BuildDraft;
import java.util.List;

/** A player-approved relative edit box. A reviewer cannot enlarge it. */
public record BuildEditScope(List<Integer> from, List<Integer> to, String instruction) {
    public BuildEditScope { from=List.copyOf(from);to=List.copyOf(to); }
    public void validate(int width,int height,int depth) {
        new ReviewDecision("inspect","scope",List.of(),
            List.of(new ReviewDecision.Camera("edit scope",0,from,to))).validate("scope",width,height,depth);
        if(instruction==null || instruction.isBlank() || instruction.length()>4000)
            throw new PortableExchangeException("invalid_edit_instruction");
    }
    public void validatePatch(BuildDraft patch) {
        if(patch==null) throw new PortableExchangeException("missing_edit_patch");
        for(String op:patch.operations()) {
            String[] values=op.split(",",-1);
            if(values.length!=4) throw new PortableExchangeException("invalid_patch_operation");
            for(int axis=0;axis<3;axis++) {
                int n;
                try {n=Integer.parseInt(values[axis]);} catch(NumberFormatException e){throw new PortableExchangeException("invalid_patch_operation",e);}
                if(n<from.get(axis)||n>to.get(axis))
                    throw new PortableExchangeException("patch_outside_player_edit_scope");
            }
        }
    }
}
