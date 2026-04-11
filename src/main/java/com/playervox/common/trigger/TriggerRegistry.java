package com.playervox.common.trigger;

import java.util.*;

/**
 * 所有已从数据包加载的 TriggerDefinition 的静态单例集合。
 * 按 namespace（语音包 ID）分组存储，数据包重载时由 VoxDatapackLoader 调用 reload() 重建。
 */
public class TriggerRegistry {

    /** namespace → TriggerDefinition 列表 */
    private static Map<String, List<TriggerDefinition>> definitionsByNamespace = Collections.emptyMap();

    public static void reload(Map<String, List<TriggerDefinition>> newDefinitions) {
        Map<String, List<TriggerDefinition>> copy = new HashMap<>();
        for (Map.Entry<String, List<TriggerDefinition>> entry : newDefinitions.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        definitionsByNamespace = Collections.unmodifiableMap(copy);
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
}
