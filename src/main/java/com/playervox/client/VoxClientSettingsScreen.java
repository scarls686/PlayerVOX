package com.playervox.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * PlayerVOX 客户���设置界面：字幕 + 轮盘。
 * 内容区可滚动（scissor + 滚轮），底部按钮固定。
 */
@OnlyIn(Dist.CLIENT)
public class VoxClientSettingsScreen extends Screen {

    private final Screen parent;

    private static final int ROW_H = 24;
    private static final int INPUT_H = 20;
    private static final int SECTION_GAP = 14;
    private static final int TITLE_H = 20;
    private static final int BOTTOM_H = 30;
    private static final int SCROLL_STEP = ROW_H;

    private EditBox xInput;
    private EditBox yInput;
    private EditBox scaleInput;
    private EditBox nameColorInput;
    private EditBox sensitivityInput;

    /** 可滚动控件列表（底部按钮不在其中） */
    private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();

    /** 滚动偏移量 */
    private int scrollOffset = 0;

    /** 内容总高度（虚拟坐标） */
    private int contentTotalH;

    /** 可滚动区域的屏幕边界 */
    private int scrollTop;
    private int scrollBottom;

    // 各行虚拟 Y 坐标（相对于内容起始，不含 scroll）
    private int subtitleSectionVY;
    private int[] fieldRowVY = new int[4];
    private int hintVY;
    private int radialSectionVY;
    private int sensitivityRowVY;

    public VoxClientSettingsScreen(Screen parent) {
        super(Component.translatable("gui.playervox.client_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        scrollableWidgets.clear();
        VoxClientConfig config = VoxClientConfig.get();

        int cx = this.width / 2;
        int inputW = 60;
        int inputX = cx + 10;
        int toggleW = 240;
        int toggleX = cx - toggleW / 2;

        scrollTop = TITLE_H;
        scrollBottom = this.height - BOTTOM_H;

        // 所有内容使用虚拟 Y 坐标（从 0 开始），渲染时加 scrollTop - scrollOffset
        int vy = 4;

        // ===== 字幕分类标题 =====
        subtitleSectionVY = vy;
        vy += SECTION_GAP;

        // X 坐标
        fieldRowVY[0] = vy;
        xInput = addScrollableEditBox(inputX, vy, inputW, String.valueOf(config.x), "X");
        vy += ROW_H;

        // Y 坐标
        fieldRowVY[1] = vy;
        yInput = addScrollableEditBox(inputX, vy, inputW, String.valueOf(config.y), "Y");
        vy += ROW_H;

        // 缩放
        fieldRowVY[2] = vy;
        scaleInput = addScrollableEditBox(inputX, vy, inputW, String.valueOf(config.scale), "Scale");
        vy += ROW_H;

        // 名字颜色
        fieldRowVY[3] = vy;
        nameColorInput = addScrollableEditBox(inputX, vy, inputW, config.nameColor, "Color");
        nameColorInput.setMaxLength(6);
        vy += ROW_H;

        // 提示文字行
        hintVY = vy;
        vy += font.lineHeight + 6;

        // 自己的字幕开关
        Button showOwnBtn = Button.builder(
                getToggleText("gui.playervox.subtitle_show_own", config.showOwn),
                b -> {
                    VoxClientConfig cfg = VoxClientConfig.get();
                    cfg.showOwn = !cfg.showOwn;
                    b.setMessage(getToggleText("gui.playervox.subtitle_show_own", cfg.showOwn));
                    cfg.save();
                }
        ).pos(toggleX, 0).size(toggleW, INPUT_H).build();
        addRenderableWidget(showOwnBtn);
        scrollableWidgets.add(showOwnBtn);
        setVirtualY(showOwnBtn, vy);
        vy += ROW_H;

        // 他人的字幕开关
        Button showOtherBtn = Button.builder(
                getToggleText("gui.playervox.subtitle_show_other", config.showOther),
                b -> {
                    VoxClientConfig cfg = VoxClientConfig.get();
                    cfg.showOther = !cfg.showOther;
                    b.setMessage(getToggleText("gui.playervox.subtitle_show_other", cfg.showOther));
                    cfg.save();
                }
        ).pos(toggleX, 0).size(toggleW, INPUT_H).build();
        addRenderableWidget(showOtherBtn);
        scrollableWidgets.add(showOtherBtn);
        setVirtualY(showOtherBtn, vy);
        vy += ROW_H + SECTION_GAP;

        // ===== 轮盘分类标题 =====
        radialSectionVY = vy;
        vy += SECTION_GAP;

        // 鼠标灵敏度
        sensitivityRowVY = vy;
        sensitivityInput = addScrollableEditBox(inputX, vy, inputW, String.valueOf(config.sensitivity), "Sens");
        vy += ROW_H;

        contentTotalH = vy;

        // 确保 scrollOffset 不越界
        clampScroll();
        applyScroll();

        // ===== 底部固定按钮 =====
        int btnW = 120;
        int gap = 10;
        int totalW = btnW * 2 + gap;
        int bx = cx - totalW / 2;
        int by = this.height - BOTTOM_H + 5;

        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.subtitle_reset"), b -> {
            VoxClientConfig cfg = VoxClientConfig.get();
            cfg.x = -1;
            cfg.y = -1;
            cfg.scale = 1.0f;
            cfg.nameColor = "FFAA00";
            cfg.showOwn = true;
            cfg.showOther = true;
            cfg.sensitivity = 0.6f;
            cfg.save();
            rebuildWidgets();
        }).pos(bx, by).size(btnW, INPUT_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.playervox.done"), b -> {
            applyConfig();
            this.minecraft.setScreen(parent);
        }).pos(bx + btnW + gap, by).size(btnW, INPUT_H).build());
    }

    // ── 滚动辅助 ──

    private EditBox addScrollableEditBox(int x, int vy, int w, String value, String hint) {
        EditBox box = new EditBox(this.font, x, 0, w, INPUT_H, Component.literal(hint));
        box.setValue(value);
        addRenderableWidget(box);
        scrollableWidgets.add(box);
        setVirtualY(box, vy);
        return box;
    }

    /** 将虚拟 Y 映射到屏幕 Y，并设置可见性 */
    private void setVirtualY(AbstractWidget widget, int vy) {
        int screenY = scrollTop + vy - scrollOffset;
        widget.setY(screenY);
        boolean visible = (screenY + widget.getHeight() > scrollTop) && (screenY < scrollBottom);
        widget.visible = visible;
        widget.active = visible;
    }

    private void applyScroll() {
        // EditBox 的虚拟 Y 需要从 fieldRowVY 等取
        setVirtualY(xInput, fieldRowVY[0]);
        setVirtualY(yInput, fieldRowVY[1]);
        setVirtualY(scaleInput, fieldRowVY[2]);
        setVirtualY(nameColorInput, fieldRowVY[3]);
        setVirtualY(sensitivityInput, sensitivityRowVY);

        // Button：用它们在 scrollableWidgets 中的顺序还原虚拟 Y
        // 按 init 顺序：5个EditBox, showOwnBtn, showOtherBtn
        // 直接遍历，跳过 EditBox 即可
        int btnIndex = 0;
        int[] btnVYs = {
                fieldRowVY[3] + ROW_H + font.lineHeight + 6,   // showOwn
                fieldRowVY[3] + ROW_H + font.lineHeight + 6 + ROW_H  // showOther
        };
        for (AbstractWidget w : scrollableWidgets) {
            if (w instanceof Button) {
                if (btnIndex < btnVYs.length) {
                    setVirtualY(w, btnVYs[btnIndex]);
                    btnIndex++;
                }
            }
        }
    }

    private void clampScroll() {
        int visibleH = scrollBottom - scrollTop;
        int maxScroll = Math.max(0, contentTotalH - visibleH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private int toScreenY(int vy) {
        return scrollTop + vy - scrollOffset;
    }

    // ── 滚轮 ──

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= scrollTop && mouseY <= scrollBottom) {
            scrollOffset -= (int) (delta * SCROLL_STEP);
            clampScroll();
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ── 点击：滚动区域外的控件正常响应，滚动区域内裁剪外的不响应 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 底部按钮区域正常
        if (mouseY >= scrollBottom) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 滚动区域内：只有可见的控件才响应
        if (mouseY < scrollTop) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── 配置 ──

    private void applyConfig() {
        VoxClientConfig config = VoxClientConfig.get();
        try { config.x = Integer.parseInt(xInput.getValue()); } catch (NumberFormatException ignored) {}
        try { config.y = Integer.parseInt(yInput.getValue()); } catch (NumberFormatException ignored) {}
        try { config.scale = Float.parseFloat(scaleInput.getValue()); } catch (NumberFormatException ignored) {}
        String color = nameColorInput.getValue().replaceAll("[^0-9a-fA-F]", "");
        if (color.length() == 6) config.nameColor = color;
        try {
            float sens = Float.parseFloat(sensitivityInput.getValue());
            config.sensitivity = Math.max(0.1f, Math.min(3.0f, sens));
        } catch (NumberFormatException ignored) {}
        config.save();
    }

    private Component getToggleText(String translationKey, boolean value) {
        String state = value ? "\u00a7aON" : "\u00a7cOFF";
        return Component.translatable(translationKey).append(": " + state);
    }

    // ── 渲染 ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int cx = this.width / 2;
        int labelX = cx - 110;

        // 标题（固定不滚动）
        g.drawCenteredString(this.font, this.title, cx, 6, 0xFFFFFF);

        // 分隔���
        g.fill(4, scrollBottom, this.width - 4, scrollBottom + 1, 0x44FFFFFF);

        // ── 可滚动区域 scissor 裁剪 ──
        g.enableScissor(0, scrollTop, this.width, scrollBottom);

        // 字幕分类标题
        int sy = toScreenY(subtitleSectionVY);
        g.drawCenteredString(this.font, Component.translatable("gui.playervox.category_subtitle"), cx, sy, 0x55FF55);

        // 字幕参数标签
        String[] labels = {
                "gui.playervox.subtitle_x",
                "gui.playervox.subtitle_y",
                "gui.playervox.subtitle_scale",
                "gui.playervox.subtitle_name_color"
        };
        for (int i = 0; i < labels.length; i++) {
            int ly = toScreenY(fieldRowVY[i]) + 6;
            g.drawString(this.font, Component.translatable(labels[i]), labelX, ly, 0xE0E0E0);
        }

        // 提示
        int hy = toScreenY(hintVY);
        g.drawString(this.font, Component.translatable("gui.playervox.subtitle_hint"), labelX, hy, 0x666666);

        // 轮盘分类标题
        int ry = toScreenY(radialSectionVY);
        g.drawCenteredString(this.font, Component.translatable("gui.playervox.category_radial"), cx, ry, 0x55FF55);

        // 灵敏度标签
        int sly = toScreenY(sensitivityRowVY) + 6;
        g.drawString(this.font, Component.translatable("gui.playervox.radial_sensitivity"), labelX, sly, 0xE0E0E0);

        // 滚动区域内的控件
        for (AbstractWidget w : scrollableWidgets) {
            if (w.visible) {
                w.render(g, mouseX, mouseY, partialTick);
            }
        }

        // 滚动条
        int visibleH = scrollBottom - scrollTop;
        if (contentTotalH > visibleH) {
            int maxScroll = contentTotalH - visibleH;
            int barX = this.width - 6;
            int thumbH = Math.max(10, visibleH * visibleH / contentTotalH);
            int thumbY = scrollTop + (int) ((long) scrollOffset * (visibleH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 3, scrollBottom, 0x44FFFFFF);
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xAAFFFFFF);
        }

        g.disableScissor();

        // 底部按钮（固定，不在 scissor 内）
        for (var renderable : this.renderables) {
            if (renderable instanceof AbstractWidget aw && !scrollableWidgets.contains(aw)) {
                aw.render(g, mouseX, mouseY, partialTick);
            }
        }
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
