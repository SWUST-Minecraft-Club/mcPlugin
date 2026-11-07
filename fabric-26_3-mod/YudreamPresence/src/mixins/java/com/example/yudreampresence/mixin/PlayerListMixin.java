package com.example.yudreampresence.mixin;

import com.example.yudreampresence.YudreamPresence;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 进服 / 退服。{@code placeNewPlayer} 的 RETURN 表示玩家已经完全加入。 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("RETURN"), require = 0, remap = false)
    private void yudream$onJoin(Connection connection, ServerPlayer player,
                                CommonListenerCookie cookie, CallbackInfo callback) {
        YudreamPresence.attachServer(((PlayerList) (Object) this).getServer());
        YudreamPresence.onJoin(player);
    }

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onQuit(ServerPlayer player, CallbackInfo callback) {
        YudreamPresence.onQuit(player);
    }
}
