package com.playervox.common.radial;

import java.util.*;

/**
 * 所有已加载的轮盘扇区定义的静态单例集合。
 * 按 namespace（语音包 ID）分组存储，数据包重载时由 RadialDatapackLoader 调用 reload() 重建。
 */
public class RadialRegistry {

    /** namespace → 按 (page, slot) 排序的扇区定义列表 */
    private static Map<String, List<RadialSlotDefinition>> slotsByNamespace = Collections.emptyMap();

    public static void reload(Map<String, List<RadialSlotDefinition>> newSlots) {
        Map<String, List<RadialSlotDefinition>> copy = new HashMap<>();
        for (Map.Entry<String, List<RadialSlotDefinition>> entry : newSlots.entrySet()) {
            List<RadialSlotDefinition> list = new ArrayList<>(entry.getValue());
            list.sort(Comparator.comparingInt((RadialSlotDefinition s) -> s.page)
                    .thenComparingInt(s -> s.slot));
            copy.put(entry.getKey(), Collections.unmodifiableList(list));
        }
        slotsByNamespace = Collections.unmodifiableMap(copy);
    }

    /** 获取指定语音包的所有扇区定义 */
    public static List<RadialSlotDefinition> getSlots(String namespace) {
        return slotsByNamespace.getOrDefault(namespace, Collections.emptyList());
    }

    /** 获取指定语音包某一页的扇区定义 */
    public static List<RadialSlotDefinition> getSlotsForPage(String namespace, int page) {
        List<RadialSlotDefinition> all = getSlots(namespace);
        List<RadialSlotDefinition> result = new ArrayList<>();
        for (RadialSlotDefinition slot : all) {
            if (slot.page == page) {
                result.add(slot);
            }
        }
        return result;
    }

    /** 获取指定语音包的总页数 */
    public static int getPageCount(String namespace) {
        List<RadialSlotDefinition> all = getSlots(namespace);
        int maxPage = 0;
        for (RadialSlotDefinition slot : all) {
            if (slot.page > maxPage) maxPage = slot.page;
        }
        return maxPage;
    }

    /** 按 namespace + slotId 查找单个扇区定义 */
    public static RadialSlotDefinition getSlotById(String namespace, String slotId) {
        List<RadialSlotDefinition> all = getSlots(namespace);
        for (RadialSlotDefinition slot : all) {
            if (slot.id.toString().equals(slotId)) {
                return slot;
            }
        }
        return null;
    }

    /** 获取所有已注册的 namespace 列表 */
    public static Set<String> getNamespaces() {
        return slotsByNamespace.keySet();
    }

    /** 获取全部数据（用于网络同步） */
    public static Map<String, List<RadialSlotDefinition>> getAllSlots() {
        return slotsByNamespace;
    }
}
