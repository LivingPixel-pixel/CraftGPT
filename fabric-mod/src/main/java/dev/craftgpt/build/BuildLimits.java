package dev.craftgpt.build;

import java.util.Set;

/**
 * Protocol and safety limits shared by the client compiler and server verifier.
 */
public final class BuildLimits {
    public static final int SCHEMA_VERSION = 1;
    public static final int HARD_MAX_OPERATIONS = 10_000;
    public static final int MAX_PALETTE_ENTRIES = 256;
    public static final int MAX_BLOCK_STATE_LENGTH = 256;
    public static final int MAX_STATE_PROPERTIES = 32;
    public static final int MAX_OPERATION_TEXT_LENGTH = 64;
    public static final int MAX_SUMMARY_LENGTH = 4_096;
    public static final int MAX_ARTIFACT_JSON_BYTES = 2_000_000;

    private static final Set<String> DANGEROUS_BLOCK_IDS = Set.of(
        "minecraft:anvil",
        "minecraft:barrier",
        "minecraft:bedrock",
        "minecraft:black_concrete_powder",
        "minecraft:blue_concrete_powder",
        "minecraft:brown_concrete_powder",
        "minecraft:cave_air",
        "minecraft:chain_command_block",
        "minecraft:chipped_anvil",
        "minecraft:command_block",
        "minecraft:cyan_concrete_powder",
        "minecraft:damaged_anvil",
        "minecraft:dragon_egg",
        "minecraft:end_gateway",
        "minecraft:end_portal",
        "minecraft:end_portal_frame",
        "minecraft:fire",
        "minecraft:gray_concrete_powder",
        "minecraft:green_concrete_powder",
        "minecraft:gravel",
        "minecraft:jigsaw",
        "minecraft:lava",
        "minecraft:light",
        "minecraft:light_blue_concrete_powder",
        "minecraft:light_gray_concrete_powder",
        "minecraft:lime_concrete_powder",
        "minecraft:magenta_concrete_powder",
        "minecraft:moving_piston",
        "minecraft:nether_portal",
        "minecraft:orange_concrete_powder",
        "minecraft:pink_concrete_powder",
        "minecraft:piston_head",
        "minecraft:purple_concrete_powder",
        "minecraft:repeating_command_block",
        "minecraft:red_concrete_powder",
        "minecraft:red_sand",
        "minecraft:sand",
        "minecraft:soul_fire",
        "minecraft:spawner",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:suspicious_gravel",
        "minecraft:suspicious_sand",
        "minecraft:test_block",
        "minecraft:test_instance_block",
        "minecraft:tnt",
        "minecraft:trial_spawner",
        "minecraft:vault",
        "minecraft:void_air",
        "minecraft:water",
        "minecraft:white_concrete_powder",
        "minecraft:yellow_concrete_powder"
    );

    private BuildLimits() {
    }

    public static int effectiveMaximumOperations(int configuredMaximum, long areaVolume) {
        if (configuredMaximum <= 0 || areaVolume <= 0) {
            throw new IllegalArgumentException("Build limits must be positive");
        }
        return (int) Math.min(Math.min(configuredMaximum, HARD_MAX_OPERATIONS), areaVolume);
    }

    public static boolean isDangerousBlockId(String blockId) {
        return DANGEROUS_BLOCK_IDS.contains(blockId);
    }

    public static boolean isDangerousState(String canonicalBlockState) {
        if (canonicalBlockState == null) {
            return true;
        }
        int propertiesStart = canonicalBlockState.indexOf('[');
        String blockId = propertiesStart < 0
            ? canonicalBlockState
            : canonicalBlockState.substring(0, propertiesStart);
        return isDangerousBlockId(blockId)
            || canonicalBlockState.contains("waterlogged=true");
    }

    public static Set<String> dangerousBlockIds() {
        return DANGEROUS_BLOCK_IDS;
    }
}
