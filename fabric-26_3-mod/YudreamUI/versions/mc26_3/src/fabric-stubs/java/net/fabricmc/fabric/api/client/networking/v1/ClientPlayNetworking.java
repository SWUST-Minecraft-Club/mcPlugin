package net.fabricmc.fabric.api.client.networking.v1;
import net.minecraft.client.Minecraft; import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class ClientPlayNetworking {
public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler){throw new UnsupportedOperationException();}
public static void send(CustomPacketPayload payload){throw new UnsupportedOperationException();}
public static boolean canSend(CustomPacketPayload.Type<?> type){throw new UnsupportedOperationException();}
public interface PlayPayloadHandler<T extends CustomPacketPayload>{void receive(T payload, Context context);}
public interface Context {Minecraft client();}
}
