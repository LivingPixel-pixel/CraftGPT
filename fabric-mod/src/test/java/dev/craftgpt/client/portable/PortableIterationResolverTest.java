package dev.craftgpt.client.portable;

import com.google.gson.Gson;
import dev.craftgpt.build.model.*;
import dev.craftgpt.client.build.storage.BuildArtifactHasher;
import dev.craftgpt.client.planning.model.IntentionSpec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PortableIterationResolverTest {
    private final CompiledBuildArtifact active = new CompiledBuildArtifact(1, "build", "area", "project",
        "version", "plan", "context", "now", "model", "low", "base",
        List.of("minecraft:oak_planks", "minecraft:bricks"),
        List.of(new BuildOperation(0,0,0,0), new BuildOperation(1,0,0,1)));
    private final BuildDraft patch = new BuildDraft(1, "changed",
        List.of("minecraft:quartz_block"), List.of("1,0,0,0"));
    private final IntentionSpec plan = new IntentionSpec("Revised build","Summary",List.of(),"style",null,
        "south",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),1,"reason","brief");

    private PortableBuildRequest request(String hash, BuildEditScope scope) {
        return new PortableBuildRequest(1,"request","now","context","instruction","change",100,"plan",null,
            new PortableAreaContext("overworld",3,3,3,27,0,0,0,0,Map.of(),List.of(),List.of(),null),
            hash, new BuildDraft(1,"tampered exported base",List.of("minecraft:dirt"),List.of("0,0,0,0")),scope);
    }
    private PortableBuildResult result(String decision, String hash) {
        return new PortableBuildResult(1,"request","context","instruction",
            decision.equals("repair") ? plan : null, decision.equals("repair") ? patch : null,
            new ReviewDecision(decision,hash,List.of(),List.of()));
    }
    private String hash() { return BuildArtifactHasher.sha256(active); }

    @Test void manualRepairDoesNotRequireVisualReviewCounter() {
        var resolved = PortableIterationResolver.resolve(request(hash(),null),result("repair",hash()),active,null);
        assertFalse(resolved.kept());
        assertEquals(List.of("minecraft:oak_planks","minecraft:quartz_block"),resolved.draft().palette());
        assertEquals(List.of("0,0,0,0","1,0,0,1"),resolved.draft().operations());
        assertEquals(List.of("minecraft:oak_planks","minecraft:bricks"),active.palette());
    }
    @Test void changedActiveBaseIsRejectedRatherThanRebased() {
        var error=assertThrows(PortableExchangeException.class,()->PortableIterationResolver.resolve(
            request("old",null),result("repair","old"),active,null));
        assertEquals("portable_patch_base_mismatch",error.getMessage());
    }
    @Test void reviewHashMustMatchLiveDraftToo() {
        assertThrows(PortableExchangeException.class,()->PortableIterationResolver.resolve(
            request(hash(),null),result("repair","old"),active,null));
    }
    @Test void repairCannotCreateAnInitialDraftWithoutBase() {
        assertThrows(PortableExchangeException.class,()->PortableIterationResolver.resolve(
            request(hash(),null),result("repair",hash()),null,null));
    }
    @Test void keepIsANoOpWithNoReplacementDraft() {
        var resolved=PortableIterationResolver.resolve(request(hash(),null),result("keep",hash()),active,null);
        assertTrue(resolved.kept()); assertNull(resolved.draft());
    }
    @Test void originalFullBuildStillWorks() {
        var full=new PortableBuildResult(1,"request","context","instruction",plan,patch);
        assertSame(patch,PortableIterationResolver.resolve(request(null,null),full,null,null).draft());
    }
    @Test void exportedScopeIsEnforced() {
        var scope=new BuildEditScope(List.of(0,0,0),List.of(0,0,0),"only origin");
        assertThrows(PortableExchangeException.class,()->PortableIterationResolver.resolve(
            request(hash(),scope),result("repair",hash()),active,null));
    }
    @Test void playerScopeCannotBeEnlargedByExport() {
        var scope=new BuildEditScope(List.of(0,0,0),List.of(0,0,0),"only origin");
        assertThrows(PortableExchangeException.class,()->PortableIterationResolver.resolve(
            request(hash(),null),result("repair",hash()),active,scope));
    }
    @Test void inspectionGetsAnActionableFailureOutsideVisualReview() {
        var inspect=new PortableBuildResult(1,"request","context","instruction",null,null,
            new ReviewDecision("inspect",hash(),List.of(),
                List.of(new ReviewDecision.Camera("inside",0,List.of(0,0,0),List.of(1,1,1)))));
        var error=assertThrows(PortableExchangeException.class,()->PortableIterationResolver.resolve(
            request(hash(),null),inspect,active,null));
        assertEquals("inspection_requires_visual_review",error.getMessage());
    }
}
