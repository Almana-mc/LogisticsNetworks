package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RequestNetworkGraphPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<RequestNetworkGraphPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "request_network_graph"));
    public static final StreamCodec<FriendlyByteBuf, RequestNetworkGraphPayload> STREAM_CODEC =
            StreamCodec.of(RequestNetworkGraphPayload::write, RequestNetworkGraphPayload::read);

    public static RequestNetworkGraphPayload read(FriendlyByteBuf buf) {
        return new RequestNetworkGraphPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, RequestNetworkGraphPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

