package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncFilterScanResultPayload(
        ItemStack filter,
        int added,
        boolean storageFound,
        boolean filterFull) implements CustomPacketPayload {
    public static final Type<SyncFilterScanResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_filter_scan_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFilterScanResultPayload> STREAM_CODEC = StreamCodec
            .of(SyncFilterScanResultPayload::write, SyncFilterScanResultPayload::read);

    private static SyncFilterScanResultPayload read(RegistryFriendlyByteBuf buf) {
        return new SyncFilterScanResultPayload(
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    private static void write(RegistryFriendlyByteBuf buf, SyncFilterScanResultPayload payload) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.filter());
        buf.writeVarInt(payload.added());
        buf.writeBoolean(payload.storageFound());
        buf.writeBoolean(payload.filterFull());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
