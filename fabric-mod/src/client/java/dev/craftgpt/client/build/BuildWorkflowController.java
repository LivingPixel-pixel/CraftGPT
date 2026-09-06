package dev.craftgpt.client.build;

import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiCallResult;
import dev.craftgpt.client.api.ApiCostEstimate;
import dev.craftgpt.client.build.api.BuilderApiClient;
import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.client.build.api.BuilderRequestSettings;
import dev.craftgpt.client.build.preview.GhostPreviewManager;
import dev.craftgpt.client.build.storage.BuildArtifactRepository;
import dev.craftgpt.client.build.storage.BuildArtifactSnapshot;
import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.network.BuildPreviewRequestPayload;
import dev.craftgpt.network.BuildPreviewResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class BuildWorkflowController {
    private static final long SERVER_VALIDATION_TIMEOUT_SECONDS = 45L;

    private final CraftGptConfig config;
    private final BuilderApiClient apiClient;
    private BuildArtifactRepository repository;

    private BuildArtifactSnapshot activeBuild;
    private CompletableFuture<ApiCallResult<CompiledBuildArtifact>> activeApiRequest;
    private CompiledBuildArtifact pendingArtifact;
    private String pendingValidationRequestId;
    private ApiCallMetrics lastMetrics;
    private long requestGeneration;
    private boolean requestInFlight;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private Runnable timeoutListener = () -> {};

    public void onValidationTimeout(Runnable listener) { timeoutListener = listener; }

    public void restoreDraft(BuildArtifactSnapshot draft, AreaContext context) {
        cancelRequests();
        activeBuild = draft;
        if(draft != null && context != null) GhostPreviewManager.INSTANCE.show(draft.artifact(), context);
    }

    public BuildWorkflowController(CraftGptConfig config) {
        this(config, new BuilderApiClient());
    }

    BuildWorkflowController(CraftGptConfig config, BuilderApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
    }

    public void setWorldScope(String worldScopeId) {
        cancelRequests();
        activeBuild = null;
        lastMetrics = null;
        GhostPreviewManager.INSTANCE.clear();
        repository = new BuildArtifactRepository(worldScopeId);
        clearStatus();
    }

    public void disconnect() {
        cancelRequests();
        activeBuild = null;
        lastMetrics = null;
        repository = null;
        GhostPreviewManager.INSTANCE.clear();
        clearStatus();
    }

    public void clearSessionPreview() {
        cancelRequests();
        activeBuild = null;
        lastMetrics = null;
        GhostPreviewManager.INSTANCE.clear();
        clearStatus();
    }

    public void sourcePlanChanged(ProjectSnapshot source) {
        cancelRequests();
        activeBuild = null;
        lastMetrics = null;
        GhostPreviewManager.INSTANCE.clear();
        if (repository != null && source != null) {
            try {
                repository.abandon(source);
            } catch (IOException exception) {
                setError("craftgpt.build.error.save");
                return;
            }
        }
        clearStatus();
    }

    public void loadForCurrentSource(AreaContext context, ProjectSnapshot source) {
        if (repository == null || context == null || source == null || requestInFlight) {
            return;
        }
        activeBuild = repository.load(source, context).orElse(null);
        if (activeBuild != null) {
            lastMetrics = repository.loadMetrics(source, activeBuild.artifact().buildId()).orElse(null);
            GhostPreviewManager.INSTANCE.show(activeBuild.artifact(), context);
        }
    }

    public void compile(
        Minecraft minecraft,
        AreaContext context,
        ProjectSnapshot source,
        Supplier<Optional<AreaContext>> currentContext,
        Supplier<Optional<ProjectSnapshot>> currentSource
    ) {
        if (requestInFlight) {
            setError("craftgpt.build.error.busy");
            return;
        }
        if (repository == null) {
            setError("craftgpt.planning.error.no_scope");
            return;
        }
        if (context == null || source == null) {
            setError("craftgpt.build.error.no_plan");
            return;
        }
        if (config.contextMode().full() && !context.exactBlocks().complete()) {
            setError("craftgpt.planning.error.full_context_unavailable");
            return;
        }
        if (config.contextMode().full()
            && context.exactBlocks().blockCount() > config.maxFullContextBlocks()) {
            setError("craftgpt.planning.error.full_context_too_large");
            return;
        }

        BuilderRequestSettings settings = BuilderRequestSettings.from(config);
        try {
            settings.validate();
        } catch (BuilderException exception) {
            setError("craftgpt.build.error.settings");
            return;
        }

        long token = ++requestGeneration;
        requestInFlight = true;
        pendingArtifact = null;
        pendingValidationRequestId = null;
        status = Component.translatable("craftgpt.build.status.compiling", settings.model());
        statusColor = 0xFFFFFF55;

        CompletableFuture<ApiCallResult<CompiledBuildArtifact>> future;
        try {
            future = apiClient.generate(
                settings,
                context,
                source.project().projectId(),
                source.activeVersion()
            );
        } catch (RuntimeException exception) {
            requestInFlight = false;
            status = Component.translatable("craftgpt.build.error.api", safeFailureMessage(exception));
            statusColor = 0xFFFF5555;
            return;
        }
        activeApiRequest = future;

        future.whenComplete((callResult, failure) -> minecraft.execute(() -> {
            if (token != requestGeneration) {
                return;
            }
            activeApiRequest = null;
            if (failure != null) {
                requestInFlight = false;
                status = Component.translatable("craftgpt.build.error.api", safeFailureMessage(failure));
                statusColor = 0xFFFF5555;
                notifyPlayer(minecraft, status);
                return;
            }
            CompiledBuildArtifact artifact;
            try { artifact = BlockStateNormalizer.normalize(callResult.value()); }
            catch(RuntimeException e) { requestInFlight=false; setError("craftgpt.build.error.server.invalid_state"); return; }
            lastMetrics = callResult.metrics();
            if (!matchesCurrent(context, source, currentContext.get(), currentSource.get())) {
                requestInFlight = false;
                setError("craftgpt.build.error.stale");
                notifyPlayer(minecraft, status);
                return;
            }
            if (!ClientPlayNetworking.canSend(BuildPreviewRequestPayload.TYPE)) {
                requestInFlight = false;
                setError("craftgpt.build.error.server_unavailable");
                notifyPlayer(minecraft, status);
                return;
            }

            String requestId = UUID.randomUUID().toString();
            pendingArtifact = artifact;
            pendingValidationRequestId = requestId;
            status = Component.translatable("craftgpt.build.status.validating");
            statusColor = 0xFFFFFF55;
            try {
                ClientPlayNetworking.send(BuildPreviewRequestPayload.create(requestId, artifact));
            } catch (RuntimeException exception) {
                requestInFlight = false;
                pendingArtifact = null;
                pendingValidationRequestId = null;
                setError("craftgpt.build.error.server_unavailable");
                notifyPlayer(minecraft, status);
                return;
            }

            CompletableFuture.delayedExecutor(
                SERVER_VALIDATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            ).execute(() -> minecraft.execute(() -> validationTimedOut(token, requestId, minecraft)));
        }));
    }

    public void validatePortableArtifact(Minecraft minecraft, CompiledBuildArtifact artifact) {
        if (requestInFlight) {
            setError("craftgpt.build.error.busy");
            return;
        }
        if (repository == null || artifact == null) {
            setError("craftgpt.portable.error.import");
            return;
        }
        artifact = BlockStateNormalizer.normalize(artifact);
        if (!ClientPlayNetworking.canSend(BuildPreviewRequestPayload.TYPE)) {
            setError("craftgpt.build.error.server_unavailable");
            return;
        }

        long token = ++requestGeneration;
        requestInFlight = true;
        lastMetrics = null;
        String requestId = UUID.randomUUID().toString();
        pendingArtifact = artifact;
        pendingValidationRequestId = requestId;
        status = Component.translatable("craftgpt.portable.status.validating");
        statusColor = 0xFFFFFF55;
        try {
            ClientPlayNetworking.send(BuildPreviewRequestPayload.create(requestId, artifact));
        } catch (RuntimeException exception) {
            requestInFlight = false;
            pendingArtifact = null;
            pendingValidationRequestId = null;
            setError("craftgpt.build.error.server_unavailable");
            return;
        }
        CompletableFuture.delayedExecutor(
            SERVER_VALIDATION_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        ).execute(() -> minecraft.execute(() -> validationTimedOut(token, requestId, minecraft)));
    }

    public ValidationOutcome handleValidationResponse(
        Minecraft minecraft,
        BuildPreviewResponsePayload response,
        Optional<AreaContext> currentContext,
        Optional<ProjectSnapshot> currentSource
    ) {
        if (!requestInFlight
            || pendingValidationRequestId == null
            || !pendingValidationRequestId.equals(response.requestId())) {
            return ValidationOutcome.IGNORED;
        }

        requestInFlight = false;
        pendingValidationRequestId = null;
        CompiledBuildArtifact artifact = pendingArtifact;
        pendingArtifact = null;
        if (!response.accepted()) {
            status = rejectionMessage(response.code());
            statusColor = 0xFFFF5555;
            notifyPlayer(minecraft, status);
            return ValidationOutcome.REJECTED;
        }
        if (artifact == null
            || currentContext.isEmpty()
            || currentSource.isEmpty()
            || !matchesArtifact(artifact, currentContext.get(), currentSource.get())) {
            setError("craftgpt.build.error.stale");
            notifyPlayer(minecraft, status);
            return ValidationOutcome.REJECTED;
        }

        try {
            activeBuild = repository.save(
                currentSource.get(),
                currentContext.get(),
                artifact,
                response.actualChanges(),
                lastMetrics
            );
            GhostPreviewManager.INSTANCE.show(artifact, currentContext.get());
            status = Component.translatable(
                "craftgpt.build.status.ready",
                response.actualChanges()
            );
            statusColor = 0xFF55FF55;
            notifyPlayer(minecraft, Component.translatable(
                "craftgpt.build.message.ready",
                response.actualChanges()
            ));
            return ValidationOutcome.ACCEPTED;
        } catch (IOException | RuntimeException exception) {
            setError("craftgpt.build.error.save");
            notifyPlayer(minecraft, status);
            return ValidationOutcome.REJECTED;
        }
    }

    public boolean accept(ProjectSnapshot source, AreaContext context) {
        if (repository == null || activeBuild == null) {
            setError("craftgpt.build.error.no_preview");
            return false;
        }
        try {
            activeBuild = repository.accept(source, context);
            status = Component.translatable("craftgpt.build.status.accepted");
            statusColor = 0xFF55FF55;
            return true;
        } catch (IOException | RuntimeException exception) {
            setError("craftgpt.build.error.save");
            return false;
        }
    }

    public boolean abandon(ProjectSnapshot source) {
        if (repository == null || source == null) {
            setError("craftgpt.build.error.no_preview");
            return false;
        }
        try {
            repository.abandon(source);
            activeBuild = null;
            GhostPreviewManager.INSTANCE.clear();
            status = Component.translatable("craftgpt.build.status.abandoned");
            statusColor = 0xFFFFFF55;
            return true;
        } catch (IOException exception) {
            setError("craftgpt.build.error.save");
            return false;
        }
    }

    public Optional<BuildArtifactSnapshot> activeBuild() {
        return Optional.ofNullable(activeBuild);
    }

    public void hideGhostPreview() {
        GhostPreviewManager.INSTANCE.clear();
    }

    public void restoreGhostPreview(AreaContext context) {
        if (activeBuild != null && context != null) {
            GhostPreviewManager.INSTANCE.show(activeBuild.artifact(), context);
        }
    }

    public boolean requestInFlight() {
        return requestInFlight;
    }

    public boolean cancelActiveRequest() {
        if (!requestInFlight) {
            return false;
        }
        cancelRequests();
        status = Component.translatable("craftgpt.build.status.cancelled");
        statusColor = 0xFFFFFF55;
        return true;
    }

    public Optional<ApiCallMetrics> lastMetrics() {
        return Optional.ofNullable(lastMetrics);
    }

    public ApiCostEstimate estimateCost(AreaContext context, ProjectSnapshot source) {
        if (context == null || source == null) {
            return ApiCostEstimate.unavailable();
        }
        try {
            BuilderRequestSettings settings = BuilderRequestSettings.from(config);
            settings.validate();
            return apiClient.estimateCost(settings, context, source.activeVersion());
        } catch (RuntimeException exception) {
            return ApiCostEstimate.unavailable();
        }
    }

    public Component status() {
        return status;
    }

    public int statusColor() {
        return statusColor;
    }

    public boolean hasStatus() {
        return !status.getString().isEmpty();
    }

    public void clearStatus() {
        status = Component.empty();
        statusColor = 0xFFAAAAAA;
    }

    private void validationTimedOut(long token, String requestId, Minecraft minecraft) {
        if (token != requestGeneration
            || !requestInFlight
            || !requestId.equals(pendingValidationRequestId)) {
            return;
        }
        requestInFlight = false;
        pendingArtifact = null;
        pendingValidationRequestId = null;
        setError("craftgpt.build.error.validation_timeout");
        notifyPlayer(minecraft, status);
        timeoutListener.run();
    }

    private boolean matchesCurrent(
        AreaContext requestContext,
        ProjectSnapshot requestSource,
        Optional<AreaContext> currentContext,
        Optional<ProjectSnapshot> currentSource
    ) {
        if (currentContext.isEmpty() || currentSource.isEmpty()) {
            return false;
        }
        return requestContext.selectionId().equals(currentContext.get().selectionId())
            && AreaContextHasher.sha256(requestContext).equals(AreaContextHasher.sha256(currentContext.get()))
            && requestSource.project().projectId().equals(currentSource.get().project().projectId())
            && requestSource.activeVersion().id().equals(currentSource.get().activeVersion().id())
            && requestSource.activeVersion().contentHash().equals(currentSource.get().activeVersion().contentHash());
    }

    private boolean matchesArtifact(
        CompiledBuildArtifact artifact,
        AreaContext context,
        ProjectSnapshot source
    ) {
        return artifact.selectionId().equals(context.selectionId())
            && artifact.contextHash().equals(AreaContextHasher.sha256(context))
            && artifact.projectId().equals(source.project().projectId())
            && artifact.planVersionId().equals(source.activeVersion().id())
            && artifact.planContentHash().equals(source.activeVersion().contentHash());
    }

    private Component rejectionMessage(String code) {
        return switch (code) {
            case "no_selection", "incomplete_selection", "invalid_selection", "selection_mismatch" ->
                Component.translatable("craftgpt.build.error.server.selection");
            case "dimension_mismatch" -> Component.translatable("craftgpt.build.error.server.dimension");
            case "unloaded_area" -> Component.translatable("craftgpt.build.error.server.unloaded");
            case "stale_context" -> Component.translatable("craftgpt.build.error.server.stale");
            case "too_many_operations" -> Component.translatable("craftgpt.build.error.server.too_many");
            case "invalid_palette", "invalid_block_state", "invalid_palette_index" ->
                Component.translatable("craftgpt.build.error.server.invalid_state");
            case "unsafe_block" -> Component.translatable("craftgpt.build.error.server.unsafe");
            case "out_of_bounds", "duplicate_position" ->
                Component.translatable("craftgpt.build.error.server.invalid_operations");
            case "no_changes" -> Component.translatable("craftgpt.build.error.server.no_changes");
            case "rate_limited" -> Component.translatable("craftgpt.build.error.server.rate_limited");
            default -> Component.translatable("craftgpt.build.error.server.generic", code);
        };
    }

    private void cancelRequests() {
        requestGeneration++;
        if (activeApiRequest != null) {
            activeApiRequest.cancel(true);
            activeApiRequest = null;
        }
        requestInFlight = false;
        pendingArtifact = null;
        pendingValidationRequestId = null;
    }

    private void setError(String translationKey) {
        status = Component.translatable(translationKey);
        statusColor = 0xFFFF5555;
    }

    private String safeFailureMessage(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null
            && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "request_failed";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private void notifyPlayer(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }

    public enum ValidationOutcome {
        ACCEPTED,
        REJECTED,
        IGNORED
    }
}
