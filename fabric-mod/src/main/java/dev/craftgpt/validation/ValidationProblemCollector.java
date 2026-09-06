package dev.craftgpt.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded collector that avoids repeating the same code at the same location. */
public final class ValidationProblemCollector {
    private final List<ValidationProblem> problems = new ArrayList<>();
    private final Set<String> identities = new HashSet<>();

    public void add(String code, String location) {
        add(ValidationProblemCatalog.problem(code, location));
    }

    public void add(ValidationProblem problem) {
        if (problem == null || problems.size() >= ValidationProblem.MAX_PROBLEMS) return;
        String identity = problem.code() + "\n" + problem.location();
        if (identities.add(identity)) problems.add(problem);
    }

    public boolean isEmpty() {
        return problems.isEmpty();
    }

    public List<ValidationProblem> problems() {
        return List.copyOf(problems);
    }
}
