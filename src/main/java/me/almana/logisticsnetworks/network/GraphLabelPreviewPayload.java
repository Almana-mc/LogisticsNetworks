package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GraphLabelPreviewPayload(UUID requestId, UUID networkId, List<UUID> nodeIds, String label,
                                       UUID settingsSource, GraphLabelChange.Kind kind, int replacedLabels,
                                       UUID expectedState, Result result) implements CustomPacketPayload {
    public enum Result {
        CONFIRM,
        APPLIED,
        NONE,
        FAILED
    }

    public static final Type<GraphLabelPreviewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "graph_label_preview"));
    public static final StreamCodec<FriendlyByteBuf, GraphLabelPreviewPayload> STREAM_CODEC = StreamCodec.of(
            GraphLabelPreviewPayload::write, GraphLabelPreviewPayload::read);

    public GraphLabelPreviewPayload {
        nodeIds = List.copyOf(nodeIds);
    }

    private static GraphLabelPreviewPayload read(FriendlyByteBuf buf) {
        UUID requestId = buf.readUUID();
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > SetNodeLabelsPayload.MAX_NODES) {
            throw new IllegalArgumentException("Invalid node count");
        }
        List<UUID> nodeIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) nodeIds.add(buf.readUUID());
        return new GraphLabelPreviewPayload(requestId, networkId, nodeIds, buf.readUtf(64), buf.readUUID(),
                buf.readEnum(GraphLabelChange.Kind.class), buf.readVarInt(), buf.readUUID(),
                buf.readEnum(Result.class));
    }

    private static void write(FriendlyByteBuf buf, GraphLabelPreviewPayload payload) {
        if (payload.nodeIds().size() > SetNodeLabelsPayload.MAX_NODES) {
            throw new IllegalArgumentException("Too many nodes");
        }
        buf.writeUUID(payload.requestId());
        buf.writeUUID(payload.networkId());
        buf.writeVarInt(payload.nodeIds().size());
        payload.nodeIds().forEach(buf::writeUUID);
        buf.writeUtf(payload.label(), 64);
        buf.writeUUID(payload.settingsSource());
        buf.writeEnum(payload.kind());
        buf.writeVarInt(payload.replacedLabels());
        buf.writeUUID(payload.expectedState());
        buf.writeEnum(payload.result());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
