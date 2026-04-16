package com.playervox.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketRadialSelect;
import com.playervox.common.radial.RadialRegistry;
import com.playervox.common.radial.RadialSlotDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 语音轮盘 GUI Screen。
 * 打开时 Minecraft 自动锁定鼠标（不转视角），鼠标位置直接决定选中扇区，滚轮翻页，关闭时确认选择。
 */
public class RadialScreen extends Screen {

    /** 轮盘半径占屏幕高度的比例 */
    private static final float RADIUS_RATIO = 0.25f;
    /** 死区为半径的 25% */
    private static final float DEADZONE_RATIO = 0.25f;

    private int currentPage;
    private int hoveredSlotIndex = -1;

    public RadialScreen() {
        super(Component.empty());
        String packId = VoxClientState.getSelectedPackId();
        currentPage = RadialPageState.getPage(packId != null ? packId : "");
        int maxPage = (packId != null && !packId.isEmpty()) ? RadialRegistry.getPageCount(packId) : 1;
        if (currentPage > maxPage) currentPage = 1;
        if (currentPage < 1) currentPage = 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // 按键松开时由 Screen 自身负责关闭，避免依赖外部 tick 的时序
        if (!isRadialKeyDown()) {
            onClose();
            minecraft.setScreen(null);
        }
    }

    private boolean isRadialKeyDown() {
        if (minecraft == null || minecraft.getWindow() == null) return false;
        InputConstants.Key key = VoxKeyBindings.RADIAL_WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return GLFW.glfwGetKey(minecraft.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();

        String packId = VoxClientState.getSelectedPackId();
        if (packId == null || packId.isEmpty()) return;

        RadialPageState.setPage(packId, currentPage);

        if (hoveredSlotIndex >= 0) {
            List<RadialSlotDefinition> pageSlots = RadialRegistry.getSlotsForPage(packId, currentPage);
            if (hoveredSlotIndex < pageSlots.size()) {
                RadialSlotDefinition selected = pageSlots.get(hoveredSlotIndex);
                NetworkHandler.INSTANCE.sendToServer(new PacketRadialSelect(selected.id.toString()));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        String packId = VoxClientState.getSelectedPackId();
        if (packId == null || packId.isEmpty()) return false;

        int maxPage = RadialRegistry.getPageCount(packId);
        if (maxPage <= 1) return false;

        if (delta > 0) {
            currentPage--;
            if (currentPage < 1) currentPage = maxPage;
        } else if (delta < 0) {
            currentPage++;
            if (currentPage > maxPage) currentPage = 1;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super.render()，不绘制半透明背景

        String packId = VoxClientState.getSelectedPackId();
        if (packId == null || packId.isEmpty()) return;

        List<RadialSlotDefinition> pageSlots = RadialRegistry.getSlotsForPage(packId, currentPage);
        int maxPage = RadialRegistry.getPageCount(packId);

        float centerX = width / 2f;
        float centerY = height / 2f;

        float radius = height * RADIUS_RATIO;
        float deadzone = radius * DEADZONE_RATIO;

        // 鼠标相对于屏幕中心的偏移
        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        int totalSlots = pageSlots.isEmpty() ? 1 : pageSlots.get(0).pageTotalSlots;
        float sectorAngle = (float) (2 * Math.PI / totalSlots);

        // 判断 hover 扇区
        if (dist < deadzone) {
            hoveredSlotIndex = -1;
        } else {
            float angle = (float) Math.atan2(dy, dx);
            float normalizedAngle = (float) (angle + Math.PI / 2);
            if (normalizedAngle < 0) normalizedAngle += (float) (2 * Math.PI);
            if (normalizedAngle >= 2 * Math.PI) normalizedAngle -= (float) (2 * Math.PI);

            int sectorIndex = (int) (normalizedAngle / sectorAngle);
            if (sectorIndex >= totalSlots) sectorIndex = totalSlots - 1;

            hoveredSlotIndex = -1;
            for (int i = 0; i < pageSlots.size(); i++) {
                if (pageSlots.get(i).slot - 1 == sectorIndex) {
                    hoveredSlotIndex = i;
                    break;
                }
            }
        }

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        // 绘制半透明背景圆
        drawFilledCircle(poseStack, centerX, centerY, radius, 64, 0x40404040);

        // 绘制死区圆
        drawFilledCircle(poseStack, centerX, centerY, deadzone, 32, 0x50303030);

        // 绘制扇区分割线
        for (int i = 0; i < totalSlots; i++) {
            float lineAngle = i * sectorAngle - (float) (Math.PI / 2);
            float x1 = centerX + (float) Math.cos(lineAngle) * deadzone;
            float y1 = centerY + (float) Math.sin(lineAngle) * deadzone;
            float x2 = centerX + (float) Math.cos(lineAngle) * radius;
            float y2 = centerY + (float) Math.sin(lineAngle) * radius;
            drawLine(poseStack, x1, y1, x2, y2, 0x60FFFFFF);
        }

        // 绘制高亮扇区
        if (hoveredSlotIndex >= 0 && hoveredSlotIndex < pageSlots.size()) {
            int slotIdx = pageSlots.get(hoveredSlotIndex).slot - 1;
            float startAngle = slotIdx * sectorAngle - (float) (Math.PI / 2);
            float endAngle = startAngle + sectorAngle;
            drawArcFilled(poseStack, centerX, centerY, deadzone, radius, startAngle, endAngle, 16, 0x40FFFFFF);
        }

        // 绘制圆环边框
        drawCircleOutline(poseStack, centerX, centerY, radius, 64, 0x60FFFFFF);
        drawCircleOutline(poseStack, centerX, centerY, deadzone, 32, 0x60FFFFFF);

        // 绘制扇区标签和图标
        for (int i = 0; i < pageSlots.size(); i++) {
            RadialSlotDefinition slot = pageSlots.get(i);
            int slotIdx = slot.slot - 1;
            float midAngle = slotIdx * sectorAngle + sectorAngle / 2 - (float) (Math.PI / 2);
            float textDist = (deadzone + radius) / 2f;
            float textX = centerX + (float) Math.cos(midAngle) * textDist;
            float textY = centerY + (float) Math.sin(midAngle) * textDist;

            if (slot.icon != null) {
                ResourceLocation iconTexture = new ResourceLocation(
                        slot.icon.getNamespace(),
                        "textures/" + slot.icon.getPath() + ".png");
                boolean iconExists = minecraft.getResourceManager().getResource(iconTexture).isPresent();
                if (iconExists) {
                    int iconSize = 16;
                    RenderSystem.setShaderTexture(0, iconTexture);
                    graphics.blit(iconTexture,
                            (int) (textX - iconSize / 2f), (int) (textY - iconSize / 2f - font.lineHeight / 2f - 1),
                            0, 0, iconSize, iconSize, iconSize, iconSize);
                    textY += iconSize / 2f + 2;
                }
            }

            Component label = Component.translatable(slot.label);
            int maxWidth = (int) ((radius - deadzone) * 0.85f);
            List<FormattedCharSequence> lines = font.split(label, maxWidth);
            if (lines.size() > 2) lines = lines.subList(0, 2);

            int textColor = (i == hoveredSlotIndex) ? 0xFFFFFF00 : 0xFFC0C0C0;
            int totalTextHeight = lines.size() * (font.lineHeight + 1);
            float startTextY = textY - totalTextHeight / 2f;

            for (int j = 0; j < lines.size(); j++) {
                int lineWidth = font.width(lines.get(j));
                graphics.drawString(font, lines.get(j),
                        (int) (textX - lineWidth / 2f),
                        (int) (startTextY + j * (font.lineHeight + 1)),
                        textColor, true);
            }
        }

        // 中心：页码显示
        String pageText = currentPage + "/" + maxPage;
        int pageTextWidth = font.width(pageText);
        int centerColor = (hoveredSlotIndex == -1 && dist >= 1) ? 0xFFFFFF00 : 0xFFAAAAAA;
        graphics.drawString(font, pageText,
                (int) (centerX - pageTextWidth / 2f),
                (int) (centerY - font.lineHeight / 2f),
                centerColor, true);

        poseStack.popPose();
    }

    // ===== 渲染工具方法 =====

    private static void drawFilledCircle(PoseStack poseStack, float cx, float cy, float r, int segments, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, cx, cy, 0).color(red, green, blue, a).endVertex();
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            buf.vertex(matrix, cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r, 0)
                    .color(red, green, blue, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    private static void drawCircleOutline(PoseStack poseStack, float cx, float cy, float r, int segments, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            buf.vertex(matrix, cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r, 0)
                    .color(red, green, blue, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    private static void drawLine(PoseStack poseStack, float x1, float y1, float x2, float y2, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, x1, y1, 0).color(red, green, blue, a).endVertex();
        buf.vertex(matrix, x2, y2, 0).color(red, green, blue, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    private static void drawArcFilled(PoseStack poseStack, float cx, float cy, float innerR, float outerR,
                                      float startAngle, float endAngle, int segments, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + (endAngle - startAngle) * i / segments;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buf.vertex(matrix, cx + cos * innerR, cy + sin * innerR, 0).color(red, green, blue, a).endVertex();
            buf.vertex(matrix, cx + cos * outerR, cy + sin * outerR, 0).color(red, green, blue, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }
}
