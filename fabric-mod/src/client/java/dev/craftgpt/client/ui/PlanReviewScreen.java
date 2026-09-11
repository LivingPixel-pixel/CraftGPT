package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;

import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiCostEstimate;
import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanVersion;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Human-readable review of the complete structured plan before build compilation. */
public final class PlanReviewScreen extends Screen {
    private static final int CONTENT_WIDTH = 460;
    private static final int PAGE_COUNT = 5;

    private final Screen parent;
    private final PlanningController controller;
    private Optional<ProjectSnapshot> snapshot = Optional.empty();
    private int page;
    private Button previousButton;
    private Button nextButton;
    private Button pageButton;
    private Button generateButton;
    private Button codexButton;

    public PlanReviewScreen(Screen parent, PlanningController controller) {
        super(Component.translatable("craftgpt.plan_review.title"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        snapshot = controller.activeForCurrentArea();
        int contentWidth = Math.min(CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        int navigationY = height - 58;
        int actionsY = height - 32;

        previousButton = addRenderableWidget(Button.builder(Component.literal("←"), button -> {
            page = Math.max(0, page - 1);
            updateButtons();
            CraftGptSoundFeedback.page(minecraft);
        }).bounds(left, navigationY, 44, 20).build());

        nextButton = addRenderableWidget(Button.builder(Component.literal("→"), button -> {
            page = Math.min(PAGE_COUNT - 1, page + 1);
            updateButtons();
            CraftGptSoundFeedback.page(minecraft);
        }).bounds(left + contentWidth - 44, navigationY, 44, 20).build());

        int pageWidth = contentWidth - 100;
        pageButton = addRenderableWidget(Button.builder(pageLabel(), button -> {
        }).bounds(left + 50, navigationY, pageWidth, 20).build());
        pageButton.active = false;

        int third=(contentWidth-16)/3;
        codexButton=addRenderableWidget(Button.builder(Component.translatable("craftgpt.codex.build_short"),button->{
            if(controller.startCodexBuildCurrentPlan(minecraft))ClientPlatform.setScreen(minecraft, new CodexGenerationScreen(parent,controller));
        }).bounds(left,actionsY,third,20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftgpt.ui.tools"),button->
            ClientPlatform.setScreen(minecraft, new ActionMenuScreen(this,"craftgpt.ui.plan_tools","craftgpt.ui.tools_hint",List.of(
                ActionMenuScreen.Entry.of("craftgpt.plan_review.revise",()->!controller.requestInFlight(),
                    screen->ClientPlatform.setScreen(minecraft, new IntentionPlanningScreen(screen,controller,""))),
                ActionMenuScreen.Entry.of("craftgpt.planning.versions",()->!controller.requestInFlight(),
                    screen->controller.openVersions(minecraft,screen,null)),
                ActionMenuScreen.Entry.of("craftgpt.plan_review.generate_api",()->controller.currentPlanMatchesContext()&&!controller.requestInFlight(),
                    screen->UiMenus.confirm(screen,"craftgpt.plan_review.generate_api","craftgpt.ui.api_charge",()->{
                        ClientPlatform.setScreen(minecraft, new PreviewReviewScreen(this,controller));controller.compilePreview(minecraft);
                    })))))).bounds(left+third+8,actionsY,third,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.close"),button->onClose())
            .bounds(left+2*(third+8),actionsY,contentWidth-2*(third+8),20).build());

        updateButtons();
    }

    private void updateButtons() {
        if (previousButton != null) previousButton.active = page > 0;
        if (nextButton != null) nextButton.active = page < PAGE_COUNT - 1;
        if (pageButton != null) pageButton.setMessage(pageLabel());
        if (generateButton != null) {
            generateButton.active = snapshot.isPresent()
                && controller.currentPlanMatchesContext()
                && !controller.requestInFlight();
        }
        if (codexButton != null) {
            codexButton.active = snapshot.isPresent()
                && controller.currentPlanMatchesContext()
                && !controller.requestInFlight();
        }
    }

    private Component pageLabel() {
        return Component.translatable("craftgpt.plan_review.page", page + 1, PAGE_COUNT, pageTitle());
    }

    private Component pageTitle() {
        return Component.translatable(switch (page) {
            case 0 -> "craftgpt.plan_review.page.overview";
            case 1 -> "craftgpt.plan_review.page.features";
            case 2 -> "craftgpt.plan_review.page.materials";
            case 3 -> "craftgpt.plan_review.page.constraints";
            default -> "craftgpt.plan_review.page.implementation";
        });
    }

    @Override
    public void onClose() {
        ClientPlatform.setScreen(minecraft, parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        snapshot = controller.activeForCurrentArea();
        updateButtons();
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int contentWidth = Math.min(CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFD966);

        if (snapshot.isEmpty()) {
            graphics.centeredText(
                font,
                Component.translatable("craftgpt.plan_review.none"),
                width / 2,
                38,
                0xFFFF5555
            );
            return;
        }

        ProjectSnapshot project = snapshot.get();
        PlanVersion version = project.activeVersion();
        graphics.text(font, font.plainSubstrByWidth(Component.translatable(
            "craftgpt.ui.plan_version",
            version.id(),
            project.project().name()
        ).getString(), contentWidth), left, 24, 0xFFAAAAAA, false);

        int y = 40;
        int maximumY = height - 66;
        for (Component paragraph : pageContent(version)) {
            List<FormattedCharSequence> wrapped = font.split(paragraph, contentWidth);
            for (FormattedCharSequence line : wrapped) {
                if (y + font.lineHeight > maximumY) {
                    graphics.text(
                        font,
                        Component.translatable("craftgpt.plan_review.more"),
                        left,
                        y,
                        0xFFFFAA55,
                        false
                    );
                    return;
                }
                graphics.text(font, line, left, y, 0xFFDDDDDD, false);
                y += font.lineHeight + 1;
            }
            y += 3;
        }
    }

    private List<Component> pageContent(PlanVersion version) {
        IntentionSpec plan = version.intention();
        List<Component> lines = new ArrayList<>();
        switch (page) {
            case 0 -> {
                lines.add(Component.literal(plan.title()).withColor(0x55FFFF));
                lines.add(Component.literal(plan.summary()));
                lines.add(Component.translatable(
                    "craftgpt.plan_review.overview",
                    plan.style(),
                    plan.orientation(),
                    plan.targetDimensions().width(),
                    plan.targetDimensions().height(),
                    plan.targetDimensions().depth(),
                    plan.estimatedBlockChanges()
                ));
                Optional<ApiCallMetrics> actual = controller.lastPlanningMetrics();
                lines.add(actual.map(ApiUiText::actual)
                    .orElseGet(() -> Component.translatable("craftgpt.api.actual.session_only")));
                ApiCostEstimate buildEstimate = controller.estimateBuildCost();
                lines.add(Component.translatable(
                    "craftgpt.plan_review.build_estimate",
                    ApiUiText.estimate(buildEstimate)
                ));
            }
            case 1 -> {
                addList(lines, "craftgpt.plan_review.goals", plan.goals());
                addList(lines, "craftgpt.plan_review.required", plan.requiredFeatures());
                addList(lines, "craftgpt.plan_review.preferred", plan.preferredFeatures());
            }
            case 2 -> {
                lines.add(Component.translatable("craftgpt.plan_review.materials"));
                if (plan.materialRoles().isEmpty()) {
                    lines.add(Component.translatable("craftgpt.plan_review.empty"));
                }
                for (MaterialRole role : plan.materialRoles()) {
                    lines.add(Component.translatable(
                        "craftgpt.plan_review.material",
                        role.role(),
                        String.join(", ", role.blockCandidates()),
                        role.purpose()
                    ));
                }
            }
            case 3 -> {
                addList(lines, "craftgpt.plan_review.constraints", plan.constraints());
                addList(lines, "craftgpt.plan_review.avoid", plan.avoid());
                addList(lines, "craftgpt.plan_review.assumptions", plan.assumptions());
            }
            default -> {
                lines.add(Component.translatable("craftgpt.plan_review.rationale"));
                lines.add(Component.literal(plan.designRationale()));
                lines.add(Component.translatable("craftgpt.plan_review.brief"));
                lines.add(Component.literal(plan.implementationBrief()));
                controller.lastPlanningMetrics().ifPresent(metrics -> {
                    lines.add(ApiUiText.details(metrics));
                    lines.add(Component.translatable(
                        "craftgpt.plan_review.pricing_note",
                        metrics.model()
                    ));
                });
            }
        }
        return lines;
    }

    private void addList(List<Component> destination, String heading, List<String> values) {
        destination.add(Component.translatable(heading));
        if (values.isEmpty()) {
            destination.add(Component.translatable("craftgpt.plan_review.empty"));
            return;
        }
        for (String value : values) {
            destination.add(Component.literal("• " + value));
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
