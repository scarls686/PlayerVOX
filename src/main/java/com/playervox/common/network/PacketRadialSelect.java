package com.playervox.common.network;

import com.playervox.common.handler.RadialHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：玩家在轮盘中选择了一个扇区。
 * 服务端收到后走 RadialHandler 处理冷却、随机选音效、广播。
 */
public class PacketRadialSelect {

    private final String slotId; // 扇区完整ID，如 "takina:regroup"

    public PacketRadialSelect(String slotId) {
        this.slotId = slotId;
    }

    public static void encode(PacketRadialSelect pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.slotId);
    }

    public static PacketRadialSelect decode(FriendlyByteBuf buf) {
        return new PacketRadialSelect(buf.readUtf());
    }

    public static void handle(PacketRadialSelect pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                RadialHandler.handleRadialSelect(player, pkt.slotId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
