package com.playervox.common.radial;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playervox.PlayerVoxMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

/**
 * 读取所有数据包中 vox/radial/ 下的 JSON，解析为 RadialSlotDefinition 并载入 RadialRegistry。
 * 同一页+同一slot冲突时，先加载的保留，后加载的跳过并警告。
 */
public class RadialDatapackLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    /** 冲突警告信息暂存，登录时发送给玩家 */
    private static final List<String> pendingWarnings = new ArrayList<>();

    public RadialDatapackLoader() {
        super(GSON, "vox/radial");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<RadialSlotDefinition>> byNamespace = new HashMap<>();
        // 冲突检测: namespace → (page:slot → 先加载的文件ID)
        Map<String, Map<String, ResourceLocation>> occupiedSlots = new HashMap<>();

        pendingWarnings.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            String namespace = id.getNamespace();
            try {
                RadialSlotDefinition def = parse(id, entry.getValue().getAsJsonObject());

                // 冲突检测
                String slotKey = def.page + ":" + def.slot;
                Map<String, ResourceLocation> nsOccupied = occupiedSlots.computeIfAbsent(namespace, k -> new HashMap<>());

                if (nsOccupied.containsKey(slotKey)) {
                    String warning = String.format(
                            "[PlayerVOX] 轮盘扇区冲突: %s 的 page %d slot %d 已被 %s 占用，跳过 %s",
                            namespace, def.page, def.slot, nsOccupied.get(slotKey), id);
                    PlayerVoxMod.LOGGER.warn(warning);
                    pendingWarnings.add(warning);
                    continue; // 跳过后加载的
                }

                nsOccupied.put(slotKey, id);
                byNamespace.computeIfAbsent(namespace, k -> new ArrayList<>()).add(def);
            } catch (Exception e) {
                PlayerVoxMod.LOGGER.error("PlayerVOX: 解析轮盘扇区 {} 失败: {}", id, e.getMessage());
            }
        }

        RadialRegistry.reload(byNamespace);

        int total = byNamespace.values().stream().mapToInt(List::size).sum();
        PlayerVoxMod.LOGGER.info("PlayerVOX: 已加载 {} 个轮盘扇区定义，来自 {} 个语音包",
                total, byNamespace.size());
    }

    /** 获取并清空待发送的冲突警告 */
    public static List<String> drainWarnings() {
        List<String> copy = new ArrayList<>(pendingWarnings);
        pendingWarnings.clear();
        return copy;
    }

    private static RadialSlotDefinition parse(ResourceLocation id, JsonObject obj) {
        String label = obj.get("label").getAsString();

        ResourceLocation icon = null;
        if (obj.has("icon") && !obj.get("icon").getAsString().isEmpty()) {
            icon = new ResourceLocation(obj.get("icon").getAsString());
        }

        // page: [页码, 本页slot总数]
        JsonArray pageArr = obj.getAsJsonArray("page");
        int page = pageArr.get(0).getAsInt();
        int pageTotalSlots = pageArr.get(1).getAsInt();

        int slot = obj.get("slot").getAsInt();
        int cooldown = obj.has("cooldown") ? obj.get("cooldown").getAsInt() : 0;

        List<RadialEntry> entries = new ArrayList<>();
        JsonArray arr = obj.getAsJsonArray("entries");
        for (JsonElement el : arr) {
            JsonObject e = el.getAsJsonObject();
            ResourceLocation sound = new ResourceLocation(e.get("sound").getAsString());
            int weight = e.has("weight") ? e.get("weight").getAsInt() : 1;
            String subtitle = e.has("text") && !e.get("text").getAsString().isEmpty()
                    ? e.get("text").getAsString() : null;
            entries.add(new RadialEntry(sound, weight, subtitle));
        }

        return new RadialSlotDefinition(id, label, icon, page, pageTotalSlots, slot, cooldown, entries);
    }
}
