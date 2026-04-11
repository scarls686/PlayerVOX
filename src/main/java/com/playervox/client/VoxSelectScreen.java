package com.playervox.client;

import com.playervox.common.loader.VoxPackMeta;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketSelectVoxPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 语音包选择 GUI，使用 ObjectSelectionList 支持滚动。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSelectScreen extends Screen {

    private VoxPackList packList;

    public VoxSelectScreen() {
        super(Component.translatable("gui.claudevox.select_pack"));
    }

    @Override
    protected void init() {
        super.init();

        // 列表区域：顶部留 40px 给标题，底部留 30px
        this.packList = new VoxPackList(this.minecraft, this.width, this.height, 40, this.height - 30, 24);
        this.addWidget(this.packList);

        // 添加 "无语音包" 选项
        this.packList.addEntry(new VoxPackEntry("", "无语音包", ""));

        // 添加各语音包
        List<VoxPackMeta> packs = VoxClientState.getAvailablePacks();
        for (VoxPackMeta meta : packs) {
            this.packList.addEntry(new VoxPackEntry(meta.getId(), meta.getName(), meta.getDescription()));
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
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        this.packList.render(graphics, mouseX, mouseY, partialTick);

        // 标题
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 当前选择提示
        String currentId = VoxClientState.getSelectedPackId();
        String info = currentId.isEmpty() ? "当前未选择语音包" : "当前: " + currentId;
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

    @OnlyIn(Dist.CLIENT)
    class VoxPackEntry extends ObjectSelectionList.Entry<VoxPackEntry> {

        final String packId;
        private final String name;
        private final String description;

        VoxPackEntry(String packId, String name, String description) {
            this.packId = packId;
            this.name = name;
            this.description = description;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            boolean selected = packId.equals(VoxClientState.getSelectedPackId());
            int color = selected ? 0x55FF55 : (hovered ? 0xFFFFFF : 0xCCCCCC);
            String prefix = selected ? "\u25b6 " : "  ";

            graphics.drawString(VoxSelectScreen.this.font, prefix + name, left + 4, top + 2, color, false);

            if (!description.isEmpty()) {
                graphics.drawString(VoxSelectScreen.this.font, description, left + 16, top + 13, 0x888888, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            VoxSelectScreen.this.packList.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }
    }
}
