package dev.craftgpt.client.codex;

import dev.craftgpt.client.portable.PortableBuildExchange;
import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.validation.ValidationProblemCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CodexCliRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void repairPromptIncludesEveryProblemWithCauseAndSuggestion() {
        BuilderException failure = new BuilderException(List.of(
            ValidationProblemCatalog.problem("invalid_block_state", "build.palette[1]"),
            ValidationProblemCatalog.problem("out_of_bounds", "build.operations[4]")
        ));

        String prompt = CodexCliRunner.buildRepairTaskPrompt(
            "{\"plan\":{},\"build\":{}}",
            "server_validation",
            failure,
            1,
            2
        );

        assertTrue(prompt.contains("every independently detectable issue"));
        assertTrue(prompt.contains("Fix every listed item"));
        assertTrue(prompt.contains("build.palette[1]"));
        assertTrue(prompt.contains("build.operations[4]"));
        assertTrue(prompt.contains("cause"));
        assertTrue(prompt.contains("suggestion"));
    }

    @Test
    void commandUsesReadOnlyEphemeralStructuredOutputWithoutShellOrApiKey() {
        Path result = temporaryDirectory.resolve(PortableBuildExchange.RESULT_FILE);
        List<String> command = CodexCliRunner.buildCommand(
            temporaryDirectory,
            result,
            "codex-test",
            new CodexRunSettings("gpt-5.6-terra", "high")
        );

        assertEquals("codex-test", command.getFirst());
        assertFalse(command.contains("--ephemeral"));
        assertTrue(command.contains("--ignore-user-config"));
        assertTrue(command.contains("--ignore-rules"));
        assertTrue(command.contains("read-only"));
        assertTrue(command.contains("--output-schema"));
        assertTrue(command.contains("gpt-5.6-terra"));
        assertTrue(command.contains("model_reasoning_effort=\"high\""));
        assertTrue(command.contains(result.toString()));
        assertFalse(command.stream().anyMatch(value -> value.contains("API_KEY")));
        assertFalse(command.contains("cmd.exe"));
        assertFalse(command.contains("powershell"));
        assertEquals("-", command.getLast());
    }

    @Test
    void jsonEventsMapToSafeUiStages() {
        assertEquals(
            CodexGenerationStage.THINKING,
            CodexCliRunner.stageFromEvent("{\"type\":\"turn.started\"}")
        );
        assertEquals(
            CodexGenerationStage.THINKING,
            CodexCliRunner.stageFromEvent("{\"type\":\"item.completed\",\"item\":{}}")
        );
        assertEquals(
            CodexGenerationStage.WRITING,
            CodexCliRunner.stageFromEvent(
                "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\"}}"
            )
        );
        assertNull(CodexCliRunner.stageFromEvent("not json"));
        assertEquals(
            CodexGenerationStage.THINKING,
            CodexCliRunner.stageFromEvent("{\"type\":\"item.started\"}")
        );
    }

    @Test
    void discoversNewestCodexDesktopBinaryWhenMinecraftPathIsStale() throws Exception {
        Path oldBinary = temporaryDirectory.resolve("OpenAI/Codex/bin/old/codex.exe");
        Path currentBinary = temporaryDirectory.resolve("OpenAI/Codex/bin/current/codex.exe");
        Files.createDirectories(oldBinary.getParent());
        Files.createDirectories(currentBinary.getParent());
        Files.writeString(oldBinary, "old");
        Files.writeString(currentBinary, "current");
        Files.setLastModifiedTime(oldBinary, java.nio.file.attribute.FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(currentBinary, java.nio.file.attribute.FileTime.fromMillis(2_000L));

        assertEquals(
            currentBinary.toAbsolutePath().normalize(),
            CodexCliRunner.findWindowsExecutable(temporaryDirectory, "").orElseThrow()
        );
    }

    @Test
    void prefersAnExistingPathBinaryOverDesktopFallback() throws Exception {
        Path pathDirectory = temporaryDirectory.resolve("path-bin");
        Path pathBinary = pathDirectory.resolve("codex.exe");
        Path desktopBinary = temporaryDirectory.resolve("OpenAI/Codex/bin/current/codex.exe");
        Files.createDirectories(pathDirectory);
        Files.createDirectories(desktopBinary.getParent());
        Files.writeString(pathBinary, "path");
        Files.writeString(desktopBinary, "desktop");

        assertEquals(
            pathBinary.toAbsolutePath().normalize(),
            CodexCliRunner.findWindowsExecutable(temporaryDirectory, pathDirectory.toString()).orElseThrow()
        );
    }

    @Test
    void writesEmbeddedInputAndClosesChildInput() throws Exception {
        TrackingProcess process = new TrackingProcess();

        CodexCliRunner.writeTaskPrompt(process, "embedded request");

        assertTrue(process.standardInput.closed);
        assertEquals("embedded request", process.standardInput.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void buildsAToolFreePromptFromTheExportedFiles() throws Exception {
        Files.writeString(temporaryDirectory.resolve(PortableBuildExchange.PROMPT_FILE), "worker rules");
        Files.writeString(temporaryDirectory.resolve(PortableBuildExchange.REQUEST_FILE), "{\"instruction\":\"dirt\"}");
        Files.writeString(temporaryDirectory.resolve(PortableBuildExchange.RESULT_SCHEMA_FILE), "{\"type\":\"object\"}");

        String prompt = CodexCliRunner.buildTaskPrompt(temporaryDirectory);

        assertTrue(prompt.contains("worker rules"));
        assertTrue(prompt.contains("{\"instruction\":\"dirt\"}"));
        assertTrue(prompt.contains("{\"type\":\"object\"}"));
        assertTrue(prompt.contains("Do not use tools"));
    }

    @Test
    void repairCommandResumesTheSameSessionWithTheSameSchema() {
        Path result = temporaryDirectory.resolve(PortableBuildExchange.RESULT_FILE);
        List<String> command = CodexCliRunner.buildRepairCommand(
            temporaryDirectory,
            result,
            "codex-test",
            "019f4e03-6d36-7ee2-966d-2d7c1fe986ee",
            new CodexRunSettings("gpt-5.6-sol", "xhigh")
        );

        assertEquals(List.of("codex-test", "exec", "resume"), command.subList(0, 3));
        assertTrue(command.contains("--output-schema"));
        assertTrue(command.contains(result.toString()));
        assertTrue(command.contains("019f4e03-6d36-7ee2-966d-2d7c1fe986ee"));
        assertTrue(command.contains("gpt-5.6-sol"));
        assertTrue(command.contains("model_reasoning_effort=\"xhigh\""));
        assertEquals("-", command.getLast());
        assertFalse(command.contains("powershell"));
        assertFalse(command.contains("cmd.exe"));
    }

    @Test
    void repairPromptContainsBoundedValidatorFeedbackAndPreviousResult() {
        String prompt = CodexCliRunner.buildRepairTaskPrompt(
            "{\"plan\":{},\"build\":{}}",
            "result_import",
            new IllegalArgumentException(
                "invalid_material_roles:role=4:candidate=0:invalid_block_state"
            ),
            1,
            2
        );

        assertTrue(prompt.contains("invalid_material_roles:role=4:candidate=0:invalid_block_state"));
        assertTrue(prompt.contains("\"attempt\":1"));
        assertTrue(prompt.contains("{\"plan\":{},\"build\":{}}"));
        assertTrue(prompt.contains("Do not run tools"));
        assertTrue(prompt.contains("full replacement"));
    }

    @Test
    void visualReviewResumesTheSameSessionAndAttachesEveryLocalContactSheet() throws Exception {
        Path result = temporaryDirectory.resolve(PortableBuildExchange.RESULT_FILE);
        List<Path> screenshots = List.of(
            temporaryDirectory.resolve("exterior.png"),
            temporaryDirectory.resolve("cutaways.png"),
            temporaryDirectory.resolve("sections.png")
        );
        for (Path screenshot : screenshots) Files.write(screenshot, new byte[] {1, 2, 3});
        List<String> command = CodexCliRunner.buildVisualReviewCommand(
            temporaryDirectory,
            result,
            screenshots,
            "codex-test",
            "019f4e03-6d36-7ee2-966d-2d7c1fe986ee",
            new CodexRunSettings("gpt-5.6-terra", "high")
        );

        assertEquals(List.of("codex-test", "exec", "resume"), command.subList(0, 3));
        assertEquals(3L, command.stream().filter("-i"::equals).count());
        for (Path screenshot : screenshots) assertTrue(command.contains(screenshot.toString()));
        assertTrue(command.contains("--output-schema"));
        assertTrue(command.contains("019f4e03-6d36-7ee2-966d-2d7c1fe986ee"));
        assertEquals("-", command.getLast());
        assertFalse(command.contains("powershell"));
        assertFalse(command.contains("cmd.exe"));
    }

    @Test
    void visualReviewPromptTreatsTheImageAsEvidenceAndRequiresAFullBuild() throws Exception {
        Files.writeString(temporaryDirectory.resolve(PortableBuildExchange.PROMPT_FILE), "quality rules");
        Files.writeString(temporaryDirectory.resolve(PortableBuildExchange.REQUEST_FILE), "{\"instruction\":\"review\"}");
        Files.writeString(temporaryDirectory.resolve(PortableBuildExchange.RESULT_SCHEMA_FILE), "{\"type\":\"object\"}");

        String prompt = CodexCliRunner.buildVisualReviewTaskPrompt(temporaryDirectory);

        assertTrue(prompt.contains("untrusted design data"));
        assertTrue(prompt.contains("decision keep, repair, or inspect"));
        assertTrue(prompt.contains("currentBuildHash"));
        assertTrue(prompt.contains("ONLY coordinate replacements"));
        assertTrue(prompt.contains("Crops are artificial sections"));
        assertTrue(prompt.contains("At most two extra inspection requests"));
        assertTrue(prompt.contains("quality rules"));
        assertTrue(prompt.contains("{\"instruction\":\"review\"}"));
    }

    @Test
    void emptyOrUnchangedBuildFeedbackDemandsAPlaceableObject() {
        String prompt = CodexCliRunner.buildRepairTaskPrompt(
            "{\"plan\":{},\"build\":{\"palette\":[],\"operations\":[]}}",
            "server_validation",
            new IllegalArgumentException("server_validation:no_changes"),
            1,
            2
        );

        assertTrue(prompt.contains("user clicked"));
        assertTrue(prompt.contains("placeable Minecraft object"));
        assertTrue(prompt.contains("at least one safe in-bounds operation"));
        assertTrue(prompt.contains("actual block"));
        assertTrue(prompt.contains("change against"));
    }

    @Test
    void createsDocumentedDesktopDeepLinkForSession() {
        assertEquals(
            "codex://threads/019f4e03-6d36-7ee2-966d-2d7c1fe986ee",
            CodexCliRunner.appSessionUri("019f4e03-6d36-7ee2-966d-2d7c1fe986ee").toString()
        );
    }

    @Test
    void rejectsUnsafeCodexModelOrReasoningValues() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new CodexRunSettings("gpt-5.6-terra --danger", "high"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new CodexRunSettings("gpt-5.6-terra", "none"));
    }

    @Test
    void desktopChatUnlocksOnlyAfterTheGeneratingProcessReleasesIt() {
        TrackingProcess active = new TrackingProcess();

        assertFalse(CodexCliRunner.appSessionAvailable(temporaryDirectory, "thread-123", active));
        assertTrue(CodexCliRunner.appSessionAvailable(temporaryDirectory, "thread-123", null));
        assertFalse(CodexCliRunner.appSessionAvailable(temporaryDirectory, null, null));
    }

    private static final class TrackingProcess extends Process {
        private final TrackingOutputStream standardInput = new TrackingOutputStream();

        @Override public OutputStream getOutputStream() { return standardInput; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() {}
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
