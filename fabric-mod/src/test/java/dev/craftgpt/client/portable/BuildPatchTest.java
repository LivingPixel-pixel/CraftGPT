package dev.craftgpt.client.portable;
import dev.craftgpt.build.model.BuildDraft;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BuildPatchTest {
    @Test void paletteIndicesNeverLeakAcrossDrafts() {
        var base=new BuildDraft(1,"oak and brick",List.of("minecraft:oak_planks","minecraft:bricks"),List.of("0,0,0,0","1,0,0,1"));
        var patch=new BuildDraft(1,"replace brick",List.of("minecraft:quartz_block"),List.of("1,0,0,0"));
        var merged=BuildPatch.merge(base,patch);
        assertEquals("minecraft:oak_planks",merged.palette().get(Integer.parseInt(merged.operations().get(0).split(",")[3])));
        assertEquals("minecraft:quartz_block",merged.palette().get(Integer.parseInt(merged.operations().get(1).split(",")[3])));
        assertEquals(List.of("minecraft:oak_planks","minecraft:bricks"),base.palette());
    }
    @Test void airIsAnExplicitRemovalNotAMissingOperation() {
        var base=new BuildDraft(1,"base",List.of("minecraft:stone"),List.of("0,0,0,0","1,0,0,0"));
        var merged=BuildPatch.merge(base,new BuildDraft(1,"remove",List.of("minecraft:air"),List.of("0,0,0,0")));
        assertEquals(2,merged.operations().size());assertTrue(merged.palette().contains("minecraft:air"));
    }
    @Test void duplicatePatchCoordinatesAreRejected() {
        var d=new BuildDraft(1,"bad",List.of("minecraft:stone"),List.of("0,0,0,0","0,0,0,0"));
        assertThrows(PortableExchangeException.class,()->BuildPatch.merge(d,d));
    }
}
