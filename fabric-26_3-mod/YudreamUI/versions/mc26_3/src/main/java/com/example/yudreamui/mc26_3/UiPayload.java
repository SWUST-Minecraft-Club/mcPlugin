package com.example.yudreamui.mc26_3;
import com.example.yudreamui.protocol.Wire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
record UiPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<UiPayload> TYPE = new Type<>(Identifier.parse(Wire.CHANNEL));
    static final StreamCodec<RegistryFriendlyByteBuf, UiPayload> CODEC = new StreamCodec<>() {
        public UiPayload decode(RegistryFriendlyByteBuf buf) {
            if (buf.readableBytes() > Wire.CHUNK + 20) throw new IllegalArgumentException("UI fragment exceeds limit");
            byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); return new UiPayload(bytes);
        }
        public void encode(RegistryFriendlyByteBuf buf, UiPayload payload) { buf.writeBytes(payload.bytes); }
    };
    public Type<UiPayload> type() { return TYPE; }
}
