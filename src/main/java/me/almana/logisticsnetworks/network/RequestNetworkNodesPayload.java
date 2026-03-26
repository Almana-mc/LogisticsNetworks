package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RequestNetworkNodesPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkNodesPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "request_network_nodes"));

    public static final StreamCodec<FriendlyByteBuf, RequestNetworkNodesPayload> STREAM_CODEC = StreamCodec
            .of(RequestNetworkNodesPayload::write, RequestNetworkNodesPayload::read);

    public static RequestNetworkNodesPayload read(FriendlyByteBuf buf) {
        return new RequestNetworkNodesPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, RequestNetworkNodesPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
