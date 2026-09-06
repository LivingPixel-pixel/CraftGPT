package dev.craftgpt.client.ui;

import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.network.PlacementMaintenanceRequestPayload;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RecoveryToolsScreen extends Screen {
    private static final int CONTENT_WIDTH = 360;
    private final Screen parent;
    private final PlanningController controller;
    private PlacementHistoryEntry entry;
    private String confirmation = "";

    public RecoveryToolsScreen(Screen parent, PlanningController controller, PlacementHistoryEntry entry) {
        super(Component.translatable("craftgpt.recovery.title"));
        this.parent = parent;
        this.controller = controller;
        this.entry = entry;
    }

    @Override
    protected void init() {
        entry = controller.placementHistory().stream()
            .filter(candidate -> candidate.placementId().equals(entry.placementId()))
            .findFirst().orElse(entry);
        int contentWidth = Math.min(CONTENT_WIDTH, width - 24);
        int left = (width - contentWidth) / 2;
        int top = Math.max(18, (height - 230) / 2);

        Button marker = addRenderableWidget(Button.builder(markerLabel(), button -> {
            controller.toggleRecoveryVisualization(entry.placementId());
            button.setMessage(markerLabel());
        }).bounds(left, top + 34, contentWidth, 20).build());

        Button export = addRenderableWidget(Button.builder(
            Component.translatable(entry.archived() ? "craftgpt.recovery.export_again" : "craftgpt.recovery.export"),
            button -> {
                if (controller.maintainPlacement(entry.placementId(), PlacementMaintenanceRequestPayload.EXPORT)) onClose();
            }).bounds(left, top + 60, contentWidth, 20).build());
        export.active = entry.live() && !controller.placementBusy();

        Button importButton = addRenderableWidget(Button.builder(importLabel(), button -> {
            if (!"import".equals(confirmation)) {
                confirmation = "import";
                button.setMessage(importLabel());
            } else if (controller.maintainPlacement(entry.placementId(), PlacementMaintenanceRequestPayload.IMPORT)) {
                onClose();
            }
        }).bounds(left, top + 86, contentWidth, 20).build());
        importButton.active = entry.archived() && !entry.live() && !controller.placementBusy();

        Button preview = addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.recovery.open_preview"),
            button -> controller.openPreviewReview(minecraft, this)
        ).bounds(left, top + 112, contentWidth, 20).build());
        preview.active = controller.historyMatchesActiveBuild(entry);

        Button prune = addRenderableWidget(Button.builder(pruneLabel(), button -> {
            if (!"prune".equals(confirmation)) {
                confirmation = "prune";
                button.setMessage(pruneLabel());
            } else if (controller.maintainPlacement("", PlacementMaintenanceRequestPayload.PRUNE)) {
                onClose();
            }
        }).bounds(left, top + 138, contentWidth, 20).build());
        prune.active = !controller.placementBusy();

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(left, top + 174, contentWidth, 20).build());
    }

    private Component markerLabel() {
        return Component.translatable(controller.recoveryVisualizationVisible(entry.placementId())
            ? "craftgpt.recovery.hide_marker" : "craftgpt.recovery.show_marker");
    }

    private Component importLabel() {
        return Component.translatable("import".equals(confirmation)
            ? "craftgpt.recovery.confirm_import" : "craftgpt.recovery.import");
    }

    private Component pruneLabel() {
        return Component.translatable("prune".equals(confirmation)
            ? "craftgpt.recovery.confirm_prune" : "craftgpt.recovery.prune",
            controller.recoveryRetentionCount());
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentWidth = Math.min(CONTENT_WIDTH, width - 24);
        int left = (width - contentWidth) / 2;
        int top = Math.max(18, (height - 230) / 2);
        graphics.centeredText(font, title, width / 2, top, 0xFFFFD966);
        String state = entry.live() && entry.archived() ? "live + archive" : entry.live() ? "live" : "archive only";
        graphics.centeredText(font, font.plainSubstrByWidth(state + " · " + entry.status(), contentWidth),
            width / 2, top + 18, 0xFFAAAAAA);
        Component status = controller.status();
        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, font.plainSubstrByWidth(status.getString(), contentWidth),
                width / 2, top + 202, controller.statusColor());
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
