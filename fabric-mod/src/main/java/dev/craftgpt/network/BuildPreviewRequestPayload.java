package dev.craftgpt.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.craftgpt.CraftGptMod;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildValidationRequest;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client-to-server request envelope. Only validation-relevant geometry crosses the network;
 * private planning metadata remains local to the client.
 */
public record BuildPreviewRequestPayload(String json) implements CustomPacketPayload {
    public static final int MAX_REQUEST_ID_LENGTH = 64;
    public static final int MAX_JSON_BYTES = BuildLimits.MAX_ARTIFACT_JSON_BYTES + 512;
    public static final int MAX_PACKET_BYTES = MAX_JSON_BYTES + 5;

    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_REQUEST_ID_LENGTH + "}");
    private static final Set<String> ENVELOPE_FIELDS = Set.of("requestId", "build");
    private static final Set<String> BUILD_FIELDS = Set.of(
        "schemaVersion",
        "selectionId",
        "contextHash",
        "palette",
        "operations"
    );
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final Type<BuildPreviewRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "build_preview_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreviewRequestPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_JSON_BYTES),
            BuildPreviewRequestPayload::json,
            BuildPreviewRequestPayload::new
        );

    public static BuildPreviewRequestPayload create(String requestId, CompiledBuildArtifact artifact) {
        validateRequestId(requestId);
        BuildValidationRequest build = BuildValidationRequest.from(artifact);
        validateBuildSize(GSON.toJson(build));
        String json = GSON.toJson(new RequestBody(requestId, build));
        validateJsonSize(json);
        return new BuildPreviewRequestPayload(json);
    }

    public RequestBody decodeValidated() {
        validateJsonSize(json);
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Build preview request must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.keySet().equals(ENVELOPE_FIELDS)) {
            throw new IllegalArgumentException("Build preview request has invalid fields");
        }
        JsonElement buildElement = root.get("build");
        if (buildElement == null
            || !buildElement.isJsonObject()
            || !buildElement.getAsJsonObject().keySet().equals(BUILD_FIELDS)) {
            throw new IllegalArgumentException("Build preview data has invalid fields");
        }
        validateBuildSize(GSON.toJson(buildElement));
        RequestBody body = GSON.fromJson(root, RequestBody.class);
        if (body == null || body.build() == null) {
            throw new IllegalArgumentException("Build preview request is incomplete");
        }
        validateRequestId(body.requestId());
        return body;
    }

    public String requestIdOrEmpty() {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return "";
            }
            JsonElement requestId = parsed.getAsJsonObject().get("requestId");
            if (requestId == null || !requestId.isJsonPrimitive()) {
                return "";
            }
            String value = requestId.getAsString();
            return REQUEST_ID.matcher(value).matches() ? value : "";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static void validateRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("Invalid build preview request ID");
        }
    }

    private static void validateJsonSize(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Build preview request is too large");
        }
    }

    private static void validateBuildSize(String buildJson) {
        if (buildJson == null
            || buildJson.getBytes(StandardCharsets.UTF_8).length > BuildLimits.MAX_ARTIFACT_JSON_BYTES) {
            throw new IllegalArgumentException("Build preview data is too large");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record RequestBody(String requestId, BuildValidationRequest build) {
    }
}
