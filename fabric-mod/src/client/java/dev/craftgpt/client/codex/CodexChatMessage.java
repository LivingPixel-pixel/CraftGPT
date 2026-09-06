package dev.craftgpt.client.codex;

/** A safe, read-only presentation of one visible Codex event. */
public record CodexChatMessage(Role role, String text) {
    public enum Role {
        USER,
        CODEX,
        TOOL,
        SYSTEM,
        ERROR
    }
}
