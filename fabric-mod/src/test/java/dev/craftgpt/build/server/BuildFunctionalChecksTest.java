package dev.craftgpt.build.server;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildFunctionalChecksTest {
    @BeforeAll static void boot(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    Map<BlockPos,BlockState> door() {
        Map<BlockPos,BlockState> blocks=new HashMap<>();
        blocks.put(new BlockPos(0,0,0),Blocks.STONE.defaultBlockState());
        blocks.put(new BlockPos(0,1,0),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
        blocks.put(new BlockPos(0,2,0),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        return blocks;
    }
    List<dev.craftgpt.validation.ValidationProblem> check(Map<BlockPos,BlockState> blocks) {
        return BuildFunctionalChecks.check(blocks,p->Blocks.AIR.defaultBlockState(),BlockPos.ZERO);
    }
    @Test void validPairedSupportedDoor(){assertTrue(check(door()).isEmpty());}
    @Test void reportsMissingHalfAndMissingSupportTogether(){
        var blocks=door();blocks.remove(new BlockPos(0,0,0));blocks.remove(new BlockPos(0,2,0));
        var codes=check(blocks).stream().map(p->p.code()).toList();
        assertTrue(codes.contains("incomplete_door"));assertTrue(codes.contains("unsupported_door"));
    }
    @Test void rejectsMismatchedMaterials(){
        var blocks=door();blocks.put(new BlockPos(0,2,0),Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        assertTrue(check(blocks).stream().allMatch(p->p.code().equals("incomplete_door")));
        assertFalse(check(blocks).isEmpty());
    }
    @Test void lowerSlabIsNotSturdyEnough(){
        var blocks=door();blocks.put(BlockPos.ZERO,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
        assertTrue(check(blocks).stream().anyMatch(p->p.code().equals("unsupported_door")));
    }
    @Test void removingExistingSupportIsChecked(){
        var original=door();
        var result=BuildFunctionalChecks.check(Map.of(BlockPos.ZERO,Blocks.AIR.defaultBlockState()),p->original.getOrDefault(p,Blocks.AIR.defaultBlockState()),BlockPos.ZERO);
        assertTrue(result.stream().anyMatch(p->p.code().equals("unsupported_door")));
    }
    @Test void sculptureIsNotForcedToHaveAHouseFloor() throws Exception {
        assertTrue(check(Map.of(new BlockPos(2,4,1), BuildPreviewValidator.parseCanonicalState(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK, "minecraft:white_concrete"))).isEmpty());
    }
}
