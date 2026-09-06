package dev.craftgpt.validation;

/** Stable explanations and repair suggestions for CraftGPT validation codes. */
public final class ValidationProblemCatalog {
    private ValidationProblemCatalog() {
    }

    public static ValidationProblem problem(String rawCode, String location) {
        String code = rawCode == null || rawCode.isBlank() ? "unknown_failure" : rawCode;
        String primary = primaryCode(code);
        return switch (primary) {
            case "invalid_build_schema" -> problem(code, location,
                "The build object is missing or uses an unsupported schema version.",
                "Return the complete build object with schemaVersion 1 and all required fields.");
            case "invalid_build_summary" -> problem(code, location,
                "The build summary is missing, blank, or longer than the allowed limit.",
                "Provide a short non-empty summary that describes the placeable result.");
            case "invalid_build_palette", "invalid_palette" -> problem(code, location,
                "The palette is missing, empty, too large, or contains an invalid entry.",
                "Return a non-empty bounded palette and make every entry a unique canonical block state.");
            case "invalid_build_operations" -> problem(code, location,
                "The operation list is missing or empty, so the result cannot place a build.",
                "Return at least one safe in-bounds operation that changes the supplied world context.");
            case "too_many_build_operations", "too_many_operations" -> problem(code, location,
                "The result contains more operations than the selected area or configured limit allows.",
                "Simplify the design and keep the operation count at or below the supplied maximum.");
            case "invalid_block_state" -> problem(code, location,
                "The block state is unknown, non-canonical, malformed, or invalid for this Minecraft version.",
                "Use a registered lowercase namespaced block ID and only valid properties sorted alphabetically.");
            case "dangerous_block_state", "unsafe_block" -> problem(code, location,
                "The target or existing block is unsafe for automated preview placement.",
                "Use ordinary non-fluid blocks without block entities, commands, fire, explosives, portals, or protected blocks.");
            case "duplicate_palette_state" -> problem(code, location,
                "The same canonical block state appears more than once in the palette.",
                "Keep the first occurrence, remove the duplicate, and update operation palette indices.");
            case "invalid_build_operation", "invalid_artifact" -> problem(code, location,
                "A required result field or compact operation is missing or malformed.",
                "Return every operation exactly as x,y,z,paletteIndex using non-negative canonical integers.");
            case "build_operation_out_of_bounds", "out_of_bounds" -> problem(code, location,
                "An operation coordinate lies outside the selected width, height, or depth.",
                "Move or remove the operation so every coordinate remains inside the supplied relative bounds.");
            case "invalid_palette_index" -> problem(code, location,
                "An operation references a palette index that does not exist.",
                "Use an index from 0 through palette.length minus 1 and recheck every operation after palette edits.");
            case "duplicate_build_coordinate", "duplicate_position" -> problem(code, location,
                "More than one operation targets the same relative block position.",
                "Keep exactly one final operation for this coordinate and remove the conflicting duplicate.");
            case "no_changes" -> problem(code, location,
                "All operations reproduce blocks that already exist, so accepting the preview would change nothing.",
                "Compare against the supplied context and return at least one safe operation with a different target state.");
            case "invalid_plan_text" -> problem(code, location,
                "A required planning text field is blank, too long, or the total plan text exceeds its limit.",
                "Provide concise non-empty planning text for every required field and shorten oversized content.");
            case "invalid_plan_dimensions" -> problem(code, location,
                "The planned dimensions are missing, non-positive, or larger than the selected area.",
                "Choose positive width, height, and depth values that fit completely inside the supplied area.");
            case "invalid_change_estimate" -> problem(code, location,
                "The estimated block-change count is negative or exceeds the allowed maximum.",
                "Set the estimate between 0 and the supplied maximumBlockChanges value.");
            case "invalid_plan_list" -> problem(code, location,
                "A planning list is missing, too large, or contains a blank or oversized entry.",
                "Return a bounded list of concise non-empty strings for this field.");
            case "invalid_material_roles" -> problem(code, location,
                "A material role, purpose, candidate list, or block candidate is invalid.",
                "Provide complete roles and use only safe canonical Minecraft block states for every candidate.");
            case "missing_builder_settings" -> problem(code, location,
                "The local builder settings were unavailable.",
                "Open CraftGPT settings, select a valid model and reasoning level, then retry.");
            case "invalid_area_context" -> problem(code, location,
                "The captured area context is incomplete or inconsistent.",
                "Select and scan the area again before starting a new generation.");
            case "invalid_project_id", "invalid_plan_version" -> problem(code, location,
                "The local project or plan metadata is missing or malformed.",
                "Start a fresh plan for the current selection so CraftGPT can recreate valid metadata.");
            case "plan_context_mismatch", "selection_mismatch", "stale_context" -> problem(code, location,
                "The selected area or world contents changed after this result was generated.",
                "Rescan the area and regenerate from the fresh context instead of repairing old coordinates.");
            case "plan_content_mismatch" -> problem(code, location,
                "The plan content no longer matches its saved integrity hash.",
                "Reopen or recreate the intended plan version before compiling the build.");
            case "unloaded_area" -> problem(code, location,
                "Part of the selected area is not currently loaded on the server.",
                "Move close enough to load the full selection and retry validation.");
            case "no_selection", "incomplete_selection", "invalid_selection", "dimension_mismatch" -> problem(code, location,
                "The active area selection is missing, incomplete, invalid, or in another dimension.",
                "Complete a valid selection in the current dimension and start the build again.");
            case "rate_limited" -> problem(code, location,
                "Preview validation was requested again before the server cooldown ended.",
                "Wait a moment and retry once instead of sending repeated preview requests.");
            case "invalid_request" -> problem(code, location,
                "The server could not decode the preview request envelope.",
                "Regenerate the preview locally and resend the newly created request.");
            case "internal_error" -> problem(code, location,
                "The server encountered an unexpected validation failure.",
                "Open the detailed log, keep the request folder, and retry once with a fresh area scan.");
            case "portable_patch_base_mismatch" -> problem(code, location,
                "This patch targets a different draft than the one currently active. Nothing was replaced.",
                "Restore the draft used for this request, or request changes again from the current preview.");
            case "inspection_requires_visual_review" -> problem(code, location,
                "The reply requested camera views during a direct build or manual import.",
                "Return repair with a placeable patch, or keep with null plan and build. Request cameras only during visual review.");
            case "missing_review_decision", "invalid_review_decision", "unexpected_review_result" -> problem(code, location,
                "This review did not identify a supported decision or the exact current build hash.",
                "Return keep, repair or inspect and copy currentBuildHash into review.baseBuildHash. Do not reuse an older draft.");
            case "invalid_review_payload" -> problem(code, location,
                "A keep or inspect reply also included a replacement plan or build.",
                "For keep and inspect set plan and build to null. Only repair carries a plan and a coordinate patch.");
            case "invalid_review_camera", "invalid_review_cameras" -> problem(code, location,
                "The requested view count, rotation or crop bounds are invalid.",
                "Use one to four views for inspect, rotations 0 to 3 and inclusive from/to inside the supplied area. Other decisions use cameras=[].");
            case "invalid_review_finding" -> problem(code, location,
                "A review finding is missing its severity, location, evidence or corrective suggestion, or its text is too long.",
                "Provide concise complete findings with severity error, warning or style. Do not invent evidence.");
            case "patch_outside_player_edit_scope" -> problem(code, location,
                "The proposed patch would change blocks outside the region explicitly selected by the player.",
                "Keep every expanded operation inside editScope.from/to. Preserve all outside blocks and materials unchanged.");
            case "missing_patch_base", "invalid_patch_operation", "invalid_patch_palette" -> problem(code, location,
                "The patch is missing its base or contains a malformed, repeated or invalid palette-referencing operation.",
                "Use currentBuild as the base and return unique x,y,z,paletteIndex replacements with indices from the patch palette.");
            case "invalid_component", "invalid_components", "unknown_component_kind", "invalid_door_component" -> problem(code, location,
                "A component has invalid fields, an unsupported kind or incompatible door geometry.",
                "Use a unique id, supported kind, model-selected paletteIndex and inclusive bounds. Doors need matching vertically adjacent cells.");
            case "component_work_limit", "component_operation_limit", "component_palette_limit", "patch_too_large" -> problem(code, location,
                "Expansion or patch merging exceeds the bounded local work, block or palette budget.",
                "Reduce component volume and overlap. Keep the complete merged draft within the supplied block and palette limits.");
            case "roof_too_tall_for_bounds", "inverted_component_bounds", "invalid_component_coordinate", "invalid_roof_axis" -> problem(code, location,
                "The requested component shape does not fit its bounds or uses an invalid axis.",
                "Use increasing relative bounds. For gables reserve floor(cross-span/2) height and select ridge axis x or z.");
            default -> problem(code, location,
                "The validator rejected this value because it does not satisfy a CraftGPT requirement.",
                "Use the code and location to replace the value, then audit the complete result again.");
        };
    }

    private static ValidationProblem problem(
        String code,
        String location,
        String cause,
        String suggestion
    ) {
        return new ValidationProblem(code, location, cause, suggestion);
    }

    private static String primaryCode(String code) {
        int separator = code.indexOf(':');
        return separator < 0 ? code : code.substring(0, separator);
    }
}
