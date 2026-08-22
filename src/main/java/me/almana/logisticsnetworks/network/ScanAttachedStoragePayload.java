package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScanAttachedStoragePayload() implements CustomPacketPayload {
    public static final ScanAttachedStoragePayload INSTANCE = new ScanAttachedStoragePayload();
    public static final Type<ScanAttachedStoragePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "scan_attached_storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanAttachedStoragePayload> STREAM_CODEC = StreamCodec
            .unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
