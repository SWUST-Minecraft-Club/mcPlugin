package org.spongepowered.asm.mixin.injection.callback;

/** 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明。 */
public class CallbackInfo {

    public boolean isCancellable() {
        return false;
    }

    public void cancel() {
    }
}
