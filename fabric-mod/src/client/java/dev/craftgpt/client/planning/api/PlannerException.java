package dev.craftgpt.client.planning.api;

import dev.craftgpt.validation.DetailedValidationFailure;
import dev.craftgpt.validation.ValidationProblem;
import dev.craftgpt.validation.ValidationProblemCatalog;

import java.util.List;

public final class PlannerException extends RuntimeException implements DetailedValidationFailure {
    private final List<ValidationProblem> problems;

    public PlannerException(String message) {
        super(message);
        problems = List.of(ValidationProblemCatalog.problem(message, "plan"));
    }

    public PlannerException(String message, Throwable cause) {
        super(message, cause);
        problems = List.of(ValidationProblemCatalog.problem(message, "plan"));
    }

    public PlannerException(List<ValidationProblem> problems) {
        super(firstCode(problems));
        if (problems == null || problems.isEmpty()) {
            throw new IllegalArgumentException("Planner problems cannot be empty");
        }
        this.problems = List.copyOf(problems);
    }

    @Override
    public List<ValidationProblem> problems() {
        return problems;
    }

    private static String firstCode(List<ValidationProblem> problems) {
        return problems == null || problems.isEmpty() ? "unknown_failure" : problems.getFirst().code();
    }
}
