package net.fabricmc.fabric.api.client.networking.v1;
import net.fabricmc.fabric.api.event.Event; import net.fabricmc.fabric.api.networking.v1.PacketSender; import net.minecraft.client.Minecraft; import net.minecraft.client.multiplayer.ClientPacketListener;
public final class ClientPlayConnectionEvents {
public static final Event<Join> JOIN=null; public static final Event<Disconnect> DISCONNECT=null;
public interface Join {void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client);}
public interface Disconnect {void onPlayDisconnect(ClientPacketListener handler,Minecraft client);}
}
