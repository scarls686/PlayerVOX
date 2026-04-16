package com.playervox.common.trigger;

import com.google.gson.JsonObject;

import java.util.*;

/**
 * 所有已从数据包加载的 TriggerDefinition 的静态单例集合。
 * 按 namespace（语音包 ID）分组存储，数据包重载时由 VoxDatapackLoader 调用 reload() 重建。
 *
 * reload() 时同步预计算 once 组缓存，避免每 tick 重复分组遍历。
 */
public class TriggerRegistry {

    /** namespace → TriggerDefinition 列表 */
    private static Map<String, List<TriggerDefinition>> definitionsByNamespace = Collections.emptyMap();

    /**
     * 预计算的 once 组缓存。
     * key: "namespace\0trigger"  value: once 条件组列表（conditionsKey → conditions）
     */
    private static Map<String, List<Map.Entry<String, JsonObject>>> onceGroupCache = Collections.emptyMap();

    public static void reload(Map<String, List<TriggerDefinition>> newDefinitions) {
        Map<String, List<TriggerDefinition>> copy = new HashMap<>();
        for (Map.Entry<String, List<TriggerDefinition>> entry : newDefinitions.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        definitionsByNamespace = Collections.unmodifiableMap(copy);
        onceGroupCache = buildOnceGroupCache(definitionsByNamespace);
    }

    /** 返回指定 namespace + trigger 大类的所有定义 */
    public static List<TriggerDefinition> getByTrigger(String namespace, String trigger) {
        List<TriggerDefinition> defs = definitionsByNamespace.getOrDefault(namespace, Collections.emptyList());
        List<TriggerDefinition> result = new ArrayList<>();
        for (TriggerDefinition def : defs) {
            if (def.trigger.equals(trigger)) {
                result.add(def);
            }
        }
        return result;
    }

    /** 获取所有已注册的 namespace 列表 */
    public static Set<String> getNamespaces() {
        return definitionsByNamespace.keySet();
    }

    /**
     * 返回预计算好的 once 组列表，供每 tick 检查使用。
     * 结果在 reload() 时已计算完毕，此处直接取缓存，无遍历开销。
     */
    public static List<Map.Entry<String, JsonObject>> getCachedOnceGroups(String namespace, String trigger) {
        String key = namespace + '\0' + trigger;
        List<Map.Entry<String, JsonObject>> result = onceGroupCache.get(key);
        return result != null ? result : Collections.emptyList();
    }

    // -------------------------------------------------------------------------

    private static Map<String, List<Map.Entry<String, JsonObject>>> buildOnceGroupCache(
            Map<String, List<TriggerDefinition>> defs) {

        Map<String, List<Map.Entry<String, JsonObject>>> cache = new HashMap<>();

        for (Map.Entry<String, List<TriggerDefinition>> nsEntry : defs.entrySet()) {
            String namespace = nsEntry.getKey();
            // 收集该 namespace 下所有出现的 trigger 名
            Set<String> triggers = new HashSet<>();
            for (TriggerDefinition def : nsEntry.getValue()) {
                triggers.add(def.trigger);
            }
            for (String trigger : triggers) {
                List<Map.Entry<String, JsonObject>> groups = computeOnceGroups(nsEntry.getValue(), trigger);
                if (!groups.isEmpty()) {
                    cache.put(namespace + '\0' + trigger, Collections.unmodifiableList(groups));
                }
            }
        }

        return Collections.unmodifiableMap(cache);
    }

    /** 从 TriggerDefinition 列表中提取指定 trigger 的所有 once 条件组 */
    private static List<Map.Entry<String, JsonObject>> computeOnceGroups(
            List<TriggerDefinition> allDefs, String trigger) {

        List<Map.Entry<String, JsonObject>> result = new ArrayList<>();

        for (TriggerDefinition def : allDefs) {
            if (!def.trigger.equals(trigger)) continue;

            Set<String> seen = new HashSet<>();
            JsonObject groupConditions = null;
            boolean groupStarted = false;
            boolean groupOnce = false;

            for (TriggerEntry entry : def.entries) {
                if (!groupStarted) {
                    groupConditions = entry.conditions;
                    groupStarted = true;
                    groupOnce = entry.once;
                } else if (conditionsEqual(entry.conditions, groupConditions)) {
                    if (entry.once) groupOnce = true;
                } else {
                    if (groupOnce) {
                        String key = groupConditions != null ? groupConditions.toString() : "";
                        if (seen.add(key)) {
                            result.add(Map.entry(key, groupConditions != null ? groupConditions : new JsonObject()));
                        }
                    }
                    groupConditions = entry.conditions;
                    groupOnce = entry.once;
                }
            }
            if (groupOnce) {
                String key = groupConditions != null ? groupConditions.toString() : "";
                if (seen.add(key)) {
                    result.add(Map.entry(key, groupConditions != null ? groupConditions : new JsonObject()));
                }
            }
        }

        return result;
    }

    private static boolean conditionsEqual(JsonObject a, JsonObject b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }
}
