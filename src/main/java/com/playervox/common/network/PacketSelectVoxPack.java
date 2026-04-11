package com.playervox.common.network;

import com.playervox.PlayerVoxMod;
import com.playervox.common.handler.VoxSelectionManager;
import com.playervox.common.loader.VoxPackFinder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：玩家选择语音包。
 */
public class PacketSelectVoxPack {

    private final String packId;

    public PacketSelectVoxPack(String packId) {
        this.packId = packId;
    }

    public static void encode(PacketSelectVoxPack pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.packId);
    }

    public static PacketSelectVoxPack decode(FriendlyByteBuf buf) {
        return new PacketSelectVoxPack(buf.readUtf());
    }

    public static void handle(PacketSelectVoxPack pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // 验证包 ID 是否存在
            if (!pkt.packId.isEmpty() && VoxPackFinder.getPackMeta(pkt.packId) == null) {
                PlayerVoxMod.LOGGER.warn("PlayerVOX: 玩家 {} 选择了不存在的语音包 {}",
                        player.getName().getString(), pkt.packId);
                return;
            }

            VoxSelectionManager manager = VoxSelectionManager.get(player.serverLevel());
            if (pkt.packId.isEmpty()) {
                manager.clearSelection(player.getUUID());
            } else {
                manager.setSelection(player.getUUID(), pkt.packId);
            }

            // 回复确认（同步回客户端）
            NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncVoxSelection(pkt.packId)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
