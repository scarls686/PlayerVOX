package com.playervox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 在 HUD 上渲染语音字幕。
 * 最多同时显示 3 条，新字幕将旧字幕向上推。
 * 最后 10 tick 线性淡出。基于游戏 tick 计时。
 */
@OnlyIn(Dist.CLIENT)
public class SubtitleOverlay {

    private static final int MAX_ENTRIES = 3;
    private static final int DISPLAY_TICKS = 60; // 3 秒
    private static final int FADEOUT_TICKS = 10;
    private static final int MAX_LINES_PER_ENTRY = 2;
    private static final int MAX_TEXT_WIDTH = 200;
    private static final double MAX_DISTANCE = 16.0;

    private static final Deque<SubtitleEntry> entries = new ArrayDeque<>();
    private static long lastTickCount = -1;

    /**
     * 收到字幕时调用（客户端线程）。
     */
    public static void onSubtitle(String playerName, String translationKey, boolean isOwn, double distance) {
        VoxSubtitleConfig config = VoxSubtitleConfig.get();

        if (distance > MAX_DISTANCE) return;
        if (isOwn && !config.showOwn) return;
        if (!isOwn && !config.showOther) return;

        entries.addLast(new SubtitleEntry(playerName, translationKey, DISPLAY_TICKS));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;
        if (entries.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;

        // 基于 tick 计时
        if (mc.level != null) {
            long currentTick = mc.level.getGameTime();
            if (currentTick != lastTickCount) {
                lastTickCount = currentTick;
                Iterator<SubtitleEntry> it = entries.iterator();
                while (it.hasNext()) {
                    SubtitleEntry entry = it.next();
                    entry.remainingTicks--;
                    if (entry.remainingTicks <= 0) {
                        it.remove();
                    }
                }
            }
        }

        if (entries.isEmpty()) return;

        Font font = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();
        VoxSubtitleConfig config = VoxSubtitleConfig.get();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int baseX = config.x >= 0 ? config.x : screenWidth / 2;
        int baseY = config.y >= 0 ? config.y : screenHeight - 48;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        float scale = config.scale;
        if (scale != 1.0f) {
            poseStack.translate(baseX, baseY, 0);
            poseStack.scale(scale, scale, 1.0f);
            poseStack.translate(-baseX, -baseY, 0);
        }

        int nameColor = config.getNameColorInt();
        int lineHeight = font.lineHeight + 2;

        // 计算总高度
        int totalHeight = 0;
        for (SubtitleEntry entry : entries) {
            int lines = getLineCount(font, entry);
            totalHeight += lines * lineHeight + 2;
        }

        int currentY = baseY - totalHeight;

        for (SubtitleEntry entry : entries) {
            float alpha = 1.0f;
            if (entry.remainingTicks <= FADEOUT_TICKS) {
                alpha = (float) entry.remainingTicks / FADEOUT_TICKS;
            }
            int alphaInt = (int) (alpha * 255);
            if (alphaInt <= 0) continue;

            MutableComponent subtitleComponent = Component.translatable(entry.translationKey);
            String namePrefix = entry.playerName + "\uff1a";

            int nameArgb = (alphaInt << 24) | nameColor;
            int textArgb = (alphaInt << 24) | 0xFFFFFF;

            int nameWidth = font.width(namePrefix);

            List<FormattedCharSequence> lines = font.split(subtitleComponent, MAX_TEXT_WIDTH);
            if (lines.size() > MAX_LINES_PER_ENTRY) {
                lines = lines.subList(0, MAX_LINES_PER_ENTRY);
            }

            int firstLineTextWidth = lines.isEmpty() ? 0 : font.width(lines.get(0));
            int firstLineWidth = nameWidth + firstLineTextWidth;
            int maxLineWidth = firstLineWidth;
            for (int i = 1; i < lines.size(); i++) {
                maxLineWidth = Math.max(maxLineWidth, font.width(lines.get(i)));
            }

            int startX = baseX - maxLineWidth / 2;

            if (!lines.isEmpty()) {
                graphics.drawString(font, namePrefix, startX, currentY, nameArgb, false);
                graphics.drawString(font, lines.get(0), startX + nameWidth, currentY, textArgb, false);
                currentY += lineHeight;
            }

            for (int i = 1; i < lines.size(); i++) {
                int lineWidth = font.width(lines.get(i));
                int lineX = baseX - lineWidth / 2;
                graphics.drawString(font, lines.get(i), lineX, currentY, textArgb, false);
                currentY += lineHeight;
            }

            currentY += 2;
        }

        poseStack.popPose();
    }

    private static int getLineCount(Font font, SubtitleEntry entry) {
        List<FormattedCharSequence> lines = font.split(
                Component.translatable(entry.translationKey), MAX_TEXT_WIDTH
        );
        return Math.min(lines.size(), MAX_LINES_PER_ENTRY);
    }

    private static class SubtitleEntry {
        final String playerName;
        final String translationKey;
        int remainingTicks;

        SubtitleEntry(String playerName, String translationKey, int ticks) {
            this.playerName = playerName;
            this.translationKey = translationKey;
            this.remainingTicks = ticks;
        }
    }
}
