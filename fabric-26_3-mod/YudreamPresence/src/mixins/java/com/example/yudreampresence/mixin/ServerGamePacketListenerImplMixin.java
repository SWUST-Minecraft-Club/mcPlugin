package com.example.yudreampresence.mixin;

import com.example.yudreampresence.YudreamPresence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AFK 的“有操作”信号：移动（按方块坐标变化）、聊天、命令、交互/攻击/使用物品、潜行/冲刺切换。
 *
 * <p>全部 {@code require = 0}：26.3 后续改动方法名时这些信号会静默失效，但不会影响进服/退服/快照。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onMove(CallbackInfo callback) {
        YudreamPresence.onMove(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleChat", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onChat(CallbackInfo callback) {
        YudreamPresence.onChatActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onChatCommand(CallbackInfo callback) {
        YudreamPresence.onCommandActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onSignedChatCommand(CallbackInfo callback) {
        YudreamPresence.onCommandActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onInteract(CallbackInfo callback) {
        YudreamPresence.onInteractActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onUseItem(CallbackInfo callback) {
        YudreamPresence.onInteractActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onUseItemOn(CallbackInfo callback) {
        YudreamPresence.onInteractActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handlePunch", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onPunch(CallbackInfo callback) {
        YudreamPresence.onInteractActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onPlayerAction(CallbackInfo callback) {
        YudreamPresence.onInteractActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onPlayerCommand(CallbackInfo callback) {
        YudreamPresence.onInteractActivity(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
