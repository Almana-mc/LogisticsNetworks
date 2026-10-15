package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncNetworkListPayload(
        List<NetworkEntry> networks, boolean first, boolean more) implements CustomPacketPayload {

    public record NetworkEntry(UUID id, String name, int nodeCount, boolean pinned, boolean canDelete, int color,
            long createdAt) {
    }

    public static final CustomPacketPayload.Type<SyncNetworkListPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkListPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkListPayload::write, SyncNetworkListPayload::read);

    public static void send(ServerPlayer player, List<NetworkEntry> networks) {
        List<List<NetworkEntry>> parts = NetworkHandler.split(networks, SyncNetworkListPayload::writeEntry);
        for (int i = 0; i < parts.size(); i++) {
            NetworkHandler.sendToPlayer(player,
                    new SyncNetworkListPayload(parts.get(i), i == 0, i < parts.size() - 1));
        }
    }

    public static SyncNetworkListPayload read(FriendlyByteBuf buf) {
        boolean first = buf.readBoolean();
        boolean more = buf.readBoolean();
        int count = buf.readVarInt();
        List<NetworkEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            int nodeCount = buf.readVarInt();
            boolean pinned = buf.readBoolean();
            boolean canDelete = buf.readBoolean();
            int color = buf.readInt();
            long createdAt = buf.readLong();
            entries.add(new NetworkEntry(id, name, nodeCount, pinned, canDelete, color, createdAt));
        }
        return new SyncNetworkListPayload(entries, first, more);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkListPayload payload) {
        buf.writeBoolean(payload.first);
        buf.writeBoolean(payload.more);
        buf.writeVarInt(payload.networks.size());
        for (NetworkEntry entry : payload.networks) {
            writeEntry(buf, entry);
        }
    }

    private static void writeEntry(FriendlyByteBuf buf, NetworkEntry entry) {
        buf.writeUUID(entry.id);
        buf.writeUtf(entry.name, 64);
        buf.writeVarInt(entry.nodeCount);
        buf.writeBoolean(entry.pinned);
        buf.writeBoolean(entry.canDelete);
        buf.writeInt(entry.color);
        buf.writeLong(entry.createdAt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
