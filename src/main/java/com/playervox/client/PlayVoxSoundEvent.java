package com.playervox.client;

import com.playervox.PlayerVoxMod;
import com.mojang.blaze3d.audio.SoundBuffer;
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
        if (event.getSound() instanceof VoxSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
                PlayerVoxMod.LOGGER.debug("PlayerVOX: PlaySoundSourceEvent 注入音频 buffer: {}", instance.getRegistryName());
            } else {
                PlayerVoxMod.LOGGER.warn("PlayerVOX: PlaySoundSourceEvent 未找到音频缓存: {}", instance.getRegistryName());
            }
        }
    }
}
