package dev.craftgpt.client.build.api;

import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.planning.model.PlanVersion;
import dev.craftgpt.client.planning.storage.PlanContentHasher;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.context.AreaContextValidator;
import dev.craftgpt.validation.ValidationProblemCatalog;
import dev.craftgpt.validation.ValidationProblemCollector;
import net.minecraft.resources.Identifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BuilderResponseValidator {
    private static final Pattern DECIMAL_INTEGER = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Pattern PROPERTY_VALUE = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern PLAN_VERSION_ID = Pattern.compile("v[1-9][0-9]*");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private BuilderResponseValidator() {
    }

    public static CompiledBuildArtifact validateAndCompile(
        BuildDraft draft,
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        BuilderRequestSettings settings
    ) {
        validateInputs(context, projectId, planVersion, settings);
        return compileValidated(
            draft,
            context,
            projectId,
            planVersion,
            settings.model(),
            settings.reasoningLevel(),
            settings.maximumOperations()
        );
    }

    public static CompiledBuildArtifact validateAndCompilePortable(
        BuildDraft draft,
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        int maximumOperations
    ) {
        validatePlanInputs(context, projectId, planVersion);
        return compileValidated(
            draft,
            context,
            projectId,
            planVersion,
            "craftgpt-building-skill",
            "subscription",
            maximumOperations
        );
    }

    public static void validateDraft(BuildDraft draft, AreaContext context, int maximumOperations) {
        validateDraftAndParse(draft, context, maximumOperations);
    }

    private static CompiledBuildArtifact compileValidated(
        BuildDraft draft,
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        String model,
        String reasoningLevel,
        int maximumOperations
    ) {
        List<BuildOperation> operations = validateDraftAndParse(draft, context, maximumOperations);

        return new CompiledBuildArtifact(
            BuildLimits.SCHEMA_VERSION,
            UUID.randomUUID().toString(),
            context.selectionId(),
            projectId,
            planVersion.id(),
            planVersion.contentHash(),
            AreaContextHasher.sha256(context),
            Instant.now().toString(),
            model,
            reasoningLevel,
            draft.summary(),
            draft.palette(),
            operations
        );
    }

    private static List<BuildOperation> validateDraftAndParse(
        BuildDraft draft,
        AreaContext context,
        int configuredMaximumOperations
    ) {
        try {
            AreaContextValidator.validate(context);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BuilderException("invalid_area_context", exception);
        }
        if (draft == null) {
            throw new BuilderException("invalid_build_schema");
        }
        ValidationProblemCollector problems = new ValidationProblemCollector();
        if (draft.schemaVersion() != BuildLimits.SCHEMA_VERSION) {
            problems.add("invalid_build_schema", "build.schemaVersion");
        }
        if (draft.summary() == null
            || draft.summary().isBlank()
            || draft.summary().length() > BuildLimits.MAX_SUMMARY_LENGTH) {
            problems.add("invalid_build_summary", "build.summary");
        }
        if (draft.palette() == null || draft.palette().size() > BuildLimits.MAX_PALETTE_ENTRIES) {
            problems.add("invalid_build_palette", "build.palette");
        }
        if (draft.operations() == null || draft.operations().isEmpty()) {
            problems.add("invalid_build_operations", "build.operations");
        }

        int maximumOperations = BuildLimits.effectiveMaximumOperations(
            configuredMaximumOperations,
            context.volume()
        );
        if (draft.operations() != null && draft.operations().size() > maximumOperations) {
            problems.add("too_many_build_operations", "build.operations");
        }
        if (draft.palette() != null && draft.palette().isEmpty()) {
            problems.add("invalid_build_palette", "build.palette");
        }

        validatePalette(draft.palette(), problems);
        List<BuildOperation> operations = parseOperations(
            draft.operations(),
            draft.palette() == null ? 0 : draft.palette().size(),
            context,
            problems
        );
        if (!problems.isEmpty()) throw new BuilderException(problems.problems());
        return operations;
    }

    public static void validateInputs(
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        BuilderRequestSettings settings
    ) {
        if (settings == null) {
            throw new BuilderException("missing_builder_settings");
        }
        settings.validate();
        validatePlanInputs(context, projectId, planVersion);
    }

    private static void validatePlanInputs(
        AreaContext context,
        String projectId,
        PlanVersion planVersion
    ) {
        try {
            AreaContextValidator.validate(context);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BuilderException("invalid_area_context", exception);
        }
        validateUuid(projectId, "invalid_project_id");
        if (planVersion == null
            || planVersion.schemaVersion() != 1
            || planVersion.id() == null
            || !PLAN_VERSION_ID.matcher(planVersion.id()).matches()
            || planVersion.intention() == null) {
            throw new BuilderException("invalid_plan_version");
        }

        String actualContextHash = AreaContextHasher.sha256(context);
        if (planVersion.contextHash() == null
            || !SHA_256.matcher(planVersion.contextHash()).matches()
            || !actualContextHash.equals(planVersion.contextHash())) {
            throw new BuilderException("plan_context_mismatch");
        }
        String actualContentHash = PlanContentHasher.sha256(planVersion.intention());
        if (planVersion.contentHash() == null
            || !SHA_256.matcher(planVersion.contentHash()).matches()
            || !actualContentHash.equals(planVersion.contentHash())) {
            throw new BuilderException("plan_content_mismatch");
        }
    }

    private static void validatePalette(
        List<String> palette,
        ValidationProblemCollector problems
    ) {
        if (palette == null) return;
        Set<String> states = new HashSet<>();
        for (int index = 0; index < palette.size(); index++) {
            String state = palette.get(index);
            try {
                validateCanonicalBlockState(state);
            } catch (BuilderException exception) {
                problems.add(ValidationProblemCatalog.problem(
                    exception.getMessage(),
                    "build.palette[" + index + "]"
                ));
            }
            if (state != null && !states.add(state)) {
                problems.add("duplicate_palette_state", "build.palette[" + index + "]");
            }
        }
    }

    public static void validateCanonicalBlockState(String state) {
        if (state == null || state.isBlank() || state.length() > BuildLimits.MAX_BLOCK_STATE_LENGTH) {
            throw new BuilderException("invalid_block_state");
        }

        int propertiesStart = state.indexOf('[');
        String blockId;
        if (propertiesStart < 0) {
            if (state.indexOf(']') >= 0) {
                throw new BuilderException("invalid_block_state");
            }
            blockId = state;
        } else {
            if (propertiesStart == 0
                || !state.endsWith("]")
                || state.indexOf('[', propertiesStart + 1) >= 0
                || state.indexOf(']', propertiesStart) != state.length() - 1) {
                throw new BuilderException("invalid_block_state");
            }
            blockId = state.substring(0, propertiesStart);
            validateProperties(state.substring(propertiesStart + 1, state.length() - 1));
        }

        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !identifier.toString().equals(blockId)) {
            throw new BuilderException("invalid_block_state");
        }
        if (BuildLimits.isDangerousState(state)) {
            throw new BuilderException("dangerous_block_state");
        }
    }

    private static void validateProperties(String propertiesText) {
        if (propertiesText.isEmpty()) {
            throw new BuilderException("invalid_block_state");
        }
        String[] properties = propertiesText.split(",", -1);
        if (properties.length > BuildLimits.MAX_STATE_PROPERTIES) {
            throw new BuilderException("invalid_block_state");
        }
        String previousName = null;
        for (String property : properties) {
            int separator = property.indexOf('=');
            if (separator <= 0
                || separator != property.lastIndexOf('=')
                || separator == property.length() - 1) {
                throw new BuilderException("invalid_block_state");
            }
            String name = property.substring(0, separator);
            String value = property.substring(separator + 1);
            if (!PROPERTY_NAME.matcher(name).matches()
                || !PROPERTY_VALUE.matcher(value).matches()
                || (previousName != null && previousName.compareTo(name) >= 0)) {
                throw new BuilderException("invalid_block_state");
            }
            previousName = name;
        }
    }

    private static List<BuildOperation> parseOperations(
        List<String> compactOperations,
        int paletteSize,
        AreaContext context,
        ValidationProblemCollector problems
    ) {
        if (compactOperations == null) return List.of();
        List<BuildOperation> operations = new ArrayList<>(compactOperations.size());
        Set<RelativeCoordinate> coordinates = new HashSet<>();
        for (int index = 0; index < compactOperations.size(); index++) {
            String compact = compactOperations.get(index);
            String location = "build.operations[" + index + "]";
            if (compact == null
                || compact.isEmpty()
                || compact.length() > BuildLimits.MAX_OPERATION_TEXT_LENGTH) {
                problems.add("invalid_build_operation", location);
                continue;
            }
            String[] values = compact.split(",", -1);
            if (values.length != 4) {
                problems.add("invalid_build_operation", location);
                continue;
            }
            int[] parsed = new int[4];
            boolean validIntegers = true;
            for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
                try {
                    parsed[valueIndex] = parseCanonicalInteger(values[valueIndex]);
                } catch (BuilderException exception) {
                    validIntegers = false;
                }
            }
            if (!validIntegers) {
                problems.add("invalid_build_operation", location);
                continue;
            }
            int x = parsed[0];
            int y = parsed[1];
            int z = parsed[2];
            int paletteIndex = parsed[3];
            boolean valid = true;
            if (x >= context.width() || y >= context.height() || z >= context.depth()) {
                problems.add("build_operation_out_of_bounds", location);
                valid = false;
            }
            if (paletteIndex >= paletteSize) {
                problems.add("invalid_palette_index", location);
                valid = false;
            }
            if (!coordinates.add(new RelativeCoordinate(x, y, z))) {
                problems.add("duplicate_build_coordinate", location);
                valid = false;
            }
            if (valid) operations.add(new BuildOperation(x, y, z, paletteIndex));
        }
        return List.copyOf(operations);
    }

    private static int parseCanonicalInteger(String text) {
        if (!DECIMAL_INTEGER.matcher(text).matches()) {
            throw new BuilderException("invalid_build_operation");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new BuilderException("invalid_build_operation", exception);
        }
    }

    private static void validateUuid(String value, String error) {
        try {
            UUID parsed = UUID.fromString(value == null ? "" : value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("UUID is not canonical");
            }
        } catch (IllegalArgumentException exception) {
            throw new BuilderException(error, exception);
        }
    }

    private record RelativeCoordinate(int x, int y, int z) {
    }
}
