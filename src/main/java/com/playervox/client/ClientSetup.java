package com.playervox.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playervox.common.radial.RadialRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

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
            event.register(VoxKeyBindings.RADIAL_WHEEL);
        }
    }

    /** forgeBus 上监听按键按下 + 字幕渲染 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class ForgeBusEvents {

        private static boolean radialKeyWasDown = false;

        @SubscribeEvent
        public static void onRenderGui(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
            SubtitleOverlay.onRenderGui(event);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (VoxKeyBindings.OPEN_VOX_SCREEN.consumeClick()) {
                mc.setScreen(new VoxSelectScreen());
            }

            // 轮盘按键：按下时打开（无其他 Screen 时）；关闭由 RadialScreen.tick() 自行负责
            boolean radialKeyDown = isRadialKeyDown(mc);
            if (radialKeyDown && !radialKeyWasDown && mc.screen == null) {
                String packId = VoxClientState.getSelectedPackId();
                if (packId != null && !packId.isEmpty() && !RadialRegistry.getSlots(packId).isEmpty()) {
                    mc.setScreen(new RadialScreen());
                }
            }
            radialKeyWasDown = radialKeyDown;
        }

        /** 通过 GLFW 直接检测按键是否被按住 */
        private static boolean isRadialKeyDown(Minecraft mc) {
            if (mc.getWindow() == null) return false;
            InputConstants.Key key = VoxKeyBindings.RADIAL_WHEEL.getKey();
            if (key.getType() == InputConstants.Type.KEYSYM) {
                return GLFW.glfwGetKey(mc.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
            }
            return false;
        }
    }
}
