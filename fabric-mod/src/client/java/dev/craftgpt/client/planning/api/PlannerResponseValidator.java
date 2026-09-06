package dev.craftgpt.client.planning.api;

import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.client.build.api.BuilderResponseValidator;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.validation.ValidationProblemCatalog;
import dev.craftgpt.validation.ValidationProblemCollector;

import java.util.List;

public final class PlannerResponseValidator {
    private static final int MAX_LIST_ITEMS = 48;
    private static final int MAX_TEXT_LENGTH = 16_384;
    private static final int MAX_BLOCK_ID_LENGTH = 256;
    private static final int MAX_TOTAL_TEXT_LENGTH = 128 * 1_024;

    private PlannerResponseValidator() {
    }

    public static void validate(IntentionSpec plan, AreaContext context, int maximumBlockChanges) {
        if (plan == null) {
            throw new PlannerException("invalid_plan_text");
        }
        ValidationProblemCollector problems = new ValidationProblemCollector();
        validateText(plan.title(), "plan.title", problems);
        validateText(plan.summary(), "plan.summary", problems);
        validateText(plan.style(), "plan.style", problems);
        validateText(plan.orientation(), "plan.orientation", problems);
        validateText(plan.designRationale(), "plan.designRationale", problems);
        validateText(plan.implementationBrief(), "plan.implementationBrief", problems);
        if (plan.targetDimensions() == null
            || plan.targetDimensions().width() <= 0
            || plan.targetDimensions().height() <= 0
            || plan.targetDimensions().depth() <= 0
            || plan.targetDimensions().width() > context.width()
            || plan.targetDimensions().height() > context.height()
            || plan.targetDimensions().depth() > context.depth()) {
            problems.add("invalid_plan_dimensions", "plan.targetDimensions");
        }
        if (plan.estimatedBlockChanges() < 0 || plan.estimatedBlockChanges() > maximumBlockChanges) {
            problems.add("invalid_change_estimate", "plan.estimatedBlockChanges");
        }

        validateList(plan.goals(), "plan.goals", problems);
        validateList(plan.requiredFeatures(), "plan.requiredFeatures", problems);
        validateList(plan.preferredFeatures(), "plan.preferredFeatures", problems);
        validateList(plan.constraints(), "plan.constraints", problems);
        validateList(plan.avoid(), "plan.avoid", problems);
        validateList(plan.assumptions(), "plan.assumptions", problems);
        if (plan.materialRoles() == null || plan.materialRoles().size() > MAX_LIST_ITEMS) {
            problems.add("invalid_material_roles", "plan.materialRoles");
        } else {
            for (int roleIndex = 0; roleIndex < plan.materialRoles().size(); roleIndex++) {
                MaterialRole role = plan.materialRoles().get(roleIndex);
                String location = "plan.materialRoles[" + roleIndex + "]";
                if (role == null) {
                    problems.add(
                        "invalid_material_roles:role=" + roleIndex + ":invalid_metadata",
                        location
                    );
                    continue;
                }
                if (blank(role.role()) || blank(role.purpose())) {
                    problems.add(
                        "invalid_material_roles:role=" + roleIndex + ":invalid_metadata",
                        location
                    );
                }
                validateMaterialCandidates(role.blockCandidates(), roleIndex, problems);
            }
        }
        if (totalTextLength(plan) > MAX_TOTAL_TEXT_LENGTH) {
            problems.add("invalid_plan_text", "plan.totalTextLength");
        }
        if (!problems.isEmpty()) throw new PlannerException(problems.problems());
    }

    private static void validateText(
        String value,
        String location,
        ValidationProblemCollector problems
    ) {
        if (blank(value)) problems.add("invalid_plan_text", location);
    }

    private static void validateList(
        List<String> values,
        String location,
        ValidationProblemCollector problems
    ) {
        if (values == null || values.size() > MAX_LIST_ITEMS) {
            problems.add("invalid_plan_list", location);
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            if (blank(values.get(index))) {
                problems.add("invalid_plan_list", location + "[" + index + "]");
            }
        }
    }

    private static void validateMaterialCandidates(
        List<String> values,
        int roleIndex,
        ValidationProblemCollector problems
    ) {
        if (values == null || values.size() > MAX_LIST_ITEMS) {
            problems.add(
                "invalid_material_roles:role=" + roleIndex + ":invalid_candidate_list",
                "plan.materialRoles[" + roleIndex + "].blockCandidates"
            );
            return;
        }
        for (int candidateIndex = 0; candidateIndex < values.size(); candidateIndex++) {
            String value = values.get(candidateIndex);
            String location = "plan.materialRoles[" + roleIndex + "].blockCandidates["
                + candidateIndex + "]";
            if (blank(value) || value.length() > MAX_BLOCK_ID_LENGTH) {
                problems.add(ValidationProblemCatalog.problem(
                    materialCandidateCode(roleIndex, candidateIndex, "invalid_block_state"),
                    location
                ));
                continue;
            }
            try {
                BuilderResponseValidator.validateCanonicalBlockState(value);
            } catch (BuilderException exception) {
                problems.add(ValidationProblemCatalog.problem(
                    materialCandidateCode(roleIndex, candidateIndex, exception.getMessage()),
                    location
                ));
            }
        }
    }

    private static String materialCandidateCode(
        int roleIndex,
        int candidateIndex,
        String reason
    ) {
        return "invalid_material_roles:role=" + roleIndex
            + ":candidate=" + candidateIndex
            + ":" + (reason == null || reason.isBlank() ? "invalid_block_state" : reason);
    }

    private static long totalTextLength(IntentionSpec plan) {
        long total = textLength(plan.title())
            + textLength(plan.summary())
            + textLength(plan.style())
            + textLength(plan.orientation())
            + textLength(plan.designRationale())
            + textLength(plan.implementationBrief());
        total += listTextLength(plan.goals());
        total += listTextLength(plan.requiredFeatures());
        total += listTextLength(plan.preferredFeatures());
        total += listTextLength(plan.constraints());
        total += listTextLength(plan.avoid());
        total += listTextLength(plan.assumptions());
        if (plan.materialRoles() != null) {
            for (MaterialRole role : plan.materialRoles()) {
                if (role == null) continue;
                total += textLength(role.role()) + textLength(role.purpose());
                total += listTextLength(role.blockCandidates());
            }
        }
        return total;
    }

    private static long listTextLength(List<String> values) {
        return values == null ? 0 : values.stream().mapToLong(PlannerResponseValidator::textLength).sum();
    }

    private static int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH;
    }
}
