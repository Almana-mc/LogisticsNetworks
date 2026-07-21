package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ToggleNetworkNodeHighlightPayload(UUID networkId, UUID nodeId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleNetworkNodeHighlightPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "toggle_network_node_highlight"));

    public static final StreamCodec<FriendlyByteBuf, ToggleNetworkNodeHighlightPayload> STREAM_CODEC = StreamCodec
            .of(ToggleNetworkNodeHighlightPayload::write, ToggleNetworkNodeHighlightPayload::read);

    public static ToggleNetworkNodeHighlightPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        UUID nodeId = buf.readUUID();
        return new ToggleNetworkNodeHighlightPayload(networkId, nodeId);
    }

    public static void write(FriendlyByteBuf buf, ToggleNetworkNodeHighlightPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUUID(payload.nodeId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
