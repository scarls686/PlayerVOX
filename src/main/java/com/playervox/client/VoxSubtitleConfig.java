package com.playervox.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.playervox.PlayerVoxMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端字幕配置，存储在 config/playervox-subtitle.json。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSubtitleConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "playervox-subtitle.json");

    private static VoxSubtitleConfig instance;

    public boolean showOwn = true;
    public boolean showOther = true;
    public int x = -1; // -1 = 居中
    public int y = -1; // -1 = 物品栏上方
    public float scale = 1.0f;
    public String nameColor = "FFAA00"; // 玩家名颜色，十六进制 RGB

    public static VoxSubtitleConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static VoxSubtitleConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = GSON.fromJson(json, VoxSubtitleConfig.class);
                if (instance == null) instance = new VoxSubtitleConfig();
                return instance;
            }
        } catch (Exception e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: 加载字幕配置失败", e);
        }
        instance = new VoxSubtitleConfig();
        instance.save();
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: 保存字幕配置失败", e);
        }
    }

    /**
     * 解析 nameColor 为 0xRRGGBB int 值。
     */
    public int getNameColorInt() {
        try {
            return Integer.parseInt(nameColor, 16);
        } catch (NumberFormatException e) {
            return 0xFFAA00;
        }
    }
}
