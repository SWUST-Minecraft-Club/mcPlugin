package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * 编译期桩：真实类由 Fabric API（fabric-lifecycle-events-v1 模块）提供，运行期使用真实实现。
 * 只参与编译，不会打进模组 jar。
 */
public final class ClientTickEvents {

    public static final Event<EndTick> END_CLIENT_TICK = null;

    private ClientTickEvents() {
    }

    /** 每个客户端 tick 结束时触发 */
    public interface EndTick {

        void onEndTick(Minecraft client);
    }
}
