package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestNetworkGraphPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<RequestNetworkGraphPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "request_network_graph"));
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
