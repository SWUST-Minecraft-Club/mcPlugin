package com.example.yudreampresence.mixin;

import com.example.yudreampresence.YudreamPresence;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** 服务端生命周期：第一次 tick 时接管，停服前 flush。 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "tickServer", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$attachServer(BooleanSupplier hasTimeLeft, CallbackInfo callback) {
        YudreamPresence.attachServer((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"), require = 0, remap = false)
    private void yudream$onServerStopping(CallbackInfo callback) {
        YudreamPresence.onServerStopping((MinecraftServer) (Object) this);
    }
}
