package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestNetworkNodesPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkNodesPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "request_network_nodes"));

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
