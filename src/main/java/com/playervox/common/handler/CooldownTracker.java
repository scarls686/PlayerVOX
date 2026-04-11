package com.playervox.common.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端冷却状态：UUID → (trigger大类 → 冷却到期的游戏刻)
 * 重登不保留冷却（玩家断线时移除）。
 */
public class CooldownTracker {

    private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    /** 检查指定玩家的 trigger 是否在冷却中 */
    public static boolean isOnCooldown(UUID playerUUID, String trigger, long currentTick) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) return false;
        Long expiry = playerCooldowns.get(trigger);
        return expiry != null && currentTick < expiry;
    }

    /** 设置冷却 */
    public static void setCooldown(UUID playerUUID, String trigger, long currentTick, int cooldownTicks) {
        cooldowns.computeIfAbsent(playerUUID, k -> new HashMap<>())
                .put(trigger, currentTick + cooldownTicks);
    }

    /** 清除指定玩家的某个 trigger 冷却 */
    public static void clearCooldown(UUID playerUUID, String trigger) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns != null) {
            playerCooldowns.remove(trigger);
        }
    }

    /** 玩家断线时清理，防止 Map 无限增长 */
    public static void remove(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }
}
