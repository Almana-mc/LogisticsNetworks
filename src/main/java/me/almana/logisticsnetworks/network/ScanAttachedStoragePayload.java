package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ScanAttachedStoragePayload() implements CustomPacketPayload {
    public static final ScanAttachedStoragePayload INSTANCE = new ScanAttachedStoragePayload();
    public static final Type<ScanAttachedStoragePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "scan_attached_storage"));
    public static final StreamCodec<FriendlyByteBuf, ScanAttachedStoragePayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> {
            }, buf -> INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
