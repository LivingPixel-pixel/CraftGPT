package dev.craftgpt.build.server;

import dev.craftgpt.validation.ValidationProblem;
import dev.craftgpt.validation.ValidationProblemCatalog;

import java.util.List;

/** Result of server-side preview validation. Validation never places blocks. */
public record BuildPreviewValidationResult(
    boolean accepted,
    String code,
    int actualChanges,
    List<ValidationProblem> problems
) {
    public static final String OK = "ok";
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String NO_SELECTION = "no_selection";
    public static final String INCOMPLETE_SELECTION = "incomplete_selection";
    public static final String INVALID_SELECTION = "invalid_selection";
    public static final String SELECTION_MISMATCH = "selection_mismatch";
    public static final String DIMENSION_MISMATCH = "dimension_mismatch";
    public static final String UNLOADED_AREA = "unloaded_area";
    public static final String STALE_CONTEXT = "stale_context";
    public static final String INVALID_ARTIFACT = "invalid_artifact";
    public static final String TOO_MANY_OPERATIONS = "too_many_operations";
    public static final String INVALID_PALETTE = "invalid_palette";
    public static final String INVALID_BLOCK_STATE = "invalid_block_state";
    public static final String UNSAFE_BLOCK = "unsafe_block";
    public static final String OUT_OF_BOUNDS = "out_of_bounds";
    public static final String DUPLICATE_POSITION = "duplicate_position";
    public static final String INVALID_PALETTE_INDEX = "invalid_palette_index";
    public static final String NO_CHANGES = "no_changes";
    public static final String INTERNAL_ERROR = "internal_error";

    public BuildPreviewValidationResult {
        problems = problems == null ? List.of() : List.copyOf(problems);
        if (code == null
            || code.isBlank()
            || actualChanges < 0
            || accepted != OK.equals(code)
            || (!accepted && actualChanges != 0)
            || problems.size() > ValidationProblem.MAX_PROBLEMS
            || (accepted && !problems.isEmpty())
            || (!accepted && problems.isEmpty())) {
            throw new IllegalArgumentException("Invalid build preview validation result");
        }
    }

    public static BuildPreviewValidationResult accepted(int actualChanges) {
        return new BuildPreviewValidationResult(true, OK, actualChanges, List.of());
    }

    public static BuildPreviewValidationResult rejected(String code) {
        return rejected(List.of(ValidationProblemCatalog.problem(code, "serverValidation")));
    }

    public static BuildPreviewValidationResult rejected(List<ValidationProblem> problems) {
        if (problems == null || problems.isEmpty()) {
            throw new IllegalArgumentException("Rejected validation requires at least one problem");
        }
        List<ValidationProblem> bounded = problems.stream()
            .limit(ValidationProblem.MAX_PROBLEMS)
            .toList();
        return new BuildPreviewValidationResult(false, bounded.getFirst().code(), 0, bounded);
    }
}
