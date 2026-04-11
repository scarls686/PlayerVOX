package com.playervox.common.init;

import com.playervox.PlayerVoxMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 注册一个占位 SoundEvent，所有 VOX 语音都通过它播放。
 * 实际音频数据在 PlaySoundSourceEvent 中由 VoxSoundInstance 的缓存 buffer 覆盖。
 */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PlayerVoxMod.MOD_ID);

    public static final RegistryObject<SoundEvent> VOX =
            SOUNDS.register("vox", () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(PlayerVoxMod.MOD_ID, "vox")));
}
