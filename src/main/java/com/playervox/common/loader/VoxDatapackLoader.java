package com.playervox.common.loader;

import com.playervox.PlayerVoxMod;
import com.playervox.common.trigger.TriggerDefinition;
import com.playervox.common.trigger.TriggerEntry;
import com.playervox.common.trigger.TriggerRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取所有数据包中 vox/triggers/ 下的 JSON，解析为 TriggerDefinition 并按 namespace 分组载入 TriggerRegistry。
 * ResourceLocation 的 namespace 即为语音包 ID（与 pack_meta.json 的 id 对应）。
 */
public class VoxDatapackLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public VoxDatapackLoader() {
        super(GSON, "vox/triggers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<TriggerDefinition>> byNamespace = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            String namespace = id.getNamespace();
            try {
                TriggerDefinition def = parse(entry.getValue().getAsJsonObject());
                byNamespace.computeIfAbsent(namespace, k -> new ArrayList<>()).add(def);
            } catch (Exception e) {
                PlayerVoxMod.LOGGER.error("PlayerVOX: 解析触发器 {} 失败: {}", id, e.getMessage());
            }
        }

        TriggerRegistry.reload(byNamespace);

        int total = byNamespace.values().stream().mapToInt(List::size).sum();
        PlayerVoxMod.LOGGER.info("PlayerVOX: 已加载 {} 条触发器定义，来自 {} 个语音包",
                total, byNamespace.size());
    }

    private static TriggerDefinition parse(JsonObject obj) {
        String trigger = obj.get("trigger").getAsString();
        int cooldown = obj.has("cooldown") ? obj.get("cooldown").getAsInt() : 0;

        List<TriggerEntry> entries = new ArrayList<>();
        JsonArray arr = obj.getAsJsonArray("entries");
        for (JsonElement el : arr) {
            JsonObject e = el.getAsJsonObject();
            ResourceLocation sound = new ResourceLocation(e.get("sound").getAsString());
            int weight = e.has("weight") ? e.get("weight").getAsInt() : 1;
            JsonObject conditions = e.has("conditions") ? e.getAsJsonObject("conditions") : null;
            boolean once = e.has("once") && e.get("once").getAsBoolean();
            String subtitle = e.has("text") && !e.get("text").getAsString().isEmpty()
                    ? e.get("text").getAsString() : null;
            entries.add(new TriggerEntry(sound, weight, conditions, once, subtitle));
        }

        return new TriggerDefinition(trigger, cooldown, entries);
    }
}
