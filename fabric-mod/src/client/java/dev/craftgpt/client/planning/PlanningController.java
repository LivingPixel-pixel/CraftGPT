package dev.craftgpt.client.planning;

import dev.craftgpt.client.platform.ClientPlatform;

import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.config.CodexGenerationEffort;
import dev.craftgpt.client.config.ContextMode;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiCallResult;
import dev.craftgpt.client.api.ApiCostEstimate;
import dev.craftgpt.client.build.BuildWorkflowController;
import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.client.build.api.BuilderResponseValidator;
import dev.craftgpt.client.build.preview.GhostPreviewManager;
import dev.craftgpt.client.build.preview.GhostPreviewStats;
import dev.craftgpt.client.build.preview.BuildVisualSheetRenderer;
import dev.craftgpt.client.build.preview.MinecraftBlockTextureProvider;
import dev.craftgpt.client.build.storage.BuildArtifactSnapshot;
import dev.craftgpt.client.codex.CodexCliRunner;
import dev.craftgpt.client.codex.CodexChatMessage;
import dev.craftgpt.client.codex.CodexGenerationStage;
import dev.craftgpt.client.codex.CodexRunException;
import dev.craftgpt.client.codex.CodexRunSettings;
import dev.craftgpt.client.planning.api.PlannerApiClient;
import dev.craftgpt.client.planning.api.PlannerException;
import dev.craftgpt.client.planning.api.PlannerRequestSettings;
import dev.craftgpt.client.planning.api.PlannerResponseValidator;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.PlanVersion;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import dev.craftgpt.client.planning.storage.PlanProjectRepository;
import dev.craftgpt.client.placement.PlacementWorkflowController;
import dev.craftgpt.client.placement.RecoveryVisualizationState;
import dev.craftgpt.client.portable.PortableBuildExchange;
import dev.craftgpt.client.portable.PortableExchangeException;
import dev.craftgpt.network.PlacementMaintenanceRequestPayload;
import dev.craftgpt.network.AreaContextRefreshRequestPayload;
import dev.craftgpt.client.ui.IntentionPlanningScreen;
import dev.craftgpt.client.ui.PlanReviewScreen;
import dev.craftgpt.client.ui.PreviewReviewScreen;
import dev.craftgpt.client.ui.PlacementHistoryScreen;
import dev.craftgpt.client.ui.VersionHistoryScreen;
import dev.craftgpt.client.ui.CraftGptSoundFeedback;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.context.AreaContextValidator;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.network.PlanningActionPayload;
import dev.craftgpt.network.BuildPreviewResponsePayload;
import dev.craftgpt.network.PlacementStatusPayload;
import dev.craftgpt.network.PlacementHistoryPayload;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class PlanningController {
    private static final int MAX_CODEX_REPAIR_ATTEMPTS = 2;
    private static final String VISUAL_REVIEW_IMAGE = "build-visual-step-%d.png";
    private static final String VISUAL_REVIEW_INSTRUCTION =
        dev.craftgpt.client.build.api.BuildWorkerContract.REVIEW;

    private final CraftGptConfig config;
    private final PlannerApiClient apiClient;
    private final BuildWorkflowController buildWorkflow;
    private final CodexCliRunner codexRunner = new CodexCliRunner();
    private final PlacementWorkflowController placementWorkflow = new PlacementWorkflowController();
    private PlanProjectRepository repository;
    private PortableBuildExchange portableExchange;

    private AreaContext currentContext;
    private String currentContextHash;
    private boolean requestInFlight;
    private CompletableFuture<ApiCallResult<IntentionSpec>> activeRequest;
    private ApiCallMetrics lastPlanningMetrics;
    private long requestGeneration;
    private long codexGeneration;
    private CompletableFuture<java.nio.file.Path> activeCodexRequest;
    private String pendingCodexInstruction;
    private long pendingCodexRefreshToken;
    private PendingCodexValidation pendingCodexValidation;
    private CodexGenerationStage codexStage = CodexGenerationStage.IDLE;
    private boolean codexPreviewPending;
    private long codexStartedNanos;
    private long codexLastActivityNanos;
    private long codexEndedNanos;
    private int codexActivityEvents;
    private int visualRefinementStep;
    private int visualRefinementTargetSteps;
    private dev.craftgpt.client.codex.ReviewBudget reviewBudget = new dev.craftgpt.client.codex.ReviewBudget(0);
    private Screen visualRefinementReturnScreen;
    private ProjectSnapshot candidateSourceCheckpoint;
    private BuildArtifactSnapshot candidateBuildCheckpoint;
    private java.util.List<dev.craftgpt.client.portable.ReviewDecision.Camera> requestedViews = java.util.List.of();
    private String reviewNotes = "";
    private dev.craftgpt.client.portable.BuildEditScope editScope;
    private String codexLastEventType = "none";
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public PlanningController(CraftGptConfig config) {
        this(config, null, new PlannerApiClient(), new BuildWorkflowController(config));
    }

    PlanningController(CraftGptConfig config, PlanProjectRepository repository, PlannerApiClient apiClient) {
        this(config, repository, apiClient, new BuildWorkflowController(config));
    }

    PlanningController(
        CraftGptConfig config,
        PlanProjectRepository repository,
        PlannerApiClient apiClient,
        BuildWorkflowController buildWorkflow
    ) {
        this.config = config;
        this.repository = repository;
        this.apiClient = apiClient;
        this.buildWorkflow = buildWorkflow;
        this.buildWorkflow.onValidationTimeout(() -> {
            if(codexPreviewPending) {
                restoreCandidateDraft();
                codexPreviewPending=false;
                pendingCodexValidation=null;
                codexStage=CodexGenerationStage.FAILED;
                finishCodexActivity();
                status=Component.translatable("craftgpt.build.error.validation_timeout");
                statusColor=0xFFFFAA55;
            }
        });
    }

    public void updateContext(AreaContext context) {
        AreaContext validatedContext = AreaContextValidator.validate(context);
        String validatedContextHash = AreaContextHasher.sha256(validatedContext);
        boolean pendingCodexRefresh = pendingCodexInstruction != null
            && codexStage == CodexGenerationStage.REFRESHING;
        boolean changed = currentContext == null
            || !currentContext.sameSelection(validatedContext)
            || !validatedContextHash.equals(currentContextHash);
        if (changed) {
            cancelActiveRequest();
            if (!pendingCodexRefresh) cancelCodexSilently();
            buildWorkflow.clearSessionPreview();
            lastPlanningMetrics = null;
            if (!pendingCodexRefresh) status = Component.empty();
        }
        this.currentContext = validatedContext;
        this.currentContextHash = validatedContextHash;
        if (changed) {
            activeForCurrentArea().ifPresent(source ->
                buildWorkflow.loadForCurrentSource(validatedContext, source));
        }
        if (pendingCodexRefresh) {
            String instruction = pendingCodexInstruction;
            long token = pendingCodexRefreshToken;
            pendingCodexInstruction = null;
            pendingCodexRefreshToken = 0L;
            launchCodexBuild(Minecraft.getInstance(), instruction, token);
        }
    }

    public void clearContext() {
        cancelActiveRequest();
        cancelCodexSilently();
        buildWorkflow.clearSessionPreview();
        lastPlanningMetrics = null;
        this.currentContext = null;
        this.currentContextHash = null;
        status = Component.empty();
    }

    public void rejectContext() {
        clearContext();
        setError("craftgpt.planning.error.invalid_context");
    }

    public void setWorldScope(String worldScopeId) {
        cancelActiveRequest();
        cancelCodexSilently();
        currentContext = null;
        currentContextHash = null;
        repository = new PlanProjectRepository(worldScopeId);
        portableExchange = new PortableBuildExchange(worldScopeId);
        lastPlanningMetrics = null;
        buildWorkflow.setWorldScope(worldScopeId);
        placementWorkflow.disconnect();
        status = Component.empty();
    }

    public void disconnect() {
        cancelActiveRequest();
        cancelCodexSilently();
        currentContext = null;
        currentContextHash = null;
        repository = null;
        portableExchange = null;
        lastPlanningMetrics = null;
        buildWorkflow.disconnect();
        placementWorkflow.disconnect();
        status = Component.empty();
    }

    public void handleAction(Minecraft minecraft, PlanningActionPayload payload) {
        switch (payload.action()) {
            case PlanningActionPayload.OPEN -> openServerRequestedPlanning(minecraft, "");
            case PlanningActionPayload.PROMPT, PlanningActionPayload.DISCUSS ->
                openServerRequestedPlanning(minecraft, payload.value());
            case PlanningActionPayload.VERSIONS -> openServerRequestedVersions(minecraft, null);
            case PlanningActionPayload.REVERT -> openServerRequestedVersions(minecraft, payload.value());
            case PlanningActionPayload.PREVIEW -> openServerRequestedPreview(minecraft);
            case PlanningActionPayload.PLACE, PlanningActionPayload.UNDO ->
                openServerRequestedPreview(minecraft);
            case PlanningActionPayload.HISTORY -> openPlacementHistory(minecraft, null);
            case PlanningActionPayload.EXPORT -> exportPortable(minecraft, payload.value());
            case PlanningActionPayload.IMPORT -> importPortable(minecraft);
            case PlanningActionPayload.EXCHANGE -> openPortableFolder();
            default -> setError("craftgpt.planning.error.unknown_action");
        }
    }

    public void openPlanning(Minecraft minecraft, String prefilledText) {
        ClientPlatform.setScreen(minecraft, new IntentionPlanningScreen(null, this, prefilledText));
    }

    public void openVersions(Minecraft minecraft) {
        ClientPlatform.setScreen(minecraft, new VersionHistoryScreen(null, this, null));
    }

    public void openVersions(Minecraft minecraft, Screen parent, String selectedVersionId) {
        ClientPlatform.setScreen(minecraft, new VersionHistoryScreen(parent, this, selectedVersionId));
    }

    public void openPreviewReview(Minecraft minecraft, Screen parent) {
        ClientPlatform.setScreen(minecraft, new PreviewReviewScreen(parent, this));
    }

    public void openPlanReview(Minecraft minecraft, Screen parent) {
        ClientPlatform.setScreen(minecraft, new PlanReviewScreen(parent, this));
    }

    public void submit(Minecraft minecraft, String instruction) {
        placementWorkflow.clearStatus();
        String trimmedInstruction = instruction == null ? "" : instruction.trim();
        if (trimmedInstruction.isEmpty()) {
            setError("craftgpt.planning.error.empty");
            return;
        }
        if (trimmedInstruction.length() > 8_000) {
            setError("craftgpt.planning.error.too_long");
            return;
        }
        if (currentContext == null) {
            setError("craftgpt.planning.error.no_context");
            return;
        }
        if (currentContext.unloadedBlocks() > 0) {
            setError("craftgpt.planning.error.unloaded_context");
            return;
        }
        if (currentContext.volume() > config.maxAreaVolume()) {
            setError("craftgpt.planning.error.local_area_limit");
            return;
        }
        if (config.contextMode().full() && !currentContext.exactBlocks().complete()) {
            setError("craftgpt.planning.error.full_context_unavailable");
            return;
        }
        if (config.contextMode().full()
            && currentContext.exactBlocks().blockCount() > config.maxFullContextBlocks()) {
            setError("craftgpt.planning.error.full_context_too_large");
            return;
        }
        if (requestInFlight || buildWorkflow.requestInFlight()) {
            setError("craftgpt.planning.error.busy");
            return;
        }
        if (repository == null) {
            setError("craftgpt.planning.error.no_scope");
            return;
        }

        PlannerRequestSettings settings = PlannerRequestSettings.from(config);
        try {
            settings.validate();
        } catch (PlannerException exception) {
            setError("craftgpt.planning.error.settings");
            return;
        }

        AreaContext requestContext = currentContext;
        String requestContextHash = currentContextHash;
        Optional<ProjectSnapshot> activeForArea = activeForCurrentArea();
        PlanProjectRepository requestRepository = repository;
        IntentionSpec previousPlan = activeForArea.map(snapshot -> snapshot.activeVersion().intention()).orElse(null);

        long requestToken = ++requestGeneration;
        requestInFlight = true;
        buildWorkflow.clearStatus();
        status = Component.translatable(previousPlan == null
            ? "craftgpt.planning.status.creating"
            : "craftgpt.planning.status.iterating");
        statusColor = 0xFFFFFF55;

        CompletableFuture<ApiCallResult<IntentionSpec>> planningFuture;
        try {
            planningFuture = apiClient.plan(settings, requestContext, trimmedInstruction, previousPlan);
        } catch (RuntimeException exception) {
            requestInFlight = false;
            status = Component.translatable("craftgpt.planning.error.api", safeFailureMessage(exception));
            statusColor = 0xFFFF5555;
            return;
        }
        activeRequest = planningFuture;

        planningFuture.whenComplete((callResult, failure) -> minecraft.execute(() -> {
                if (requestToken != requestGeneration) {
                    return;
                }
                requestInFlight = false;
                activeRequest = null;
                if (failure != null) {
                    status = Component.translatable(
                        "craftgpt.planning.error.api",
                        safeFailureMessage(failure)
                    );
                    statusColor = 0xFFFF5555;
                    notifyPlayer(minecraft, status);
                    return;
                }
                IntentionSpec plan = callResult.value();
                if (currentContext == null
                    || !requestContext.selectionId().equals(currentContext.selectionId())
                    || !requestContextHash.equals(currentContextHash)) {
                    setError("craftgpt.planning.error.stale_context");
                    notifyPlayer(minecraft, status);
                    return;
                }

                try {
                    ProjectSnapshot saved = activeForArea.isPresent()
                        ? requestRepository.iterate(
                            requestContext,
                            trimmedInstruction,
                            plan,
                            settings.model(),
                            settings.reasoningLevel()
                        )
                        : requestRepository.create(
                            requestContext,
                            trimmedInstruction,
                            plan,
                            settings.model(),
                            settings.reasoningLevel()
                        );
                    status = Component.translatable(
                        "craftgpt.planning.status.saved",
                        saved.activeVersion().id()
                    );
                    statusColor = 0xFF55FF55;
                    lastPlanningMetrics = callResult.metrics();
                    requestRepository.saveApiMetrics(saved, callResult.metrics());
                    CraftGptSoundFeedback.complete(minecraft);
                    buildWorkflow.sourcePlanChanged(saved);
                    notifyPlayer(minecraft, Component.translatable(
                        "craftgpt.planning.message.complete",
                        saved.activeVersion().id()
                    ));
                    if (ClientPlatform.screen(minecraft) instanceof IntentionPlanningScreen planningScreen) {
                        planningScreen.onPlanSaved();
                    }
                } catch (IOException | RuntimeException exception) {
                    setError("craftgpt.planning.error.save");
                    notifyPlayer(minecraft, status);
                }
            }));
    }

    public boolean revert(String versionId) {
        if (requestInFlight || buildWorkflow.requestInFlight()) {
            setError("craftgpt.planning.error.busy");
            return false;
        }
        if (repository == null) {
            setError("craftgpt.planning.error.no_scope");
            return false;
        }
        if (activeProject().isEmpty()) {
            setError("craftgpt.planning.error.version");
            return false;
        }
        if (currentContext == null) {
            setError("craftgpt.planning.error.no_context");
            return false;
        }
        if (currentContext.unloadedBlocks() > 0) {
            setError("craftgpt.planning.error.unloaded_context");
            return false;
        }
        try {
            ProjectSnapshot restored = repository.revert(versionId, currentContext);
            status = Component.translatable(
                "craftgpt.planning.status.reverted",
                versionId,
                restored.activeVersion().id()
            );
            statusColor = 0xFF55FF55;
            lastPlanningMetrics = null;
            buildWorkflow.sourcePlanChanged(restored);
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            setError("craftgpt.planning.error.version");
            return false;
        }
    }

    public Optional<AreaContext> currentContext() {
        return Optional.ofNullable(currentContext);
    }

    public Optional<ProjectSnapshot> activeForCurrentArea() {
        if (currentContext == null || repository == null) {
            return Optional.empty();
        }
        return repository.active().filter(snapshot -> snapshot.project().areaContext().sameArea(currentContext));
    }

    public Optional<ProjectSnapshot> activeProject() {
        if (repository == null) {
            return Optional.empty();
        }
        Optional<ProjectSnapshot> active = repository.active();
        if (currentContext == null) {
            return active;
        }
        return active.filter(snapshot -> snapshot.project().areaContext().sameArea(currentContext));
    }

    public List<PlanVersion> versions() {
        return repository == null || activeProject().isEmpty()
            ? List.of()
            : repository.versionsNewestFirst();
    }

    public void compilePreview(Minecraft minecraft) {
        placementWorkflow.clearStatus();
        if (requestInFlight) {
            setError("craftgpt.planning.error.busy");
            return;
        }
        AreaContext context = currentContext;
        ProjectSnapshot source = activeForCurrentArea().orElse(null);
        if (context != null && source != null && !currentPlanMatchesContext(source)) {
            setError("craftgpt.build.error.plan_context_stale");
            return;
        }
        buildWorkflow.compile(
            minecraft,
            context,
            source,
            this::currentContext,
            this::activeForCurrentArea
        );
    }

    public boolean exportPortable(Minecraft minecraft, String instruction) {
        placementWorkflow.clearStatus();
        try {
            PortableBuildExchange.PortableExport exported = preparePortableExport(instruction);
            status = Component.translatable(
                "craftgpt.portable.status.exported",
                exported.requestFile().getFileName().toString()
            );
            statusColor = 0xFF55FF55;
            CraftGptSoundFeedback.complete(minecraft);
            notifyPlayer(minecraft, Component.translatable("craftgpt.portable.message.exported"));
            portableExchange.openLatestFolder();
            return true;
        } catch (PortableExchangeException exception) {
            if ("portable_export_failed".equals(exception.getMessage())) {
                setError("craftgpt.portable.error.export");
            }
            return false;
        }
    }

    /** Starts the complete subscription workflow without requiring an installed skill. */
    public boolean startCodexBuild(Minecraft minecraft, String instruction) {
        placementWorkflow.clearStatus();
        String trimmed = instruction == null ? "" : instruction.trim();
        if (trimmed.isEmpty()) {
            setError("craftgpt.planning.error.empty");
            return false;
        }
        if (trimmed.length() > PlanningActionPayload.MAX_VALUE_LENGTH) {
            setError("craftgpt.planning.error.too_long");
            return false;
        }
        if (currentContext == null) {
            setError("craftgpt.planning.error.no_context");
            return false;
        }
        if (requestInFlight()) {
            setError("craftgpt.planning.error.busy");
            return false;
        }
        if (!ClientPlayNetworking.canSend(AreaContextRefreshRequestPayload.TYPE)) {
            setError("craftgpt.codex.error.context_refresh");
            return false;
        }

        long token = ++codexGeneration;
        pendingCodexInstruction = trimmed;
        pendingCodexRefreshToken = token;
        codexStage = CodexGenerationStage.REFRESHING;
        codexPreviewPending = false;
        pendingCodexValidation = null;
        codexStartedNanos = System.nanoTime();
        codexLastActivityNanos = codexStartedNanos;
        codexEndedNanos = 0L;
        codexActivityEvents = 0;
        codexLastEventType = "context_refresh";
        buildWorkflow.clearStatus();
        status = Component.translatable("craftgpt.codex.status.refreshing_context");
        statusColor = 0xFFFFFF55;
        CraftGptSoundFeedback.working(minecraft);
        try {
            ClientPlayNetworking.send(AreaContextRefreshRequestPayload.create());
        } catch (RuntimeException exception) {
            pendingCodexInstruction = null;
            pendingCodexRefreshToken = 0L;
            codexStage = CodexGenerationStage.FAILED;
            finishCodexActivity();
            setError("craftgpt.codex.error.context_refresh");
            return false;
        }
        CompletableFuture.delayedExecutor(10, java.util.concurrent.TimeUnit.SECONDS).execute(() ->
            minecraft.execute(() -> {
                if (token != codexGeneration || pendingCodexInstruction == null) return;
                pendingCodexInstruction = null;
                pendingCodexRefreshToken = 0L;
                codexStage = CodexGenerationStage.FAILED;
                finishCodexActivity();
                setError("craftgpt.codex.error.context_refresh");
                CraftGptSoundFeedback.error(minecraft);
            })
        );
        return true;
    }

    private void launchCodexBuild(Minecraft minecraft, String instruction, long token) {
        editScope=null;
        requestedViews=java.util.List.of();
        reviewNotes="";
        reviewBudget = new dev.craftgpt.client.codex.ReviewBudget(
            CodexGenerationEffort.visualReviewRounds(config.codexGenerationEffort()));
        PortableBuildExchange.PortableExport exported;
        try {
            exported = preparePortableExport(instruction, true);
        } catch (PortableExchangeException exception) {
            if ("portable_export_failed".equals(exception.getMessage())) {
                setError("craftgpt.portable.error.export");
            }
            visualRefinementStep = 0;
            visualRefinementTargetSteps = 0;
            visualRefinementReturnScreen = null;
            codexStage = CodexGenerationStage.FAILED;
            finishCodexActivity();
            CraftGptSoundFeedback.error(minecraft);
            return;
        }

        AreaContext requestContext = currentContext;
        String requestContextHash = currentContextHash;
        codexStage = CodexGenerationStage.STARTING;
        codexPreviewPending = false;
        codexStartedNanos = System.nanoTime();
        codexLastActivityNanos = codexStartedNanos;
        codexEndedNanos = 0L;
        codexActivityEvents = 0;
        codexLastEventType = "none";
        buildWorkflow.clearStatus();
        status = Component.translatable("craftgpt.codex.status.starting");
        statusColor = 0xFFFFFF55;
        CraftGptSoundFeedback.working(minecraft);

        CodexRunSettings codexSettings;
        try {
            codexSettings = new CodexRunSettings(
                config.codexModel(),
                config.codexReasoningLevel().serializedName()
            );
        } catch (IllegalArgumentException exception) {
            codexStage = CodexGenerationStage.FAILED;
            finishCodexActivity();
            setError("craftgpt.codex.error.settings");
            CraftGptSoundFeedback.error(minecraft);
            return;
        }
        activeCodexRequest = codexRunner.start(exported.directory(), codexSettings, update -> minecraft.execute(() -> {
            CodexGenerationStage stage = update.stage();
            if (token != codexGeneration || !stage.active()) return;
            recordCodexActivity(update.codexEvent());
            if (update.codexEvent()) codexLastEventType = update.eventType();
            if (codexStage != CodexGenerationStage.WRITING || stage == CodexGenerationStage.WRITING) {
                codexStage = stage;
            }
            status = Component.translatable(codexStage == CodexGenerationStage.WRITING
                ? "craftgpt.codex.status.writing"
                : "craftgpt.codex.status.thinking");
            statusColor = 0xFFFFFF55;
        }));
        activeCodexRequest.whenComplete((resultPath, failure) -> minecraft.execute(() ->
            handleCodexResult(
                minecraft,
                requestContext,
                requestContextHash,
                token,
                0,
                resultPath,
                failure
            )
        ));
    }

    private void handleCodexResult(
        Minecraft minecraft,
        AreaContext requestContext,
        String requestContextHash,
        long token,
        int repairAttempts,
        java.nio.file.Path resultPath,
        Throwable failure
    ) {
        if (token != codexGeneration) return;
        activeCodexRequest = null;
        if (failure != null) {
            finishFailedCodexRun(minecraft, failure);
            return;
        }
        if (currentContext == null
            || !requestContext.selectionId().equals(currentContext.selectionId())
            || !requestContextHash.equals(currentContextHash)) {
            visualRefinementStep = 0;
            visualRefinementTargetSteps = 0;
            visualRefinementReturnScreen = null;
            codexStage = CodexGenerationStage.FAILED;
            finishCodexActivity();
            setError("craftgpt.planning.error.stale_context");
            notifyPlayer(minecraft, status);
            return;
        }

        if (visualRefinementStep > 0 && resultPath != null) {
            try {
                var imported = portableExchange.loadResult(resultPath.getParent(), requestContext);
                var decision = imported.result().review();
                if (decision == null) throw new PortableExchangeException("missing_review_decision");
                if (decision != null) {
                    String actualHash = activeBuild().map(d -> dev.craftgpt.client.build.storage.BuildArtifactHasher.sha256(d.artifact())).orElse(null);
                    decision.validate(actualHash, currentContext.width(), currentContext.height(), currentContext.depth());
                    reviewNotes = String.join("\n", decision.findings().stream()
                        .map(f -> f.location()+": "+f.problem()+" Because: "+f.evidence()+" Suggestion: "+f.suggestion()).toList());
                    if ("keep".equals(decision.decision())) {
                        reviewBudget.keep();
                        finishReviewReady(minecraft, "craftgpt.codex.status.kept");
                        return;
                    }
                    if ("inspect".equals(decision.decision())) {
                        if (!reviewBudget.requestInspection()) {
                            finishReviewReady(minecraft, "craftgpt.codex.status.view_budget");
                            return;
                        }
                        requestedViews = decision.cameras();
                        if (!beginVisualRefinementStep(minecraft, visualRefinementReturnScreen)) {
                            codexStage = CodexGenerationStage.FAILED;
                            finishCodexActivity();
                        }
                        return;
                    }
                }
            } catch (RuntimeException exception) {
                if (repairAttempts < MAX_CODEX_REPAIR_ATTEMPTS) {
                    startCodexRepair(minecraft,requestContext,requestContextHash,token,repairAttempts+1,resultPath,exception);
                } else finishFailedCodexRun(minecraft, exception);
                return;
            }
        }
        codexStage = CodexGenerationStage.IMPORTING;
        recordCodexActivity(false);
        status = Component.translatable("craftgpt.codex.status.importing");
        statusColor = 0xFFFFFF55;
        codexPreviewPending = true;
        PortableImportOutcome outcome = importPortableInternal(
            minecraft,
            true,
            resultPath == null ? null : resultPath.getParent(),
            repairAttempts
        );
        if (outcome.kept()) {
            codexPreviewPending = false;
            finishReviewReady(minecraft, "craftgpt.codex.status.kept");
            return;
        }
        if (outcome.started()) {
            pendingCodexValidation = new PendingCodexValidation(
                requestContext,
                requestContextHash,
                token,
                repairAttempts,
                resultPath
            );
            codexStage = CodexGenerationStage.VALIDATING;
            recordCodexActivity(false);
            status = Component.translatable("craftgpt.codex.status.validating");
            statusColor = 0xFFFFFF55;
            return;
        }

        codexPreviewPending = false;
        if (repairAttempts < MAX_CODEX_REPAIR_ATTEMPTS && repairableCodexResult(outcome.failure())) {
            startCodexRepair(
                minecraft,
                requestContext,
                requestContextHash,
                token,
                repairAttempts + 1,
                resultPath,
                outcome.failure()
            );
            return;
        }
        restoreCandidateDraft();
        visualRefinementStep = 0;
        visualRefinementTargetSteps = 0;
        visualRefinementReturnScreen = null;
        codexStage = CodexGenerationStage.FAILED;
        finishCodexActivity();
        CraftGptSoundFeedback.error(minecraft);
        notifyPlayer(minecraft, status);
    }

    private void startCodexRepair(
        Minecraft minecraft,
        AreaContext requestContext,
        String requestContextHash,
        long token,
        int attempt,
        java.nio.file.Path resultPath,
        Throwable validationFailure
    ) {
        codexStage = CodexGenerationStage.REPAIRING;
        recordCodexActivity(false);
        status = Component.translatable(
            "craftgpt.codex.status.repairing_attempt",
            attempt,
            MAX_CODEX_REPAIR_ATTEMPTS
        );
        statusColor = 0xFFFFAA00;
        CraftGptSoundFeedback.working(minecraft);
        activeCodexRequest = codexRunner.repair(
            resultPath.getParent(),
            "result_import",
            validationFailure,
            attempt,
            MAX_CODEX_REPAIR_ATTEMPTS,
            update -> minecraft.execute(() -> {
                if (token != codexGeneration || !codexStage.active()) return;
                recordCodexActivity(update.codexEvent());
                if (update.codexEvent()) codexLastEventType = update.eventType();
                codexStage = CodexGenerationStage.REPAIRING;
                status = Component.translatable(
                    "craftgpt.codex.status.repairing_attempt",
                    attempt,
                    MAX_CODEX_REPAIR_ATTEMPTS
                );
                statusColor = 0xFFFFAA00;
            })
        );
        activeCodexRequest.whenComplete((replacementPath, failure) -> minecraft.execute(() ->
            handleCodexResult(
                minecraft,
                requestContext,
                requestContextHash,
                token,
                attempt,
                replacementPath,
                failure
            )
        ));
    }

    private void finishFailedCodexRun(Minecraft minecraft, Throwable failure) {
        restoreCandidateDraft();
        visualRefinementStep = 0;
        visualRefinementTargetSteps = 0;
        visualRefinementReturnScreen = null;
        CodexRunException runFailure = codexFailure(failure);
        if (runFailure != null && runFailure.code() == CodexRunException.Code.CANCELLED) {
            codexStage = CodexGenerationStage.CANCELLED;
            finishCodexActivity();
            status = Component.translatable("craftgpt.codex.status.cancelled");
            statusColor = 0xFFFFFF55;
            return;
        }
        codexStage = CodexGenerationStage.FAILED;
        finishCodexActivity();
        status = codexFailureMessage(runFailure);
        statusColor = 0xFFFF5555;
        CraftGptSoundFeedback.error(minecraft);
        notifyPlayer(minecraft, status);
    }

    public boolean exportPortableCurrentPlan(Minecraft minecraft) {
        ProjectSnapshot active = activeForCurrentArea().orElse(null);
        if (active == null) {
            setError("craftgpt.build.error.no_plan");
            return false;
        }
        return exportPortable(
            minecraft,
            "Implement the approved active plan exactly as reviewed. Preserve its intent and constraints."
        );
    }

    public boolean startCodexBuildCurrentPlan(Minecraft minecraft) {
        ProjectSnapshot active = activeForCurrentArea().orElse(null);
        if (active == null) {
            setError("craftgpt.build.error.no_plan");
            return false;
        }
        return startCodexBuild(
            minecraft,
            "Implement the approved active plan exactly as reviewed. Preserve its intent and constraints."
        );
    }

    /** Renders all exterior angles and center cutaways, then resumes Codex with the visual sheet. */
    public boolean startVisualReview(Minecraft minecraft, Screen returnScreen) {
        return startVisualReview(minecraft,returnScreen,null);
    }

    public boolean startVisualReview(Minecraft minecraft, Screen returnScreen,
        dev.craftgpt.client.portable.BuildEditScope scope) {
        if (!canVisualReview()) {
            setError("craftgpt.codex.error.visual_review_unavailable");
            return false;
        }

        editScope=scope;
        requestedViews=java.util.List.of();
        reviewNotes="";
        visualRefinementStep = 1;
        visualRefinementTargetSteps = 1;
        reviewBudget = new dev.craftgpt.client.codex.ReviewBudget(1);
        visualRefinementReturnScreen = returnScreen;
        codexStartedNanos = System.nanoTime();
        codexLastActivityNanos = codexStartedNanos;
        codexEndedNanos = 0L;
        codexActivityEvents = 0;
        boolean started=beginVisualRefinementStep(minecraft,returnScreen);
        if(started) ClientPlatform.setScreen(minecraft, new dev.craftgpt.client.ui.CodexGenerationScreen(returnScreen,this));
        return started;
    }

    private boolean beginVisualRefinementStep(Minecraft minecraft, Screen returnScreen) {
        PortableBuildExchange.PortableExport exported;
        try {
            exported = preparePortableExport(
                VISUAL_REVIEW_INSTRUCTION
                    + "\nThis is visual improvement step " + visualRefinementStep
                    + " of " + visualRefinementTargetSteps
                    + ". " + visualRefinementFocus(visualRefinementStep, visualRefinementTargetSteps)
                , true
            );
        } catch (PortableExchangeException exception) {
            visualRefinementStep = 0;
            visualRefinementTargetSteps = 0;
            visualRefinementReturnScreen = null;
            setError("craftgpt.codex.error.visual_review_unavailable");
            return false;
        }

        AreaContext requestContext = currentContext;
        String requestContextHash = currentContextHash;
        Path visualSheetBase = exported.directory()
            .resolve(VISUAL_REVIEW_IMAGE.formatted(visualRefinementStep))
            .normalize();
        long token = ++codexGeneration;
        codexStage = CodexGenerationStage.REVIEWING;
        codexPreviewPending = false;
        pendingCodexValidation = null;
        codexLastEventType = "visual_sheet_" + visualRefinementStep + "_of_" + visualRefinementTargetSteps;
        buildWorkflow.clearStatus();
        status = Component.translatable(
            "craftgpt.codex.status.visual_capture_step",
            visualRefinementStep,
            visualRefinementTargetSteps
        );
        statusColor = 0xFFFFFF55;
        CraftGptSoundFeedback.working(minecraft);
        BuildArtifactSnapshot currentBuild = activeBuild().orElse(null);
        if (currentBuild == null) {
            failVisualReviewCapture(minecraft, returnScreen, new IllegalStateException("visual_build_missing"));
            return false;
        }
        try {
            java.util.Set<String> states = new java.util.LinkedHashSet<>(currentBuild.artifact().palette());
            states.addAll(requestContext.exactBlocks().palette());
            var models=dev.craftgpt.client.build.preview.MinecraftModelSnapshot.capture(minecraft,states);
            var cameras=requestedViews;
            requestedViews=java.util.List.of();
            // Only immutable model data leaves the client thread. PNG encoding stays off the game loop.
            CompletableFuture.supplyAsync(() -> {
                try {
                    return cameras.isEmpty()
                        ? BuildVisualSheetRenderer.renderInspectionSet(visualSheetBase,currentBuild.artifact(),requestContext,models)
                        : BuildVisualSheetRenderer.renderRequested(visualSheetBase,currentBuild.artifact(),requestContext,models,cameras);
                } catch(IOException e) { throw new java.util.concurrent.CompletionException(e); }
            }).whenComplete((sheets,failure) -> minecraft.execute(() -> {
                if(token!=codexGeneration) return;
                if(failure!=null) { failVisualReviewCapture(minecraft,returnScreen,failure); return; }
                launchVisualReview(minecraft,returnScreen,exported.directory(),sheets,requestContext,requestContextHash,token);
            }));
        } catch (RuntimeException exception) {
            failVisualReviewCapture(minecraft,returnScreen,exception);
            return false;
        }
        return true;
    }

    private static String visualRefinementFocus(int step, int totalSteps) {
        if (totalSteps <= 1) {
            return "Perform one holistic pass covering form, function, materials, interiors, details, and final cleanup across all twelve views.";
        }
        if (step == totalSteps) {
            return "Perform the final holistic audit. Remove remaining incoherence and return the strongest complete, placeable version.";
        }
        return switch (step) {
            case 1 -> "Prioritize fundamental massing, silhouette, grounding, proportions, and removal of incoherent objects. Inspect roof and foundation only for architecture.";
            case 2 -> "Apply an object-specific functional audit. For buildings inspect entrances, cutaway circulation, windows, and support. For sculptures inspect anatomy, pose, face, contours, and balance.";
            case 3 -> "Prioritize material coherence, palette separation, texture rhythm, and transitions between structural and decorative materials.";
            case 4 -> "Inspect every exterior angle for facade rhythm, depth, asymmetry, awkward gaps, unsupported details, and recognizable silhouette.";
            default -> "Inspect every cutaway and section for interior clearance, circulation, trapped terrain, center obstructions, and consistency with the exterior.";
        };
    }

    public boolean canVisualReview() {
        GhostPreviewStats stats = previewStats();
        return activeBuild().isPresent()
            && !activeBuildPlaced()
            && currentContext != null
            && currentContext.exactBlocks().complete()
            && stats.hasPreview()
            && stats.visible()
            && stats.dimensionMatches()
            && codexRunner.sessionAvailable()
            && !requestInFlight();
    }

    private void launchVisualReview(
        Minecraft minecraft,
        Screen returnScreen,
        Path directory,
        List<Path> visualSheets,
        AreaContext requestContext,
        String requestContextHash,
        long token
    ) {
        if (currentContext == null
            || !requestContext.selectionId().equals(currentContext.selectionId())
            || !requestContextHash.equals(currentContextHash)) {
            visualRefinementStep = 0;
            visualRefinementTargetSteps = 0;
            visualRefinementReturnScreen = null;
            codexStage = CodexGenerationStage.FAILED;
            finishCodexActivity();
            setError("craftgpt.planning.error.stale_context");
            CraftGptSoundFeedback.error(minecraft);
            ClientPlatform.setScreen(minecraft, returnScreen);
            return;
        }

        status = Component.translatable(
            "craftgpt.codex.status.visual_reviewing_step",
            visualRefinementStep,
            visualRefinementTargetSteps
        );
        codexLastEventType = "image_attached";
        recordCodexActivity(false);
        activeCodexRequest = codexRunner.visualReview(directory, visualSheets, update -> minecraft.execute(() -> {
            if (token != codexGeneration || !codexStage.active()) return;
            recordCodexActivity(update.codexEvent());
            if (update.codexEvent()) codexLastEventType = update.eventType();
            codexStage = CodexGenerationStage.REVIEWING;
            status = Component.translatable(
                "craftgpt.codex.status.visual_reviewing_step",
                visualRefinementStep,
                visualRefinementTargetSteps
            );
            statusColor = 0xFFFFFF55;
        }));
        // Background rounds do not steal focus from gameplay.
        activeCodexRequest.whenComplete((resultPath, failure) -> minecraft.execute(() ->
            handleCodexResult(
                minecraft,
                requestContext,
                requestContextHash,
                token,
                0,
                resultPath,
                failure
            )
        ));
    }

    private void failVisualReviewCapture(Minecraft minecraft, Screen returnScreen, Throwable failure) {
        codexRunner.recordClientFailure("visual_capture", failure);
        visualRefinementStep = 0;
        visualRefinementTargetSteps = 0;
        visualRefinementReturnScreen = null;
        codexStage = CodexGenerationStage.FAILED;
        finishCodexActivity();
        setError("craftgpt.codex.error.visual_capture");
        CraftGptSoundFeedback.error(minecraft);
        notifyPlayer(minecraft, status);
        // Preserve whichever screen or gameplay the user chose.
    }

    public boolean importPortable(Minecraft minecraft) {
        PortableImportOutcome outcome = importPortableInternal(minecraft, false, null, 0);
        if (outcome.kept()) finishReviewReady(minecraft, "craftgpt.codex.status.kept");
        return outcome.started() || outcome.kept();
    }

    private PortableImportOutcome importPortableInternal(
        Minecraft minecraft,
        boolean automaticCodexImport,
        java.nio.file.Path exactDirectory,
        int repairAttempts
    ) {
        placementWorkflow.clearStatus();
        if (currentContext == null) {
            setError("craftgpt.planning.error.no_context");
            return PortableImportOutcome.failed(new PortableExchangeException("missing_area_context"));
        }
        if (portableExchange == null || repository == null) {
            setError("craftgpt.planning.error.no_scope");
            return PortableImportOutcome.failed(new PortableExchangeException("missing_scope"));
        }
        if (requestInFlight || buildWorkflow.requestInFlight() || placementWorkflow.busy()
            || (!automaticCodexImport && codexStage.active())) {
            setError("craftgpt.planning.error.busy");
            return PortableImportOutcome.failed(new PortableExchangeException("busy"));
        }

        try {
            PortableBuildExchange.PortableImport imported = exactDirectory == null
                ? portableExchange.loadLatestResult(currentContext)
                : portableExchange.loadResult(exactDirectory, currentContext);
            Optional<ProjectSnapshot> active = activeForCurrentArea();
            String expectedPreviousHash = imported.request().previousPlanContentHash();
            boolean sameAutomaticRepairRequest = automaticCodexImport
                && repairAttempts > 0
                && exactDirectory != null;
            if (!sameAutomaticRepairRequest && ((expectedPreviousHash == null && active.isPresent())
                || (expectedPreviousHash != null && (active.isEmpty()
                    || !expectedPreviousHash.equals(active.get().activeVersion().contentHash()))))) {
                throw new PortableExchangeException("portable_previous_plan_mismatch");
            }

            int maximumOperations = Math.min(
                Math.min(config.maxBlockChanges(), BuildLimits.HARD_MAX_OPERATIONS),
                imported.request().maximumOperations()
            );
            var resolved = dev.craftgpt.client.portable.PortableIterationResolver.resolve(
                imported.request(), imported.result(), activeBuild().map(d -> d.artifact()).orElse(null), editScope);
            if (resolved.kept()) {
                restoreCandidateDraft();
                buildWorkflow.clearStatus();
                return PortableImportOutcome.keptSuccessfully();
            }
            dev.craftgpt.build.model.BuildDraft importedDraft = resolved.draft();
            PlannerResponseValidator.validate(
                imported.result().plan(),
                currentContext,
                maximumOperations
            );
            BuilderResponseValidator.validateDraft(
                importedDraft,
                currentContext,
                maximumOperations
            );

            if (candidateSourceCheckpoint == null && active.isPresent()) {
                candidateSourceCheckpoint = active.get();
                candidateBuildCheckpoint = activeBuild().orElse(null);
            }
            ProjectSnapshot saved = active.isPresent()
                ? repository.iterate(
                    currentContext,
                    imported.request().instruction(),
                    imported.result().plan(),
                    "craftgpt-building-skill",
                    "subscription"
                )
                : repository.create(
                    currentContext,
                    imported.request().instruction(),
                    imported.result().plan(),
                    "craftgpt-building-skill",
                    "subscription"
                );
            CompiledBuildArtifact artifact = BuilderResponseValidator.validateAndCompilePortable(
                importedDraft,
                currentContext,
                saved.project().projectId(),
                saved.activeVersion(),
                maximumOperations
            );
            lastPlanningMetrics = null;
            buildWorkflow.validatePortableArtifact(minecraft, artifact);
            if (!buildWorkflow.requestInFlight()) {
                throw new PortableExchangeException("portable_server_validation_unavailable");
            }
            CraftGptSoundFeedback.working(minecraft);
            return PortableImportOutcome.startedSuccessfully();
        } catch (IOException | RuntimeException exception) {
            codexRunner.recordClientFailure("result_import", exception);
            if(!automaticCodexImport) restoreCandidateDraft();
            setError("craftgpt.portable.error.import");
            var problem = dev.craftgpt.validation.ValidationProblemJson.fromThrowable(exception).getFirst();
            status = Component.translatable("craftgpt.portable.error.import_detail",
                problem.code(), problem.cause(), problem.suggestion());
            if (!automaticCodexImport) CraftGptSoundFeedback.error(minecraft);
            return PortableImportOutcome.failed(exception);
        }
    }

    private static boolean repairableCodexResult(Throwable failure) {
        if (failure instanceof PlannerException || failure instanceof BuilderException) return true;
        if (!(failure instanceof PortableExchangeException portable)) return false;
        return switch (portable.getMessage()) {
            case "portable_file_invalid", "portable_file_too_large", "portable_result_mismatch",
                "inspection_requires_visual_review", "invalid_review_payload" -> true;
            default -> false;
        };
    }

    private PortableBuildExchange.PortableExport preparePortableExport(String instruction) {
        return preparePortableExport(instruction, false);
    }

    private PortableBuildExchange.PortableExport preparePortableExport(
        String instruction,
        boolean refreshedCodexLaunch
    ) {
        String trimmed = instruction == null ? "" : instruction.trim();
        if (trimmed.isEmpty()) {
            setError("craftgpt.planning.error.empty");
            throw new PortableExchangeException("empty_instruction");
        }
        if (trimmed.length() > PlanningActionPayload.MAX_VALUE_LENGTH) {
            setError("craftgpt.planning.error.too_long");
            throw new PortableExchangeException("instruction_too_long");
        }
        if (currentContext == null) {
            setError("craftgpt.planning.error.no_context");
            throw new PortableExchangeException("missing_context");
        }
        if (currentContext.unloadedBlocks() > 0) {
            setError("craftgpt.planning.error.unloaded_context");
            throw new PortableExchangeException("unloaded_context");
        }
        if (!currentContext.exactBlocks().complete()) {
            setError("craftgpt.planning.error.full_context_unavailable");
            throw new PortableExchangeException("exact_context_unavailable");
        }
        if (portableExchange == null || repository == null) {
            setError("craftgpt.planning.error.no_scope");
            throw new PortableExchangeException("missing_scope");
        }
        if (dev.craftgpt.client.codex.ReviewBudget.exportBlocked(requestInFlight,
            buildWorkflow.requestInFlight(), placementWorkflow.busy(),
            codexStage.active(), refreshedCodexLaunch)) {
            setError("craftgpt.planning.error.busy");
            throw new PortableExchangeException("busy");
        }
        IntentionSpec previous = activeForCurrentArea()
            .map(snapshot -> snapshot.activeVersion().intention())
            .orElse(null);
        return portableExchange.export(
            currentContext,
            trimmed,
            Math.min(config.maxBlockChanges(), BuildLimits.HARD_MAX_OPERATIONS),
            previous,
            activeBuild().map(BuildArtifactSnapshot::artifact).orElse(null),
            refreshedCodexLaunch && visualRefinementStep>0 ? editScope : null
        );
    }

    public boolean openPortableFolder() {
        if (portableExchange == null || !portableExchange.openLatestFolder()) {
            setError("craftgpt.portable.error.no_export");
            return false;
        }
        return true;
    }

    public boolean openPortableViewer() {
        if (portableExchange == null || !portableExchange.openLatestViewer()) {
            setError("craftgpt.portable.error.no_export");
            return false;
        }
        return true;
    }

    public void handleBuildValidationResponse(
        Minecraft minecraft,
        BuildPreviewResponsePayload response
    ) {
        PendingCodexValidation validation = pendingCodexValidation;
        BuildWorkflowController.ValidationOutcome outcome = buildWorkflow.handleValidationResponse(
            minecraft,
            response,
            currentContext(),
            activeForCurrentArea()
        );
        if (outcome == BuildWorkflowController.ValidationOutcome.ACCEPTED) {
            candidateSourceCheckpoint = null;
            candidateBuildCheckpoint = null;
            pendingCodexValidation = null;
            CraftGptSoundFeedback.success(minecraft);
            if (codexPreviewPending) {
                codexPreviewPending = false;
                if (visualRefinementStep == 0) {
                    int automaticReviews = reviewBudget.rounds();
                    if (automaticReviews > 0) {
                        pendingCodexValidation = null;
                        visualRefinementStep = 1;
                        visualRefinementTargetSteps = automaticReviews;
                        visualRefinementReturnScreen = null;
                        codexStage = CodexGenerationStage.REVIEWING;
                        if (beginVisualRefinementStep(minecraft, null)) {
                            return;
                        }
                        visualRefinementStep = 0;
                        visualRefinementTargetSteps = 0;
                        visualRefinementReturnScreen = null;
                        codexStage = CodexGenerationStage.FAILED;
                        finishCodexActivity();
                        return;
                    }
                }
                if (visualRefinementStep > 0 && reviewBudget.hasNext()) reviewBudget.completeRound();
                if (visualRefinementStep > 0
                    && visualRefinementStep < visualRefinementTargetSteps) {
                    pendingCodexValidation = null;
                    visualRefinementStep++;
                    codexStage = CodexGenerationStage.REVIEWING;
                    if (beginVisualRefinementStep(minecraft, visualRefinementReturnScreen)) {
                        return;
                    }
                    codexStage = CodexGenerationStage.FAILED;
                    finishCodexActivity();
                    CraftGptSoundFeedback.error(minecraft);
                    return;
                }
                visualRefinementStep = 0;
                visualRefinementTargetSteps = 0;
                visualRefinementReturnScreen = null;
                codexStage = CodexGenerationStage.READY;
                finishCodexActivity();
                status = Component.translatable("craftgpt.codex.status.ready");
                statusColor = 0xFF55FF55;
                CraftGptSoundFeedback.complete(minecraft);
                notifyPlayer(minecraft, Component.translatable("craftgpt.codex.message.ready"));
            } else if (ClientPlatform.screen(minecraft) instanceof PreviewReviewScreen reviewScreen) {
                reviewScreen.onBuildUpdated();
            } else if (allowsServerRequestedScreen(minecraft)) {
                openPreviewReview(minecraft, ClientPlatform.screen(minecraft));
            }
        } else if (outcome == BuildWorkflowController.ValidationOutcome.REJECTED && codexPreviewPending) {
            codexPreviewPending = false;
            pendingCodexValidation = null;
            if (validation != null
                && validation.token() == codexGeneration
                && validation.repairAttempts() < MAX_CODEX_REPAIR_ATTEMPTS
                && repairableServerValidation(response.code())) {
                BuilderException failure = new BuilderException(response.decodedProblems());
                codexRunner.recordClientFailure("server_validation", failure);
                buildWorkflow.clearStatus();
                startCodexRepair(
                    minecraft,
                    validation.context(),
                    validation.contextHash(),
                    validation.token(),
                    validation.repairAttempts() + 1,
                    validation.resultPath(),
                    failure
                );
                return;
            }
            restoreCandidateDraft();
            visualRefinementStep = 0;
            visualRefinementTargetSteps = 0;
            visualRefinementReturnScreen = null;
            codexStage = CodexGenerationStage.FAILED;
            finishCodexActivity();
        }
        if(outcome == BuildWorkflowController.ValidationOutcome.REJECTED && !codexPreviewPending
            && codexStage != CodexGenerationStage.REPAIRING) restoreCandidateDraft();
    }

    private static boolean repairableServerValidation(String code) {
        return switch (code == null ? "" : code) {
            case "invalid_artifact", "too_many_operations", "invalid_palette",
                 "invalid_block_state", "unsafe_block", "out_of_bounds",
                 "duplicate_position", "invalid_palette_index", "no_changes", "incomplete_door", "unsupported_door" -> true;
            default -> false;
        };
    }

    private void restoreCandidateDraft() {
        if (candidateSourceCheckpoint == null) return;
        try {
            if (currentContext != null && candidateSourceCheckpoint.project().areaContext().sameSelection(currentContext)) {
                repository.restoreHead(candidateSourceCheckpoint);
                buildWorkflow.restoreDraft(candidateBuildCheckpoint, currentContext);
            }
        } catch (IOException | RuntimeException exception) {
            codexRunner.recordClientFailure("draft_recovery", exception);
        } finally {
            candidateSourceCheckpoint=null;
            candidateBuildCheckpoint=null;
        }
    }

    private void finishReviewReady(Minecraft minecraft, String key) {
        visualRefinementStep=0;
        visualRefinementTargetSteps=0;
        requestedViews=java.util.List.of();
        codexStage=CodexGenerationStage.READY;
        finishCodexActivity();
        status=Component.translatable(key);
        statusColor=0xFF55FF55;
        CraftGptSoundFeedback.complete(minecraft);
        notifyPlayer(minecraft,status);
    }

    public String reviewNotes() { return reviewNotes; }

    /** Select generated geometry along the player's gaze, including blocks not yet in the world. */
    public Optional<dev.craftgpt.client.portable.BuildEditScope> aimedEditScope(Minecraft minecraft,int radius,String instruction) {
        if(minecraft.player==null || currentContext==null || activeBuild().isEmpty() || radius<0 || radius>8)
            return Optional.empty();
        var eye=minecraft.player.getEyePosition();
        var end=eye.add(minecraft.player.getViewVector(1f).scale(128));
        dev.craftgpt.build.model.BuildOperation best=null; double nearest=Double.POSITIVE_INFINITY;
        for(var op:activeBuild().get().artifact().operations()) {
            double x=currentContext.min().x()+op.relativeX(),y=currentContext.min().y()+op.relativeY(),z=currentContext.min().z()+op.relativeZ();
            var hit=new net.minecraft.world.phys.AABB(x,y,z,x+1,y+1,z+1).clip(eye,end);
            if(hit.isPresent() && eye.distanceToSqr(hit.get())<nearest){nearest=eye.distanceToSqr(hit.get());best=op;}
        }
        if(best==null)return Optional.empty();
        return Optional.of(new dev.craftgpt.client.portable.BuildEditScope(
            java.util.List.of(Math.max(0,best.relativeX()-radius),Math.max(0,best.relativeY()-radius),Math.max(0,best.relativeZ()-radius)),
            java.util.List.of(Math.min(currentContext.width()-1,best.relativeX()+radius),Math.min(currentContext.height()-1,best.relativeY()+radius),Math.min(currentContext.depth()-1,best.relativeZ()+radius)),instruction));
    }

    public CompletableFuture<java.util.List<Path>> inspectionImages(Minecraft minecraft) {
        var draft=activeBuild().orElse(null);
        var context=currentContext;
        if(draft==null||context==null||!context.exactBlocks().complete())
            return CompletableFuture.failedFuture(new IllegalStateException("missing_inspection_context"));
        java.util.Set<String> states=new java.util.LinkedHashSet<>(draft.artifact().palette());
        states.addAll(context.exactBlocks().palette());
        var models=dev.craftgpt.client.build.preview.MinecraftModelSnapshot.capture(minecraft,states);
        Path output=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("craftgpt/inspections").resolve(draft.artifact().buildId()).resolve("build.png");
        return CompletableFuture.supplyAsync(()->{
            try{return BuildVisualSheetRenderer.renderInspectionSet(output,draft.artifact(),context,models);}
            catch(IOException e){throw new java.util.concurrent.CompletionException(e);}
        });
    }

    public Optional<BuildArtifactSnapshot> activeBuild() {
        return buildWorkflow.activeBuild();
    }

    /** True only when the active plan was produced from the current scanned world state. */
    public boolean currentPlanMatchesContext() {
        return activeForCurrentArea().map(this::currentPlanMatchesContext).orElse(false);
    }

    public boolean acceptPreview(Minecraft minecraft) {
        ProjectSnapshot source = activeForCurrentArea().orElse(null);
        if (source == null || currentContext == null || !buildWorkflow.accept(source, currentContext)) {
            return false;
        }
        notifyPlayer(minecraft, Component.translatable("craftgpt.build.message.accepted"));
        CraftGptSoundFeedback.select(minecraft);
        return true;
    }

    public boolean abandonPreview() {
        ProjectSnapshot source = activeForCurrentArea().orElse(null);
        return buildWorkflow.abandon(source);
    }

    public boolean togglePreviewVisible() {
        return GhostPreviewManager.INSTANCE.toggleVisible();
    }

    public boolean previewVisible() {
        return GhostPreviewManager.INSTANCE.isVisible();
    }

    public GhostPreviewStats previewStats() {
        return GhostPreviewManager.INSTANCE.stats();
    }

    public boolean placeAccepted(Minecraft minecraft) {
        BuildArtifactSnapshot snapshot = activeBuild().orElse(null);
        if (snapshot == null || !snapshot.accepted()) {
            setError("craftgpt.placement.error.not_accepted");
            return false;
        }
        return placementWorkflow.place(
            minecraft,
            snapshot,
            config.allowBlockEntityReplacement()
        );
    }

    public boolean undoPlacement(Minecraft minecraft) {
        return placementWorkflow.undo(minecraft);
    }

    public void refreshPlacementStatus() {
        placementWorkflow.refreshStatus();
    }

    public void handlePlacementStatus(Minecraft minecraft, PlacementStatusPayload payload) {
        PlacementWorkflowController.Outcome outcome = placementWorkflow.handle(payload);
        boolean activeBuildMatchesPlacement = activeBuild()
            .map(snapshot -> placementWorkflow.latestMatchesBuild(snapshot.artifact().buildId()))
            .orElse(false);
        switch (outcome) {
            case PLACING, PLACED, REDOING, REDONE, REDONE_WITH_CONFLICTS, INTERRUPTED -> {
                if (activeBuildMatchesPlacement) {
                    buildWorkflow.hideGhostPreview();
                }
            }
            case UNDONE -> {
                if (activeBuildMatchesPlacement && currentContext != null) {
                    buildWorkflow.restoreGhostPreview(currentContext);
                }
            }
            default -> {
            }
        }
        if (outcome == PlacementWorkflowController.Outcome.MAINTENANCE) {
            CraftGptSoundFeedback.success(minecraft);
            placementWorkflow.refreshHistory();
        }
        if (outcome == PlacementWorkflowController.Outcome.REJECTED) {
            CraftGptSoundFeedback.error(minecraft);
        }
        if (outcome == PlacementWorkflowController.Outcome.PLACED
            || outcome == PlacementWorkflowController.Outcome.UNDONE
            || outcome == PlacementWorkflowController.Outcome.UNDONE_WITH_CONFLICTS
            || outcome == PlacementWorkflowController.Outcome.REDONE
            || outcome == PlacementWorkflowController.Outcome.REDONE_WITH_CONFLICTS
            || outcome == PlacementWorkflowController.Outcome.INTERRUPTED) {
            notifyPlayer(minecraft, placementWorkflow.status());
            if (outcome == PlacementWorkflowController.Outcome.PLACED) {
                CraftGptSoundFeedback.complete(minecraft);
            } else if (outcome != PlacementWorkflowController.Outcome.INTERRUPTED) {
                CraftGptSoundFeedback.success(minecraft);
            } else {
                CraftGptSoundFeedback.error(minecraft);
            }
            placementWorkflow.refreshHistory();
        }
    }

    public void handlePlacementHistory(Minecraft minecraft, PlacementHistoryPayload payload) {
        if (placementWorkflow.handleHistory(payload)
            && ClientPlatform.screen(minecraft) instanceof PlacementHistoryScreen historyScreen) {
            historyScreen.onHistoryUpdated();
        }
    }

    public void openPlacementHistory(Minecraft minecraft, Screen parent) {
        ClientPlatform.setScreen(minecraft, new PlacementHistoryScreen(parent, this));
    }

    public void refreshPlacementHistory() {
        placementWorkflow.refreshHistory();
    }

    public List<PlacementHistoryEntry> placementHistory() {
        return placementWorkflow.history();
    }

    public int placementHistoryRevision() {
        return placementWorkflow.historyRevision();
    }

    public boolean recoverPlacement(String placementId, String action) {
        return placementWorkflow.recover(placementId, action);
    }

    public boolean maintainPlacement(String placementId, String action) {
        int retention = PlacementMaintenanceRequestPayload.PRUNE.equals(action)
            ? config.recoveryRetentionCount() : 0;
        return placementWorkflow.maintain(placementId, action, retention);
    }

    public boolean toggleRecoveryVisualization(String placementId) {
        return RecoveryVisualizationState.INSTANCE.toggle(placementId);
    }

    public boolean recoveryVisualizationVisible(String placementId) {
        return RecoveryVisualizationState.INSTANCE.visibleFor(placementId);
    }

    public boolean historyMatchesActiveBuild(PlacementHistoryEntry entry) {
        return entry != null && activeBuild()
            .map(snapshot -> snapshot.artifact().buildId().equals(entry.buildId()))
            .orElse(false);
    }

    public int recoveryRetentionCount() {
        return config.recoveryRetentionCount();
    }

    public ContextMode contextMode() {
        return config.contextMode();
    }

    public String codexModel() {
        return config.codexModel();
    }

    public String codexReasoningLevel() {
        return config.codexReasoningLevel().serializedName();
    }

    public int codexGenerationEffort() {
        return config.codexGenerationEffort();
    }

    public ApiCostEstimate estimatePlanningCost(String instruction) {
        if (currentContext == null || instruction == null || instruction.isBlank()) {
            return ApiCostEstimate.unavailable();
        }
        try {
            PlannerRequestSettings settings = PlannerRequestSettings.from(config);
            settings.validate();
            IntentionSpec previous = activeForCurrentArea()
                .map(snapshot -> snapshot.activeVersion().intention())
                .orElse(null);
            return apiClient.estimateCost(settings, currentContext, instruction.trim(), previous);
        } catch (RuntimeException exception) {
            return ApiCostEstimate.unavailable();
        }
    }

    public ApiCostEstimate estimateBuildCost() {
        return buildWorkflow.estimateCost(
            currentContext,
            activeForCurrentArea().orElse(null)
        );
    }

    public Optional<ApiCallMetrics> lastPlanningMetrics() {
        if (lastPlanningMetrics != null) {
            return Optional.of(lastPlanningMetrics);
        }
        return repository == null
            ? Optional.empty()
            : activeForCurrentArea()
                .flatMap(snapshot -> repository.apiMetrics(snapshot.activeVersion()));
    }

    public Optional<ApiCallMetrics> lastBuildMetrics() {
        return buildWorkflow.lastMetrics();
    }

    public int maxFullContextBlocks() {
        return config.maxFullContextBlocks();
    }

    public boolean blockEntityReplacementEnabled() {
        return config.allowBlockEntityReplacement();
    }

    public boolean canPlaceAccepted() {
        BuildArtifactSnapshot snapshot = activeBuild().orElse(null);
        return snapshot != null
            && snapshot.accepted()
            && !placementWorkflow.busy()
            && !placementWorkflow.placedForBuild(snapshot.artifact().buildId());
    }

    public boolean placementBusy() {
        return placementWorkflow.busy();
    }

    public boolean placementPlaced() {
        return placementWorkflow.placed();
    }

    public boolean activeBuildPlaced() {
        return activeBuild()
            .map(snapshot -> placementWorkflow.placedForBuild(snapshot.artifact().buildId()))
            .orElse(false);
    }

    public boolean undoAvailable() {
        return placementWorkflow.undoAvailable();
    }

    public int placementProcessed() {
        return placementWorkflow.processed();
    }

    public int placementTotal() {
        return placementWorkflow.total();
    }

    public int placementConflicts() {
        return placementWorkflow.conflicts();
    }

    private boolean currentPlanMatchesContext(ProjectSnapshot source) {
        return currentContextHash != null
            && source != null
            && currentContextHash.equals(source.activeVersion().contextHash());
    }

    public boolean requestInFlight() {
        return requestInFlight || buildWorkflow.requestInFlight() || placementWorkflow.busy()
            || codexStage.active();
    }

    public boolean generationRequestInFlight() {
        return requestInFlight || buildWorkflow.requestInFlight() || codexStage.active();
    }

    public boolean cancelGeneration() {
        if (codexStage.active()) {
            cancelCodexGeneration();
            return true;
        }
        if (requestInFlight) {
            cancelActiveRequest();
            status = Component.translatable("craftgpt.planning.status.cancelled");
            statusColor = 0xFFFFFF55;
            return true;
        }
        return buildWorkflow.cancelActiveRequest();
    }

    public boolean cancelCodexGeneration() {
        restoreCandidateDraft();
        if (!codexStage.active()) return false;
        codexGeneration++;
        codexRunner.cancel();
        activeCodexRequest = null;
        pendingCodexInstruction = null;
        pendingCodexRefreshToken = 0L;
        pendingCodexValidation = null;
        codexPreviewPending = false;
        visualRefinementStep = 0;
        visualRefinementTargetSteps = 0;
        visualRefinementReturnScreen = null;
        if (buildWorkflow.requestInFlight()) buildWorkflow.cancelActiveRequest();
        codexStage = CodexGenerationStage.CANCELLED;
        finishCodexActivity();
        status = Component.translatable("craftgpt.codex.status.cancelled");
        statusColor = 0xFFFFFF55;
        return true;
    }

    public CodexGenerationStage codexStage() {
        return codexStage;
    }

    public long codexElapsedSeconds() {
        if (codexStartedNanos == 0L) return 0L;
        long end = codexStage.active()
            ? System.nanoTime()
            : (codexEndedNanos == 0L ? System.nanoTime() : codexEndedNanos);
        return Math.max(0L, (end - codexStartedNanos) / 1_000_000_000L);
    }

    public long codexSecondsSinceActivity() {
        if (codexLastActivityNanos == 0L) return 0L;
        long end = codexStage.active()
            ? System.nanoTime()
            : (codexEndedNanos == 0L ? System.nanoTime() : codexEndedNanos);
        return Math.max(0L, (end - codexLastActivityNanos) / 1_000_000_000L);
    }

    public int codexActivityEvents() {
        return codexActivityEvents;
    }

    public int visualRefinementStep() {
        return visualRefinementStep;
    }

    public int visualRefinementSteps() {
        return visualRefinementTargetSteps;
    }

    public String codexLastEventType() {
        return codexLastEventType;
    }

    public List<String> codexLogLines() {
        return codexRunner.logLines();
    }

    public List<CodexChatMessage> codexChatMessages() {
        return codexRunner.chatMessages();
    }

    public boolean openCodexLogFile() {
        return codexRunner.openLogFile();
    }

    public boolean openCodexEventLogFile() {
        return codexRunner.openEventLogFile();
    }

    public boolean codexSessionAvailable() {
        return codexRunner.sessionAvailable() && !codexStage.active();
    }

    public boolean codexAppSessionAvailable() {
        return codexRunner.appSessionAvailable();
    }

    public boolean openCodexSessionInApp() {
        return codexRunner.openSessionInApp();
    }

    public boolean openCodexSessionConsole() {
        return !codexStage.active() && codexRunner.openSessionConsole();
    }

    public void tick(Minecraft minecraft) {
        if (codexStage == CodexGenerationStage.VALIDATING
            && codexPreviewPending
            && !buildWorkflow.requestInFlight()) {
            codexPreviewPending = false;
            pendingCodexValidation = null;
            codexStage = activeBuild().isPresent()
                ? CodexGenerationStage.READY
                : CodexGenerationStage.FAILED;
            finishCodexActivity();
            if (codexStage == CodexGenerationStage.FAILED) {
                notifyPlayer(minecraft, Component.translatable("craftgpt.codex.error.validation_failed"));
            }
        }
    }

    public Component status() {
        if (placementWorkflow.hasStatus()) {
            return placementWorkflow.status();
        }
        return buildWorkflow.hasStatus() ? buildWorkflow.status() : status;
    }

    public int statusColor() {
        if (placementWorkflow.hasStatus()) {
            return placementWorkflow.statusColor();
        }
        return buildWorkflow.hasStatus() ? buildWorkflow.statusColor() : statusColor;
    }

    private void setError(String translationKey) {
        placementWorkflow.clearStatus();
        buildWorkflow.clearStatus();
        status = Component.translatable(translationKey);
        statusColor = 0xFFFF5555;
    }

    private void cancelActiveRequest() {
        requestGeneration++;
        if (activeRequest != null) {
            activeRequest.cancel(true);
            activeRequest = null;
        }
        requestInFlight = false;
    }

    private void cancelCodexSilently() {
        restoreCandidateDraft();
        editScope=null;
        visualRefinementStep=0;
        visualRefinementTargetSteps=0;
        visualRefinementReturnScreen=null;
        if (codexStage.active()) codexRunner.cancel();
        codexGeneration++;
        activeCodexRequest = null;
        pendingCodexInstruction = null;
        pendingCodexRefreshToken = 0L;
        pendingCodexValidation = null;
        codexPreviewPending = false;
        codexStage = CodexGenerationStage.IDLE;
        codexStartedNanos = 0L;
        codexLastActivityNanos = 0L;
        codexEndedNanos = 0L;
        codexActivityEvents = 0;
        codexLastEventType = "none";
    }

    private void recordCodexActivity(boolean codexEvent) {
        codexLastActivityNanos = System.nanoTime();
        if (codexEvent) codexActivityEvents++;
    }

    private void finishCodexActivity() {
        recordCodexActivity(false);
        codexEndedNanos = codexLastActivityNanos;
    }

    private void notifyPlayer(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            ClientPlatform.notifyPlayer(minecraft, message);
        }
    }

    private void openServerRequestedPlanning(Minecraft minecraft, String draft) {
        if (allowsServerRequestedScreen(minecraft)) {
            openPlanning(minecraft, draft);
        } else {
            status = Component.translatable("craftgpt.planning.status.ready");
            statusColor = 0xFFFFFF55;
        }
    }

    private void openServerRequestedVersions(Minecraft minecraft, String selectedVersionId) {
        if (allowsServerRequestedScreen(minecraft)) {
            openVersions(minecraft, null, selectedVersionId);
        } else {
            status = Component.translatable("craftgpt.planning.status.versions_ready");
            statusColor = 0xFFFFFF55;
        }
    }

    private void openServerRequestedPreview(Minecraft minecraft) {
        if (allowsServerRequestedScreen(minecraft)) {
            openPreviewReview(minecraft, ClientPlatform.screen(minecraft));
        } else {
            status = Component.translatable("craftgpt.build.status.review_ready");
            statusColor = 0xFFFFFF55;
        }
    }

    private boolean allowsServerRequestedScreen(Minecraft minecraft) {
        return ClientPlatform.screen(minecraft) == null
            || ClientPlatform.screen(minecraft) instanceof ChatScreen
            || ClientPlatform.screen(minecraft) instanceof IntentionPlanningScreen
            || ClientPlatform.screen(minecraft) instanceof PlanReviewScreen
            || ClientPlatform.screen(minecraft) instanceof VersionHistoryScreen
            || ClientPlatform.screen(minecraft) instanceof PreviewReviewScreen;
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

    private CodexRunException codexFailure(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null
            && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        return cause instanceof CodexRunException codex ? codex : null;
    }

    private Component codexFailureMessage(CodexRunException failure) {
        if (failure == null) return Component.translatable("craftgpt.codex.error.failed");
        return Component.translatable(switch (failure.code()) {
            case NOT_INSTALLED -> "craftgpt.codex.error.not_installed";
            case CHATGPT_LOGIN_REQUIRED -> "craftgpt.codex.error.login";
            case RESULT_MISSING -> "craftgpt.codex.error.result_missing";
            case PROCESS_FAILED -> "craftgpt.codex.error.failed";
            case CANCELLED -> "craftgpt.codex.status.cancelled";
        });
    }

    private record PortableImportOutcome(boolean started, boolean kept, Throwable failure) {
        static PortableImportOutcome startedSuccessfully() {
            return new PortableImportOutcome(true, false, null);
        }

        static PortableImportOutcome keptSuccessfully() {
            return new PortableImportOutcome(false, true, null);
        }

        static PortableImportOutcome failed(Throwable failure) {
            return new PortableImportOutcome(false, false, failure);
        }
    }

    private record PendingCodexValidation(
        AreaContext context,
        String contextHash,
        long token,
        int repairAttempts,
        java.nio.file.Path resultPath
    ) {
    }
}
