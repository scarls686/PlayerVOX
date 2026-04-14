package com.playervox.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 字幕设置界面：X/Y 坐标、缩放、名字颜色、自己/他人开关。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSubtitleSettingsScreen extends Screen {

    private final Screen parent;
    private EditBox xInput;
    private EditBox yInput;
    private EditBox scaleInput;
    private EditBox nameColorInput;

    public VoxSubtitleSettingsScreen(Screen parent) {
        super(Component.translatable("gui.playervox.subtitle_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        VoxSubtitleConfig config = VoxSubtitleConfig.get();

        int centerX = this.width / 2;
        int y = 45;
        int labelWidth = 100;
        int inputWidth = 60;

        // X 坐标
        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.subtitle_x"), b -> {})
                .pos(centerX - 120, y).size(labelWidth, 20).build()).active = false;
        xInput = new EditBox(this.font, centerX - 20, y, inputWidth, 20, Component.literal("X"));
        xInput.setValue(String.valueOf(config.x));
        addRenderableWidget(xInput);

        // Y 坐标
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.subtitle_y"), b -> {})
                .pos(centerX - 120, y).size(labelWidth, 20).build()).active = false;
        yInput = new EditBox(this.font, centerX - 20, y, inputWidth, 20, Component.literal("Y"));
        yInput.setValue(String.valueOf(config.y));
        addRenderableWidget(yInput);

        // 缩放
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.subtitle_scale"), b -> {})
                .pos(centerX - 120, y).size(labelWidth, 20).build()).active = false;
        scaleInput = new EditBox(this.font, centerX - 20, y, inputWidth, 20, Component.literal("Scale"));
        scaleInput.setValue(String.valueOf(config.scale));
        addRenderableWidget(scaleInput);

        // 名字颜色
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.subtitle_name_color"), b -> {})
                .pos(centerX - 120, y).size(labelWidth, 20).build()).active = false;
        nameColorInput = new EditBox(this.font, centerX - 20, y, inputWidth, 20, Component.literal("Color"));
        nameColorInput.setValue(config.nameColor);
        nameColorInput.setMaxLength(6);
        addRenderableWidget(nameColorInput);

        // 自己的字幕开关
        y += 30;
        addRenderableWidget(Button.builder(
                getToggleText("gui.playervox.subtitle_show_own", config.showOwn),
                b -> {
                    VoxSubtitleConfig cfg = VoxSubtitleConfig.get();
                    cfg.showOwn = !cfg.showOwn;
                    b.setMessage(getToggleText("gui.playervox.subtitle_show_own", cfg.showOwn));
                    cfg.save();
                }
        ).pos(centerX - 120, y).size(240, 20).build());

        // 他人的字幕开关
        y += 24;
        addRenderableWidget(Button.builder(
                getToggleText("gui.playervox.subtitle_show_other", config.showOther),
                b -> {
                    VoxSubtitleConfig cfg = VoxSubtitleConfig.get();
                    cfg.showOther = !cfg.showOther;
                    b.setMessage(getToggleText("gui.playervox.subtitle_show_other", cfg.showOther));
                    cfg.save();
                }
        ).pos(centerX - 120, y).size(240, 20).build());

        // 重置按钮
        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.subtitle_reset"), b -> {
            VoxSubtitleConfig cfg = VoxSubtitleConfig.get();
            cfg.x = -1;
            cfg.y = -1;
            cfg.scale = 1.0f;
            cfg.nameColor = "FFAA00";
            cfg.showOwn = true;
            cfg.showOther = true;
            cfg.save();
            rebuildWidgets();
        }).pos(centerX - 60, y).size(120, 20).build());

        // 返回
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.done"), b -> {
            applyConfig();
            this.minecraft.setScreen(parent);
        }).pos(centerX - 60, y).size(120, 20).build());
    }

    private void applyConfig() {
        VoxSubtitleConfig config = VoxSubtitleConfig.get();
        try { config.x = Integer.parseInt(xInput.getValue()); } catch (NumberFormatException ignored) {}
        try { config.y = Integer.parseInt(yInput.getValue()); } catch (NumberFormatException ignored) {}
        try { config.scale = Float.parseFloat(scaleInput.getValue()); } catch (NumberFormatException ignored) {}
        String color = nameColorInput.getValue().replaceAll("[^0-9a-fA-F]", "");
        if (color.length() == 6) config.nameColor = color;
        config.save();
    }

    private Component getToggleText(String translationKey, boolean value) {
        String state = value ? "\u00a7aON" : "\u00a7cOFF";
        return Component.translatable(translationKey).append(": " + state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.playervox.subtitle_hint"),
                this.width / 2, 28, 0x888888);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        applyConfig();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
