package com.playervox.common.network;

import com.playervox.client.VoxClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步玩家当前选择的语音包 ID。
 */
public class PacketSyncVoxSelection {

    private final String packId;

    public PacketSyncVoxSelection(String packId) {
        this.packId = packId;
    }

    public static void encode(PacketSyncVoxSelection pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.packId);
    }

    public static PacketSyncVoxSelection decode(FriendlyByteBuf buf) {
        return new PacketSyncVoxSelection(buf.readUtf());
    }

    public static void handle(PacketSyncVoxSelection pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VoxClientState.setSelectedPack(pkt.packId))
        );
        ctx.get().setPacketHandled(true);
    }
}
