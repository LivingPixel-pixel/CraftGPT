package dev.craftgpt.client.ui;

import dev.craftgpt.client.codex.CodexChatMessage;
import dev.craftgpt.client.planning.PlanningController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Live read-only transcript built from visible Codex JSON events. */
public final class CodexChatScreen extends Screen {
    private static final int CONTENT_WIDTH = 520;

    private final Screen parent;
    private final PlanningController controller;
    private List<ChatLine> lines = List.of();
    private int pageFromNewest;
    private boolean rawOutput;
    private int refreshTicks;
    private Button olderButton;
    private Button newerButton;
    private Button appButton;

    public CodexChatScreen(Screen parent, PlanningController controller) {
        super(Component.translatable("craftgpt.codex.chat.title"));
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
            Component.translatable(rawOutput ? "craftgpt.ui.readable_chat" : "craftgpt.ui.raw_chat"),
            button -> {
                rawOutput=!rawOutput;
                pageFromNewest=0;
                button.setMessage(Component.translatable(rawOutput ? "craftgpt.ui.readable_chat" : "craftgpt.ui.raw_chat"));
                reload();
            }
        ).bounds(left + (third + 6) * 2, height - 52, contentWidth - (third + 6) * 2, 20).build());

        addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.log.back"),
            button -> minecraft.setScreen(parent)
        ).bounds(left, height - 26, third, 20).build());
        addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.screen.log"),
            button -> minecraft.setScreen(new CodexLogScreen(this, controller))
        ).bounds(left + third + 6, height - 26, third, 20).build());
        appButton = addRenderableWidget(Button.builder(
            Component.translatable("craftgpt.codex.screen.app"),
            button -> {
                if (!controller.openCodexSessionInApp()) CraftGptSoundFeedback.error(minecraft);
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
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFD966);
        graphics.centeredText(
            font,
            Component.translatable(
                "craftgpt.codex.chat.page",
                Integer.toString(displayPage()),
                Integer.toString(pageCount())
            ),
            width / 2,
            22,
            0xFFAAAAAA
        );
        boolean appReady = controller.codexAppSessionAvailable();
        boolean generationActive = controller.codexStage() != null && controller.codexStage().active();
        Component appNotice=Component.translatable(appReady ? "craftgpt.codex.chat.app_ready"
            : generationActive ? "craftgpt.codex.chat.app_wait" : "craftgpt.codex.chat.app_unavailable");
        graphics.centeredText(
            font,
            Component.literal(font.plainSubstrByWidth(appNotice.getString(),Math.min(CONTENT_WIDTH,width-20))),
            width / 2,
            34,
            appReady ? 0xFF55FF55 : (generationActive ? 0xFFFFAA55 : 0xFF888888)
        );

        int visible = visibleLineCount();
        int end = Math.max(0, lines.size() - pageFromNewest * visible);
        int start = Math.max(0, end - visible);
        int x = Math.max(10, (width - Math.min(CONTENT_WIDTH, width - 20)) / 2);
        int y = 50;
        for (int index = start; index < end; index++) {
            ChatLine line = lines.get(index);
            graphics.text(font, line.text(), x, y, line.color(), false);
            y += 10;
        }
    }

    private void reload() {
        int contentWidth = Math.min(CONTENT_WIDTH, Math.max(80, width - 20));
        List<ChatLine> wrapped = new ArrayList<>();
        for (CodexChatMessage message : controller.codexChatMessages()) {
            if(!rawOutput && message.role()==CodexChatMessage.Role.TOOL)continue;
            String prefix = Component.translatable(roleKey(message.role())).getString() + ": ";
            List<FormattedCharSequence> parts = font.split(
                Component.literal(prefix + (!rawOutput && message.role()==CodexChatMessage.Role.CODEX
                    ? ChatPresentation.readable(message.text()) : message.text())),
                contentWidth
            );
            for (FormattedCharSequence part : parts) {
                wrapped.add(new ChatLine(part, roleColor(message.role())));
            }
        }
        lines = List.copyOf(wrapped);
        pageFromNewest = Math.min(pageFromNewest, pageCount() - 1);
        updateButtons();
    }

    private int visibleLineCount() {
        return Math.max(4, (height - 110) / 10);
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
        if (appButton != null) appButton.active = controller.codexAppSessionAvailable();
    }

    private static String roleKey(CodexChatMessage.Role role) {
        return "craftgpt.codex.chat.role." + role.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static int roleColor(CodexChatMessage.Role role) {
        return switch (role) {
            case USER -> 0xFF55FFFF;
            case CODEX -> 0xFFFFFFFF;
            case TOOL -> 0xFFAAAAAA;
            case SYSTEM -> 0xFFFFFF55;
            case ERROR -> 0xFFFF5555;
        };
    }

    private record ChatLine(FormattedCharSequence text, int color) {}
    @Override public boolean isPauseScreen() { return false; }
}
