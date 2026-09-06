package dev.craftgpt.build;
import dev.craftgpt.build.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComponentCompilerTest {
    BuildComponent c(String kind,List<Integer> to) { return new BuildComponent("main",kind,List.of(0,0,0),to,0,"z","south"); }
    @Test void fillsAndOverridesDeterministically() {
        BuildDraft d=new BuildDraft(1,"wall",List.of("minecraft:stone","minecraft:air"),List.of("1,1,0,1"),List.of(c("fill",List.of(2,2,0))));
        assertEquals(9,d.operations().size()); assertTrue(d.operations().contains("1,1,0,1"));
        assertTrue(d.components().isEmpty());
    }
    @Test void shellPreservesInterior() {
        var d=new BuildDraft(1,"room",List.of("minecraft:stone"),List.of(),List.of(c("shell",List.of(2,2,2))));
        assertEquals(26,d.operations().size()); assertFalse(d.operations().contains("1,1,1,0"));
    }
    @Test void roofFitsAndOrientsStairs() {
        var d=new BuildDraft(1,"roof",List.of("minecraft:oak_stairs"),List.of(),List.of(c("gable",List.of(4,2,5))));
        assertEquals(30,d.operations().size());
        assertTrue(d.palette().stream().anyMatch(s->s.contains("facing=east")));
        assertTrue(d.palette().stream().anyMatch(s->s.contains("facing=west")));
    }
    @Test void pairedDoor() {
        var d=new BuildDraft(1,"door",List.of("minecraft:oak_door"),List.of(),List.of(c("door",List.of(0,1,0))));
        assertEquals(2,d.operations().size()); assertTrue(d.palette().get(1).contains("half=lower"));
        assertTrue(d.palette().get(2).contains("half=upper"));
    }
    @Test void preservesDifferentFloorMaterialsAndExplicitPattern() {
        var oak=new BuildComponent("oak_floor","fill",List.of(0,0,0),List.of(1,0,2),0,"z","south");
        var stone=new BuildComponent("stone_floor","fill",List.of(2,0,0),List.of(3,0,2),1,"z","south");
        var palette=List.of("minecraft:dark_oak_planks","minecraft:polished_andesite","minecraft:red_terracotta");
        var draft=new BuildDraft(1,"Two rooms and an inlay",palette,List.of("1,0,1,2"),List.of(oak,stone));
        assertEquals(palette,draft.palette());
        assertTrue(draft.operations().contains("0,0,1,0"));
        assertTrue(draft.operations().contains("3,0,1,1"));
        assertTrue(draft.operations().contains("1,0,1,2"));
        assertEquals(12,draft.operations().size());
    }
    @Test void roofOnlyChangesOrientationNotTheChosenMaterial() {
        var draft=new BuildDraft(1,"Copper roof",List.of("minecraft:weathered_cut_copper_stairs"),List.of(),
            List.of(c("gable",List.of(4,2,2))));
        assertTrue(draft.palette().stream().allMatch(s->s.startsWith("minecraft:weathered_cut_copper_stairs")));
    }
    @Test void rejectsHugeExpansionBeforeAllocating() {
        assertThrows(IllegalArgumentException.class,()->new BuildDraft(1,"bad",List.of("minecraft:stone"),List.of(),List.of(c("fill",List.of(255,255,255)))));
    }
    @Test void rejectsInvertedAndUnknownComponents() {
        assertThrows(IllegalArgumentException.class,()->new BuildDraft(1,"bad",List.of("minecraft:stone"),List.of(),List.of(c("execute",List.of(1,1,1)))));
    }
}
