package com.playervox.common.network;

import com.playervox.client.VoxSoundInstance;
import com.playervox.common.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：播放绑定在实体上的语音。
 * 声音会跟随实体移动。客户端收到新语音时自动打断旧语音。
 */
public class PacketPlaySound {

    private final ResourceLocation sound;
    private final int entityId;
    private final float volume;
    private final float pitch;

    /** 客户端按实体 ID 跟踪正在播放的 VOX 语音，每个实体独立打断 */
    @OnlyIn(Dist.CLIENT)
    private static final Map<Integer, SoundInstance> activeVoxSounds = new ConcurrentHashMap<>();

    public PacketPlaySound(ResourceLocation sound, int entityId, float volume, float pitch) {
        this.sound = sound;
        this.entityId = entityId;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(PacketPlaySound pkt, FriendlyByteBuf buf) {
        buf.writeResourceLocation(pkt.sound);
        buf.writeInt(pkt.entityId);
        buf.writeFloat(pkt.volume);
        buf.writeFloat(pkt.pitch);
    }

    public static PacketPlaySound decode(FriendlyByteBuf buf) {
        return new PacketPlaySound(
                buf.readResourceLocation(),
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public static void handle(PacketPlaySound pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> playOnClient(pkt))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void playOnClient(PacketPlaySound pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(pkt.entityId);
        if (entity == null) return;

        // 打断该实体正在播放的旧 VOX 语音（不影响其他实体的语音）
        SoundInstance old = activeVoxSounds.get(pkt.entityId);
        if (old != null) {
            mc.getSoundManager().stop(old);
        }

        // 清理已停止播放的条目，防止 Map 无限增长
        cleanupStopped(mc);

        // 使用已注册的占位 SoundEvent，实际音频在 PlaySoundSourceEvent 中由缓存 buffer 覆盖
        VoxSoundInstance instance = new VoxSoundInstance(
                ModSounds.VOX.get(), SoundSource.PLAYERS,
                pkt.volume, pkt.pitch, entity, pkt.sound
        );

        activeVoxSounds.put(pkt.entityId, instance);
        mc.getSoundManager().play(instance);

        com.playervox.PlayerVoxMod.LOGGER.info("PlayerVOX: 客户端播放 {} (绑定实体 {})",
                pkt.sound, entity.getName().getString());
    }

    /** 清理已停止播放的条目，防止 Map 无限增长 */
    @OnlyIn(Dist.CLIENT)
    private static void cleanupStopped(Minecraft mc) {
        Iterator<Map.Entry<Integer, SoundInstance>> it = activeVoxSounds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, SoundInstance> entry = it.next();
            if (!mc.getSoundManager().isActive(entry.getValue())) {
                it.remove();
            }
        }
    }
}
