package com.playervox.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.playervox.PlayerVoxMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端按语音包 ID 独立记忆轮盘上次打开的页码。
 * 存储在 config/playervox-radial-page.json。
 */
@OnlyIn(Dist.CLIENT)
public class RadialPageState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "playervox-radial-page.json");
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, Integer>>() {}.getType();

    /** packId → 上次页码 (1-based) */
    private static Map<String, Integer> pageMap;

    private static Map<String, Integer> getMap() {
        if (pageMap == null) {
            pageMap = load();
        }
        return pageMap;
    }

    public static int getPage(String packId) {
        return getMap().getOrDefault(packId, 1);
    }

    public static void setPage(String packId, int page) {
        getMap().put(packId, page);
        save();
    }

    private static Map<String, Integer> load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                Map<String, Integer> map = GSON.fromJson(json, MAP_TYPE);
                if (map != null) return map;
            }
        } catch (Exception e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: 加载轮盘页码配置失败", e);
        }
        return new HashMap<>();
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(getMap()));
        } catch (IOException e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: 保存轮盘页码配置失败", e);
        }
    }
}
