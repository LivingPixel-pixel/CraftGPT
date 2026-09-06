package dev.craftgpt.client.planning.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiCallResult;
import dev.craftgpt.client.api.ApiCostEstimate;
import dev.craftgpt.client.api.ApiCostEstimator;
import dev.craftgpt.client.api.ApiUsageParser;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.context.AreaContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class PlannerApiClient {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public CompletableFuture<ApiCallResult<IntentionSpec>> plan(
        PlannerRequestSettings settings,
        AreaContext context,
        String instruction,
        IntentionSpec previousPlan
    ) {
        settings.validate();
        URI endpoint;
        try {
            endpoint = URI.create(settings.endpoint());
        } catch (IllegalArgumentException exception) {
            throw new PlannerException("invalid_endpoint", exception);
        }

        JsonObject body = buildRequestBody(settings, context, instruction, previousPlan);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofMinutes(3))
            .header("Authorization", "Bearer " + settings.apiKey())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
            .build();

        long startedNanos = System.nanoTime();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(response -> parseResponse(
                response,
                context,
                settings,
                elapsedMillis(startedNanos)
            ))
            .exceptionallyCompose(exception -> CompletableFuture.failedFuture(normalizeFailure(exception)));
    }

    public ApiCostEstimate estimateCost(
        PlannerRequestSettings settings,
        AreaContext context,
        String instruction,
        IntentionSpec previousPlan
    ) {
        JsonObject body = buildRequestBody(settings, context, instruction, previousPlan);
        long expectedOutput = switch (settings.reasoningLevel()) {
            case "none" -> 1_500L;
            case "low" -> 2_000L;
            case "medium" -> 3_500L;
            case "high" -> 5_500L;
            case "xhigh" -> 8_500L;
            case "max" -> 12_000L;
            default -> 4_000L;
        };
        return ApiCostEstimator.estimate(
            settings.model(),
            GSON.toJson(body),
            expectedOutput,
            16_000L
        );
    }

    JsonObject buildRequestBody(
        PlannerRequestSettings settings,
        AreaContext context,
        String instruction,
        IntentionSpec previousPlan
    ) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("instructions", PlannerPromptFactory.SYSTEM_PROMPT);
        body.addProperty("input", PlannerPromptFactory.userPrompt(
            context,
            instruction,
            previousPlan,
            settings.maximumBlockChanges(),
            settings.contextMode(),
            settings.maximumFullContextBlocks()
        ));
        body.addProperty("store", false);
        body.addProperty("max_output_tokens", 16_000);

        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", settings.reasoningLevel());
        body.add("reasoning", reasoning);

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty("name", "craftgpt_plan");
        format.addProperty("strict", true);
        format.add("schema", PlannerSchema.json());
        JsonObject text = new JsonObject();
        text.add("format", format);
        body.add("text", text);
        return body;
    }

    private ApiCallResult<IntentionSpec> parseResponse(
        HttpResponse<InputStream> response,
        AreaContext context,
        PlannerRequestSettings settings,
        long latencyMillis
    ) {
        return parseResponseBodyWithUsage(
            response.statusCode(),
            readResponseBody(response),
            context,
            settings.maximumBlockChanges(),
            settings.model(),
            settings.reasoningLevel(),
            latencyMillis
        );
    }

    ApiCallResult<IntentionSpec> parseResponseBodyWithUsage(
        int statusCode,
        String responseBody,
        AreaContext context,
        int maximumBlockChanges,
        String model,
        String reasoningLevel,
        long latencyMillis
    ) {
        IntentionSpec plan = parseResponseBody(statusCode, responseBody, context, maximumBlockChanges);
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        ApiCallMetrics metrics = new ApiCallMetrics(
            model,
            reasoningLevel,
            ApiUsageParser.parse(root),
            latencyMillis
        );
        return new ApiCallResult<>(plan, metrics);
    }

    String readResponseBody(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw new PlannerException("response_too_large");
            }

            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new PlannerException("response_too_large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (PlannerException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PlannerException("network_error", exception);
        }
    }

    IntentionSpec parseResponseBody(
        int statusCode,
        String responseBody,
        AreaContext context,
        int maximumBlockChanges
    ) {
        String body = responseBody == null ? "" : responseBody;
        if (body.length() > MAX_RESPONSE_BYTES) {
            throw new PlannerException("response_too_large");
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new PlannerException("api_http_" + statusCode + ": " + safeErrorMessage(body));
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new PlannerException("invalid_api_response", exception);
        }
        if (root.has("error") && root.get("error").isJsonObject()) {
            throw new PlannerException("api_error: " + safeErrorMessage(body));
        }
        if (root.has("status") && root.get("status").isJsonPrimitive()
            && !"completed".equals(root.get("status").getAsString())) {
            throw new PlannerException("incomplete_response");
        }

        String outputText = extractOutputText(root);
        try {
            IntentionSpec plan = GSON.fromJson(outputText, IntentionSpec.class);
            PlannerResponseValidator.validate(plan, context, maximumBlockChanges);
            return plan;
        } catch (PlannerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlannerException("invalid_structured_plan", exception);
        }
    }

    private String extractOutputText(JsonObject root) {
        if (root.has("output_text") && root.get("output_text").isJsonPrimitive()) {
            return root.get("output_text").getAsString();
        }
        JsonArray output = root.has("output") && root.get("output").isJsonArray()
            ? root.getAsJsonArray("output")
            : new JsonArray();
        for (JsonElement outputElement : output) {
            if (!outputElement.isJsonObject()) continue;
            JsonObject outputItem = outputElement.getAsJsonObject();
            if (!outputItem.has("content") || !outputItem.get("content").isJsonArray()) continue;
            for (JsonElement contentElement : outputItem.getAsJsonArray("content")) {
                if (!contentElement.isJsonObject()) continue;
                JsonObject content = contentElement.getAsJsonObject();
                if (content.has("text") && content.get("text").isJsonPrimitive()) {
                    return content.get("text").getAsString();
                }
            }
        }
        throw new PlannerException("missing_output_text");
    }

    private String safeErrorMessage(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject error = root.has("error") && root.get("error").isJsonObject()
                ? root.getAsJsonObject("error")
                : null;
            String message = error != null && error.has("message") ? error.get("message").getAsString() : "request_failed";
            return message.length() > 240 ? message.substring(0, 240) : message;
        } catch (RuntimeException exception) {
            return "request_failed";
        }
    }

    PlannerException normalizeFailure(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null
            && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        if (cause instanceof PlannerException plannerException) {
            return plannerException;
        }
        return new PlannerException("network_error", cause);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
