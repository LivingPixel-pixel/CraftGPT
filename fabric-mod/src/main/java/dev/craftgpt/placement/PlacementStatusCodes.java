package dev.craftgpt.placement;

import java.util.Set;

/** Stable protocol and journal states used by both sides of the placement workflow. */
public final class PlacementStatusCodes {
    public static final String PREPARED = "prepared";
    public static final String PLACING = "placing";
    public static final String PLACED = "placed";
    public static final String PLACED_WITH_CONFLICTS = "placed_with_conflicts";
    public static final String UNDOING = "undoing";
    public static final String UNDONE = "undone";
    public static final String UNDONE_WITH_CONFLICTS = "undone_with_conflicts";
    public static final String REDOING = "redoing";
    public static final String REDONE = "redone";
    public static final String REDONE_WITH_CONFLICTS = "redone_with_conflicts";
    public static final String INTERRUPTED = "interrupted";

    public static final String NO_PLACEMENT = "no_placement";
    public static final String FORBIDDEN = "forbidden";
    public static final String BUSY = "busy";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_BUILD = "invalid_build";
    public static final String BLOCK_ENTITY_UNSAFE = "block_entity_unsafe";
    public static final String PHYSICS_UNSAFE = "physics_unsafe";
    public static final String STALE_CONTEXT = "stale_context";
    public static final String UNLOADED_AREA = "unloaded_area";
    public static final String STORAGE_ERROR = "storage_error";
    public static final String DIMENSION_UNAVAILABLE = "dimension_unavailable";
    public static final String ALREADY_UNDONE = "already_undone";
    public static final String NOT_REDOABLE = "not_redoable";
    public static final String NEWER_OVERLAP = "newer_overlap";
    public static final String BLOCK_ENTITY_LIMIT = "block_entity_limit";
    public static final String EXPORTED = "exported";
    public static final String IMPORTED = "imported";
    public static final String PRUNED = "pruned";
    public static final String ARCHIVE_MISSING = "archive_missing";
    public static final String JOURNAL_EXISTS = "journal_exists";

    private static final Set<String> JOURNAL_STATES = Set.of(
        PREPARED,
        PLACING,
        PLACED,
        PLACED_WITH_CONFLICTS,
        UNDOING,
        UNDONE,
        UNDONE_WITH_CONFLICTS,
        REDOING,
        REDONE,
        REDONE_WITH_CONFLICTS,
        INTERRUPTED
    );

    private PlacementStatusCodes() {
    }

    public static boolean isJournalState(String value) {
        return JOURNAL_STATES.contains(value);
    }

    public static boolean isBusy(String value) {
        return PLACING.equals(value) || UNDOING.equals(value) || REDOING.equals(value);
    }

    public static boolean isPlaced(String value) {
        return PLACED.equals(value)
            || PLACED_WITH_CONFLICTS.equals(value)
            || REDONE.equals(value)
            || REDONE_WITH_CONFLICTS.equals(value)
            || INTERRUPTED.equals(value)
            || PLACING.equals(value)
            || REDOING.equals(value);
    }

    public static boolean isUndone(String value) {
        return UNDONE.equals(value) || UNDONE_WITH_CONFLICTS.equals(value);
    }
}
