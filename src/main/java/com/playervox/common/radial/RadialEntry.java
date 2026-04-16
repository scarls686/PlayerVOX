package com.playervox.common.radial;

import net.minecraft.resources.ResourceLocation;

/**
 * 轮盘扇区中的单条音效 + 权重。
 */
public class RadialEntry {

    public final ResourceLocation sound;
    public final int weight;
    public final String subtitle; // nullable

    public RadialEntry(ResourceLocation sound, int weight, String subtitle) {
        this.sound = sound;
        this.weight = weight;
        this.subtitle = subtitle;
    }
}
