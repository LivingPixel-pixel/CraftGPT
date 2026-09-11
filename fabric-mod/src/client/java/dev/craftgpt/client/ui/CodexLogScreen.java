package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;

import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Read-only live viewer for the persistent human-readable Codex run log. */
public final class CodexLogScreen extends Screen {
    private static final int CONTENT_WIDTH = 520;

    private final Screen parent;
    private final PlanningController controller;
    private List<FormattedCharSequence> lines = List.of();
    private int pageFromNewest;
    private int refreshTicks;
    private Button olderButton;
    private Button newerButton;

    public CodexLogScreen(Screen parent, PlanningController controller) {
        super(Component.translatable("craftgpt.codex.log.title"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        reload();
        int contentWidth = Math.min(CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        int third = (contentWidth - 12) / 3;

        olderButton = addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.older"),
            button -> {
                pageFromNewest = Math.min(pageCount() - 1, pageFromNewest + 1);
                updateButtons();
            }
        ).bounds(left, height - 52, third, 20).build());
        newerButton = addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.newer"),
            button -> {
                pageFromNewest = Math.max(0, pageFromNewest - 1);
                updateButtons();
            }
        ).bounds(left + third + 6, height - 52, third, 20).build());
        addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.refresh"),
            button -> reload()
        ).bounds(left + (third + 6) * 2, height - 52, contentWidth - (third + 6) * 2, 20).build());

        addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.back"),
            button -> ClientPlatform.setScreen(minecraft, parent)
        ).bounds(left, height - 26, third, 20).build());
        addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.open"),
            button -> {
                if (!controller.openCodexLogFile()) CraftGptSoundFeedback.error(minecraft);
            }
        ).bounds(left + third + 6, height - 26, third, 20).build());
        addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.raw"),
            button -> {
                if (!controller.openCodexEventLogFile()) CraftGptSoundFeedback.error(minecraft);
            }
        ).bounds(left + (third + 6) * 2, height - 26, contentWidth - (third + 6) * 2, 20).build());
        updateButtons();
    }

    @Override
    public void tick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            reload();
        }
    }

    @Override
    public void onClose() {
        ClientPlatform.setScreen(minecraft, parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFD966);
        graphics.centeredText(
            font,
            Component.translatable(
                "craftgpt.codex.log.page",
                Integer.toString(displayPage()),
                Integer.toString(pageCount())
            ),
            width / 2,
            22,
            0xFFAAAAAA
        );

        int visible = visibleLineCount();
        int end = Math.max(0, lines.size() - pageFromNewest * visible);
        int start = Math.max(0, end - visible);
        int x = Math.max(10, (width - Math.min(CONTENT_WIDTH, width - 20)) / 2);
        int y = 38;
        if (lines.isEmpty()) {
            graphics.text(font, Component.translatable("craftgpt.codex.log.empty"), x, y, 0xFFAAAAAA, false);
            return;
        }
        for (int index = start; index < end; index++) {
            graphics.text(font, lines.get(index), x, y, 0xFFDDDDDD, false);
            y += 10;
        }
    }

    private void reload() {
        int contentWidth = Math.min(CONTENT_WIDTH, Math.max(80, width - 20));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : controller.codexLogLines()) {
            List<FormattedCharSequence> parts = font.split(Component.literal(line), contentWidth);
            wrapped.addAll(parts);
        }
        lines = List.copyOf(wrapped);
        pageFromNewest = Math.min(pageFromNewest, pageCount() - 1);
        updateButtons();
    }

    private int visibleLineCount() {
        return Math.max(4, (height - 98) / 10);
    }

    private int pageCount() {
        return Math.max(1, (lines.size() + visibleLineCount() - 1) / visibleLineCount());
    }

    private int displayPage() {
        return Math.max(1, pageCount() - pageFromNewest);
    }

    private void updateButtons() {
        if (olderButton != null) olderButton.active = pageFromNewest < pageCount() - 1;
        if (newerButton != null) newerButton.active = pageFromNewest > 0;
    }
    @Override public boolean isPauseScreen() { return false; }
}
