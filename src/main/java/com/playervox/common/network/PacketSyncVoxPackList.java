package com.playervox.common.network;

import com.playervox.client.VoxClientState;
import com.playervox.common.loader.VoxPackMeta;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步服务端可用的语音包列表。
 * 玩家登录时发送。
 */
public class PacketSyncVoxPackList {

    private final List<VoxPackMeta> packs;

    public PacketSyncVoxPackList(List<VoxPackMeta> packs) {
        this.packs = packs;
    }

    public static void encode(PacketSyncVoxPackList pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.packs.size());
        for (VoxPackMeta meta : pkt.packs) {
            buf.writeUtf(meta.getId());
            buf.writeUtf(meta.getName());
            buf.writeUtf(meta.getDescription());
            buf.writeUtf(meta.getIcon());
        }
    }

    public static PacketSyncVoxPackList decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<VoxPackMeta> packs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            packs.add(new VoxPackMeta(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
            ));
        }
        return new PacketSyncVoxPackList(packs);
    }

    public static void handle(PacketSyncVoxPackList pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VoxClientState.setAvailablePacks(pkt.packs))
        );
        ctx.get().setPacketHandled(true);
    }
}
