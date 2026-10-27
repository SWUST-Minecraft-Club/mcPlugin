package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.network.*; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
<T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B,T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B,T> codec);
static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay(){throw new UnsupportedOperationException();}
static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay(){throw new UnsupportedOperationException();}
}
