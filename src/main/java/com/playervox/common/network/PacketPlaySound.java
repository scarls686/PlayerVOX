package com.playervox.common.network;

import com.playervox.client.SubtitleOverlay;
import com.playervox.client.VoxPositionedSoundInstance;
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
    private final String subtitle;   // nullable
    private final String playerName; // 发声玩家的游戏名
    private final double posX, posY, posZ; // 实体坐标，用于 entity==null 时的 fallback

    /** 客户端按实体 ID 跟踪正在播放的 VOX 语音，每个实体独立打断 */
    @OnlyIn(Dist.CLIENT)
    private static final Map<Integer, SoundInstance> activeVoxSounds = new ConcurrentHashMap<>();

    public PacketPlaySound(ResourceLocation sound, int entityId, float volume, float pitch,
                           String subtitle, String playerName,
                           double posX, double posY, double posZ) {
        this.sound = sound;
        this.entityId = entityId;
        this.volume = volume;
        this.pitch = pitch;
        this.subtitle = subtitle;
        this.playerName = playerName;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public static void encode(PacketPlaySound pkt, FriendlyByteBuf buf) {
        buf.writeResourceLocation(pkt.sound);
        buf.writeInt(pkt.entityId);
        buf.writeFloat(pkt.volume);
        buf.writeFloat(pkt.pitch);
        buf.writeBoolean(pkt.subtitle != null);
        if (pkt.subtitle != null) {
            buf.writeUtf(pkt.subtitle);
        }
        buf.writeUtf(pkt.playerName);
        buf.writeDouble(pkt.posX);
        buf.writeDouble(pkt.posY);
        buf.writeDouble(pkt.posZ);
    }

    public static PacketPlaySound decode(FriendlyByteBuf buf) {
        ResourceLocation sound = buf.readResourceLocation();
        int entityId = buf.readInt();
        float volume = buf.readFloat();
        float pitch = buf.readFloat();
        boolean hasSubtitle = buf.readBoolean();
        String subtitle = hasSubtitle ? buf.readUtf() : null;
        String playerName = buf.readUtf();
        double posX = buf.readDouble();
        double posY = buf.readDouble();
        double posZ = buf.readDouble();
        return new PacketPlaySound(sound, entityId, volume, pitch, subtitle, playerName, posX, posY, posZ);
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

        // 打断该实体正在播放的旧 VOX 语音（不影响其他实体的语音）
        SoundInstance old = activeVoxSounds.get(pkt.entityId);
        if (old != null) {
            mc.getSoundManager().stop(old);
        }

        // 清理已停止播放的条目，防止 Map 无限增长
        cleanupStopped(mc);

        Entity entity = mc.level.getEntity(pkt.entityId);

        SoundInstance instance;
        if (entity != null) {
            // 实体存在：绑定实体，跟踪位置（实体移除后自动冻结坐标）
            instance = new VoxSoundInstance(
                    ModSounds.VOX.get(), SoundSource.PLAYERS,
                    pkt.volume, pkt.pitch, entity, pkt.sound
            );
        } else {
            // 实体不存在（如死亡后已移除）：用服务端发来的坐标固定播放
            instance = new VoxPositionedSoundInstance(
                    ModSounds.VOX.get(), SoundSource.PLAYERS,
                    pkt.volume, pkt.pitch,
                    pkt.posX, pkt.posY, pkt.posZ, pkt.sound
            );
        }

        activeVoxSounds.put(pkt.entityId, instance);
        mc.getSoundManager().play(instance);

        // 字幕处理
        if (pkt.subtitle != null) {
            boolean isOwn = (pkt.entityId == (mc.player != null ? mc.player.getId() : -1));
            double distance = mc.player != null
                    ? mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(pkt.posX, pkt.posY, pkt.posZ))
                    : 0;
            SubtitleOverlay.onSubtitle(pkt.playerName, pkt.subtitle, isOwn, distance);
        }

        com.playervox.PlayerVoxMod.LOGGER.info("PlayerVOX: 客户端播放 {} (实体ID={}, 坐标=[{},{},{}])",
                pkt.sound, pkt.entityId, pkt.posX, pkt.posY, pkt.posZ);
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
