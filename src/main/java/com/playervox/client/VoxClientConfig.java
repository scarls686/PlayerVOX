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
 * PlayerVOX 客户端配置，存储在 config/playervox-client.json。
 * 合并字幕设置与轮盘设置。
 */
@OnlyIn(Dist.CLIENT)
public class VoxClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "playervox-client.json");

    private static VoxClientConfig instance;

    // ---- 字幕设置 ----
    public boolean showOwn = true;
    public boolean showOther = true;
    public int x = -1; // -1 = 居中
    public int y = -1; // -1 = 物品栏上方
    public float scale = 1.0f;
    public String nameColor = "FFAA00"; // 玩家名颜色，十六进制 RGB

    // ---- 轮盘设置 ----
    /** 鼠标灵敏度（0.1~3.0），影响虚拟光标移动速度 */
    public float sensitivity = 0.6f;

    public static VoxClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static VoxClientConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = GSON.fromJson(json, VoxClientConfig.class);
                if (instance == null) instance = new VoxClientConfig();
                instance.clamp();
                return instance;
            }
        } catch (Exception e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: failed to load client config", e);
        }
        instance = new VoxClientConfig();
        instance.save();
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: failed to save client config", e);
        }
    }

    private void clamp() {
        sensitivity = Math.max(0.1f, Math.min(3.0f, sensitivity));
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
