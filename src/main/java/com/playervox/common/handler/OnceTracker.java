package com.playervox.common.handler;

import java.util.*;

/**
 * 追踪 once 类型条件组的已触发状态。
 * key: (playerUUID, trigger, conditionsKey) → 已触发
 * 当条件不再满足时由外部调用 reset() 清除。
 * 玩家断线时调用 remove() 清理。
 */
public class OnceTracker {

    // playerUUID → (trigger + "#" + conditionsKey) → true
    private static final Map<UUID, Set<String>> triggered = new HashMap<>();

    /** 检查某条件组是否已被 once 触发过 */
    public static boolean isTriggered(UUID playerUUID, String trigger, String conditionsKey) {
        Set<String> set = triggered.get(playerUUID);
        if (set == null) return false;
        return set.contains(makeKey(trigger, conditionsKey));
    }

    /** 标记某条件组已触发 */
    public static void markTriggered(UUID playerUUID, String trigger, String conditionsKey) {
        triggered.computeIfAbsent(playerUUID, k -> new HashSet<>())
                .add(makeKey(trigger, conditionsKey));
    }

    /** 重置某条件组（条件不再满足时调用） */
    public static void reset(UUID playerUUID, String trigger, String conditionsKey) {
        Set<String> set = triggered.get(playerUUID);
        if (set != null) {
            set.remove(makeKey(trigger, conditionsKey));
            if (set.isEmpty()) triggered.remove(playerUUID);
        }
    }

    /** 玩家断线时清理 */
    public static void remove(UUID playerUUID) {
        triggered.remove(playerUUID);
    }

    private static String makeKey(String trigger, String conditionsKey) {
        return trigger + "#" + conditionsKey;
    }
}
