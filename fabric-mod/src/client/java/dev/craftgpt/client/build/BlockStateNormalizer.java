package dev.craftgpt.client.build;
import dev.craftgpt.build.model.*;
import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.validation.ValidationProblem;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.brigadier.StringReader;
import java.util.*;

/** Resolve omitted defaults using the game's registry. Server safety checks still run independently. */
public final class BlockStateNormalizer {
    private BlockStateNormalizer() { }
    public static CompiledBuildArtifact normalize(CompiledBuildArtifact a) {
        List<String> palette=new ArrayList<>(); int[] mapping=new int[a.palette().size()];
        List<ValidationProblem> problems=new ArrayList<>();
        for(int i=0;i<a.palette().size();i++) {
            try {
                StringReader reader=new StringReader(a.palette().get(i));
                var parsed=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,reader,false);
                if(reader.canRead()||parsed.nbt()!=null) throw new IllegalArgumentException();
                String canonical=BlockStateParser.serialize(parsed.blockState());
                int p=palette.indexOf(canonical);if(p<0){p=palette.size();palette.add(canonical);}mapping[i]=p;
            } catch(Exception e) { problems.add(new ValidationProblem("invalid_block_state","build.palette["+i+"]",
                "This block or property is not recognized by the installed Minecraft registry.",
                "Use a valid block ID and properties for this Minecraft version. Omitted defaults are filled automatically.")); }
        }
        if(!problems.isEmpty()) throw new BuilderException(problems);
        var ops=a.operations().stream().map(o->new BuildOperation(o.relativeX(),o.relativeY(),o.relativeZ(),mapping[o.paletteIndex()])).toList();
        return new CompiledBuildArtifact(a.schemaVersion(),a.buildId(),a.selectionId(),a.projectId(),a.planVersionId(),
            a.planContentHash(),a.contextHash(),a.createdAt(),a.model(),a.reasoningLevel(),a.summary(),palette,ops);
    }
}
