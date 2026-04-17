package com.playervox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketRadialSelect;
import com.playervox.common.radial.RadialRegistry;
import com.playervox.common.radial.RadialSlotDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 语音轮盘 HUD Overlay。
 * 不使用 Screen，玩家可自由移动；视角转动由 MouseHandlerMixin 阻止。
 * 鼠标增量通过 GLFW 光标位置差值计算，用于扇区选择。
 */
@OnlyIn(Dist.CLIENT)
public class RadialOverlay {

    private static final float RADIUS_RATIO = 0.25f;
    private static final float DEADZONE_RATIO = 0.25f;
    /** 光标指示点半径 */
    private static final int CURSOR_DOT_SIZE = 2;

    private static boolean isOpen = false;
    private static int currentPage;
    private static int hoveredSlotIndex = -1;

    /** 虚拟光标位置（以屏幕中心为原点） */
    private static float cursorX = 0;
    private static float cursorY = 0;

    /** GLFW 光标上一帧的绝对位置 */
    private static double lastGlfwX = 0;
    private static double lastGlfwY = 0;

    /** Mixin 查询用 */
    public static boolean isActive() {
        return isOpen;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        String packId = VoxClientState.getSelectedPackId();
        if (packId == null || packId.isEmpty()) return;
        if (RadialRegistry.getSlots(packId).isEmpty()) return;

        isOpen = true;
        hoveredSlotIndex = -1;
        cursorX = 0;
        cursorY = 0;

        currentPage = RadialPageState.getPage(packId);
        int maxPage = RadialRegistry.getPageCount(packId);
        if (currentPage < 1 || currentPage > maxPage) currentPage = 1;

        // 记录当前 GLFW 光标位置作为基准
        double[] xArr = new double[1], yArr = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), xArr, yArr);
        lastGlfwX = xArr[0];
        lastGlfwY = yArr[0];
    }

    public static void close() {
        if (!isOpen) return;
        isOpen = false;

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

    public static void onScroll(double delta) {
        if (!isOpen) return;
        String packId = VoxClientState.getSelectedPackId();
        if (packId == null || packId.isEmpty()) return;

        int maxPage = RadialRegistry.getPageCount(packId);
        if (maxPage <= 1) return;

        if (delta > 0) {
            currentPage--;
            if (currentPage < 1) currentPage = maxPage;
        } else if (delta < 0) {
            currentPage++;
            if (currentPage > maxPage) currentPage = 1;
        }
    }

    /** 由 ClientSetup.onRenderGui 调用 */
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (!isOpen) return;
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getWindow() == null) return;

        String packId = VoxClientState.getSelectedPackId();
        if (packId == null || packId.isEmpty()) return;

        // 读取 GLFW 光标增量并累加到虚拟光标
        double[] xArr = new double[1], yArr = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), xArr, yArr);
        double dx = xArr[0] - lastGlfwX;
        double dy = yArr[0] - lastGlfwY;
        lastGlfwX = xArr[0];
        lastGlfwY = yArr[0];

        float sensitivity = VoxClientConfig.get().sensitivity;
        cursorX += (float) (dx * sensitivity);
        cursorY += (float) (dy * sensitivity);

        // 渲染
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        List<RadialSlotDefinition> pageSlots = RadialRegistry.getSlotsForPage(packId, currentPage);
        int maxPage = RadialRegistry.getPageCount(packId);

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float radius = screenHeight * RADIUS_RATIO;
        float deadzone = radius * DEADZONE_RATIO;

        float dist = (float) Math.sqrt(cursorX * cursorX + cursorY * cursorY);

        int totalSlots = pageSlots.isEmpty() ? 1 : pageSlots.get(0).pageTotalSlots;
        float sectorAngle = (float) (2 * Math.PI / totalSlots);

        // 判断 hover 扇区
        if (dist < deadzone) {
            hoveredSlotIndex = -1;
        } else {
            float angle = (float) Math.atan2(cursorY, cursorX);
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

        // 半透明背景圆
        drawFilledCircle(poseStack, centerX, centerY, radius, 64, 0x40404040);

        // 死区圆
        drawFilledCircle(poseStack, centerX, centerY, deadzone, 32, 0x50303030);

        // 扇区分割线
        for (int i = 0; i < totalSlots; i++) {
            float lineAngle = i * sectorAngle - (float) (Math.PI / 2);
            float x1 = centerX + (float) Math.cos(lineAngle) * deadzone;
            float y1 = centerY + (float) Math.sin(lineAngle) * deadzone;
            float x2 = centerX + (float) Math.cos(lineAngle) * radius;
            float y2 = centerY + (float) Math.sin(lineAngle) * radius;
            drawLine(poseStack, x1, y1, x2, y2, 0x60FFFFFF);
        }

        // 高亮扇区
        if (hoveredSlotIndex >= 0 && hoveredSlotIndex < pageSlots.size()) {
            int slotIdx = pageSlots.get(hoveredSlotIndex).slot - 1;
            float startAngle = slotIdx * sectorAngle - (float) (Math.PI / 2);
            float endAngle = startAngle + sectorAngle;
            drawArcFilled(poseStack, centerX, centerY, deadzone, radius, startAngle, endAngle, 16, 0x40FFFFFF);
        }

        // 圆环边框
        drawCircleOutline(poseStack, centerX, centerY, radius, 64, 0x60FFFFFF);
        drawCircleOutline(poseStack, centerX, centerY, deadzone, 32, 0x60FFFFFF);

        // 扇区标签和图标
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
                boolean iconExists = mc.getResourceManager().getResource(iconTexture).isPresent();
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

        // 中心页码
        String pageText = currentPage + "/" + maxPage;
        int pageTextWidth = font.width(pageText);
        int centerColor = (hoveredSlotIndex == -1 && dist >= 1) ? 0xFFFFFF00 : 0xFFAAAAAA;
        graphics.drawString(font, pageText,
                (int) (centerX - pageTextWidth / 2f),
                (int) (centerY - font.lineHeight / 2f),
                centerColor, true);

        // 虚拟光标指示点（用 fill 走标准 GUI 管线，不被遮盖）
        int cx = (int) (centerX + cursorX);
        int cy = (int) (centerY + cursorY);
        graphics.fill(cx - CURSOR_DOT_SIZE, cy - CURSOR_DOT_SIZE,
                cx + CURSOR_DOT_SIZE, cy + CURSOR_DOT_SIZE, 0xDDFFFFFF);

        poseStack.popPose();
    }

    // ===== 渲染工具方法（从 RadialScreen 搬运） =====

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
