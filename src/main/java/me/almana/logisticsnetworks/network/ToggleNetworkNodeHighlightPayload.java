package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ToggleNetworkNodeHighlightPayload(UUID networkId, UUID nodeId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleNetworkNodeHighlightPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "toggle_network_node_highlight"));

    public static final StreamCodec<FriendlyByteBuf, ToggleNetworkNodeHighlightPayload> STREAM_CODEC = StreamCodec
            .of(ToggleNetworkNodeHighlightPayload::write, ToggleNetworkNodeHighlightPayload::read);

    public static ToggleNetworkNodeHighlightPayload read(FriendlyByteBuf buf) {
        return new ToggleNetworkNodeHighlightPayload(buf.readUUID(), buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, ToggleNetworkNodeHighlightPayload payload) {
        buf.writeUUID(payload.networkId());
        buf.writeUUID(payload.nodeId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
