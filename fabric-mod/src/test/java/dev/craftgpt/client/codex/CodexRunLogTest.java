package dev.craftgpt.client.codex;

import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.validation.ValidationProblemCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CodexRunLogTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void showsAllDetailedProblemsWithReasonsAndSuggestions() throws Exception {
        new CodexRunLog(temporaryDirectory);
        BuilderException failure = new BuilderException(List.of(
            ValidationProblemCatalog.problem("invalid_block_state", "build.palette[1]"),
            ValidationProblemCatalog.problem("out_of_bounds", "build.operations[4]")
        ));

        CodexRunLog.appendClientFailure(temporaryDirectory, "server_validation", failure);

        CodexChatMessage message = CodexRunLog.chatMessages(temporaryDirectory).stream()
            .filter(candidate -> candidate.role() == CodexChatMessage.Role.ERROR)
            .findFirst()
            .orElseThrow();
        assertTrue(message.text().contains("2 problem(s)"));
        assertTrue(message.text().contains("build.palette[1]"));
        assertTrue(message.text().contains("Cause:"));
        assertTrue(message.text().contains("Suggestion:"));
    }

    @Test
    void persistsReadableAndRawDiagnosticsWithSessionId() throws Exception {
        CodexRunLog log = new CodexRunLog(temporaryDirectory);
        String event = "{\"type\":\"thread.started\",\"thread_id\":\"019f4e03-6d36-7ee2-966d-2d7c1fe986ee\"}";

        log.info("Started safely");
        log.output(event);
        log.saveSession(CodexRunLog.sessionId(event));

        String readable = Files.readString(temporaryDirectory.resolve(CodexRunLog.RUN_LOG_FILE));
        String raw = Files.readString(temporaryDirectory.resolve(CodexRunLog.EVENT_LOG_FILE));
        String session = Files.readString(temporaryDirectory.resolve(CodexRunLog.SESSION_FILE)).trim();
        assertTrue(readable.contains("Started safely"));
        assertTrue(readable.contains("thread.started"));
        assertEquals(event + System.lineSeparator(), raw);
        assertEquals("019f4e03-6d36-7ee2-966d-2d7c1fe986ee", session);
    }

    @Test
    void summarizesAgentEventsWithoutDroppingTheirType() {
        String event = "{\"type\":\"item.completed\",\"item\":{\"type\":\"reasoning\",\"text\":\"Checked bounds\"}}";

        assertEquals("item.completed", CodexRunLog.eventType(event));
        assertEquals("item.completed item=reasoning detail=Checked bounds", CodexRunLog.summarize(event));
    }

    @Test
    void convertsVisibleJsonEventsIntoChatMessages() {
        CodexChatMessage reasoning = CodexRunLog.chatMessage(
            "{\"type\":\"item.completed\",\"item\":{\"type\":\"reasoning\",\"text\":\"Planning one block\"}}"
        );
        CodexChatMessage tool = CodexRunLog.chatMessage(
            "{\"type\":\"item.completed\",\"item\":{\"type\":\"command_execution\",\"command\":\"read request\",\"status\":\"completed\"}}"
        );

        assertEquals(CodexChatMessage.Role.CODEX, reasoning.role());
        assertEquals("Planning one block", reasoning.text());
        assertEquals(CodexChatMessage.Role.TOOL, tool.role());
        assertEquals("read request", tool.text());
    }

    @Test
    void presentsExpectedStdinStreamingAsNormalProgress() {
        CodexChatMessage message = CodexRunLog.chatMessage("Reading additional input from stdin...");

        assertEquals(CodexChatMessage.Role.SYSTEM, message.role());
        assertTrue(message.text().contains("streaming"));
    }

    @Test
    void persistsClientImportFailuresInBothDiagnosticViews() throws Exception {
        new CodexRunLog(temporaryDirectory);

        CodexRunLog.appendClientFailure(
            temporaryDirectory,
            "result_import",
            new IllegalArgumentException("invalid_material_roles")
        );

        String readable = Files.readString(temporaryDirectory.resolve(CodexRunLog.RUN_LOG_FILE));
        String raw = Files.readString(temporaryDirectory.resolve(CodexRunLog.EVENT_LOG_FILE));
        CodexChatMessage message = CodexRunLog.chatMessage(raw.strip());
        assertTrue(readable.contains("invalid_material_roles"));
        assertEquals(CodexChatMessage.Role.ERROR, message.role());
        assertTrue(message.text().contains("result_import"));
    }

    @Test
    void showsAutomaticRepairFeedbackInTheVisibleChat() throws Exception {
        new CodexRunLog(temporaryDirectory);

        CodexRunLog.appendRepairStarted(
            temporaryDirectory,
            1,
            2,
            "invalid_material_roles:role=4:candidate=0:invalid_block_state"
        );
        CodexRunLog.appendRepairCompleted(temporaryDirectory, 1);

        var messages = CodexRunLog.chatMessages(temporaryDirectory);
        assertTrue(messages.stream().anyMatch(message ->
            message.text().contains("Minecraft sent validator feedback to Codex")));
        assertTrue(messages.stream().anyMatch(message ->
            message.text().contains("replacement result for repair 1")));
    }
}
