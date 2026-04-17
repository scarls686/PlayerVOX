package com.playervox.client;

import com.playervox.PlayerVoxMod;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 监听 PlaySoundSourceEvent，当播放的是 VoxSoundInstance 时，
 * 从缓存中获取音频 buffer 并注入到音频通道，覆盖原版加载的静音占位音频。
 */
@Mod.EventBusSubscriber(modid = PlayerVoxMod.MOD_ID, value = Dist.CLIENT)
public class PlayVoxSoundEvent {

    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        SoundBuffer soundBuffer = null;
        ResourceLocation name = null;

        if (event.getSound() instanceof VoxSoundInstance instance) {
            soundBuffer = instance.getSoundBuffer();
            name = instance.getRegistryName();
        } else if (event.getSound() instanceof VoxPositionedSoundInstance instance) {
            soundBuffer = instance.getSoundBuffer();
            name = instance.getRegistryName();
        }

        if (name != null) {
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
                PlayerVoxMod.LOGGER.debug("PlayerVOX: PlaySoundSourceEvent 注入音频 buffer: {}", name);
            } else {
                PlayerVoxMod.LOGGER.warn("PlayerVOX: PlaySoundSourceEvent 未找到音频缓存: {}", name);
            }
        }
    }
}
