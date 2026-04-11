package com.playervox.common.trigger;

import java.util.List;

/**
 * 一个 JSON 文件解析的结果：trigger 大类 + 冷却 + 所有 entries。
 */
public class TriggerDefinition {

    public final String trigger;
    public final int cooldownTicks;
    public final List<TriggerEntry> entries;

    public TriggerDefinition(String trigger, int cooldownTicks, List<TriggerEntry> entries) {
        this.trigger = trigger;
        this.cooldownTicks = cooldownTicks;
        this.entries = entries;
    }
}
