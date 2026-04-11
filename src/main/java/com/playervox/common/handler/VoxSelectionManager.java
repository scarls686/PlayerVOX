package com.playervox.common.handler;

import com.playervox.PlayerVoxMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 持久化存储每个玩家选择的语音包 ID。
 * 使用 Forge 的 SavedData 机制，跟随世界存档。
 */
public class VoxSelectionManager extends SavedData {

    private static final String DATA_NAME = "playervox_selections";

    /** UUID → 语音包 ID */
    private final Map<String, String> selections = new HashMap<>();

    public VoxSelectionManager() {}

    public VoxSelectionManager(CompoundTag tag) {
        CompoundTag sel = tag.getCompound("selections");
        for (String key : sel.getAllKeys()) {
            selections.put(key, sel.getString(key));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag sel = new CompoundTag();
        for (Map.Entry<String, String> entry : selections.entrySet()) {
            sel.putString(entry.getKey(), entry.getValue());
        }
        tag.put("selections", sel);
        return tag;
    }

    /** 获取玩家选择的语音包 ID，未选择时返回 null */
    @Nullable
    public String getSelection(UUID playerUUID) {
        return selections.get(playerUUID.toString());
    }

    /** 设置玩家选择的语音包 ID */
    public void setSelection(UUID playerUUID, String packId) {
        selections.put(playerUUID.toString(), packId);
        setDirty();
        PlayerVoxMod.LOGGER.info("PlayerVOX: 玩家 {} 选择语音包 {}", playerUUID, packId);
    }

    /** 清除玩家的语音包选择 */
    public void clearSelection(UUID playerUUID) {
        selections.remove(playerUUID.toString());
        setDirty();
    }

    /** 从 ServerLevel 的 overworld 获取实例 */
    public static VoxSelectionManager get(ServerLevel level) {
        // 使用 overworld 的 DataStorage，确保跨维度共享
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                VoxSelectionManager::new,
                VoxSelectionManager::new,
                DATA_NAME
        );
    }
}
