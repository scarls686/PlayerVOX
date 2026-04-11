package com.playervox.compat.tacz;

import com.playervox.common.handler.VanillaTriggerHandler;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * 监听 TACZ 提供的枪械事件，复用 VanillaTriggerHandler.fireForPlayer 广播语音。
 * 此类仅在 TACZ 已加载时由 TaczCompat.register() 注册。
 */
public class TaczTriggerHandler {

    @SubscribeEvent
    public static void onShoot(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer player)) return;
        String gunId = getGunId(event.getGunItemStack());
        VanillaTriggerHandler.fireForPlayer(player, "tacz_shoot", null, 0, null, gunId);
    }

    @SubscribeEvent
    public static void onReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String gunId = getGunId(event.getGunItemStack());
        VanillaTriggerHandler.fireForPlayer(player, "tacz_reload", null, 0, null, gunId);
    }

    @SubscribeEvent
    public static void onDraw(GunDrawEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String gunId = getGunId(event.getCurrentGunItem());
        VanillaTriggerHandler.fireForPlayer(player, "tacz_draw", null, 0, null, gunId);
    }

    @SubscribeEvent
    public static void onMelee(GunMeleeEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer player)) return;
        String gunId = getGunId(event.getGunItemStack());
        VanillaTriggerHandler.fireForPlayer(player, "tacz_melee", null, 0, null, gunId);
    }

    @SubscribeEvent
    public static void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;
        String gunId = event.getGunId().toString();
        VanillaTriggerHandler.fireForPlayer(player, "tacz_kill", null, 0, event.getKilledEntity(), gunId);
    }

    /** 从 ItemStack 取枪械 ID，格式如 "tacz:ak47" */
    private static String getGunId(net.minecraft.world.item.ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "";
    }
}
