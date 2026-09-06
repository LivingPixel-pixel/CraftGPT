package dev.craftgpt.client.ui;

import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.client.planning.model.PlanVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class VersionHistoryScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int CONTENT_WIDTH = 380;

    private final Screen parent;
    private final PlanningController controller;
    private int page;
    private boolean initialPageResolved;
    private String selectedVersionId;
    private List<PlanVersion> versions = List.of();
    private String activeId = "";
    private String projectName = "CraftGPT";
    private Button revertButton;

    public VersionHistoryScreen(Screen parent, PlanningController controller, String selectedVersionId) {
        super(Component.translatable("craftgpt.versions.title"));
        this.parent = parent;
        this.controller = controller;
        this.selectedVersionId = selectedVersionId == null || selectedVersionId.isBlank()
            ? null
            : selectedVersionId;
    }

    @Override
    protected void init() {
        versions = controller.versions();
        controller.activeProject().ifPresentOrElse(snapshot -> {
            activeId = snapshot.activeVersion().id();
            projectName = snapshot.project().name();
        }, () -> {
            activeId = "";
            projectName = "CraftGPT";
        });

        if (!initialPageResolved && selectedVersionId != null) {
            for (int index = 0; index < versions.size(); index++) {
                if (versions.get(index).id().equals(selectedVersionId)) {
                    page = index / PAGE_SIZE;
                    break;
                }
            }
            initialPageResolved = true;
        }

        int maxPage = versions.isEmpty() ? 0 : (versions.size() - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, maxPage));

        int contentWidth = Math.min(CONTENT_WIDTH, width - 24);
        int left = (width - contentWidth) / 2;
        int top = Math.max(14, (height - 222) / 2);
        int from = Math.min(page * PAGE_SIZE, versions.size());
        int to = Math.min(from + PAGE_SIZE, versions.size());

        for (int index = from; index < to; index++) {
            PlanVersion version = versions.get(index);
            String marker = version.id().equals(activeId) ? "● " : "  ";
            String label = font.plainSubstrByWidth(
                marker + version.id() + " · " + version.changeSummary(),
                contentWidth - 12
            );
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                selectedVersionId = version.id();
                updateRevertButton();
            }).bounds(left, top + 27 + (index - from) * 22, contentWidth, 20).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(left, top + 162, 40, 20).build());
        previous.active = page > 0;

        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 45, top + 162, 40, 20).build());
        next.active = to < versions.size();

        revertButton = addRenderableWidget(Button.builder(Component.translatable("craftgpt.versions.revert"), button -> {
            if (selectedVersionId != null && controller.revert(selectedVersionId)) {
                if (parent instanceof IntentionPlanningScreen planningScreen) {
                    planningScreen.onVersionChanged();
                }
                rebuildWidgets();
            }
        }).bounds(left + 90, top + 162, contentWidth - 90, 20).build());
        updateRevertButton();

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> returnToParent())
            .bounds(left, top + 188, contentWidth, 20).build());
    }

    private void updateRevertButton() {
        if (revertButton != null) {
            revertButton.active = selectedVersionId != null
                && versions.stream().anyMatch(version -> version.id().equals(selectedVersionId))
                && !selectedVersionId.equals(activeId);
        }
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private void returnToParent() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            minecraft.setScreen(new IntentionPlanningScreen(null, controller, ""));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentWidth = Math.min(CONTENT_WIDTH, width - 24);
        int left = (width - contentWidth) / 2;
        int top = Math.max(14, (height - 222) / 2);
        graphics.centeredText(font, title, width / 2, top - 12, 0xFFFFD966);

        if (versions.isEmpty()) {
            graphics.centeredText(font, Component.translatable("craftgpt.versions.none"), width / 2, top, 0xFFFF5555);
        } else {
            graphics.text(font, font.plainSubstrByWidth(Component.translatable(
                "craftgpt.versions.project",
                projectName,
                activeId
            ).getString(), contentWidth), left, top, 0xFFCCCCCC, false);
        }

        if (selectedVersionId != null) {
            versions.stream()
                .filter(version -> version.id().equals(selectedVersionId))
                .findFirst()
                .ifPresent(version -> graphics.text(
                    font,
                    font.plainSubstrByWidth(version.instruction(), contentWidth),
                    left,
                    top + 13,
                    0xFFAAAAAA,
                    false
                ));
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
