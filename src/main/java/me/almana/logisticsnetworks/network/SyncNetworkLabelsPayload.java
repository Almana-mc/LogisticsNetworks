package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncNetworkLabelsPayload(List<String> labels) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncNetworkLabelsPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_labels"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkLabelsPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkLabelsPayload::write, SyncNetworkLabelsPayload::read);

    public static SyncNetworkLabelsPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            labels.add(buf.readUtf(64));
        }
        return new SyncNetworkLabelsPayload(labels);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkLabelsPayload payload) {
        buf.writeVarInt(payload.labels.size());
        for (String label : payload.labels) {
            buf.writeUtf(label, 64);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
