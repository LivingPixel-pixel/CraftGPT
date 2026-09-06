package dev.craftgpt.client.codex;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validated per-run Codex model selection. */
public record CodexRunSettings(String model, String reasoningEffort) {
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern REASONING = Pattern.compile("low|medium|high|xhigh|max");

    public CodexRunSettings {
        model = model == null ? "" : model.trim();
        reasoningEffort = reasoningEffort == null
            ? ""
            : reasoningEffort.trim().toLowerCase(Locale.ROOT);
        if (!MODEL_ID.matcher(model).matches() || !REASONING.matcher(reasoningEffort).matches()) {
            throw new IllegalArgumentException("invalid_codex_run_settings");
        }
    }

    String reasoningConfigOverride() {
        return "model_reasoning_effort=\"" + reasoningEffort + "\"";
    }
}
