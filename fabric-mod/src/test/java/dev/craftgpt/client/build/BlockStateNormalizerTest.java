package dev.craftgpt.client.build;
import dev.craftgpt.build.model.*;
import dev.craftgpt.client.build.api.BuilderException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BlockStateNormalizerTest {
    @BeforeAll static void boot(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    CompiledBuildArtifact artifact(List<String> palette) {
        var ops=java.util.stream.IntStream.range(0,palette.size()).mapToObj(i->new BuildOperation(i,0,0,i)).toList();
        return new CompiledBuildArtifact(1,"build","area","project","v1","a".repeat(64),"b".repeat(64),"now","test","low","materials",palette,ops);
    }
    @Test void omittedPropertiesDoNotReplaceMaterials(){
        var result=BlockStateNormalizer.normalize(artifact(List.of("minecraft:dark_oak_planks","minecraft:polished_andesite","minecraft:oak_stairs[facing=west]")));
        assertEquals("minecraft:dark_oak_planks",result.palette().get(0));
        assertEquals("minecraft:polished_andesite",result.palette().get(1));
        assertTrue(result.palette().get(2).startsWith("minecraft:oak_stairs["));
        assertTrue(result.palette().get(2).contains("facing=west"));
        assertTrue(result.palette().get(2).contains("waterlogged=false"));
    }
    @Test void equivalentStatesRemapIndicesWithoutChangingAssignments(){
        var result=BlockStateNormalizer.normalize(artifact(List.of("minecraft:oak_log","minecraft:oak_log[axis=y]","minecraft:birch_log")));
        assertEquals(2,result.palette().size());
        assertEquals(List.of(0,0,1),result.operations().stream().map(BuildOperation::paletteIndex).toList());
        assertTrue(result.palette().get(1).startsWith("minecraft:birch_log["));
    }
    @Test void invalidMaterialsAreReportedNotReplacedWithStone(){
        assertThrows(BuilderException.class,()->BlockStateNormalizer.normalize(artifact(List.of("minecraft:not_a_block","minecraft:oak_stairs[facing=banana]"))));
    }
}
