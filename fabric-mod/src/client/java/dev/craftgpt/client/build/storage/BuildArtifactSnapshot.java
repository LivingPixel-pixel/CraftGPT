package dev.craftgpt.client.build.storage;

import dev.craftgpt.build.model.CompiledBuildArtifact;

public record BuildArtifactSnapshot(CompiledBuildArtifact artifact, boolean accepted, int actualChanges) {
}
