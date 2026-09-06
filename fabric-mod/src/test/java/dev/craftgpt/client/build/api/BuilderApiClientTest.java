package dev.craftgpt.client.build.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.api.ApiCallResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuilderApiClientTest {
    private static final Gson GSON = new Gson();

    @Test
    void extractsDraftAndCompilesItFromResponsesPayload() {
        BuildDraft expected = new BuildDraft(
            1,
            "Forge shell",
            List.of("minecraft:stone_bricks"),
            List.of("0,0,0,0", "1,0,0,0")
        );
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
        response.addProperty("status", "completed");
        response.add("output", output);

        CompiledBuildArtifact artifact = new BuilderApiClient().parseResponseBody(
            200,
            GSON.toJson(response),
            BuilderTestFixtures.context(),
            BuilderTestFixtures.PROJECT_ID,
            BuilderTestFixtures.planVersion(),
            BuilderTestFixtures.settings(20)
        );

        assertEquals(expected.summary(), artifact.summary());
        assertEquals(expected.palette(), artifact.palette());
        assertEquals(2, artifact.operations().size());
    }

    @Test
    void preservesReportedUsageWithCompiledArtifact() {
        BuildDraft draft = new BuildDraft(
            1,
            "Forge shell",
            List.of("minecraft:stone_bricks"),
            List.of("0,0,0,0")
        );
        JsonObject content = new JsonObject();
        content.addProperty("type", "output_text");
        content.addProperty("text", GSON.toJson(draft));
        JsonArray contentItems = new JsonArray();
        contentItems.add(content);
        JsonObject message = new JsonObject();
        message.add("content", contentItems);
        JsonArray output = new JsonArray();
        output.add(message);
        JsonObject usage = new JsonObject();
        usage.addProperty("input_tokens", 600);
        usage.addProperty("output_tokens", 200);
        usage.addProperty("total_tokens", 800);
        JsonObject response = new JsonObject();
        response.addProperty("status", "completed");
        response.add("output", output);
        response.add("usage", usage);

        ApiCallResult<CompiledBuildArtifact> result = new BuilderApiClient().parseResponseBodyWithUsage(
            200,
            GSON.toJson(response),
            BuilderTestFixtures.context(),
            BuilderTestFixtures.PROJECT_ID,
            BuilderTestFixtures.planVersion(),
            BuilderTestFixtures.settings(20),
            2_345
        );

        assertEquals(1, result.value().operations().size());
        assertEquals(800, result.metrics().usage().totalTokens());
        assertEquals(2_345, result.metrics().latencyMillis());
    }

    @Test
    void requestUsesBuilderModelPrivacyControlsReasoningAndStrictSchema() {
        BuilderRequestSettings settings = BuilderTestFixtures.settings(20);

        JsonObject body = new BuilderApiClient().buildRequestBody(
            settings,
            BuilderTestFixtures.context(),
            BuilderTestFixtures.planVersion()
        );
        String serialized = GSON.toJson(body);

        assertEquals("gpt-builder-test", body.get("model").getAsString());
        assertEquals("low", body.getAsJsonObject("reasoning").get("effort").getAsString());
        assertFalse(body.get("store").getAsBoolean());
        assertEquals(4_096, body.get("max_output_tokens").getAsInt());
        assertEquals(
            "json_schema",
            body.getAsJsonObject("text").getAsJsonObject("format").get("type").getAsString()
        );
        assertTrue(body.getAsJsonObject("text").getAsJsonObject("format").get("strict").getAsBoolean());
        assertFalse(body.has("tools"));
        assertFalse(serialized.contains(settings.apiKey()));
        assertFalse(serialized.contains(BuilderTestFixtures.context().selectionId()));
        assertFalse(serialized.contains(BuilderTestFixtures.PROJECT_ID));
        assertFalse(serialized.contains("123456"));
        assertFalse(serialized.contains("-234567"));
    }

    @Test
    void rejectsHttpFailureWithoutParsingItsBodyAsBuildData() {
        BuilderException exception = assertThrows(
            BuilderException.class,
            () -> new BuilderApiClient().parseResponseBody(
                429,
                "{\"error\":{\"message\":\"rate limited\"}}",
                BuilderTestFixtures.context(),
                BuilderTestFixtures.PROJECT_ID,
                BuilderTestFixtures.planVersion(),
                BuilderTestFixtures.settings(20)
            )
        );
        assertEquals("api_http_429: rate limited", exception.getMessage());
    }

    @Test
    void readsUtf8ResponseAndClosesBody() {
        byte[] bytes = "Gr\u00fc\u00dfe aus der Schmiede".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream body = new TrackingInputStream(bytes);

        String result = new BuilderApiClient().readResponseBody(response(body, bytes.length));

        assertEquals("Gr\u00fc\u00dfe aus der Schmiede", result);
        assertTrue(body.closed);
    }

    @Test
    void rejectsOversizedResponseBeforeReadingAndAlwaysClosesBody() {
        TrackingInputStream body = new TrackingInputStream(new byte[] {1});

        BuilderException exception = assertThrows(
            BuilderException.class,
            () -> new BuilderApiClient().readResponseBody(
                response(body, BuildLimits.MAX_ARTIFACT_JSON_BYTES + 1L)
            )
        );

        assertEquals("response_too_large", exception.getMessage());
        assertEquals(0, body.bytesRead);
        assertTrue(body.closed);
    }

    @Test
    void rejectsOversizedUnknownLengthStream() {
        TrackingInputStream body = new TrackingInputStream(
            new byte[BuildLimits.MAX_ARTIFACT_JSON_BYTES + 1]
        );

        BuilderException exception = assertThrows(
            BuilderException.class,
            () -> new BuilderApiClient().readResponseBody(response(body, -1L))
        );

        assertEquals("response_too_large", exception.getMessage());
        assertEquals(BuildLimits.MAX_ARTIFACT_JSON_BYTES + 1, body.bytesRead);
        assertTrue(body.closed);
    }

    @Test
    void normalizationOnlyUnwrapsCompletionWrappers() {
        BuilderException expected = new BuilderException("response_too_large");
        Throwable wrapped = new CompletionException(new ExecutionException(expected));
        BuilderApiClient client = new BuilderApiClient();

        assertSame(expected, client.normalizeFailure(wrapped));

        IllegalStateException transport = new IllegalStateException("transport", expected);
        BuilderException normalized = client.normalizeFailure(transport);
        assertEquals("network_error", normalized.getMessage());
        assertSame(transport, normalized.getCause());
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
            if (value >= 0) bytesRead++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) bytesRead += read;
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
