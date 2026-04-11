package com.playervox.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件：按键绑定注册 + 按键响应。
 */
public class ClientSetup {

    /** modBus 上注册按键 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(VoxKeyBindings.OPEN_VOX_SCREEN);
        }
    }

    /** forgeBus 上监听按键按下 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (VoxKeyBindings.OPEN_VOX_SCREEN.consumeClick()) {
                mc.setScreen(new VoxSelectScreen());
            }
        }
    }
}
