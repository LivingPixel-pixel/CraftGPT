package dev.craftgpt.client.codex;

/** One runner progress signal, distinguished from a real Codex JSON event. */
public record CodexProgressUpdate(
    CodexGenerationStage stage,
    boolean codexEvent,
    String eventType
) {
    public static CodexProgressUpdate lifecycle(CodexGenerationStage stage) {
        return new CodexProgressUpdate(stage, false, "lifecycle");
    }

    public static CodexProgressUpdate event(CodexGenerationStage stage, String eventType) {
        return new CodexProgressUpdate(stage, true, eventType == null ? "unknown" : eventType);
    }
}
