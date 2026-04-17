package com.playervox.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * 位置固定的 VOX SoundInstance，用于实体不存在时的 fallback 播放（如死亡后实体已移除）。
 * 不绑定实体，坐标在创建时固定，声音播放到自然结束。
 */
@OnlyIn(Dist.CLIENT)
public class VoxPositionedSoundInstance extends AbstractSoundInstance {

    private final ResourceLocation registryName;

    public VoxPositionedSoundInstance(SoundEvent soundEvent, SoundSource source,
                                      float volume, float pitch,
                                      double x, double y, double z,
                                      ResourceLocation registryName) {
        super(soundEvent, source, SoundInstance.createUnseededRandom());
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.registryName = registryName;
    }

    @Nullable
    public SoundBuffer getSoundBuffer() {
        VoxSoundAssetsManager.SoundData data = VoxSoundAssetsManager.INSTANCE.getSoundData(registryName);
        if (data == null) return null;
        return new SoundBuffer(data.byteBuffer(), data.audioFormat());
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }
}
