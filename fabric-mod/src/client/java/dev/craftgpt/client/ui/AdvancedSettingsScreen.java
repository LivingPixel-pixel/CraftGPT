package dev.craftgpt.client.ui;

import dev.craftgpt.client.platform.ClientPlatform;

import dev.craftgpt.area.AreaCommands;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.config.ContextMode;
import dev.craftgpt.client.config.ReasoningLevel;
import dev.craftgpt.client.config.ModelPreset;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.net.URI;

public final class AdvancedSettingsScreen extends FocusedScreen {
    private static final int CONTENT_WIDTH = 440;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_SPACING = 22;

    private final Screen parent;
    private final CraftGptConfig config;

    private int page;
    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> fields=new java.util.ArrayList<>();
    private final java.util.List<Button> tabs=new java.util.ArrayList<>();
    private EditBox endpointField;
    private EditBox apiKeyField;
    private EditBox planningModelField;
    private EditBox builderModelField;
    private EditBox maxAreaVolumeField;
    private EditBox maxBlockChangesField;
    private EditBox maxFullContextBlocksField;
    private Button planningReasoningButton;
    private Button builderReasoningButton;
    private Button planningPresetButton;
    private Button builderPresetButton;
    private Button blockEntityButton;
    private Button retentionButton;
    private Button contextModeButton;
    private ReasoningLevel planningReasoningLevel;
    private ReasoningLevel builderReasoningLevel;
    private ModelPreset planningPreset;
    private ModelPreset builderPreset;
    private String planningCustomModel;
    private String builderCustomModel;
    private boolean allowBlockEntityReplacement;
    private int recoveryRetentionCount;
    private ContextMode contextMode;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public AdvancedSettingsScreen(Screen parent, CraftGptConfig config, int page) {
        super(Component.translatable("craftgpt.settings.title"));
        this.page=page;
        this.parent = parent;
        this.config = config;
        this.planningReasoningLevel = config.reasoningLevel();
        this.builderReasoningLevel = config.builderReasoningLevel();
        this.planningPreset = ModelPreset.fromModel(config.planningModel());
        this.builderPreset = ModelPreset.fromModel(config.builderModel());
        this.planningCustomModel = planningPreset.custom() ? config.planningModel() : "";
        this.builderCustomModel = builderPreset.custom() ? config.builderModel() : "";
        this.allowBlockEntityReplacement = config.allowBlockEntityReplacement();
        this.recoveryRetentionCount = config.recoveryRetentionCount();
        this.contextMode = config.contextMode();
    }

    @Override protected void init() {
        if(planningModelField!=null&&planningPreset.custom())planningCustomModel=planningModelField.getValue();
        if(builderModelField!=null&&builderPreset.custom())builderCustomModel=builderModelField.getValue();
        fields.clear();tabs.clear();
        var f=frame();
        endpointField=textField(0,0,100,value(endpointField,config.apiEndpoint()),"craftgpt.settings.endpoint");
        apiKeyField=textField(0,0,100,value(apiKeyField,""),"craftgpt.settings.api_key");
        apiKeyField.setHint(Component.translatable(config.apiKey().isBlank()?"craftgpt.settings.api_key.empty":"craftgpt.settings.api_key.saved"));
        apiKeyField.addFormatter((v,index)->FormattedCharSequence.forward("•".repeat(v.length()),Style.EMPTY));
        planningPresetButton=action(planningPreset.displayName(),f.row(64,2,0),button->{
            if(planningPreset.custom())planningCustomModel=planningModelField.getValue();
            planningPreset=planningPreset.next();applyPlanningPreset();
        });
        planningModelField=textField(0,0,100,value(planningModelField,config.planningModel()),"craftgpt.settings.planning_model");
        builderPresetButton=action(builderPreset.displayName(),f.row(94,2,0),button->{
            if(builderPreset.custom())builderCustomModel=builderModelField.getValue();
            builderPreset=builderPreset.next();applyBuilderPreset();
        });
        builderModelField=textField(0,0,100,value(builderModelField,config.builderModel()),"craftgpt.settings.builder_model");
        planningReasoningButton=action(planningReasoningLabel(),f.row(124,2,0),button->{
            planningReasoningLevel=planningReasoningLevel.next();
            if(planningReasoningLevel==ReasoningLevel.NONE&&ModelPreset.requiresReasoning(planningModelField.getValue()))
                planningReasoningLevel=ReasoningLevel.LOW;
            button.setMessage(planningReasoningLabel());
        });
        builderReasoningButton=action(builderReasoningLabel(),f.row(124,2,1),button->{
            builderReasoningLevel=builderReasoningLevel.next();
            if(builderReasoningLevel==ReasoningLevel.NONE&&ModelPreset.requiresReasoning(builderModelField.getValue()))
                builderReasoningLevel=ReasoningLevel.LOW;
            button.setMessage(builderReasoningLabel());
        });
        contextModeButton=action(contextModeLabel(),f.row(64,1,0),button->{
            contextMode=contextMode.next();button.setMessage(contextModeLabel());
        });
        maxFullContextBlocksField=textField(0,0,100,value(maxFullContextBlocksField,Integer.toString(config.maxFullContextBlocks())),"craftgpt.settings.full_context_limit");
        maxAreaVolumeField=textField(0,0,100,value(maxAreaVolumeField,Integer.toString(config.maxAreaVolume())),"craftgpt.settings.max_area");
        maxBlockChangesField=textField(0,0,100,value(maxBlockChangesField,Integer.toString(config.maxBlockChanges())),"craftgpt.settings.max_changes");
        blockEntityButton=action(blockEntityLabel(),f.row(64,1,0),button->{
            allowBlockEntityReplacement=!allowBlockEntityReplacement;button.setMessage(blockEntityLabel());
        });
        retentionButton=action(retentionLabel(),f.row(94,1,0),button->{
            recoveryRetentionCount=recoveryRetentionCount==10?25:recoveryRetentionCount==25?50:10;
            button.setMessage(retentionLabel());
        });
        fields.addAll(java.util.List.of(endpointField,apiKeyField,planningPresetButton,planningModelField,
            builderPresetButton,builderModelField,planningReasoningButton,builderReasoningButton,contextModeButton,
            maxFullContextBlocksField,maxAreaVolumeField,maxBlockChangesField,blockEntityButton,retentionButton));
        Component previousStatus=status;
        applyPlanningPreset();applyBuilderPreset();status=previousStatus;
        String[] keys={"craftgpt.ui.tab_api","craftgpt.ui.tab_models","craftgpt.ui.tab_area","craftgpt.ui.tab_undo"};
        for(int i=0;i<keys.length;i++){
            final int target=i;
            tabs.add(action(keys[i],32,4,i,button->{page=target;status=Component.empty();showPage();}));
        }
        action(Component.translatable("gui.cancel"),f.footer(2,0),button->onClose());
        action(Component.translatable("gui.done"),f.footer(2,1),button->saveAndClose());
        showPage();
    }
    private static String value(EditBox previous,String fallback){return previous==null?fallback:previous.getValue();}
    private void showPage(){
        for(var field:fields)field.visible=false;
        for(int i=0;i<tabs.size();i++)tabs.get(i).active=i!=page;
        switch(page){
            case 0->{row(0,endpointField);row(1,apiKeyField);}
            case 1->{row(0,planningPresetButton,planningModelField);row(1,builderPresetButton,builderModelField);
                row(2,planningReasoningButton,builderReasoningButton);}
            case 2->{row(0,contextModeButton);row(1,maxFullContextBlocksField);row(2,maxAreaVolumeField);row(3,maxBlockChangesField);}
            default->{row(0,blockEntityButton);row(1,retentionButton);}
        }
        setFocused(null);
    }
    private void row(int index,net.minecraft.client.gui.components.AbstractWidget... widgets){
        var f=frame();int labelWidth=Math.min(106,f.width()/3);
        int x=f.left()+labelWidth+8,available=f.width()-labelWidth-8;
        for(int i=0;i<widgets.length;i++){
            var widget=widgets[i];int w=(available-(widgets.length-1)*6)/widgets.length;
            widget.setPosition(x+i*(w+6),f.top()+64+index*26);widget.setWidth(w);widget.visible=true;
        }
    }

    private void applyPlanningPreset() {
        planningPresetButton.setMessage(planningPreset.displayName());
        planningModelField.active = planningPreset.custom();
        planningModelField.setValue(planningPreset.custom() ? planningCustomModel : planningPreset.modelId());
        if(ModelPreset.requiresReasoning(planningModelField.getValue())&&planningReasoningLevel==ReasoningLevel.NONE) {
            planningReasoningLevel=ReasoningLevel.LOW;
            if(planningReasoningButton!=null)planningReasoningButton.setMessage(planningReasoningLabel());
        }
        status = planningPreset.description();
        statusColor = 0xFF55FFFF;
    }

    private void applyBuilderPreset() {
        builderPresetButton.setMessage(builderPreset.displayName());
        builderModelField.active = builderPreset.custom();
        builderModelField.setValue(builderPreset.custom() ? builderCustomModel : builderPreset.modelId());
        if(ModelPreset.requiresReasoning(builderModelField.getValue())&&builderReasoningLevel==ReasoningLevel.NONE) {
            builderReasoningLevel=ReasoningLevel.LOW;
            if(builderReasoningButton!=null)builderReasoningButton.setMessage(builderReasoningLabel());
        }
        status = builderPreset.description();
        statusColor = 0xFF55FFFF;
    }

    private EditBox textField(int x, int y, int fieldWidth, String value, String narrationKey) {
        EditBox field = new EditBox(font, x, y, fieldWidth, FIELD_HEIGHT, Component.translatable(narrationKey));
        field.setMaxLength(512);
        field.setValue(value);
        field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(narrationKey)));
        return addRenderableWidget(field);
    }

    private Component planningReasoningLabel() {
        return Component.translatable(
            "craftgpt.settings.reasoning_planner",
            planningReasoningLevel.displayName()
        );
    }

    private Component builderReasoningLabel() {
        return Component.translatable(
            "craftgpt.settings.reasoning_builder",
            builderReasoningLevel.displayName()
        );
    }

    private Component blockEntityLabel() {
        return Component.translatable(allowBlockEntityReplacement
            ? "craftgpt.settings.block_entities.on"
            : "craftgpt.settings.block_entities.off");
    }

    private Component retentionLabel() {
        return Component.translatable("craftgpt.settings.retention.value", recoveryRetentionCount);
    }

    private Component contextModeLabel() {
        return contextMode.displayName();
    }

    private boolean validateFields() {
        if(ModelPreset.requiresReasoning(planningModelField.getValue())&&planningReasoningLevel==ReasoningLevel.NONE) {
            planningReasoningLevel=ReasoningLevel.LOW;planningReasoningButton.setMessage(planningReasoningLabel());
        }
        if(ModelPreset.requiresReasoning(builderModelField.getValue())&&builderReasoningLevel==ReasoningLevel.NONE) {
            builderReasoningLevel=ReasoningLevel.LOW;builderReasoningButton.setMessage(builderReasoningLabel());
        }
        try {
            URI endpoint = URI.create(endpointField.getValue().trim());
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
                return invalid("craftgpt.validation.endpoint");
            }
            if (planningModelField.getValue().isBlank()) {
                return invalid("craftgpt.validation.planning_model");
            }
            if (builderModelField.getValue().isBlank()) {
                return invalid("craftgpt.validation.builder_model");
            }
            int areaVolume = parsePositive(maxAreaVolumeField.getValue());
            int blockChanges = parsePositive(maxBlockChangesField.getValue());
            int fullContextBlocks = parsePositive(maxFullContextBlocksField.getValue());
            if (areaVolume <= 0
                || areaVolume > AreaCommands.MAX_AREA_VOLUME
                || blockChanges <= 0
                || blockChanges > BuildLimits.HARD_MAX_OPERATIONS
                || fullContextBlocks <= 0
                || fullContextBlocks > AreaCommands.MAX_AREA_VOLUME) {
                return invalid("craftgpt.validation.limits");
            }
        } catch (IllegalArgumentException exception) {
            return invalid("craftgpt.validation.endpoint");
        }

        status = Component.translatable("craftgpt.validation.ok");
        statusColor = 0xFF55FF55;
        CraftGptSoundFeedback.success(minecraft);
        return true;
    }

    private boolean invalid(String translationKey) {
        page=translationKey.equals("craftgpt.validation.limits")?2:
            translationKey.contains("model")?1:0;
        showPage();
        status = Component.translatable(translationKey);
        statusColor = 0xFFFF5555;
        CraftGptSoundFeedback.error(minecraft);
        return false;
    }

    private int parsePositive(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void saveAndClose() {
        if (!validateFields()) {
            return;
        }

        config.apiEndpoint(endpointField.getValue());
        if (!apiKeyField.getValue().isBlank()) {
            config.apiKey(apiKeyField.getValue());
        }
        config.planningModel(planningModelField.getValue());
        config.builderModel(builderModelField.getValue());
        config.reasoningLevel(planningReasoningLevel);
        config.builderReasoningLevel(builderReasoningLevel);
        config.contextMode(contextMode);
        config.maxFullContextBlocks(parsePositive(maxFullContextBlocksField.getValue()));
        config.maxAreaVolume(parsePositive(maxAreaVolumeField.getValue()));
        config.maxBlockChanges(parsePositive(maxBlockChangesField.getValue()));
        config.allowBlockEntityReplacement(allowBlockEntityReplacement);
        config.recoveryRetentionCount(recoveryRetentionCount);

        try {
            config.save();
            CraftGptSoundFeedback.complete(minecraft);
            onClose();
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

    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);heading(g);
        String[][] labels={
            {"craftgpt.settings.endpoint","craftgpt.settings.api_key"},
            {"craftgpt.settings.planning_model","craftgpt.settings.builder_model","craftgpt.settings.reasoning_label"},
            {"craftgpt.settings.context_mode","craftgpt.settings.full_context_limit","craftgpt.settings.max_area","craftgpt.settings.max_changes"},
            {"craftgpt.settings.block_entities","craftgpt.settings.retention"}
        };
        var f=frame();int labelWidth=Math.min(106,f.width()/3);
        for(int i=0;i<labels[page].length;i++)g.text(font,
            font.plainSubstrByWidth(Component.translatable(labels[page][i]).getString(),labelWidth),
            f.left(),f.top()+70+i*26,MUTED,false);
        if(!status.getString().isBlank())wrapped(g,status,174,2,statusColor);
        else if(page==0)wrapped(g,Component.translatable("craftgpt.ui.api_hint"),124,3,MUTED);
        else if(page==3)wrapped(g,Component.translatable("craftgpt.ui.recovery_settings_hint"),124,4,MUTED);
    }
}
