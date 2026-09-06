package dev.craftgpt.client.portable;
import dev.craftgpt.build.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BuildEditScopeTest {
    final BuildEditScope scope=new BuildEditScope(List.of(1,0,1),List.of(3,1,3),"Change only the entrance floor");
    @Test void rejectsChangesOutsideEveryFace(){
        for(String p:List.of("0,0,1,0","4,0,1,0","1,2,1,0","1,0,0,0","1,0,4,0"))
            assertThrows(PortableExchangeException.class,()->scope.validatePatch(new BuildDraft(1,"bad",List.of("minecraft:stone"),List.of(p))));
    }
    @Test void permitsBoundaryCells(){
        assertDoesNotThrow(()->scope.validatePatch(new BuildDraft(1,"good",List.of("minecraft:birch_planks"),List.of("1,0,1,0","3,1,3,0"))));
    }
    @Test void expandedComponentsCannotEscapeTheScope(){
        var c=new BuildComponent("too_wide","fill",List.of(0,0,0),List.of(4,0,4),0,"z","south");
        assertThrows(PortableExchangeException.class,()->scope.validatePatch(new BuildDraft(1,"bad",List.of("minecraft:stone"),List.of(),List.of(c))));
    }
    @Test void localMergePreservesExteriorPalette(){
        var base=new BuildDraft(1,"base",List.of("minecraft:dark_oak_planks","minecraft:polished_andesite"),List.of("0,0,0,0","1,0,1,1","4,0,4,0"));
        var patch=new BuildDraft(1,"inlay",List.of("minecraft:red_terracotta"),List.of("1,0,1,0"));
        scope.validatePatch(patch);
        var merged=BuildPatch.merge(base,patch);
        for(String op:List.of(merged.operations().get(0),merged.operations().get(2)))
            assertEquals("minecraft:dark_oak_planks",merged.palette().get(Integer.parseInt(op.substring(op.lastIndexOf(',')+1))));
    }
}
