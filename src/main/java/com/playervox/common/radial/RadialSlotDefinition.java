package com.playervox.common.radial;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 单个轮盘扇区的定义，对应 data/<pack_id>/vox/radial/<name>.json。
 */
public class RadialSlotDefinition {

    /** 扇区的完整 ID (namespace:path)，由 ResourceLocation 推导 */
    public final ResourceLocation id;

    /** 显示在扇区上的翻译键 */
    public final String label;

    /** 图标的 ResourceLocation，可为 null */
    @Nullable
    public final ResourceLocation icon;

    /** 页码（1-based） */
    public final int page;

    /** 本页 slot 总数，用于计算扇区角度 */
    public final int pageTotalSlots;

    /** 扇区在本页内的位置（1-based） */
    public final int slot;

    /** 冷却 tick 数 */
    public final int cooldownTicks;

    /** 音效列表（权重随机） */
    public final List<RadialEntry> entries;

    public RadialSlotDefinition(ResourceLocation id, String label, @Nullable ResourceLocation icon,
                                int page, int pageTotalSlots, int slot,
                                int cooldownTicks, List<RadialEntry> entries) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.page = page;
        this.pageTotalSlots = pageTotalSlots;
        this.slot = slot;
        this.cooldownTicks = cooldownTicks;
        this.entries = entries;
    }
}
