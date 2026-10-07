package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public record SyncNetworkLabelsPayload(Map<String, Integer> labels) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncNetworkLabelsPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_network_labels"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkLabelsPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkLabelsPayload::write, SyncNetworkLabelsPayload::read);

    public static SyncNetworkLabelsPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count > 1024) throw new DecoderException("Too many labels: " + count);
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
