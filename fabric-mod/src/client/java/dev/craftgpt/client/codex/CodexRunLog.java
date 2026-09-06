package dev.craftgpt.client.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.craftgpt.client.portable.PortableBuildExchange;
import dev.craftgpt.validation.ValidationProblem;
import dev.craftgpt.validation.ValidationProblemJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persistent per-request diagnostics. Request content remains outside the human-readable log. */
final class CodexRunLog {
    static final String RUN_LOG_FILE = "codex-run.log";
    static final String EVENT_LOG_FILE = "codex-events.jsonl";
    static final String SESSION_FILE = "codex-session.txt";
    private static final int MAX_VISIBLE_LOG_BYTES = 512 * 1024;

    private final Path runLog;
    private final Path eventLog;
    private final Path sessionFile;

    CodexRunLog(Path directory) throws IOException {
        this(directory, false);
    }

    static CodexRunLog resume(Path directory) throws IOException {
        return new CodexRunLog(directory, true);
    }

    private CodexRunLog(Path directory, boolean appendExisting) throws IOException {
        runLog = directory.resolve(RUN_LOG_FILE).normalize();
        eventLog = directory.resolve(EVENT_LOG_FILE).normalize();
        sessionFile = directory.resolve(SESSION_FILE).normalize();
        if (appendExisting) {
            Files.writeString(runLog, "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(eventLog, "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(runLog, "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(eventLog, "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.deleteIfExists(sessionFile);
        }
    }

    Path runLog() {
        return runLog;
    }

    Path eventLog() {
        return eventLog;
    }

    synchronized void info(String message) {
        appendInfo(runLog.getParent(), message);
    }

    synchronized void output(String line) {
        String safeLine = line == null ? "" : line.replace("\r", "").replace("\n", "\\n");
        append(eventLog, safeLine + System.lineSeparator());
        info("Codex output: " + summarize(line));
    }

    synchronized void saveSession(String sessionId) {
        if (!validSessionId(sessionId)) return;
        try {
            Files.writeString(sessionFile, sessionId + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            info("Saved Codex session " + sessionId);
        } catch (IOException exception) {
            info("Could not save the Codex session ID: " + exception.getClass().getSimpleName());
        }
    }

    static List<String> readLines(Path path) {
        if (path == null || !Files.isRegularFile(path)) return List.of();
        try {
            long size = Files.size(path);
            byte[] bytes;
            if (size <= MAX_VISIBLE_LOG_BYTES) {
                bytes = Files.readAllBytes(path);
            } else {
                try (var channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
                    channel.position(size - MAX_VISIBLE_LOG_BYTES);
                    var buffer = java.nio.ByteBuffer.allocate(MAX_VISIBLE_LOG_BYTES);
                    while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                        // Continue until the tail buffer is full or EOF is reached.
                    }
                    buffer.flip();
                    bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                }
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>(content.lines().toList());
            if (size > MAX_VISIBLE_LOG_BYTES) result.addFirst("[Earlier log content omitted from viewer]");
            return List.copyOf(result);
        } catch (IOException exception) {
            return List.of("Could not read log: " + exception.getClass().getSimpleName());
        }
    }

    static void appendInfo(Path directory, String message) {
        if (directory == null) return;
        append(
            directory.resolve(RUN_LOG_FILE),
            "[" + Instant.now() + "] " + sanitizeLine(message) + System.lineSeparator()
        );
    }

    static void appendClientFailure(Path directory, String phase, Throwable failure) {
        if (directory == null) return;
        String safePhase = truncate(sanitizeLine(phase), 64);
        String message = failure == null
            ? "unknown_failure"
            : firstNonBlank(failure.getMessage(), failure.getClass().getSimpleName());
        String safeMessage = truncate(sanitizeLine(message), 320);
        List<ValidationProblem> problems = ValidationProblemJson.fromThrowable(failure);
        appendInfo(
            directory,
            "CraftGPT client " + safePhase + " found " + problems.size()
                + " problem(s): " + safeMessage
        );
        JsonObject event = new JsonObject();
        event.addProperty("type", "craftgpt.client_error");
        event.addProperty("phase", safePhase);
        event.addProperty("message", safeMessage);
        JsonArray problemJson = new JsonArray();
        for (ValidationProblem problem : problems) {
            appendInfo(
                directory,
                problem.code() + " at " + problem.location()
                    + " | Cause: " + problem.cause()
                    + " | Suggestion: " + problem.suggestion()
            );
            JsonObject item = new JsonObject();
            item.addProperty("code", problem.code());
            item.addProperty("location", problem.location());
            item.addProperty("cause", problem.cause());
            item.addProperty("suggestion", problem.suggestion());
            problemJson.add(item);
        }
        event.add("problems", problemJson);
        append(
            directory.resolve(EVENT_LOG_FILE),
            event + System.lineSeparator()
        );
    }

    static void appendRepairStarted(Path directory, int attempt, int maximumAttempts, String errorCode) {
        if (directory == null) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "craftgpt.repair_started");
        event.addProperty("attempt", attempt);
        event.addProperty("maximum_attempts", maximumAttempts);
        event.addProperty("error_code", truncate(sanitizeLine(errorCode), 320));
        append(directory.resolve(EVENT_LOG_FILE), event + System.lineSeparator());
        appendInfo(
            directory,
            "Automatic repair " + attempt + "/" + maximumAttempts + " started for " + errorCode
        );
    }

    static void appendRepairCompleted(Path directory, int attempt) {
        if (directory == null) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "craftgpt.repair_completed");
        event.addProperty("attempt", attempt);
        append(directory.resolve(EVENT_LOG_FILE), event + System.lineSeparator());
        appendInfo(directory, "Automatic repair " + attempt + " produced a replacement result");
    }

    static List<CodexChatMessage> chatMessages(Path directory) {
        List<CodexChatMessage> messages = new ArrayList<>();
        Path request = directory.resolve(PortableBuildExchange.REQUEST_FILE).normalize();
        if (Files.isRegularFile(request)) {
            try {
                JsonObject requestJson = JsonParser.parseString(Files.readString(request, StandardCharsets.UTF_8))
                    .getAsJsonObject();
                String instruction = safeString(requestJson, "instruction");
                if (!instruction.isBlank()) {
                    messages.add(new CodexChatMessage(CodexChatMessage.Role.USER, instruction));
                }
            } catch (IOException | RuntimeException ignored) {
                messages.add(new CodexChatMessage(
                    CodexChatMessage.Role.ERROR,
                    "Could not read the CraftGPT request"
                ));
            }
        }

        Path events = directory.resolve(EVENT_LOG_FILE).normalize();
        for (String line : readLines(events)) {
            CodexChatMessage message = chatMessage(line);
            if (message != null) messages.add(message);
        }
        if (messages.stream().noneMatch(message -> message.role() != CodexChatMessage.Role.USER)) {
            messages.add(new CodexChatMessage(
                CodexChatMessage.Role.SYSTEM,
                "Waiting for the first Codex event"
            ));
        }
        return List.copyOf(messages);
    }

    static CodexChatMessage chatMessage(String line) {
        JsonObject event = jsonObject(line);
        if (event == null) {
            String text = truncate(sanitizeLine(line), 1_200);
            if (text.isBlank() || text.startsWith("[Earlier log content omitted")) return null;
            if (text.contains("Reading additional input from stdin")) {
                return new CodexChatMessage(
                    CodexChatMessage.Role.SYSTEM,
                    "CraftGPT is streaming the protected build context to Codex"
                );
            }
            return new CodexChatMessage(
                text.toLowerCase(java.util.Locale.ROOT).contains("error")
                    ? CodexChatMessage.Role.ERROR
                    : CodexChatMessage.Role.SYSTEM,
                text
            );
        }

        String type = safeString(event, "type");
        if ("craftgpt.client_error".equals(type)) {
            JsonArray problems = event.has("problems") && event.get("problems").isJsonArray()
                ? event.getAsJsonArray("problems")
                : new JsonArray();
            if (!problems.isEmpty()) {
                StringBuilder text = new StringBuilder("Minecraft found ")
                    .append(problems.size()).append(" problem(s) during ")
                    .append(safeString(event, "phase")).append(":");
                for (int index = 0; index < problems.size(); index++) {
                    if (!problems.get(index).isJsonObject()) continue;
                    JsonObject problem = problems.get(index).getAsJsonObject();
                    text.append("\n").append(index + 1).append(". ")
                        .append(safeString(problem, "code"))
                        .append(" at ").append(safeString(problem, "location"))
                        .append("\nCause: ").append(safeString(problem, "cause"))
                        .append("\nSuggestion: ").append(safeString(problem, "suggestion"));
                }
                return new CodexChatMessage(CodexChatMessage.Role.ERROR, truncate(text.toString(), 4_800));
            }
            return new CodexChatMessage(
                CodexChatMessage.Role.ERROR,
                "Minecraft client " + safeString(event, "phase") + " failed: "
                    + safeString(event, "message")
            );
        }
        if ("craftgpt.repair_started".equals(type)) {
            return new CodexChatMessage(
                CodexChatMessage.Role.SYSTEM,
                "Minecraft sent validator feedback to Codex. Repair "
                    + safeString(event, "attempt") + "/" + safeString(event, "maximum_attempts")
                    + ": " + safeString(event, "error_code")
            );
        }
        if ("craftgpt.repair_completed".equals(type)) {
            return new CodexChatMessage(
                CodexChatMessage.Role.SYSTEM,
                "Codex returned replacement result for repair " + safeString(event, "attempt")
            );
        }
        if ("thread.started".equals(type)) {
            return new CodexChatMessage(
                CodexChatMessage.Role.SYSTEM,
                "Codex session started: " + safeString(event, "thread_id")
            );
        }
        if ("turn.started".equals(type)) {
            return new CodexChatMessage(CodexChatMessage.Role.SYSTEM, "Codex started working");
        }
        if ("error".equals(type) || "turn.failed".equals(type)) {
            return new CodexChatMessage(
                CodexChatMessage.Role.ERROR,
                firstNonBlank(safeString(event, "message"), event.toString())
            );
        }
        if ("turn.completed".equals(type)) {
            String usage = event.has("usage") ? " Usage: " + event.get("usage") : "";
            return new CodexChatMessage(
                CodexChatMessage.Role.SYSTEM,
                truncate("Codex turn completed." + usage, 1_200)
            );
        }
        if (!"item.completed".equals(type)) return null;

        JsonObject item = event.has("item") && event.get("item").isJsonObject()
            ? event.getAsJsonObject("item")
            : null;
        if (item == null) return null;
        String itemType = safeString(item, "type");
        if ("agent_message".equals(itemType) || "reasoning".equals(itemType)) {
            String text = firstNonBlank(safeString(item, "text"), safeString(item, "summary"));
            return text.isBlank() ? null : new CodexChatMessage(
                CodexChatMessage.Role.CODEX,
                truncate(text, 1_200)
            );
        }

        String toolText = firstNonBlank(
            safeString(item, "command"),
            safeString(item, "name"),
            safeString(item, "path"),
            safeString(item, "status"),
            itemType
        );
        String output = firstNonBlank(
            safeString(item, "aggregated_output"),
            safeString(item, "output")
        );
        return new CodexChatMessage(
            CodexChatMessage.Role.TOOL,
            truncate(toolText + (output.isBlank() ? "" : "\n" + output), 1_200)
        );
    }

    static String eventType(String line) {
        JsonObject event = jsonObject(line);
        return event != null && event.has("type") ? safeString(event, "type") : "output";
    }

    static String sessionId(String line) {
        JsonObject event = jsonObject(line);
        if (event == null || !"thread.started".equals(safeString(event, "type"))) return null;
        String sessionId = safeString(event, "thread_id");
        return validSessionId(sessionId) ? sessionId : null;
    }

    static String summarize(String line) {
        JsonObject event = jsonObject(line);
        if (event == null) return truncate(sanitizeLine(line), 320);
        String type = safeString(event, "type");
        if ("craftgpt.client_error".equals(type)) {
            return truncate(
                type + " phase=" + safeString(event, "phase")
                    + " message=" + safeString(event, "message"),
                320
            );
        }
        if ("craftgpt.repair_started".equals(type)) {
            return truncate(
                type + " attempt=" + safeString(event, "attempt")
                    + "/" + safeString(event, "maximum_attempts")
                    + " error=" + safeString(event, "error_code"),
                320
            );
        }
        if ("craftgpt.repair_completed".equals(type)) {
            return type + " attempt=" + safeString(event, "attempt");
        }
        if ("thread.started".equals(type)) {
            String sessionId = safeString(event, "thread_id");
            return sessionId.isBlank() ? type : type + " session=" + sessionId;
        }
        if ("item.started".equals(type) || "item.completed".equals(type)) {
            JsonObject item = event.has("item") && event.get("item").isJsonObject()
                ? event.getAsJsonObject("item")
                : null;
            String itemType = item == null ? "unknown" : safeString(item, "type");
            String detail = item == null ? "" : firstNonBlank(
                safeString(item, "text"),
                safeString(item, "command"),
                safeString(item, "status")
            );
            return truncate(type + " item=" + itemType + (detail.isBlank() ? "" : " detail=" + detail), 320);
        }
        if ("error".equals(type)) {
            return truncate("error message=" + safeString(event, "message"), 320);
        }
        if ("turn.completed".equals(type) && event.has("usage")) {
            return truncate(type + " usage=" + event.get("usage"), 320);
        }
        return type.isBlank() ? truncate(event.toString(), 320) : type;
    }

    private static JsonObject jsonObject(String line) {
        if (line == null || line.isBlank() || line.charAt(0) != '{') return null;
        try {
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String safeString(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static boolean validSessionId(String value) {
        if (value == null || value.length() > 128) return false;
        return value.matches("[A-Za-z0-9_-]{8,128}");
    }

    private static String sanitizeLine(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }

    private static String truncate(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength - 3) + "...";
    }

    private static void append(Path path, String value) {
        try {
            Files.writeString(path, value, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Diagnostics must never invalidate an otherwise usable build result.
        }
    }
}
