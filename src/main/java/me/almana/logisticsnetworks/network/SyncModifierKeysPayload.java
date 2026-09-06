package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncModifierKeysPayload(int mask) implements CustomPacketPayload {
    public static final Type<SyncModifierKeysPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_modifier_keys"));
    public static final StreamCodec<FriendlyByteBuf, SyncModifierKeysPayload> STREAM_CODEC =
            StreamCodec.of(SyncModifierKeysPayload::write, SyncModifierKeysPayload::read);

    public static SyncModifierKeysPayload read(FriendlyByteBuf buf) {
        return new SyncModifierKeysPayload(buf.readUnsignedByte());
    }

    public static void write(FriendlyByteBuf buf, SyncModifierKeysPayload payload) {
        buf.writeByte(payload.mask());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
