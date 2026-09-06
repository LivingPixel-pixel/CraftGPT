package dev.craftgpt.client.build.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiCallResult;
import dev.craftgpt.client.api.ApiCostEstimate;
import dev.craftgpt.client.api.ApiCostEstimator;
import dev.craftgpt.client.api.ApiUsageParser;
import dev.craftgpt.client.planning.model.PlanVersion;
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

public final class BuilderApiClient {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_RESPONSE_BYTES = BuildLimits.MAX_ARTIFACT_JSON_BYTES;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public CompletableFuture<ApiCallResult<CompiledBuildArtifact>> generate(
        BuilderRequestSettings settings,
        AreaContext context,
        String projectId,
        PlanVersion planVersion
    ) {
        BuilderResponseValidator.validateInputs(context, projectId, planVersion, settings);
        URI endpoint;
        try {
            endpoint = URI.create(settings.endpoint());
        } catch (IllegalArgumentException exception) {
            throw new BuilderException("invalid_endpoint", exception);
        }

        JsonObject body = buildRequestBody(settings, context, planVersion);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofMinutes(5))
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
                projectId,
                planVersion,
                settings,
                elapsedMillis(startedNanos)
            ))
            .exceptionallyCompose(exception -> CompletableFuture.failedFuture(normalizeFailure(exception)));
    }

    public ApiCostEstimate estimateCost(
        BuilderRequestSettings settings,
        AreaContext context,
        PlanVersion planVersion
    ) {
        JsonObject body = buildRequestBody(settings, context, planVersion);
        int maximumOperations = BuildLimits.effectiveMaximumOperations(
            settings.maximumOperations(),
            context.volume()
        );
        long maximumOutput = Math.min(64_000L, Math.max(4_096L, 2_048L + maximumOperations * 8L));
        long expectedOutput = Math.min(maximumOutput, 1_024L + maximumOperations * 5L);
        return ApiCostEstimator.estimate(
            settings.model(),
            GSON.toJson(body),
            expectedOutput,
            maximumOutput
        );
    }

    JsonObject buildRequestBody(
        BuilderRequestSettings settings,
        AreaContext context,
        PlanVersion planVersion
    ) {
        int maximumOperations = BuildLimits.effectiveMaximumOperations(
            settings.maximumOperations(),
            context.volume()
        );
        int maximumOutputTokens = Math.min(64_000, Math.max(4_096, 2_048 + maximumOperations * 8));

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("instructions", BuilderPromptFactory.SYSTEM_PROMPT);
        body.addProperty("input", BuilderPromptFactory.userPrompt(
            context,
            planVersion,
            maximumOperations,
            settings.contextMode(),
            settings.maximumFullContextBlocks()
        ));
        body.addProperty("store", false);
        body.addProperty("max_output_tokens", maximumOutputTokens);

        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", settings.reasoningLevel());
        body.add("reasoning", reasoning);

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty("name", "craftgpt_build_draft");
        format.addProperty("strict", true);
        format.add("schema", BuilderSchema.json());
        JsonObject text = new JsonObject();
        text.add("format", format);
        body.add("text", text);
        return body;
    }

    private ApiCallResult<CompiledBuildArtifact> parseResponse(
        HttpResponse<InputStream> response,
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        BuilderRequestSettings settings,
        long latencyMillis
    ) {
        return parseResponseBodyWithUsage(
            response.statusCode(),
            readResponseBody(response),
            context,
            projectId,
            planVersion,
            settings,
            latencyMillis
        );
    }

    ApiCallResult<CompiledBuildArtifact> parseResponseBodyWithUsage(
        int statusCode,
        String responseBody,
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        BuilderRequestSettings settings,
        long latencyMillis
    ) {
        CompiledBuildArtifact artifact = parseResponseBody(
            statusCode,
            responseBody,
            context,
            projectId,
            planVersion,
            settings
        );
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        ApiCallMetrics metrics = new ApiCallMetrics(
            settings.model(),
            settings.reasoningLevel(),
            ApiUsageParser.parse(root),
            latencyMillis
        );
        return new ApiCallResult<>(artifact, metrics);
    }

    String readResponseBody(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw new BuilderException("response_too_large");
            }
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new BuilderException("response_too_large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (BuilderException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BuilderException("network_error", exception);
        }
    }

    CompiledBuildArtifact parseResponseBody(
        int statusCode,
        String responseBody,
        AreaContext context,
        String projectId,
        PlanVersion planVersion,
        BuilderRequestSettings settings
    ) {
        String body = responseBody == null ? "" : responseBody;
        if (body.length() > MAX_RESPONSE_BYTES) {
            throw new BuilderException("response_too_large");
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new BuilderException("api_http_" + statusCode + ": " + safeErrorMessage(body));
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new BuilderException("invalid_api_response", exception);
        }
        if (root.has("error") && root.get("error").isJsonObject()) {
            throw new BuilderException("api_error: " + safeErrorMessage(body));
        }
        if (root.has("status")
            && root.get("status").isJsonPrimitive()
            && !"completed".equals(root.get("status").getAsString())) {
            throw new BuilderException("incomplete_response");
        }

        String outputText = extractOutputText(root);
        try {
            BuildDraft draft = GSON.fromJson(outputText, BuildDraft.class);
            return BuilderResponseValidator.validateAndCompile(
                draft,
                context,
                projectId,
                planVersion,
                settings
            );
        } catch (BuilderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BuilderException("invalid_structured_build", exception);
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
        throw new BuilderException("missing_output_text");
    }

    private String safeErrorMessage(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject error = root.has("error") && root.get("error").isJsonObject()
                ? root.getAsJsonObject("error")
                : null;
            String message = error != null && error.has("message")
                ? error.get("message").getAsString()
                : "request_failed";
            return message.length() > 240 ? message.substring(0, 240) : message;
        } catch (RuntimeException exception) {
            return "request_failed";
        }
    }

    BuilderException normalizeFailure(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null
            && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        if (cause instanceof BuilderException builderException) {
            return builderException;
        }
        return new BuilderException("network_error", cause);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
