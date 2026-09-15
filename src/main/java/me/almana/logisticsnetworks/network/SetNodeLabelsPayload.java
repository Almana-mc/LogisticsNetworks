package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SetNodeLabelsPayload(UUID networkId, List<UUID> nodeIds, String label, UUID settingsSource)
        implements CustomPacketPayload {
    public static final int MAX_NODES = 512;
    public static final Type<SetNodeLabelsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_node_labels"));
    public static final StreamCodec<FriendlyByteBuf, SetNodeLabelsPayload> STREAM_CODEC = StreamCodec.of(
            SetNodeLabelsPayload::write, SetNodeLabelsPayload::read);

    public SetNodeLabelsPayload {
        nodeIds = List.copyOf(nodeIds);
    }

    private static SetNodeLabelsPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_NODES) throw new IllegalArgumentException("Invalid node count");
        List<UUID> nodeIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) nodeIds.add(buf.readUUID());
        return new SetNodeLabelsPayload(networkId, nodeIds, buf.readUtf(64), buf.readUUID());
    }

    private static void write(FriendlyByteBuf buf, SetNodeLabelsPayload payload) {
        if (payload.nodeIds().size() > MAX_NODES) throw new IllegalArgumentException("Too many nodes");
        buf.writeUUID(payload.networkId());
        buf.writeVarInt(payload.nodeIds().size());
        payload.nodeIds().forEach(buf::writeUUID);
        buf.writeUtf(payload.label(), 64);
        buf.writeUUID(payload.settingsSource());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
