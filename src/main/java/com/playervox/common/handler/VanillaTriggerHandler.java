package com.playervox.common.handler;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.playervox.PlayerVoxMod;
import com.playervox.common.network.NetworkHandler;
import com.playervox.common.network.PacketPlaySound;
import com.playervox.common.trigger.TriggerEvaluator;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

/**
 * 监听原版 Forge 事件，匹配触发器，通过网络包广播给周围客户端播放语音。
 */
public class VanillaTriggerHandler {

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getHealth() - event.getAmount() <= 0) return;
        // 清除 low_health 冷却，让 hurt 冷却结束后 low_health 能立即恢复
        CooldownTracker.clearCooldown(player.getUUID(), "low_health");
        // 受伤后血量百分比（LivingHurtEvent 触发时伤害尚未结算，需手动减去）
        float healthPercent = Math.max(0f, player.getHealth() - event.getAmount()) / player.getMaxHealth();
        fireForPlayer(player, "hurt", event.getSource(), event.getAmount(), healthPercent, null, null);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CooldownTracker.clearCooldown(player.getUUID(), "low_health");
        fireForPlayer(player, "death", event.getSource(), 0, null, null);
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        fireForPlayer(player, "kill", event.getSource(), 0, event.getEntity(), null);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String advId = event.getAdvancement().getId() != null
                ? event.getAdvancement().getId().toString() : "";
        fireForPlayer(player, "advancement", null, 0, null, advId);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        fireForPlayer(player, "login", null, 0, null, null);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        fireForPlayer(player, "respawn", null, 0, null, null);
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        fireForPlayer(player, "sleep", null, 0, null, null);
    }

    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Item item = event.getStack().getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        String itemIdStr = itemId != null ? itemId.toString() : "";
        fireForPlayer(player, "pickup", null, 0, null, itemIdStr);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // 血量为0时跳过，避免与死亡语音竞争
        if (player.getHealth() <= 0) return;

        float healthPercent = player.getHealth() / player.getMaxHealth();
        float fallDistance = player.fallDistance;
        boolean isFalling = fallDistance > 0
                && !player.onGround()
                && player.getDeltaMovement().y < 0
                && !player.onClimbable()
                && !player.isInWater()
                && !player.isInLava();

        // once 重置检查：无论是否在冷却中都要执行，确保状态及时重置
        String packId = VoxSelectionManager.get(player.serverLevel()).getSelection(player.getUUID());
        if (packId != null && !packId.isEmpty()) {
            resetOnceGroups(player, packId, "low_health", healthPercent);
            if (!isFalling) {
                resetOnceGroups(player, packId, "fall", fallDistance);
            }
        }

        long currentTick = player.level().getGameTime();

        // hurt 或 death 冷却期内跳过，避免与受伤/死亡语音竞争
        if (CooldownTracker.isOnCooldown(player.getUUID(), "hurt", currentTick)) return;
        if (CooldownTracker.isOnCooldown(player.getUUID(), "death", currentTick)) return;

        if (isFalling) {
            fireForPlayer(player, "fall", null, fallDistance, null, null);
        }

        fireForPlayer(player, "low_health", null, healthPercent, null, null);
    }

    /**
     * 检查指定 trigger 的所有 once 条件组，如果条件不再满足则重置 OnceTracker 标记。
     */
    private static void resetOnceGroups(ServerPlayer player, String packId, String trigger, float damage) {
        List<Map.Entry<String, JsonObject>> onceGroups = TriggerEvaluator.getOnceGroups(packId, trigger);
        for (Map.Entry<String, JsonObject> entry : onceGroups) {
            String conditionsKey = entry.getKey();
            if (!OnceTracker.isTriggered(player.getUUID(), trigger, conditionsKey)) continue;

            // 检查该条件组是否仍然满足
            boolean stillMatches = TriggerEvaluator.checkConditions(
                    entry.getValue(), trigger, null, damage, null, null, null
            );
            if (!stillMatches) {
                OnceTracker.reset(player.getUUID(), trigger, conditionsKey);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("vox")
                .then(Commands.literal("trigger")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("trigger_name", StringArgumentType.word())
                            .executes(ctx -> {
                                String triggerName = StringArgumentType.getString(ctx, "trigger_name");
                                java.util.Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                for (ServerPlayer target : targets) {
                                    fireForPlayer(target, triggerName, null, 0, null, null);
                                }
                                int count = targets.size();
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal("Triggered '" + triggerName + "' for " + count + " player(s)"),
                                    false
                                );
                                return count;
                            })
                        )
                    )
                )
        );
    }

    // -------------------------------------------------------------------------

    /**
     * 核心方法：查询玩家绑定的语音包 → 评估触发器 → 检查冷却 → 广播网络包。
     */
    public static void fireForPlayer(
            ServerPlayer player,
            String trigger,
            DamageSource source,
            float damage,
            Entity target,
            String extraString
    ) {
        fireForPlayer(player, trigger, source, damage, null, target, extraString);
    }

    /**
     * 同上，额外接受 healthPercent（hurt trigger 用，传受伤后血量百分比）。
     */
    public static void fireForPlayer(
            ServerPlayer player,
            String trigger,
            DamageSource source,
            float damage,
            @Nullable Float healthPercent,
            Entity target,
            String extraString
    ) {
        // 获取玩家选择的语音包
        String packId = VoxSelectionManager.get(player.serverLevel()).getSelection(player.getUUID());
        if (packId == null || packId.isEmpty()) return; // 未选择语音包，不触发

        long currentTick = player.level().getGameTime();
        if (CooldownTracker.isOnCooldown(player.getUUID(), trigger, currentTick)) return;

        TriggerEvaluator.Result result = TriggerEvaluator.evaluate(
                packId, trigger, player.getUUID(), source, damage, healthPercent, target, extraString
        );
        if (result == null) return;

        // 冷却至少 3 tick 防抖，优先使用 JSON 定义的冷却
        int cooldown = Math.max(3, result.cooldownTicks());
        CooldownTracker.setCooldown(player.getUUID(), trigger, currentTick, cooldown);

        // 如果是 once 组，标记已触发
        if (result.once()) {
            OnceTracker.markTriggered(player.getUUID(), trigger, result.conditionsKey());
        }

        float volume = 1.0f;
        float pitch = 1.0f;

        PlayerVoxMod.LOGGER.info("PlayerVOX: 播放语音 sound={} player={} pack={}",
                result.sound(), player.getName().getString(), packId);
        PacketPlaySound packet = new PacketPlaySound(
                result.sound(), player.getId(), volume, pitch,
                result.subtitle(), player.getName().getString(),
                player.getX(), player.getY(), player.getZ()
        );
        NetworkHandler.INSTANCE.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        player.getX(), player.getY(), player.getZ(),
                        volume * 16.0, player.level().dimension()
                )),
                packet
        );
    }
}
