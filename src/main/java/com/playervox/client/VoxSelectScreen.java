package com.playervox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playervox.common.loader.VoxPackMeta;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketSelectVoxPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音包选择 GUI，使用 ObjectSelectionList 支持滚动。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSelectScreen extends Screen {

    private VoxPackList packList;

    public VoxSelectScreen() {
        super(Component.translatable("gui.playervox.select_pack"));
    }

    @Override
    protected void init() {
        super.init();

        // 列表区域：顶部留 40px 给标题，底部留 30px
        this.packList = new VoxPackList(this.minecraft, this.width, this.height, 40, this.height - 30, 28);
        this.addWidget(this.packList);

        // 添加 "无语音包" 选项
        this.packList.addEntry(new VoxPackEntry("", null, null, null));

        // 添加各语音包
        List<VoxPackMeta> packs = VoxClientState.getAvailablePacks();
        for (VoxPackMeta meta : packs) {
            this.packList.addEntry(new VoxPackEntry(meta.getId(), meta.getName(), meta.getDescription(), meta.getIcon()));
        }

        // 选中当前选择项
        String currentId = VoxClientState.getSelectedPackId();
        for (int i = 0; i < this.packList.children().size(); i++) {
            VoxPackEntry entry = this.packList.children().get(i);
            if (entry.packId.equals(currentId)) {
                this.packList.setSelected(entry);
                break;
            }
        }

        // 字幕设置按钮（右下角）
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.playervox.subtitle_settings"),
                b -> this.minecraft.setScreen(new VoxSubtitleSettingsScreen(this))
        ).pos(this.width - 130, this.height - 25).size(120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        this.packList.render(graphics, mouseX, mouseY, partialTick);

        // 标题
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 当前选择提示
        String currentId = VoxClientState.getSelectedPackId();
        Component info = currentId.isEmpty()
                ? Component.translatable("gui.playervox.current_none")
                : Component.translatable("gui.playervox.current_pack", currentId);
        graphics.drawCenteredString(this.font, info, this.width / 2, this.height - 20, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void selectPack(String packId) {
        NetworkHandler.INSTANCE.sendToServer(new PacketSelectVoxPack(packId));
        VoxClientState.setSelectedPack(packId);
    }

    // ---- 内部类：列表 ----

    @OnlyIn(Dist.CLIENT)
    class VoxPackList extends ObjectSelectionList<VoxPackEntry> {

        public VoxPackList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
            this.setRenderBackground(true);
            this.setRenderTopAndBottom(true);
        }

        @Override
        public void setSelected(@Nullable VoxPackEntry entry) {
            super.setSelected(entry);
            if (entry != null) {
                VoxSelectScreen.this.selectPack(entry.packId);
            }
        }

        @Override
        public int addEntry(VoxPackEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 124;
        }

        @Override
        public int getRowWidth() {
            return 240;
        }
    }

    // ---- 内部类：列表项 ----

    private static final int ICON_SIZE = 20;

    @OnlyIn(Dist.CLIENT)
    class VoxPackEntry extends ObjectSelectionList.Entry<VoxPackEntry> {

        final String packId;
        private final @Nullable String nameKey;        // 翻译键，null 表示 "无语音包"
        private final @Nullable String descriptionKey;  // 翻译键
        private final @Nullable String iconPath;        // icon 文件名（如 "icon.png"）
        private @Nullable ResourceLocation iconLocation; // 加载后的纹理位置
        private boolean iconLoaded = false;

        VoxPackEntry(String packId, @Nullable String nameKey, @Nullable String descriptionKey, @Nullable String iconPath) {
            this.packId = packId;
            this.nameKey = nameKey;
            this.descriptionKey = descriptionKey;
            this.iconPath = iconPath;
        }

        private Component getDisplayName() {
            if (nameKey == null || nameKey.isEmpty()) {
                return Component.translatable("gui.playervox.no_pack");
            }
            return Component.translatable(nameKey);
        }

        private void tryLoadIcon() {
            if (iconLoaded) return;
            iconLoaded = true;
            if (packId.isEmpty() || iconPath == null || iconPath.isEmpty()) return;
            // blit 需要 textures/ 前缀路径，icon 实际位于 assets/<packId>/textures/icon.png
            ResourceLocation texLoc = new ResourceLocation(packId, "textures/" + iconPath);
            if (Minecraft.getInstance().getResourceManager().getResource(texLoc).isPresent()) {
                iconLocation = texLoc;
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            boolean selected = packId.equals(VoxClientState.getSelectedPackId());
            int color = selected ? 0x55FF55 : (hovered ? 0xFFFFFF : 0xCCCCCC);

            tryLoadIcon();

            int textLeft = left + 4;

            // 渲染 icon
            if (iconLocation != null) {
                try {
                    RenderSystem.enableBlend();
                    graphics.blit(iconLocation, left + 2, top + 2, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                    RenderSystem.disableBlend();
                    textLeft = left + ICON_SIZE + 6;
                } catch (Exception e) {
                    // icon 加载失败，忽略
                    iconLocation = null;
                }
            }

            String prefix = selected ? "\u25b6 " : "  ";
            graphics.drawString(VoxSelectScreen.this.font, prefix + getDisplayName().getString(),
                    textLeft, top + 4, color, false);

            if (descriptionKey != null && !descriptionKey.isEmpty()) {
                Component desc = Component.translatable(descriptionKey);
                graphics.drawString(VoxSelectScreen.this.font, desc,
                        textLeft + 10, top + 16, 0x888888, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            VoxSelectScreen.this.packList.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return getDisplayName();
        }
    }
}
