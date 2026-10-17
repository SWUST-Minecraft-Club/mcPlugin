package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 编译期桩：真实类由 Fabric API（fabric-screen-api-v1 模块）提供，运行期使用真实实现。
 * 只参与编译，不会打进模组 jar。
 */
public final class ScreenEvents {

    public static final Event<AfterInit> AFTER_INIT = null;

    private ScreenEvents() {
    }

    /** 界面初始化完成后触发 */
    public interface AfterInit {

        void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }
}
