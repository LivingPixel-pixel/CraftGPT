package dev.craftgpt.client.ui;

import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.network.PlacementRecoveryRequestPayload;
import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PlacementHistoryScreen extends Screen {
    private static final int PAGE_SIZE = 4;
    private static final int CONTENT_WIDTH = 420;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;
    private final PlanningController controller;
    private List<PlacementHistoryEntry> entries = List.of();
    private int page;
    private String selectedPlacementId;
    private String confirmationAction;
    private boolean requested;
    private Button undoButton;
    private Button redoButton;

    public PlacementHistoryScreen(Screen parent, PlanningController controller) {
        super(Component.translatable("craftgpt.history.title"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        entries = controller.placementHistory();
        if (!requested) {
            requested = true;
            controller.refreshPlacementHistory();
        }

        int maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, maxPage));
        int contentWidth = Math.min(CONTENT_WIDTH, width - 24);
        int left = (width - contentWidth) / 2;
        int top = Math.max(14, (height - 226) / 2);
        int from = Math.min(page * PAGE_SIZE, entries.size());
        int to = Math.min(from + PAGE_SIZE, entries.size());

        for (int index = from; index < to; index++) {
            PlacementHistoryEntry entry = entries.get(index);
            String marker = entry.placementId().equals(selectedPlacementId) ? "> " : "  ";
            String nbt = entry.hasBlockEntityData() ? " · NBT" : "";
            String storage = entry.live() && entry.archived() ? " · L+A"
                : entry.archived() ? " · A" : " · L";
            String label = marker + localDate(entry.createdAt()) + " · "
                + shortDimension(entry.dimension()) + " · " + entry.status()
                + " · " + entry.total() + nbt + storage;
            addRenderableWidget(Button.builder(
                Component.literal(font.plainSubstrByWidth(label, contentWidth - 12)),
                button -> {
                    selectedPlacementId = entry.placementId();
                    confirmationAction = null;
                    rebuildWidgets();
                }
            ).bounds(left, top + 28 + (index - from) * 22, contentWidth, 20).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page = Math.max(0, page - 1);
            confirmationAction = null;
            rebuildWidgets();
        }).bounds(left, top + 120, 40, 20).build());
        previous.active = page > 0;

        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page++;
            confirmationAction = null;
            rebuildWidgets();
        }).bounds(left + 46, top + 120, 40, 20).build());
        next.active = to < entries.size();

        addRenderableWidget(Button.builder(Component.translatable("craftgpt.history.refresh"), button ->
            controller.refreshPlacementHistory()
        ).bounds(left + 92, top + 120, contentWidth - 92, 20).build());

        int half = (contentWidth - 6) / 2;
        undoButton = addRenderableWidget(Button.builder(actionLabel(PlacementRecoveryRequestPayload.UNDO), button ->
            recover(PlacementRecoveryRequestPayload.UNDO)
        ).bounds(left, top + 146, half, 20).build());
        redoButton = addRenderableWidget(Button.builder(actionLabel(PlacementRecoveryRequestPayload.REDO), button ->
            recover(PlacementRecoveryRequestPayload.REDO)
        ).bounds(left + half + 6, top + 146, contentWidth - half - 6, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("craftgpt.history.tools"), button -> {
            PlacementHistoryEntry selected = selectedEntry();
            if (selected != null) minecraft.setScreen(new RecoveryToolsScreen(this, controller, selected));
        }).bounds(left, top + 172, half, 20).build()).active = selectedEntry() != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(left + half + 6, top + 172, contentWidth - half - 6, 20).build());
        updateActionButtons();
    }

    public void onHistoryUpdated() {
        entries = controller.placementHistory();
        if (selectedPlacementId != null
            && entries.stream().noneMatch(entry -> entry.placementId().equals(selectedPlacementId))) {
            selectedPlacementId = null;
        }
        confirmationAction = null;
        if (minecraft != null && minecraft.screen == this) {
            rebuildWidgets();
        }
    }

    private void recover(String action) {
        PlacementHistoryEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        if (!action.equals(confirmationAction)) {
            confirmationAction = action;
            updateActionButtons();
            return;
        }
        if (controller.recoverPlacement(selected.placementId(), action)) {
            confirmationAction = null;
            updateActionButtons();
        }
    }

    private void updateActionButtons() {
        PlacementHistoryEntry selected = selectedEntry();
        boolean busy = controller.placementBusy();
        if (undoButton != null) {
            undoButton.active = selected != null
                && !busy
                && selected.live()
                && !PlacementStatusCodes.UNDONE.equals(selected.status());
            undoButton.setMessage(actionLabel(PlacementRecoveryRequestPayload.UNDO));
        }
        if (redoButton != null) {
            redoButton.active = selected != null && selected.live() && !busy
                && PlacementStatusCodes.isUndone(selected.status());
            redoButton.setMessage(actionLabel(PlacementRecoveryRequestPayload.REDO));
        }
    }

    private Component actionLabel(String action) {
        if (action.equals(confirmationAction)) {
            return Component.translatable(action.equals(PlacementRecoveryRequestPayload.REDO)
                ? "craftgpt.history.confirm_redo"
                : "craftgpt.history.confirm_undo");
        }
        return Component.translatable(action.equals(PlacementRecoveryRequestPayload.REDO)
            ? "craftgpt.history.redo"
            : "craftgpt.history.undo");
    }

    private PlacementHistoryEntry selectedEntry() {
        if (selectedPlacementId == null) {
            return null;
        }
        return entries.stream()
            .filter(entry -> entry.placementId().equals(selectedPlacementId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        updateActionButtons();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentWidth = Math.min(CONTENT_WIDTH, width - 24);
        int left = (width - contentWidth) / 2;
        int top = Math.max(14, (height - 226) / 2);
        graphics.centeredText(font, title, width / 2, top - 12, 0xFFFFD966);

        if (entries.isEmpty()) {
            graphics.centeredText(
                font,
                Component.translatable("craftgpt.history.none"),
                width / 2,
                top + 45,
                0xFFAAAAAA
            );
        } else {
            graphics.text(
                font,
                Component.translatable("craftgpt.history.count", entries.size()),
                left,
                top,
                0xFFCCCCCC,
                false
            );
        }

        PlacementHistoryEntry selected = selectedEntry();
        if (selected != null) {
            String detail = Component.translatable(
                "craftgpt.history.detail",
                selected.processed(),
                selected.total(),
                selected.conflicts()
            ).getString();
            graphics.text(font, font.plainSubstrByWidth(detail, contentWidth), left, top + 14,
                selected.conflicts() == 0 ? 0xFF55FF55 : 0xFFFFAA55, false);
        }

        Component status = controller.status();
        if (!status.getString().isEmpty()) {
            graphics.centeredText(
                font,
                font.plainSubstrByWidth(status.getString(), contentWidth),
                width / 2,
                top + 198,
                controller.statusColor()
            );
        }
        graphics.centeredText(
            font,
            font.plainSubstrByWidth(
                Component.translatable("craftgpt.history.safety").getString(),
                contentWidth
            ),
            width / 2,
            top + 212,
            0xFF888888
        );
    }

    private String localDate(String timestamp) {
        try {
            return DATE.format(Instant.parse(timestamp).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException exception) {
            return timestamp;
        }
    }

    private String shortDimension(String dimension) {
        int separator = dimension.indexOf(':');
        return separator >= 0 ? dimension.substring(separator + 1) : dimension;
    }
    @Override public boolean isPauseScreen() { return false; }
}
