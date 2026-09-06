package dev.craftgpt.client.portable;

import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.build.storage.BuildArtifactHasher;

/** Resolves a prevalidated exchange against the live draft, independent of UI review counters. */
public final class PortableIterationResolver {
    private PortableIterationResolver() { }

    public record Resolution(BuildDraft draft, boolean kept) { }

    public static Resolution resolve(PortableBuildRequest request, PortableBuildResult result,
                                     CompiledBuildArtifact active, BuildEditScope playerScope) {
        if (result.review() == null) return new Resolution(result.build(), false);
        if (active == null) throw new PortableExchangeException("missing_patch_base");
        String actualHash = BuildArtifactHasher.sha256(active);
        if (!actualHash.equals(request.currentBuildHash())
            || !actualHash.equals(result.review().baseBuildHash())) {
            throw new PortableExchangeException("portable_patch_base_mismatch");
        }
        var area = request.areaContext();
        result.review().validate(actualHash, area.width(), area.height(), area.depth());
        if ("inspect".equals(result.review().decision())) {
            throw new PortableExchangeException("inspection_requires_visual_review");
        }
        if ("keep".equals(result.review().decision())) {
            if (result.plan() != null || result.build() != null)
                throw new PortableExchangeException("invalid_review_payload");
            return new Resolution(null, true);
        }
        if (result.plan() == null || result.build() == null)
            throw new PortableExchangeException("invalid_review_payload");
        if (request.editScope() != null) request.editScope().validatePatch(result.build());
        if (playerScope != null) playerScope.validatePatch(result.build());
        // Use the immutable live artifact, never a potentially edited exported base.
        BuildDraft base = new BuildDraft(1, active.summary(), active.palette(),
            active.operations().stream().map(op -> op.relativeX() + "," + op.relativeY()
                + "," + op.relativeZ() + "," + op.paletteIndex()).toList());
        return new Resolution(BuildPatch.merge(base, result.build()), false);
    }
}
