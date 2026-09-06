package dev.craftgpt.build;

import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.context.AreaContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministically translates validated relative operations into Minecraft commands.
 * The model never supplies command text itself.
 */
public final class BuildCommandCompiler {
    private BuildCommandCompiler() {
    }

    public static List<String> toSetBlockCommands(
        CompiledBuildArtifact artifact,
        AreaContext context
    ) {
        List<String> commands = new ArrayList<>(artifact.operations().size());
        for (BuildOperation operation : artifact.operations()) {
            int x = Math.addExact(context.min().x(), operation.relativeX());
            int y = Math.addExact(context.min().y(), operation.relativeY());
            int z = Math.addExact(context.min().z(), operation.relativeZ());
            String blockState = artifact.palette().get(operation.paletteIndex());
            commands.add("/setblock " + x + " " + y + " " + z + " " + blockState + " replace");
        }
        return List.copyOf(commands);
    }
}
