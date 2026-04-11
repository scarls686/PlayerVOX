package com.playervox.compat.tacz;

import com.playervox.PlayerVoxMod;
import net.minecraftforge.common.MinecraftForge;

/**
 * TACZ 兼容模块：运行时检测 TACZ 是否加载，条件注册事件监听。
 * 与 AgilityPlus 处理 ParCool 的方式相同，避免 TACZ 不存在时触发类加载崩溃。
 */
public class TaczCompat {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(TaczTriggerHandler.class);
        PlayerVoxMod.LOGGER.info("PlayerVOX: TACZ detected, gun trigger features enabled.");
    }
}
