package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncNetworkNodesPayload(
        UUID networkId,
        List<NodeInfo> nodes) implements CustomPacketPayload {

    public record NodeInfo(UUID nodeId, BlockPos nodePos, BlockPos attachedPos, String blockName, String nodeLabel,
            Identifier dimension, boolean visible, boolean highlighted) {
    }

    public static final CustomPacketPayload.Type<SyncNetworkNodesPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_nodes"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkNodesPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkNodesPayload::write, SyncNetworkNodesPayload::read);

    public static SyncNetworkNodesPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        List<NodeInfo> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID nodeId = buf.readUUID();
            BlockPos nodePos = buf.readBlockPos();
            BlockPos attachedPos = buf.readBlockPos();
            String blockName = buf.readUtf(128);
            String nodeLabel = buf.readUtf(64);
            Identifier dimension = buf.readIdentifier();
            boolean visible = buf.readBoolean();
            boolean highlighted = buf.readBoolean();
            nodes.add(new NodeInfo(nodeId, nodePos, attachedPos, blockName, nodeLabel, dimension, visible, highlighted));
        }
        return new SyncNetworkNodesPayload(networkId, nodes);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkNodesPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeVarInt(payload.nodes.size());
        for (NodeInfo node : payload.nodes) {
            buf.writeUUID(node.nodeId());
            buf.writeBlockPos(node.nodePos());
            buf.writeBlockPos(node.attachedPos());
            buf.writeUtf(node.blockName(), 128);
            buf.writeUtf(node.nodeLabel(), 64);
            buf.writeIdentifier(node.dimension());
            buf.writeBoolean(node.visible());
            buf.writeBoolean(node.highlighted());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
