package com.playervox.common.trigger;

import com.google.gson.JsonObject;
import com.playervox.common.handler.OnceTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 条件匹配 + 权重随机选 entry。
 *
 * 使用方：传入 trigger 大类和上下文参数，返回应播放的音效和冷却时长，
 * 若无匹配则返回 null。
 */
public class TriggerEvaluator {

    private static final Random RANDOM = new Random();

    /** evaluate 的返回结果：音效 + 来自 JSON 的冷却时长 + once 信息 + 字幕 */
    public record Result(ResourceLocation sound, int cooldownTicks, boolean once, String conditionsKey, String subtitle) {}

    /**
     * 通用入口。
     *
     * @param namespace     语音包 ID（namespace）
     * @param trigger       触发大类，如 "hurt"
     * @param playerUUID    玩家 UUID，用于 once 状态查询
     * @param source        伤害来源，可为 null
     * @param damage        伤害量，hurt/death 时使用；low_health 时传血量百分比(0.0~1.0)
     * @param healthPercent 当前血量百分比(0.0~1.0)，hurt trigger 时传受伤后血量，其余传 null
     * @param target        击杀目标实体，kill 时使用
     * @param extraString   额外字符串参数（advancement ID、gun_id 等）
     */
    @Nullable
    public static Result evaluate(
            String namespace,
            String trigger,
            UUID playerUUID,
            @Nullable DamageSource source,
            float damage,
            @Nullable Float healthPercent,
            @Nullable Entity target,
            @Nullable String extraString
    ) {
        List<TriggerDefinition> defs = TriggerRegistry.getByTrigger(namespace, trigger);
        if (defs.isEmpty()) return null;

        for (TriggerDefinition def : defs) {
            // 先将 entries 按条件分组
            List<List<TriggerEntry>> groups = new ArrayList<>();
            List<JsonObject> groupConditionsList = new ArrayList<>();

            List<TriggerEntry> currentGroup = new ArrayList<>();
            JsonObject currentConditions = null;

            for (TriggerEntry entry : def.entries) {
                if (currentGroup.isEmpty()) {
                    currentConditions = entry.conditions;
                    currentGroup.add(entry);
                } else if (conditionsEqual(entry.conditions, currentConditions)) {
                    currentGroup.add(entry);
                } else {
                    groups.add(currentGroup);
                    groupConditionsList.add(currentConditions);
                    currentGroup = new ArrayList<>();
                    currentConditions = entry.conditions;
                    currentGroup.add(entry);
                }
            }
            if (!currentGroup.isEmpty()) {
                groups.add(currentGroup);
                groupConditionsList.add(currentConditions);
            }

            // 逐组检查：条件匹配 → once 状态 → 权重随机
            for (int i = 0; i < groups.size(); i++) {
                List<TriggerEntry> group = groups.get(i);
                JsonObject groupCond = groupConditionsList.get(i);

                if (!matchesConditions(groupCond, trigger, source, damage, healthPercent, target, extraString)) {
                    continue;
                }

                boolean groupOnce = group.stream().anyMatch(e -> e.once);
                String conditionsKey = groupCond != null ? groupCond.toString() : "";

                // once 组已触发过 → 跳过，继续匹配下一组
                if (groupOnce && OnceTracker.isTriggered(playerUUID, trigger, conditionsKey)) {
                    continue;
                }

                int totalWeight = 0;
                for (TriggerEntry e : group) totalWeight += e.weight;
                int roll = RANDOM.nextInt(totalWeight);
                int accumulated = 0;
                for (TriggerEntry e : group) {
                    accumulated += e.weight;
                    if (roll < accumulated) return new Result(e.sound, def.cooldownTicks, groupOnce, conditionsKey, e.subtitle);
                }
            }
        }

        return null;
    }

    /**
     * 返回指定 trigger 下所有带 once 标记的条件组 key 及其条件。
     * 结果来自 TriggerRegistry 在 reload() 时预计算的缓存，无遍历开销。
     */
    public static List<Map.Entry<String, JsonObject>> getOnceGroups(String namespace, String trigger) {
        return TriggerRegistry.getCachedOnceGroups(namespace, trigger);
    }

    /** 暴露条件匹配方法，供外部重置检查使用 */
    public static boolean checkConditions(
            JsonObject cond,
            String trigger,
            @Nullable DamageSource source,
            float damage,
            @Nullable Float healthPercent,
            @Nullable Entity target,
            @Nullable String extraString
    ) {
        return matchesConditions(cond, trigger, source, damage, healthPercent, target, extraString);
    }

    private static boolean matchesConditions(
            JsonObject cond,
            String trigger,
            @Nullable DamageSource source,
            float damage,
            @Nullable Float healthPercent,
            @Nullable Entity target,
            @Nullable String extraString
    ) {
        if (cond == null) return true;

        // damage_type：透传原版 DamageType Tag ID
        if (cond.has("damage_type") && source != null) {
            String tagId = cond.get("damage_type").getAsString();
            ResourceLocation tagLoc = new ResourceLocation(tagId);
            net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag =
                    net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                            tagLoc
                    );
            if (!source.is(tag)) return false;
        }

        // min_damage / max_damage（hurt/death 使用）
        if (cond.has("min_damage") && damage < cond.get("min_damage").getAsFloat()) return false;
        if (cond.has("max_damage") && damage > cond.get("max_damage").getAsFloat()) return false;

        // min_health_percent / max_health_percent
        // low_health trigger：damage 参数位传入血量百分比（healthPercent 为 null）
        // hurt trigger：healthPercent 为受伤后血量百分比，与 damage 独立
        if (cond.has("min_health_percent") || cond.has("max_health_percent")) {
            float hp = healthPercent != null ? healthPercent : damage;
            if (cond.has("min_health_percent") && hp < cond.get("min_health_percent").getAsFloat()) return false;
            if (cond.has("max_health_percent") && hp > cond.get("max_health_percent").getAsFloat()) return false;
        }

        // target_type（kill 类）
        if (cond.has("target_type") && target != null) {
            String targetType = cond.get("target_type").getAsString();
            ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getKey(target.getType());
            if (!targetType.equals(entityId != null ? entityId.toString() : "")) return false;
        }

        // target_is_player
        if (cond.has("target_is_player")) {
            boolean mustBePlayer = cond.get("target_is_player").getAsBoolean();
            boolean isPlayer = target instanceof net.minecraft.world.entity.player.Player;
            if (mustBePlayer != isPlayer) return false;
        }

        // kill_method
        if (cond.has("kill_method") && source != null) {
            String method = cond.get("kill_method").getAsString();
            if ("melee".equals(method) && source.isIndirect()) return false;
            if ("projectile".equals(method) && !source.isIndirect()) return false;
            // "any" 不过滤
        }

        // advancement / gun_id：与 extraString 比对
        if (cond.has("advancement")) {
            if (!cond.get("advancement").getAsString().equals(extraString)) return false;
        }
        if (cond.has("gun_id")) {
            if (!cond.get("gun_id").getAsString().equals(extraString)) return false;
        }

        // item（pickup trigger）：精确物品ID或 # 前缀的 Tag
        if (cond.has("item")) {
            String itemCond = cond.get("item").getAsString();
            if (extraString == null) return false;
            if (itemCond.startsWith("#")) {
                // Tag 匹配
                String tagId = itemCond.substring(1);
                net.minecraft.resources.ResourceLocation tagLoc = new net.minecraft.resources.ResourceLocation(tagId);
                net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag =
                        net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM,
                                tagLoc
                        );
                net.minecraft.resources.ResourceLocation itemLoc = new net.minecraft.resources.ResourceLocation(extraString);
                var holderOpt = net.minecraftforge.registries.ForgeRegistries.ITEMS.getHolder(itemLoc);
                if (holderOpt.isEmpty() || !holderOpt.get().is(tag)) return false;
            } else {
                // 精确 ID 匹配
                if (!itemCond.equals(extraString)) return false;
            }
        }

        // min_fall_distance / max_fall_distance（fall trigger：damage 参数位传入 fallDistance）
        if (cond.has("min_fall_distance") && damage < cond.get("min_fall_distance").getAsFloat()) return false;
        if (cond.has("max_fall_distance") && damage > cond.get("max_fall_distance").getAsFloat()) return false;

        return true;
    }

    /** 两个 conditions 对象是否相同（用 JSON 字符串比较） */
    private static boolean conditionsEqual(JsonObject a, JsonObject b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }
}
