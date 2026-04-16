package com.playervox.common.handler;

import com.playervox.PlayerVoxMod;
import com.playervox.common.event.VoxRadialSelectEvent;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketPlaySound;
import com.playervox.common.network.PacketRadialAck;
import com.playervox.common.radial.RadialEntry;
import com.playervox.common.radial.RadialRegistry;
import com.playervox.common.radial.RadialSlotDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Random;

/**
 * 服务端轮盘处理器：收到客户端 PacketRadialSelect 后执行冷却检查、随机选 entry、广播音效。
 */
public class RadialHandler {

    private static final Random RANDOM = new Random();

    /**
     * 处理客户端轮盘选择请求。
     */
    public static void handleRadialSelect(ServerPlayer player, String slotId) {
        // 获取玩家选择的语音包
        String packId = VoxSelectionManager.get(player.serverLevel()).getSelection(player.getUUID());
        if (packId == null || packId.isEmpty()) return;

        // 查找扇区定义
        RadialSlotDefinition slot = RadialRegistry.getSlotById(packId, slotId);
        if (slot == null) {
            PlayerVoxMod.LOGGER.warn("PlayerVOX: 未找到轮盘扇区 {} (语音包 {})", slotId, packId);
            return;
        }

        long currentTick = player.level().getGameTime();
        String cooldownKey = "radial:" + slotId;

        // 冷却检查
        if (CooldownTracker.isOnCooldown(player.getUUID(), cooldownKey, currentTick)) {
            // 冷却中：发送空 ACK 给操作者，客户端静默
            NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketRadialAck(null, player.getId(), null, player.getName().getString())
            );
            return;
        }

        // 随机选 entry
        List<RadialEntry> entries = slot.entries;
        if (entries.isEmpty()) return;

        int totalWeight = 0;
        for (RadialEntry e : entries) totalWeight += e.weight;
        int roll = RANDOM.nextInt(totalWeight);
        int accumulated = 0;
        RadialEntry selected = entries.get(0);
        for (RadialEntry e : entries) {
            accumulated += e.weight;
            if (roll < accumulated) {
                selected = e;
                break;
            }
        }

        // 设置冷却（至少 3 tick 防抖）
        int cooldown = Math.max(3, slot.cooldownTicks);
        CooldownTracker.setCooldown(player.getUUID(), cooldownKey, currentTick, cooldown);

        ResourceLocation sound = selected.sound;
        String subtitle = selected.subtitle;
        float volume = 1.0f;

        PlayerVoxMod.LOGGER.info("PlayerVOX: 轮盘语音 sound={} player={} slot={} pack={}",
                sound, player.getName().getString(), slotId, packId);

        // 发送 ACK 给操作者本人（客户端收到后播放）
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketRadialAck(sound, player.getId(), subtitle, player.getName().getString())
        );

        // 广播 PacketPlaySound 给周围其他玩家
        PacketPlaySound broadcastPacket = new PacketPlaySound(
                sound, player.getId(), volume, 1.0f, subtitle, player.getName().getString()
        );
        // 使用 NEAR 广播，排除操作者自己（操作者已通过 ACK 播放）
        for (ServerPlayer nearby : player.serverLevel().players()) {
            if (nearby == player) continue;
            double dist = nearby.distanceToSqr(player);
            if (dist <= (volume * 16.0) * (volume * 16.0)) {
                NetworkHandler.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> nearby),
                        broadcastPacket
                );
            }
        }

        // 发布 Forge 事件，供第三方模组监听
        MinecraftForge.EVENT_BUS.post(new VoxRadialSelectEvent(player, slotId, packId));
    }
}
