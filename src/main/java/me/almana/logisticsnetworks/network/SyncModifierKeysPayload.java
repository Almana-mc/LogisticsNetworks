package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SyncModifierKeysPayload(int mask) implements CustomPacketPayload {

    public static final Type<SyncModifierKeysPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_modifier_keys"));
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
