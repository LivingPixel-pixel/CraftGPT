package dev.craftgpt.client.portable;

import com.google.gson.Gson;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional read-only replay. Saved requests and player data are never bundled or modified. */
class PortableIterationReplayTest {
    @Test void replaySavedRepairAgainstItsRealArtifact() throws Exception {
        String directory=System.getenv("CRAFTGPT_REPLAY_REQUEST");
        String artifactPath=System.getenv("CRAFTGPT_REPLAY_ARTIFACT");
        assumeTrue(directory!=null && artifactPath!=null, "Set replay paths for a local incident replay");
        var gson=new Gson();
        var request=gson.fromJson(Files.readString(Path.of(directory,"request.craftgpt.json")),PortableBuildRequest.class);
        var result=gson.fromJson(Files.readString(Path.of(directory,"result.craftgpt.json")),PortableBuildResult.class);
        var stored=com.google.gson.JsonParser.parseString(Files.readString(Path.of(artifactPath))).getAsJsonObject();
        var artifact=gson.fromJson(stored.has("artifact") ? stored.get("artifact") : stored,CompiledBuildArtifact.class);
        assertEquals(request.requestId(),result.requestId());
        assertEquals(request.contextHash(),result.contextHash());
        assertEquals(request.instructionHash(),result.instructionHash());
        assertEquals("repair",result.review().decision());
        var resolved=PortableIterationResolver.resolve(request,result,artifact,null);
        assertFalse(resolved.kept());
        assertTrue(resolved.draft().operations().size()>=artifact.operations().size());
        var actual=new java.util.HashMap<String,String>();
        for(var op:resolved.draft().operations()) {
            int i=op.lastIndexOf(',');
            actual.put(op.substring(0,i),resolved.draft().palette().get(Integer.parseInt(op.substring(i+1))));
        }
        var expected=new java.util.HashMap<String,String>();
        for(var op:artifact.operations())
            expected.put(op.relativeX()+","+op.relativeY()+","+op.relativeZ(),artifact.palette().get(op.paletteIndex()));
        for(var op:result.build().operations()) {
            int i=op.lastIndexOf(',');
            expected.put(op.substring(0,i),result.build().palette().get(Integer.parseInt(op.substring(i+1))));
        }
        assertEquals(expected,actual,"All patched and untouched block states must match exactly");
        var a=request.areaContext();
        var context=new dev.craftgpt.context.AreaContext(1,artifact.selectionId(),a.dimensionType(),
            new dev.craftgpt.context.AreaPoint(0,0,0),
            new dev.craftgpt.context.AreaPoint(a.width()-1,a.height()-1,a.depth()-1),
            a.width(),a.height(),a.depth(),a.volume(),(int)a.volume(),a.nonAirBlocks(),0,
            a.occupiedColumns(),a.minimumRelativeSurfaceY(),a.maximumRelativeSurfaceY(),
            a.materials(),"0".repeat(64),a.layers(),a.surfaceSamples(),a.exactBlocks());
        dev.craftgpt.client.planning.api.PlannerResponseValidator.validate(
            result.plan(),context,request.maximumOperations());
        dev.craftgpt.client.build.api.BuilderResponseValidator.validateDraft(
            resolved.draft(),context,request.maximumOperations());
    }
}
