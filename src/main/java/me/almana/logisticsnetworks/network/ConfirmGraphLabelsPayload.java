package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ConfirmGraphLabelsPayload(UUID requestId, UUID networkId, List<UUID> nodeIds, String label,
                                        UUID settingsSource, UUID expectedState) implements CustomPacketPayload {
    public static final Type<ConfirmGraphLabelsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "confirm_graph_labels"));
    public static final StreamCodec<FriendlyByteBuf, ConfirmGraphLabelsPayload> STREAM_CODEC = StreamCodec.of(
            ConfirmGraphLabelsPayload::write, ConfirmGraphLabelsPayload::read);

    public ConfirmGraphLabelsPayload {
        nodeIds = List.copyOf(nodeIds);
    }

    private static ConfirmGraphLabelsPayload read(FriendlyByteBuf buf) {
        UUID requestId = buf.readUUID();
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > SetNodeLabelsPayload.MAX_NODES) {
            throw new IllegalArgumentException("Invalid node count");
        }
        List<UUID> nodeIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) nodeIds.add(buf.readUUID());
        return new ConfirmGraphLabelsPayload(requestId, networkId, nodeIds, buf.readUtf(64), buf.readUUID(),
                buf.readUUID());
    }

    private static void write(FriendlyByteBuf buf, ConfirmGraphLabelsPayload payload) {
        if (payload.nodeIds().size() > SetNodeLabelsPayload.MAX_NODES) {
            throw new IllegalArgumentException("Too many nodes");
        }
        buf.writeUUID(payload.requestId());
        buf.writeUUID(payload.networkId());
        buf.writeVarInt(payload.nodeIds().size());
        payload.nodeIds().forEach(buf::writeUUID);
        buf.writeUtf(payload.label(), 64);
        buf.writeUUID(payload.settingsSource());
        buf.writeUUID(payload.expectedState());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
