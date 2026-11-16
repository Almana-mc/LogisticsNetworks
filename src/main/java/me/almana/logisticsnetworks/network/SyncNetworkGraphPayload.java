package me.almana.logisticsnetworks.network;

import io.netty.buffer.ByteBufUtil;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.graph.GraphChannel;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncNetworkGraphPayload(UUID networkId, String networkName, int totalNodes,
        List<GraphNode> nodes, Map<String, GraphPosition> positions) implements CustomPacketPayload {
    public static final Type<SyncNetworkGraphPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_graph"));
    public static final StreamCodec<FriendlyByteBuf, SyncNetworkGraphPayload> STREAM_CODEC =
            StreamCodec.of(SyncNetworkGraphPayload::write, SyncNetworkGraphPayload::read);
    // Forge caps payloads at 1MiB
    private static final int BYTE_BUDGET = 900_000;

    // Excess nodes show as unloaded
    public static SyncNetworkGraphPayload capped(UUID networkId, String networkName, int totalNodes,
            List<GraphNode> nodes, Map<String, GraphPosition> positions) {
        int budget = BYTE_BUDGET - utfBytes(networkName);
        List<GraphNode> kept = new ArrayList<>();
        Map<String, GraphPosition> keptPositions = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            String key = NetworkGraph.key(node);
            GraphPosition position = keptPositions.containsKey(key) ? null : positions.get(key);
            int size = nodeBytes(node) + (position == null ? 0 : utfBytes(key) + 8);
            if (size > budget) break;
            budget -= size;
            kept.add(node);
            if (position != null) keptPositions.put(key, position);
        }
        return new SyncNetworkGraphPayload(networkId, networkName, totalNodes, kept, keptPositions);
    }

    private static int nodeBytes(GraphNode node) {
        return 16 + utfBytes(node.label()) + utfBytes(node.blockName()) + 8
                + utfBytes(node.dimension().toString()) + 2 + node.channels().size() * 3;
    }

    private static int utfBytes(String value) {
        return 3 + ByteBufUtil.utf8Bytes(value);
    }

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
        ResourceLocation dimension = buf.readResourceLocation();
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
        buf.writeResourceLocation(node.dimension());
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
