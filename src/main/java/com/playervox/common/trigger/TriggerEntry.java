package com.playervox.common.trigger;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * 单条触发规则：一个音效 + 权重 + 附加条件。
 * 对应 JSON entries 数组中的一个元素。
 */
public class TriggerEntry {

    public final ResourceLocation sound;
    public final int weight;
    public final JsonObject conditions;
    public final boolean once;
    public final String subtitle; // nullable

    public TriggerEntry(ResourceLocation sound, int weight, JsonObject conditions, boolean once, String subtitle) {
        this.sound = sound;
        this.weight = weight;
        this.conditions = conditions;
        this.once = once;
        this.subtitle = subtitle;
    }
}
