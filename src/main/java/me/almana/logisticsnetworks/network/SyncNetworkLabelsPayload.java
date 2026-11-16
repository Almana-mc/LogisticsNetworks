package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public record SyncNetworkLabelsPayload(Map<String, Integer> labels) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncNetworkLabelsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_labels"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkLabelsPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkLabelsPayload::write, SyncNetworkLabelsPayload::read);

    public static SyncNetworkLabelsPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<String, Integer> labels = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            labels.put(buf.readUtf(64), buf.readVarInt());
        }
        return new SyncNetworkLabelsPayload(labels);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkLabelsPayload payload) {
        buf.writeVarInt(payload.labels.size());
        payload.labels.forEach((label, nodeCount) -> {
            buf.writeUtf(label, 64);
            buf.writeVarInt(nodeCount);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
