package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;

import dev.craftgpt.client.codex.CodexRunSettings;
import dev.craftgpt.client.config.CodexGenerationEffort;
import dev.craftgpt.client.config.CodexModelPreset;
import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.config.ReasoningLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.util.List;

/** Focused model selection for the one-click local Codex workflow. */
public final class CodexSettingsScreen extends FocusedScreen {
    private static final int CONTENT_WIDTH = 400;

    private final Screen parent;
    private final CraftGptConfig config;
    private CodexModelPreset preset;
    private ReasoningLevel reasoning;
    private int generationEffort;
    private String customModel;
    private EditBox modelField;
    private Button presetButton;
    private Button reasoningButton;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public CodexSettingsScreen(Screen parent, CraftGptConfig config) {
        super(Component.translatable("craftgpt.codex_settings.title"));
        this.parent = parent;
        this.config = config;
        this.preset = CodexModelPreset.fromModel(config.codexModel());
        this.customModel = preset.custom() ? config.codexModel() : "";
        this.reasoning = config.codexReasoningLevel() == ReasoningLevel.NONE
            ? ReasoningLevel.LOW
            : config.codexReasoningLevel();
        this.generationEffort = config.codexGenerationEffort();
    }

    @Override protected void init() {
        if(modelField!=null&&preset.custom())customModel=modelField.getValue();
        var f=frame();
        presetButton=action(preset.displayName(),f.row(54,1,0),button->{
            if(preset.custom())customModel=modelField.getValue();
            preset=preset.next();applyPreset();
        });
        modelField=new EditBox(font,f.left(),f.top()+82,f.width(),20,
            Component.translatable("craftgpt.codex_settings.model"));
        modelField.setMaxLength(128);addRenderableWidget(modelField);applyPreset();
        reasoningButton=action(reasoningLabel(),f.row(112,1,0),button->{
            reasoning=nextCodexReasoning(reasoning);button.setMessage(reasoningLabel());
        });
        reasoningButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("craftgpt.ui.reasoning_hint")));
        addRenderableWidget(new GenerationEffortSlider(f.left(),f.top()+140,f.width(),20));
        action(Component.translatable("gui.cancel"),f.footer(2,0),button->onClose());
        action(Component.translatable("gui.done"),f.footer(2,1),button->saveAndClose());
    }

    private void applyPreset() {
        if (modelField == null || presetButton == null) return;
        presetButton.setMessage(preset.displayName());
        modelField.active = preset.custom();
        modelField.visible = preset.custom();
        modelField.setValue(preset.custom() ? customModel : preset.modelId());
        presetButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal(preset.modelId()).append("\n").append(preset.description())));
        status = Component.empty();
        statusColor = 0xFF55FFFF;
    }

    private Component reasoningLabel() {
        return Component.translatable("craftgpt.codex_settings.reasoning", reasoning.displayName());
    }

    private static ReasoningLevel nextCodexReasoning(ReasoningLevel current) {
        return switch (current) {
            case NONE, LOW -> ReasoningLevel.MEDIUM;
            case MEDIUM -> ReasoningLevel.HIGH;
            case HIGH -> ReasoningLevel.XHIGH;
            case XHIGH -> ReasoningLevel.MAX;
            case MAX -> ReasoningLevel.LOW;
        };
    }

    private void saveAndClose() {
        try {
            new CodexRunSettings(modelField.getValue(), reasoning.serializedName());
            config.codexModel(modelField.getValue());
            config.codexReasoningLevel(reasoning);
            config.codexGenerationEffort(generationEffort);
            config.save();
            CraftGptSoundFeedback.complete(minecraft);
            onClose();
        } catch (IllegalArgumentException exception) {
            status = Component.translatable("craftgpt.codex_settings.invalid");
            statusColor = 0xFFFF5555;
            CraftGptSoundFeedback.error(minecraft);
        } catch (IOException exception) {
            status = Component.translatable("craftgpt.validation.save_failed");
            statusColor = 0xFFFF5555;
            CraftGptSoundFeedback.error(minecraft);
        }
    }

    @Override
    public void onClose() {
        ClientPlatform.setScreen(minecraft, parent);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d) {
        super.extractRenderState(g,x,y,d);heading(g);
        line(g,Component.translatable("craftgpt.codex_settings.model_label"),35,MUTED);
        if(!preset.custom())wrapped(g,preset.description(),82,2,MUTED);
        wrapped(g,status.getString().isBlank()?effortDescription():status,168,2,
            status.getString().isBlank()?MUTED:statusColor);
    }

    private Component effortDescription() {
        int reviews = CodexGenerationEffort.visualReviewRounds(generationEffort);
        return reviews == 0
            ? Component.translatable("craftgpt.codex_settings.generation_effort_one")
            : Component.translatable(
                "craftgpt.codex_settings.generation_effort_many",
                generationEffort,
                generationEffort,
                reviews
            );
    }

    private final class GenerationEffortSlider extends AbstractSliderButton {
        GenerationEffortSlider(int x, int y, int width, int height) {
            super(
                x,
                y,
                width,
                height,
                Component.empty(),
                CodexGenerationEffort.sliderValue(generationEffort)
            );
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(effortDescription()));
            setMessage(Component.translatable(
                "craftgpt.codex_settings.generation_effort",
                generationEffort,
                CodexGenerationEffort.MAXIMUM
            ));
        }

        @Override
        protected void applyValue() {
            generationEffort = CodexGenerationEffort.fromSlider(value);
            value = CodexGenerationEffort.sliderValue(generationEffort);
            status = effortDescription();
            statusColor = 0xFF55FFFF;
        }
    }

    private void drawWrappedCentered(
        GuiGraphicsExtractor graphics,
        Component text,
        int maximumWidth,
        int y,
        int color,
        int maximumLines
    ) {
        List<FormattedCharSequence> lines = font.split(text, maximumWidth);
        for (int index = 0; index < Math.min(maximumLines, lines.size()); index++) {
            FormattedCharSequence line = lines.get(index);
            graphics.text(font, line, (width - font.width(line)) / 2, y + index * 10, color, false);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
