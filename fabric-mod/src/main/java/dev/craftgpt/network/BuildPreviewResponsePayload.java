package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.validation.ValidationProblem;
import dev.craftgpt.validation.ValidationProblemCatalog;
import dev.craftgpt.validation.ValidationProblemJson;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server validation result. No block changes have been applied when this is sent. */
public record BuildPreviewResponsePayload(
    String requestId,
    boolean accepted,
    String code,
    int actualChanges,
    String problemsJson
) implements CustomPacketPayload {
    public static final int MAX_REQUEST_ID_LENGTH = BuildPreviewRequestPayload.MAX_REQUEST_ID_LENGTH;
    public static final int MAX_CODE_LENGTH = 64;

    public static final Type<BuildPreviewResponsePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "build_preview_response")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreviewResponsePayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_REQUEST_ID_LENGTH), BuildPreviewResponsePayload::requestId,
            ByteBufCodecs.BOOL, BuildPreviewResponsePayload::accepted,
            ByteBufCodecs.stringUtf8(MAX_CODE_LENGTH), BuildPreviewResponsePayload::code,
            ByteBufCodecs.VAR_INT, BuildPreviewResponsePayload::actualChanges,
            ByteBufCodecs.stringUtf8(ValidationProblemJson.MAX_JSON_LENGTH),
                BuildPreviewResponsePayload::problemsJson,
            BuildPreviewResponsePayload::new
        );

    public BuildPreviewResponsePayload(
        String requestId,
        boolean accepted,
        String code,
        int actualChanges
    ) {
        this(
            requestId,
            accepted,
            code,
            actualChanges,
            ValidationProblemJson.encode(accepted
                ? List.of()
                : List.of(ValidationProblemCatalog.problem(code, "serverValidation")))
        );
    }

    public BuildPreviewResponsePayload {
        requestId = requestId == null ? "" : requestId;
        code = code == null ? "invalid_response" : code;
        problemsJson = problemsJson == null ? "" : problemsJson;
        if (requestId.length() > MAX_REQUEST_ID_LENGTH
            || code.isBlank()
            || code.length() > MAX_CODE_LENGTH
            || actualChanges < 0
            || problemsJson.length() > ValidationProblemJson.MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("Invalid build preview response");
        }
        List<ValidationProblem> problems = ValidationProblemJson.decode(problemsJson);
        if ((accepted && !problems.isEmpty())
            || (!accepted && problems.isEmpty())
            || (!accepted && !code.equals(problems.getFirst().code()))) {
            throw new IllegalArgumentException("Invalid build preview response diagnostics");
        }
    }

    public List<ValidationProblem> decodedProblems() {
        return ValidationProblemJson.decode(problemsJson);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
