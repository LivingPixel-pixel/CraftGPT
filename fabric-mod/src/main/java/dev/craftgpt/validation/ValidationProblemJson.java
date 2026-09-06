package dev.craftgpt.validation;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/** Bounded JSON transport for validation findings sent from server to client. */
public final class ValidationProblemJson {
    public static final int MAX_JSON_LENGTH = 32_768;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<ValidationProblem>>() { }.getType();

    private ValidationProblemJson() {
    }

    public static String encode(List<ValidationProblem> problems) {
        List<ValidationProblem> safe = problems == null
            ? List.of()
            : problems.stream().filter(java.util.Objects::nonNull)
                .limit(ValidationProblem.MAX_PROBLEMS).toList();
        String json = GSON.toJson(safe, LIST_TYPE);
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("Validation diagnostics are too large");
        }
        return json;
    }

    public static List<ValidationProblem> decode(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("Invalid validation diagnostics");
        }
        try {
            List<ValidationProblem> decoded = GSON.fromJson(json, LIST_TYPE);
            if (decoded == null || decoded.size() > ValidationProblem.MAX_PROBLEMS
                || decoded.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Invalid validation diagnostics");
            }
            return List.copyOf(decoded);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Invalid validation diagnostics", exception);
        }
    }

    public static List<ValidationProblem> fromThrowable(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (current instanceof DetailedValidationFailure detailed
                && detailed.problems() != null
                && !detailed.problems().isEmpty()) {
                return detailed.problems().stream().limit(ValidationProblem.MAX_PROBLEMS).toList();
            }
            current = current.getCause();
        }
        String message = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
            ? "unknown_failure"
            : failure.getMessage();
        return List.of(ValidationProblemCatalog.problem(message, "result"));
    }
}
