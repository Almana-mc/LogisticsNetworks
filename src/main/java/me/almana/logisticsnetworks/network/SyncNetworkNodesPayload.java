package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncNetworkNodesPayload(
        UUID networkId,
        List<NodeInfo> nodes,
        boolean first,
        boolean more) implements CustomPacketPayload {

    public record NodeInfo(UUID nodeId, BlockPos nodePos, BlockPos attachedPos, String blockName, String nodeLabel,
            ResourceLocation dimension, boolean visible, boolean highlighted) {
    }

    public static final CustomPacketPayload.Type<SyncNetworkNodesPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_nodes"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkNodesPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkNodesPayload::write, SyncNetworkNodesPayload::read);

    public static void send(ServerPlayer player, UUID networkId, List<NodeInfo> nodes) {
        List<List<NodeInfo>> parts = NetworkHandler.split(nodes, SyncNetworkNodesPayload::writeNode);
        for (int i = 0; i < parts.size(); i++) {
            NetworkHandler.sendToPlayer(player,
                    new SyncNetworkNodesPayload(networkId, parts.get(i), i == 0, i < parts.size() - 1));
        }
    }

    public static SyncNetworkNodesPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        boolean first = buf.readBoolean();
        boolean more = buf.readBoolean();
        int count = buf.readVarInt();
        List<NodeInfo> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID nodeId = buf.readUUID();
            BlockPos nodePos = buf.readBlockPos();
            BlockPos attachedPos = buf.readBlockPos();
            String blockName = buf.readUtf(128);
            String nodeLabel = buf.readUtf(64);
            ResourceLocation dimension = buf.readResourceLocation();
            boolean visible = buf.readBoolean();
            boolean highlighted = buf.readBoolean();
            nodes.add(new NodeInfo(nodeId, nodePos, attachedPos, blockName, nodeLabel, dimension, visible, highlighted));
        }
        return new SyncNetworkNodesPayload(networkId, nodes, first, more);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkNodesPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeBoolean(payload.first);
        buf.writeBoolean(payload.more);
        buf.writeVarInt(payload.nodes.size());
        for (NodeInfo node : payload.nodes) {
            writeNode(buf, node);
        }
    }

    private static void writeNode(FriendlyByteBuf buf, NodeInfo node) {
        buf.writeUUID(node.nodeId());
        buf.writeBlockPos(node.nodePos());
        buf.writeBlockPos(node.attachedPos());
        buf.writeUtf(node.blockName(), 128);
        buf.writeUtf(node.nodeLabel(), 64);
        buf.writeResourceLocation(node.dimension());
        buf.writeBoolean(node.visible());
        buf.writeBoolean(node.highlighted());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
