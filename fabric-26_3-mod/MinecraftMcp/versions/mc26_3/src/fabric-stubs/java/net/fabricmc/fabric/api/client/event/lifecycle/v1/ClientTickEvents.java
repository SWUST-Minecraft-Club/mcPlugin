package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
/** 签名按 26.3 Fabric API 核对；桩不入包。 */
public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = null;
    public interface EndTick { void onEndTick(Minecraft client); }
}
