package dev.craftgpt.client.planning.model;

import java.util.List;

public record MaterialRole(String role, List<String> blockCandidates, String purpose) {
    public MaterialRole {
        blockCandidates = List.copyOf(blockCandidates);
    }
}
