package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncNetworkExportPayload(
        UUID networkId,
        String networkName,
        List<NodeExportInfo> nodes,
        String errorKey) implements CustomPacketPayload {

    public record NodeExportInfo(String label, boolean visible, CompoundTag clipboardTag) {
    }

    public static final CustomPacketPayload.Type<SyncNetworkExportPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_network_export"));

    public static final StreamCodec<FriendlyByteBuf, SyncNetworkExportPayload> STREAM_CODEC = StreamCodec
            .of(SyncNetworkExportPayload::write, SyncNetworkExportPayload::read);

    public static SyncNetworkExportPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        String networkName = buf.readUtf(64);
        String errorKey = buf.readUtf(256);
        int count = buf.readVarInt();
        List<NodeExportInfo> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String label = buf.readUtf(64);
            boolean visible = buf.readBoolean();
            CompoundTag clipboardTag = buf.readNbt();
            nodes.add(new NodeExportInfo(label, visible, clipboardTag == null ? new CompoundTag() : clipboardTag));
        }
        return new SyncNetworkExportPayload(networkId, networkName, nodes, errorKey);
    }

    public static void write(FriendlyByteBuf buf, SyncNetworkExportPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUtf(limit(payload.networkName, 64), 64);
        buf.writeUtf(limit(payload.errorKey == null ? "" : payload.errorKey, 256), 256);
        buf.writeVarInt(payload.nodes.size());
        for (NodeExportInfo node : payload.nodes) {
            buf.writeUtf(limit(node.label(), 64), 64);
            buf.writeBoolean(node.visible());
            buf.writeNbt(node.clipboardTag());
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
