package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncQueuedNodePlacementPayload(ResourceLocation dimension, BlockPos pos, boolean queued)
        implements CustomPacketPayload {

    public static final Type<SyncQueuedNodePlacementPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_queued_node_placement"));
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
