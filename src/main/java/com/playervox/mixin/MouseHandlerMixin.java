package com.playervox.mixin;

import com.playervox.client.RadialOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 轮盘打开期间阻止视角转动。
 * 不 cancel turnPlayer()（让它正常消耗增量以免累积），
 * 而是在 turnPlayer 执行前记住旋转角，执行后恢复，等效于吃掉旋转。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Unique
    private float playervox$savedXRot;
    @Unique
    private float playervox$savedYRot;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void playervox$beforeTurn(CallbackInfo ci) {
        if (RadialOverlay.isActive()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                playervox$savedXRot = player.getXRot();
                playervox$savedYRot = player.getYRot();
            }
        }
    }

    @Inject(method = "turnPlayer", at = @At("RETURN"))
    private void playervox$afterTurn(CallbackInfo ci) {
        if (RadialOverlay.isActive()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.setXRot(playervox$savedXRot);
                player.setYRot(playervox$savedYRot);
            }
        }
    }
}
