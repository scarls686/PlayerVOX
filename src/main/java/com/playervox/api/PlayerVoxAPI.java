package com.playervox.api;

import com.playervox.common.handler.VanillaTriggerHandler;
import com.playervox.common.handler.VoxSelectionManager;
import com.playervox.common.loader.VoxPackFinder;
import com.playervox.common.loader.VoxPackMeta;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * PlayerVOX 公开 API。
 * 第三方模组（包括 KubeJS）可通过此类与 PlayerVOX 交互。
 *
 * <p>所有方法均为服务端调用，不可在客户端线程使用。</p>
 *
 * <p>监听轮盘选择事件请使用 Forge Event Bus：</p>
 * <pre>{@code
 * @SubscribeEvent
 * public void onRadialSelect(VoxRadialSelectEvent e) { ... }
 * }</pre>
 *
 * @see com.playervox.common.event.VoxRadialSelectEvent
 */
public class PlayerVoxAPI {

    private PlayerVoxAPI() {}

    /**
     * 获取玩家当前选择的语音包 ID。
     *
     * @param player 目标玩家（服务端）
     * @return 语音包 ID（如 {@code "takina"}），未选择时返回 {@code null}
     */
    @Nullable
    public static String getSelectedPackId(ServerPlayer player) {
        String selection = VoxSelectionManager.get(player.serverLevel()).getSelection(player.getUUID());
        return (selection == null || selection.isEmpty()) ? null : selection;
    }

    /**
     * 获取当前服务器上所有已加载的语音包元信息。
     *
     * @return 不可修改的语音包元信息集合
     */
    public static Collection<VoxPackMeta> getAvailablePacks() {
        return VoxPackFinder.getAllPackMetas();
    }

    /**
     * 为玩家手动触发一个语音事件。
     * 效果与 {@code /vox trigger <player> <triggerName>} 命令相同，
     * 但不需要 OP 权限，适合第三方模组直接调用。
     *
     * <p>触发遵循正常的冷却机制，冷却中时静默跳过。</p>
     * <p>内置 trigger 名（{@code hurt}、{@code death}、{@code kill} 等）及
     * {@code tacz_*} 系列均可触发，自定义名称建议使用 {@code custom_} 前缀。</p>
     *
     * @param player      目标玩家（服务端）
     * @param triggerName trigger 名称，如 {@code "custom_hello"}
     */
    public static void fireTrigger(ServerPlayer player, String triggerName) {
        VanillaTriggerHandler.fireForPlayer(player, triggerName, null, 0, null, null);
    }
}
