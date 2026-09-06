package dev.craftgpt.client.config;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.placement.PlacementLimits;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class CraftGptConfig {
    private static final Path CONFIG_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("craftgpt");
    private static final Path CONFIG_FILE = CONFIG_DIRECTORY.resolve("client.properties");
    private static final Path SECRETS_FILE = CONFIG_DIRECTORY.resolve("client-secrets.properties");

    private String apiEndpoint = "https://api.openai.com/v1/responses";
    private String apiKey = "";
    private String planningModel = "gpt-5.6-terra";
    private String builderModel = "gpt-5.6-luna";
    private String codexModel = "gpt-5.6-terra";
    private ReasoningLevel reasoningLevel = ReasoningLevel.MEDIUM;
    private ReasoningLevel builderReasoningLevel = ReasoningLevel.LOW;
    private ReasoningLevel codexReasoningLevel = ReasoningLevel.HIGH;
    private int codexGenerationEffort = CodexGenerationEffort.DEFAULT;
    private ContextMode contextMode = ContextMode.COMPRESSED;
    private int maxFullContextBlocks = 8_192;
    private int maxAreaVolume = 32_768;
    private int maxBlockChanges = 5_000;
    private boolean allowBlockEntityReplacement;
    private int recoveryRetentionCount = PlacementLimits.DEFAULT_RETENTION_COUNT;

    public static CraftGptConfig load() {
        CraftGptConfig config = new CraftGptConfig();
        Properties properties = readProperties(CONFIG_FILE, "client settings");
        config.apiEndpoint = properties.getProperty("apiEndpoint", config.apiEndpoint).trim();
        config.planningModel = properties.getProperty("planningModel", config.planningModel).trim();
        config.builderModel = properties.getProperty("builderModel", config.builderModel).trim();
        config.codexModel = properties.getProperty("codexModel", config.codexModel).trim();
        config.reasoningLevel = ReasoningLevel.parse(properties.getProperty("reasoningLevel", "medium"));
        config.builderReasoningLevel = ReasoningLevel.parse(
            properties.getProperty("builderReasoningLevel", "low")
        );
        config.codexReasoningLevel = ReasoningLevel.parse(
            properties.getProperty("codexReasoningLevel", "high")
        );
        if (config.codexReasoningLevel == ReasoningLevel.NONE) {
            config.codexReasoningLevel = ReasoningLevel.LOW;
        }
        config.codexGenerationEffort = CodexGenerationEffort.clamp(
            parsePositiveInt(
                properties.getProperty("codexGenerationEffort"),
                CodexGenerationEffort.DEFAULT
            )
        );
        config.contextMode = ContextMode.parse(properties.getProperty("contextMode", "compressed"));
        config.maxFullContextBlocks = Math.min(
            parsePositiveInt(properties.getProperty("maxFullContextBlocks"), config.maxFullContextBlocks),
            32_768
        );
        config.maxAreaVolume = parsePositiveInt(properties.getProperty("maxAreaVolume"), config.maxAreaVolume);
        config.maxBlockChanges = Math.min(
            parsePositiveInt(properties.getProperty("maxBlockChanges"), config.maxBlockChanges),
            BuildLimits.HARD_MAX_OPERATIONS
        );
        config.allowBlockEntityReplacement = Boolean.parseBoolean(
            properties.getProperty("allowBlockEntityReplacement", "false")
        );
        config.recoveryRetentionCount = Math.max(
            PlacementLimits.MIN_RETENTION_COUNT,
            Math.min(PlacementLimits.MAX_RETENTION_COUNT,
                parsePositiveInt(properties.getProperty("recoveryRetentionCount"), config.recoveryRetentionCount))
        );

        // Read a legacy key from client.properties only as a migration fallback.
        String legacyApiKey = properties.getProperty("apiKey", "");
        Properties secrets = readProperties(SECRETS_FILE, "client secrets");
        config.apiKey = secrets.getProperty("apiKey", legacyApiKey);
        return config;
    }

    public void save() throws IOException {
        Files.createDirectories(CONFIG_DIRECTORY);

        Properties properties = new Properties();
        properties.setProperty("apiEndpoint", apiEndpoint);
        properties.setProperty("planningModel", planningModel);
        properties.setProperty("builderModel", builderModel);
        properties.setProperty("codexModel", codexModel);
        properties.setProperty("reasoningLevel", reasoningLevel.serializedName());
        properties.setProperty("builderReasoningLevel", builderReasoningLevel.serializedName());
        properties.setProperty("codexReasoningLevel", codexReasoningLevel.serializedName());
        properties.setProperty("codexGenerationEffort", Integer.toString(codexGenerationEffort));
        properties.setProperty("contextMode", contextMode.serializedName());
        properties.setProperty("maxFullContextBlocks", Integer.toString(maxFullContextBlocks));
        properties.setProperty("maxAreaVolume", Integer.toString(maxAreaVolume));
        properties.setProperty("maxBlockChanges", Integer.toString(maxBlockChanges));
        properties.setProperty(
            "allowBlockEntityReplacement",
            Boolean.toString(allowBlockEntityReplacement)
        );
        properties.setProperty("recoveryRetentionCount", Integer.toString(recoveryRetentionCount));

        Properties secrets = new Properties();
        secrets.setProperty("apiKey", apiKey);

        // Commit the secret first so migration can never remove the only persisted key.
        writePropertiesAtomically(SECRETS_FILE, secrets, "CraftGPT client secret - keep this file private");
        writePropertiesAtomically(CONFIG_FILE, properties, "CraftGPT client settings");
    }

    private static Properties readProperties(Path path, String description) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            CraftGptMod.LOGGER.error("Could not load CraftGPT {}", description, exception);
        }
        return properties;
    }

    private static void writePropertiesAtomically(
        Path target,
        Properties properties,
        String comment
    ) throws IOException {
        Path temporaryFile = Files.createTempFile(CONFIG_DIRECTORY, "." + target.getFileName() + ".", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, comment);
            }
            try {
                Files.move(
                    temporaryFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public String apiEndpoint() { return apiEndpoint; }
    public void apiEndpoint(String value) { apiEndpoint = value.trim(); }
    public String apiKey() { return apiKey; }
    public void apiKey(String value) { apiKey = value; }
    public String planningModel() { return planningModel; }
    public void planningModel(String value) { planningModel = value.trim(); }
    public String builderModel() { return builderModel; }
    public void builderModel(String value) { builderModel = value.trim(); }
    public String codexModel() { return codexModel; }
    public void codexModel(String value) { codexModel = value.trim(); }
    public ReasoningLevel reasoningLevel() { return reasoningLevel; }
    public void reasoningLevel(ReasoningLevel value) { reasoningLevel = value; }
    public ReasoningLevel builderReasoningLevel() { return builderReasoningLevel; }
    public void builderReasoningLevel(ReasoningLevel value) { builderReasoningLevel = value; }
    public ReasoningLevel codexReasoningLevel() { return codexReasoningLevel; }
    public void codexReasoningLevel(ReasoningLevel value) {
        codexReasoningLevel = value == null || value == ReasoningLevel.NONE
            ? ReasoningLevel.LOW
            : value;
    }
    public int codexGenerationEffort() { return codexGenerationEffort; }
    public void codexGenerationEffort(int value) {
        codexGenerationEffort = CodexGenerationEffort.clamp(value);
    }
    public ContextMode contextMode() { return contextMode; }
    public void contextMode(ContextMode value) { contextMode = value == null ? ContextMode.COMPRESSED : value; }
    public int maxFullContextBlocks() { return maxFullContextBlocks; }
    public void maxFullContextBlocks(int value) {
        maxFullContextBlocks = Math.max(1, Math.min(32_768, value));
    }
    public int maxAreaVolume() { return maxAreaVolume; }
    public void maxAreaVolume(int value) { maxAreaVolume = value; }
    public int maxBlockChanges() { return maxBlockChanges; }
    public void maxBlockChanges(int value) { maxBlockChanges = value; }
    public boolean allowBlockEntityReplacement() { return allowBlockEntityReplacement; }
    public void allowBlockEntityReplacement(boolean value) { allowBlockEntityReplacement = value; }
    public int recoveryRetentionCount() { return recoveryRetentionCount; }
    public void recoveryRetentionCount(int value) {
        recoveryRetentionCount = Math.max(
            PlacementLimits.MIN_RETENTION_COUNT,
            Math.min(PlacementLimits.MAX_RETENTION_COUNT, value)
        );
    }
}
