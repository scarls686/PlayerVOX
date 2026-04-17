package com.playervox.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
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

    /** forgeBus 上监听按键按下 + 轮盘渲染 + 字幕渲染 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class ForgeBusEvents {

        private static boolean radialKeyWasDown = false;

        @SubscribeEvent
        public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
            RadialOverlay.onRenderGui(event);
            SubtitleOverlay.onRenderGui(event);
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            if (RadialOverlay.isActive()) {
                RadialOverlay.onScroll(event.getScrollDelta());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (VoxKeyBindings.OPEN_VOX_SCREEN.consumeClick()) {
                mc.setScreen(new VoxSelectScreen());
            }

            // 轮盘按键：按下时打开，松开时关闭（有 Screen 打开时跳过）
            boolean radialKeyDown = mc.screen == null && isRadialKeyDown(mc);
            if (radialKeyDown && !radialKeyWasDown) {
                RadialOverlay.open();
            } else if (!radialKeyDown && radialKeyWasDown) {
                RadialOverlay.close();
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
