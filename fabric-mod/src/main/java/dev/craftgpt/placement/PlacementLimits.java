package dev.craftgpt.placement;

import dev.craftgpt.build.BuildLimits;

/** Shared hard limits for server-authoritative placement and recovery. */
public final class PlacementLimits {
    public static final int SCHEMA_VERSION = 1;
    public static final int OPERATIONS_PER_JOB_TICK = 128;
    public static final int OPERATIONS_PER_SERVER_TICK = 512;
    public static final int MAX_CHANGES = BuildLimits.HARD_MAX_OPERATIONS;
    public static final int MAX_JOURNAL_JSON_BYTES = 8_000_000;
    public static final int MAX_DIMENSION_LENGTH = 256;
    public static final int MAX_HISTORY_ENTRIES = 50;
    public static final int DEFAULT_RETENTION_COUNT = 25;
    public static final int MIN_RETENTION_COUNT = 5;
    public static final int MAX_RETENTION_COUNT = 50;
    public static final int MAX_ARCHIVE_JSON_BYTES = 10_500_000;
    public static final int MAX_BLOCK_ENTITY_NBT_BYTES = 65_536;
    public static final int MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES = 2_000_000;
    public static final int MAX_BLOCK_ENTITY_CHANGES = 128;

    private PlacementLimits() {
    }
}
