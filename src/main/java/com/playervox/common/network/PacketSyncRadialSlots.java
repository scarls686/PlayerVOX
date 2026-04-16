package com.playervox.common.network;

import com.playervox.common.radial.RadialEntry;
import com.playervox.common.radial.RadialRegistry;
import com.playervox.common.radial.RadialSlotDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步所有轮盘扇区定义。
 * 玩家登录时发送，客户端收到后填充 RadialRegistry（客户端侧）。
 */
public class PacketSyncRadialSlots {

    /** namespace → 扇区定义列表 */
    private final Map<String, List<RadialSlotDefinition>> slotsByNamespace;

    public PacketSyncRadialSlots(Map<String, List<RadialSlotDefinition>> slotsByNamespace) {
        this.slotsByNamespace = slotsByNamespace;
    }

    public static void encode(PacketSyncRadialSlots pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.slotsByNamespace.size());
        for (Map.Entry<String, List<RadialSlotDefinition>> nsEntry : pkt.slotsByNamespace.entrySet()) {
            buf.writeUtf(nsEntry.getKey());
            List<RadialSlotDefinition> slots = nsEntry.getValue();
            buf.writeInt(slots.size());
            for (RadialSlotDefinition slot : slots) {
                buf.writeResourceLocation(slot.id);
                buf.writeUtf(slot.label);
                buf.writeBoolean(slot.icon != null);
                if (slot.icon != null) buf.writeResourceLocation(slot.icon);
                buf.writeInt(slot.page);
                buf.writeInt(slot.pageTotalSlots);
                buf.writeInt(slot.slot);
                buf.writeInt(slot.cooldownTicks);
                buf.writeInt(slot.entries.size());
                for (RadialEntry entry : slot.entries) {
                    buf.writeResourceLocation(entry.sound);
                    buf.writeInt(entry.weight);
                    buf.writeBoolean(entry.subtitle != null);
                    if (entry.subtitle != null) buf.writeUtf(entry.subtitle);
                }
            }
        }
    }

    public static PacketSyncRadialSlots decode(FriendlyByteBuf buf) {
        int nsCount = buf.readInt();
        Map<String, List<RadialSlotDefinition>> map = new HashMap<>();
        for (int n = 0; n < nsCount; n++) {
            String namespace = buf.readUtf();
            int slotCount = buf.readInt();
            List<RadialSlotDefinition> slots = new ArrayList<>(slotCount);
            for (int s = 0; s < slotCount; s++) {
                ResourceLocation id = buf.readResourceLocation();
                String label = buf.readUtf();
                ResourceLocation icon = buf.readBoolean() ? buf.readResourceLocation() : null;
                int page = buf.readInt();
                int pageTotalSlots = buf.readInt();
                int slotPos = buf.readInt();
                int cooldown = buf.readInt();
                int entryCount = buf.readInt();
                List<RadialEntry> entries = new ArrayList<>(entryCount);
                for (int e = 0; e < entryCount; e++) {
                    ResourceLocation sound = buf.readResourceLocation();
                    int weight = buf.readInt();
                    String subtitle = buf.readBoolean() ? buf.readUtf() : null;
                    entries.add(new RadialEntry(sound, weight, subtitle));
                }
                slots.add(new RadialSlotDefinition(id, label, icon, page, pageTotalSlots, slotPos, cooldown, entries));
            }
            map.put(namespace, slots);
        }
        return new PacketSyncRadialSlots(map);
    }

    public static void handle(PacketSyncRadialSlots pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    RadialRegistry.reload(pkt.slotsByNamespace);
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
