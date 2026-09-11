package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncQueuedNodePlacementPayload(ResourceLocation dimension, BlockPos pos, boolean queued)
        implements CustomPacketPayload {

    public static final Type<SyncQueuedNodePlacementPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_queued_node_placement"));
    public static final StreamCodec<FriendlyByteBuf, SyncQueuedNodePlacementPayload> STREAM_CODEC = StreamCodec.of(
            SyncQueuedNodePlacementPayload::write, SyncQueuedNodePlacementPayload::read);

    private static SyncQueuedNodePlacementPayload read(FriendlyByteBuf buf) {
        return new SyncQueuedNodePlacementPayload(buf.readResourceLocation(), buf.readBlockPos(), buf.readBoolean());
    }

    private static void write(FriendlyByteBuf buf, SyncQueuedNodePlacementPayload payload) {
        buf.writeResourceLocation(payload.dimension());
        buf.writeBlockPos(payload.pos());
        buf.writeBoolean(payload.queued());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
