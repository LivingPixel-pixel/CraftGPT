package dev.craftgpt.client.planning.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.craftgpt.client.api.ApiCallResult;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlannerApiClientTest {
    private static final Gson GSON = new Gson();

    @Test
    void extractsStructuredTextFromRawResponsesPayload() {
        IntentionSpec expected = plan();
        JsonObject content = new JsonObject();
        content.addProperty("type", "output_text");
        content.addProperty("text", GSON.toJson(expected));
        JsonArray contentItems = new JsonArray();
        contentItems.add(content);
        JsonObject message = new JsonObject();
        message.addProperty("type", "message");
        message.add("content", contentItems);
        JsonArray output = new JsonArray();
        output.add(message);
        JsonObject response = new JsonObject();
        response.add("output", output);

        IntentionSpec parsed = new PlannerApiClient().parseResponseBody(200, GSON.toJson(response), context(), 800);

        assertEquals(expected, parsed);
    }

    @Test
    void preservesReportedUsageAndLatencyWithStructuredPlan() {
        IntentionSpec expected = plan();
        JsonObject response = responseWithText(GSON.toJson(expected));
        JsonObject outputDetails = new JsonObject();
        outputDetails.addProperty("reasoning_tokens", 120);
        JsonObject usage = new JsonObject();
        usage.addProperty("input_tokens", 800);
        usage.addProperty("output_tokens", 300);
        usage.addProperty("total_tokens", 1_100);
        usage.add("output_tokens_details", outputDetails);
        response.add("usage", usage);

        ApiCallResult<IntentionSpec> result = new PlannerApiClient().parseResponseBodyWithUsage(
            200,
            GSON.toJson(response),
            context(),
            800,
            "gpt-5.6-terra",
            "medium",
            1_234
        );

        assertEquals(expected, result.value());
        assertEquals(1_100, result.metrics().usage().totalTokens());
        assertEquals(120, result.metrics().usage().reasoningTokens());
        assertEquals(1_234, result.metrics().latencyMillis());
    }

    @Test
    void buildsResponsesRequestWithReasoningPrivacyAndStrictSchema() {
        PlannerRequestSettings settings = new PlannerRequestSettings(
            "https://api.openai.com/v1/responses", "secret-not-serialized", "gpt-test", "high", 800
        );

        JsonObject body = new PlannerApiClient().buildRequestBody(settings, context(), "Build a forge", null);

        assertEquals("gpt-test", body.get("model").getAsString());
        assertEquals("high", body.getAsJsonObject("reasoning").get("effort").getAsString());
        assertEquals(false, body.get("store").getAsBoolean());
        assertEquals("json_schema", body.getAsJsonObject("text").getAsJsonObject("format").get("type").getAsString());
        assertEquals(true, body.getAsJsonObject("text").getAsJsonObject("format").get("strict").getAsBoolean());
        assertEquals(false, GSON.toJson(body).contains("secret-not-serialized"));
    }

    @Test
    void rejectsHttpFailureWithoutAcceptingBodyAsPlan() {
        PlannerException exception = assertThrows(PlannerException.class, () ->
            new PlannerApiClient().parseResponseBody(
                429,
                "{\"error\":{\"message\":\"rate limited\"}}",
                context(),
                800
            ));
        assertEquals("api_http_429: rate limited", exception.getMessage());
    }

    @Test
    void readsUtf8ResponseAndClosesBody() {
        byte[] bytes = "Gr\u00fc\u00dfe aus der Schmiede".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream body = new TrackingInputStream(bytes);
        HttpResponse<InputStream> response = response(body, bytes.length);

        String result = new PlannerApiClient().readResponseBody(response);

        assertEquals("Gr\u00fc\u00dfe aus der Schmiede", result);
        assertTrue(body.closed);
    }

    @Test
    void rejectsOversizedContentLengthBeforeReadingAndClosesBody() {
        TrackingInputStream body = new TrackingInputStream(new byte[] { 1 });
        HttpResponse<InputStream> response = response(body, 2_000_001L);

        PlannerException exception = assertThrows(
            PlannerException.class,
            () -> new PlannerApiClient().readResponseBody(response)
        );

        assertEquals("response_too_large", exception.getMessage());
        assertEquals(0, body.bytesRead);
        assertTrue(body.closed);
    }

    @Test
    void rejectsStreamThatExceedsLimitAndClosesBody() {
        TrackingInputStream body = new TrackingInputStream(new byte[2_000_001]);
        HttpResponse<InputStream> response = response(body, -1L);

        PlannerException exception = assertThrows(
            PlannerException.class,
            () -> new PlannerApiClient().readResponseBody(response)
        );

        assertEquals("response_too_large", exception.getMessage());
        assertEquals(2_000_001, body.bytesRead);
        assertTrue(body.closed);
    }

    @Test
    void normalizationUnwrapsOnlyCompletionWrappersAndPreservesPlannerFailure() {
        PlannerException expected = new PlannerException("response_too_large");
        Throwable wrapped = new CompletionException(new ExecutionException(expected));

        PlannerException normalized = new PlannerApiClient().normalizeFailure(wrapped);

        assertSame(expected, normalized);
    }

    @Test
    void normalizationDoesNotWalkThroughArbitraryExceptionCauses() {
        PlannerException nested = new PlannerException("must_not_escape_wrapper");
        IllegalStateException wrapper = new IllegalStateException("transport_failed", nested);

        PlannerException normalized = new PlannerApiClient().normalizeFailure(wrapper);

        assertEquals("network_error", normalized.getMessage());
        assertSame(wrapper, normalized.getCause());
    }

    private IntentionSpec plan() {
        return new IntentionSpec(
            "Forge", "Compact forge", List.of("Workshop"), "Medieval",
            new PlanDimensions(8, 7, 8), "South",
            List.of(new MaterialRole("walls", List.of("minecraft:stone_bricks"), "Shell")),
            List.of("Chimney"), List.of("Wood pile"), List.of("Stay in area"), List.of(), List.of(),
            500, "Fits the terrain.", "Build an 8 by 8 forge with a stone shell and timber roof."
        );
    }

    private JsonObject responseWithText(String text) {
        JsonObject content = new JsonObject();
        content.addProperty("type", "output_text");
        content.addProperty("text", text);
        JsonArray contentItems = new JsonArray();
        contentItems.add(content);
        JsonObject message = new JsonObject();
        message.addProperty("type", "message");
        message.add("content", contentItems);
        JsonArray output = new JsonArray();
        output.add(message);
        JsonObject response = new JsonObject();
        response.addProperty("status", "completed");
        response.add("output", output);
        return response;
    }

    private AreaContext context() {
        return new AreaContext(
            1, "00000000-0000-0000-0000-000000000001", "minecraft:overworld",
            new AreaPoint(0, 0, 0), new AreaPoint(9, 9, 9),
            10, 10, 10, 1_000, 1_000, 500, 0,
            100, 0, 5, Map.of("minecraft:stone", 500),
            "a".repeat(64),
            IntStream.range(0, 10)
                .mapToObj(y -> new LayerSummary(y, 50, "minecraft:stone"))
                .toList(),
            List.of()
        );
    }

    private HttpResponse<InputStream> response(InputStream body, long contentLength) {
        Map<String, List<String>> headers = contentLength < 0
            ? Map.of()
            : Map.of("Content-Length", List.of(Long.toString(contentLength)));
        return new TestResponse(body, HttpHeaders.of(headers, (name, value) -> true));
    }

    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closed;
        private int bytesRead;

        private TrackingInputStream(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }

    private record TestResponse(
        InputStream body,
        HttpHeaders headers
    ) implements HttpResponse<InputStream> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://example.invalid/v1/responses");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }
}
