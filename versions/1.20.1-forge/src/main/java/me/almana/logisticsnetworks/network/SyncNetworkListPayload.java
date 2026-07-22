package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncNetworkListPayload(
        List<NetworkEntry> networks) implements CustomPacketPayload {

    public record NetworkEntry(UUID id, String name, int nodeCount, int color) {
    }

    public static final CustomPacketPayload.Type<SyncNetworkListPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_network_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkListPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkListPayload::write, SyncNetworkListPayload::read);

    public static SyncNetworkListPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<NetworkEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            int nodeCount = buf.readVarInt();
            int color = buf.readInt();
            entries.add(new NetworkEntry(id, name, nodeCount, color));
        }
        return new SyncNetworkListPayload(entries);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkListPayload payload) {
        buf.writeVarInt(payload.networks.size());
        for (NetworkEntry entry : payload.networks) {
            buf.writeUUID(entry.id);
            buf.writeUtf(entry.name, 64);
            buf.writeVarInt(entry.nodeCount);
            buf.writeInt(entry.color);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

