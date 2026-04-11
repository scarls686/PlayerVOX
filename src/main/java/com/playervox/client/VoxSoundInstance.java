package com.playervox.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * 自定义 SoundInstance：绑定在实体上，从 VoxSoundAssetsManager 缓存获取音频数据。
 * 不依赖原版 sounds.json，通过 PlaySoundSourceEvent 拦截注入 buffer。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSoundInstance extends EntityBoundSoundInstance {

    private final ResourceLocation registryName;

    public VoxSoundInstance(SoundEvent soundEvent, SoundSource source,
                            float volume, float pitch, Entity entity,
                            ResourceLocation registryName) {
        super(soundEvent, source, volume, pitch, entity, 0L);
        this.registryName = registryName;
    }

    @Nullable
    public SoundBuffer getSoundBuffer() {
        VoxSoundAssetsManager.SoundData data = VoxSoundAssetsManager.INSTANCE.getSoundData(registryName);
        if (data == null) return null;
        // 立体声已在加载阶段转为单声道，直接使用
        return new SoundBuffer(data.byteBuffer(), data.audioFormat());
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }
}
