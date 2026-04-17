package com.playervox.common.network;

import com.playervox.client.SubtitleOverlay;
import com.playervox.client.VoxPositionedSoundInstance;
import com.playervox.common.init.ModSounds;
import com.playervox.client.VoxSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：轮盘选择的 ACK 响应。
 * 仅发给操作者本人，告知其播放音效（冷却通过时）或静默（冷却拒绝时 sound 为空）。
 */
public class PacketRadialAck {

    private final ResourceLocation sound; // null 表示冷却拒绝
    private final int entityId;
    private final String subtitle;        // nullable
    private final String playerName;
    private final double posX, posY, posZ;

    public PacketRadialAck(ResourceLocation sound, int entityId, String subtitle, String playerName,
                           double posX, double posY, double posZ) {
        this.sound = sound;
        this.entityId = entityId;
        this.subtitle = subtitle;
        this.playerName = playerName;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public static void encode(PacketRadialAck pkt, FriendlyByteBuf buf) {
        boolean hasSound = pkt.sound != null;
        buf.writeBoolean(hasSound);
        if (hasSound) {
            buf.writeResourceLocation(pkt.sound);
        }
        buf.writeInt(pkt.entityId);
        buf.writeBoolean(pkt.subtitle != null);
        if (pkt.subtitle != null) {
            buf.writeUtf(pkt.subtitle);
        }
        buf.writeUtf(pkt.playerName);
        buf.writeDouble(pkt.posX);
        buf.writeDouble(pkt.posY);
        buf.writeDouble(pkt.posZ);
    }

    public static PacketRadialAck decode(FriendlyByteBuf buf) {
        boolean hasSound = buf.readBoolean();
        ResourceLocation sound = hasSound ? buf.readResourceLocation() : null;
        int entityId = buf.readInt();
        boolean hasSub = buf.readBoolean();
        String subtitle = hasSub ? buf.readUtf() : null;
        String playerName = buf.readUtf();
        double posX = buf.readDouble();
        double posY = buf.readDouble();
        double posZ = buf.readDouble();
        return new PacketRadialAck(sound, entityId, subtitle, playerName, posX, posY, posZ);
    }

    public static void handle(PacketRadialAck pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> playOnClient(pkt))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void playOnClient(PacketRadialAck pkt) {
        if (pkt.sound == null) return; // 冷却拒绝，静默

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(pkt.entityId);

        net.minecraft.client.resources.sounds.SoundInstance instance;
        if (entity != null) {
            instance = new VoxSoundInstance(
                    ModSounds.VOX.get(), SoundSource.PLAYERS,
                    1.0f, 1.0f, entity, pkt.sound
            );
        } else {
            instance = new VoxPositionedSoundInstance(
                    ModSounds.VOX.get(), SoundSource.PLAYERS,
                    1.0f, 1.0f,
                    pkt.posX, pkt.posY, pkt.posZ, pkt.sound
            );
        }
        mc.getSoundManager().play(instance);

        // 字幕
        if (pkt.subtitle != null) {
            boolean isOwn = (pkt.entityId == (mc.player != null ? mc.player.getId() : -1));
            double distance = mc.player != null
                    ? mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(pkt.posX, pkt.posY, pkt.posZ))
                    : 0;
            SubtitleOverlay.onSubtitle(pkt.playerName, pkt.subtitle, isOwn, distance);
        }
    }
}
