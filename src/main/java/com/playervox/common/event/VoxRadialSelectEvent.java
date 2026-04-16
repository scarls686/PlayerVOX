package com.playervox.common.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 玩家通过语音轮盘选择扇区后触发的事件。
 * 发布在 Forge EVENT_BUS 上，第三方模组可监听此事件响应玩家的语音指令。
 *
 * 仅在服务端触发，在冷却检查通过、音效广播之后发布。
 */
public class VoxRadialSelectEvent extends Event {

    private final ServerPlayer player;
    private final String slotId;   // 扇区完整ID，如 "takina:regroup"
    private final String packId;   // 语音包ID，如 "takina"

    public VoxRadialSelectEvent(ServerPlayer player, String slotId, String packId) {
        this.player = player;
        this.slotId = slotId;
        this.packId = packId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getSlotId() { return slotId; }
    public String getPackId() { return packId; }
}
