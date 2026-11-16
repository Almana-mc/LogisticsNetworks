package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncFilterScanResultPayload(
        ItemStack filter,
        int added,
        boolean storageFound,
        boolean filterFull) implements CustomPacketPayload {
    public static final Type<SyncFilterScanResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_filter_scan_result"));
    public static final StreamCodec<FriendlyByteBuf, SyncFilterScanResultPayload> STREAM_CODEC = StreamCodec
            .of(SyncFilterScanResultPayload::write, SyncFilterScanResultPayload::read);

    private static SyncFilterScanResultPayload read(FriendlyByteBuf buf) {
        return new SyncFilterScanResultPayload(
                buf.readItem(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    private static void write(FriendlyByteBuf buf, SyncFilterScanResultPayload payload) {
        buf.writeItem(payload.filter());
        buf.writeVarInt(payload.added());
        buf.writeBoolean(payload.storageFound());
        buf.writeBoolean(payload.filterFull());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
