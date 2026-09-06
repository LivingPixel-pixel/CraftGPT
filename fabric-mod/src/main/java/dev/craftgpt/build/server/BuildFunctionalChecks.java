package dev.craftgpt.build.server;
import dev.craftgpt.validation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import java.util.*;
import java.util.function.Function;

/** Check the final overlay, never the intermediate placement order. */
public final class BuildFunctionalChecks {
    private BuildFunctionalChecks() { }
    public static List<ValidationProblem> check(Map<BlockPos,BlockState> targets, Function<BlockPos,BlockState> existing, BlockPos origin) {
        Function<BlockPos,BlockState> stateAt=p->targets.containsKey(p)?targets.get(p):existing.apply(p);
        BlockGetter view=new BlockGetter() {
            public BlockEntity getBlockEntity(BlockPos p){return null;}
            public BlockState getBlockState(BlockPos p){BlockState s=stateAt.apply(p);return s==null?Blocks.AIR.defaultBlockState():s;}
            public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
            public int getHeight(){return 4096;}
            public int getMinY(){return -2048;}
        };
        ValidationProblemCollector problems=new ValidationProblemCollector();
        Set<BlockPos> affected=new HashSet<>(targets.keySet());
        for(BlockPos p:targets.keySet()) {affected.add(p.above());affected.add(p.below());}
        for(BlockPos p:affected) {
            BlockState state=view.getBlockState(p);
            if(!(state.getBlock() instanceof DoorBlock)) continue;
            boolean lower=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER;
            BlockPos partner=lower?p.above():p.below();BlockState other=view.getBlockState(partner);
            String location="relative("+(p.getX()-origin.getX())+","+(p.getY()-origin.getY())+","+(p.getZ()-origin.getZ())+")";
            if(other.getBlock()!=state.getBlock() || other.getValue(DoorBlock.HALF)==state.getValue(DoorBlock.HALF)
                ||other.getValue(DoorBlock.FACING)!=state.getValue(DoorBlock.FACING)
                ||other.getValue(DoorBlock.HINGE)!=state.getValue(DoorBlock.HINGE)
                ||!other.getValue(DoorBlock.OPEN).equals(state.getValue(DoorBlock.OPEN))) {
                problems.add(new ValidationProblem("incomplete_door",location,
                    "The final build has missing or mismatched door halves.",
                    "Use a door component or matching upper and lower halves with the same facing, hinge and open state."));
            }
            if(lower && !view.getBlockState(p.below()).isFaceSturdy(view,p.below(),Direction.UP))
                problems.add(new ValidationProblem("unsupported_door",location,
                    "The lower door has no sturdy upper face beneath it and may break after placement.",
                    "Add a full supporting block below the door or move the entrance onto supported ground."));
        }
        return problems.problems();
    }
}
