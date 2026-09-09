package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.graph.GraphChannel;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncNetworkGraphPayload(UUID networkId, String networkName, int totalNodes,
        List<GraphNode> nodes, Map<String, GraphPosition> positions) implements CustomPacketPayload {
    public static final Type<SyncNetworkGraphPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_network_graph"));
    public static final StreamCodec<FriendlyByteBuf, SyncNetworkGraphPayload> STREAM_CODEC =
            StreamCodec.of(SyncNetworkGraphPayload::write, SyncNetworkGraphPayload::read);

    public static SyncNetworkGraphPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        String name = buf.readUtf();
        int total = buf.readVarInt();
        int count = buf.readVarInt();
        List<GraphNode> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) nodes.add(readNode(buf));
        int positionCount = buf.readVarInt();
        Map<String, GraphPosition> positions = new LinkedHashMap<>();
        for (int i = 0; i < positionCount; i++) {
            positions.put(buf.readUtf(256), new GraphPosition(buf.readFloat(), buf.readFloat()));
        }
        return new SyncNetworkGraphPayload(networkId, name, total, nodes, positions);
    }

    private static GraphNode readNode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String label = buf.readUtf(48);
        String blockName = buf.readUtf();
        BlockPos pos = buf.readBlockPos();
        Identifier dimension = buf.readIdentifier();
        boolean dimensional = buf.readBoolean();
        int count = buf.readVarInt();
        List<GraphChannel> channels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            channels.add(new GraphChannel(buf.readVarInt(), buf.readEnum(ChannelType.class),
                    buf.readEnum(ChannelMode.class)));
        }
        return new GraphNode(id, label, blockName, pos, dimension, dimensional, channels);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkGraphPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUtf(payload.networkName);
        buf.writeVarInt(payload.totalNodes);
        buf.writeVarInt(payload.nodes.size());
        for (GraphNode node : payload.nodes) writeNode(buf, node);
        buf.writeVarInt(payload.positions.size());
        payload.positions.forEach((key, position) -> {
            buf.writeUtf(key, 256);
            buf.writeFloat(position.x());
            buf.writeFloat(position.y());
        });
    }

    private static void writeNode(FriendlyByteBuf buf, GraphNode node) {
        buf.writeUUID(node.nodeId());
        buf.writeUtf(node.label(), 48);
        buf.writeUtf(node.blockName());
        buf.writeBlockPos(node.attachedPos());
        buf.writeIdentifier(node.dimension());
        buf.writeBoolean(node.dimensional());
        buf.writeVarInt(node.channels().size());
        for (GraphChannel channel : node.channels()) {
            buf.writeVarInt(channel.index());
            buf.writeEnum(channel.type());
            buf.writeEnum(channel.mode());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
