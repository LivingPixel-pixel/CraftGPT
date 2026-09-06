package dev.craftgpt.validation;

import java.util.List;

/** Implemented by failures that can expose every finding from one validation pass. */
public interface DetailedValidationFailure {
    List<ValidationProblem> problems();
}
