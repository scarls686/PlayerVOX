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
 *
 * 重写 tick()：实体被移除后（如玩家死亡），保持最后坐标继续播放，不中断声音。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSoundInstance extends EntityBoundSoundInstance {

    private final ResourceLocation registryName;
    private final Entity trackedEntity;
    private boolean entityLost = false;

    public VoxSoundInstance(SoundEvent soundEvent, SoundSource source,
                            float volume, float pitch, Entity entity,
                            ResourceLocation registryName) {
        super(soundEvent, source, volume, pitch, entity, 0L);
        this.registryName = registryName;
        this.trackedEntity = entity;
    }

    @Override
    public void tick() {
        if (entityLost) {
            // 实体已移除，保持最后坐标不动，不停止播放
            return;
        }
        if (trackedEntity.isRemoved()) {
            // 实体刚被移除（如死亡），标记并冻结坐标
            entityLost = true;
            return;
        }
        // 实体存活，正常跟踪位置
        this.x = trackedEntity.getX();
        this.y = trackedEntity.getY();
        this.z = trackedEntity.getZ();
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
