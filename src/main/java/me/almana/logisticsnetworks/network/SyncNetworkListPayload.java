package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncNetworkListPayload(
        List<NetworkEntry> networks) implements CustomPacketPayload {

    public record NetworkEntry(UUID id, String name, int nodeCount) {
    }

    public static final CustomPacketPayload.Type<SyncNetworkListPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkListPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkListPayload::write, SyncNetworkListPayload::read);

    public static SyncNetworkListPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<NetworkEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            int nodeCount = buf.readVarInt();
            entries.add(new NetworkEntry(id, name, nodeCount));
        }
        return new SyncNetworkListPayload(entries);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkListPayload payload) {
        buf.writeVarInt(payload.networks.size());
        for (NetworkEntry entry : payload.networks) {
            buf.writeUUID(entry.id);
            buf.writeUtf(entry.name, 64);
            buf.writeVarInt(entry.nodeCount);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
