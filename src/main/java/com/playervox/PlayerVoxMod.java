package com.playervox;

import com.playervox.client.VoxSoundAssetsManager;
import com.playervox.common.handler.CooldownTracker;
import com.playervox.common.handler.OnceTracker;
import com.playervox.common.handler.VanillaTriggerHandler;
import com.playervox.common.init.ModSounds;
import com.playervox.common.handler.VoxSelectionManager;
import com.playervox.common.loader.VoxDatapackLoader;
import com.playervox.common.radial.RadialDatapackLoader;
import com.playervox.common.loader.VoxPackFinder;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketSyncRadialSlots;
import com.playervox.common.network.PacketSyncVoxPackList;
import com.playervox.common.network.PacketSyncVoxSelection;
import com.playervox.common.radial.RadialRegistry;
import net.minecraft.network.chat.Component;
import com.playervox.compat.tacz.TaczCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

@Mod(PlayerVoxMod.MOD_ID)
public class PlayerVoxMod {

    public static final String MOD_ID = "playervox";
    public static final Logger LOGGER = LogManager.getLogger();

    public static boolean TACZ_LOADED = false;

    public PlayerVoxMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(PlayerVoxMod::onAddPackFinders);
        ModSounds.SOUNDS.register(modBus);

        MinecraftForge.EVENT_BUS.register(PlayerVoxMod.class);
        MinecraftForge.EVENT_BUS.register(VanillaTriggerHandler.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);

        TACZ_LOADED = ModList.get().isLoaded("tacz");
        if (TACZ_LOADED) {
            TaczCompat.register();
        } else {
            LOGGER.info("PlayerVOX: TACZ not found, gun trigger features disabled.");
        }
    }

    /** AddPackFindersEvent 在 modBus 上，每个 PackType 触发一次 */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        event.addRepositorySource(new VoxPackFinder(event.getPackType()));
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VoxDatapackLoader());
        event.addListener(new RadialDatapackLoader());
    }

    /** 客户端资源重载监听器注册 */
    @Mod.EventBusSubscriber(value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onRegisterClientReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(VoxSoundAssetsManager.INSTANCE);
        }
    }

    /** 玩家登录时同步语音包列表和当前选择 */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 同步可用语音包列表
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncVoxPackList(new ArrayList<>(VoxPackFinder.getAllPackMetas()))
        );

        // 同步该玩家的语音包选择
        VoxSelectionManager manager = VoxSelectionManager.get(player.serverLevel());
        String selection = manager.getSelection(player.getUUID());
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncVoxSelection(selection != null ? selection : "")
        );

        // 同步轮盘扇区定义到客户端
        var allRadialSlots = RadialRegistry.getAllSlots();
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncRadialSlots(allRadialSlots)
        );

        // 发送轮盘扇区加载冲突警告
        for (String warning : RadialDatapackLoader.drainWarnings()) {
            player.sendSystemMessage(Component.literal(warning));
        }
    }

    /** 玩家断线时清理冷却数据 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CooldownTracker.remove(event.getEntity().getUUID());
        OnceTracker.remove(event.getEntity().getUUID());
    }
}
