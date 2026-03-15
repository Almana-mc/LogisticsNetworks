package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestNetworkLabelsPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkLabelsPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "request_network_labels"));

    public static final StreamCodec<FriendlyByteBuf, RequestNetworkLabelsPayload> STREAM_CODEC = StreamCodec
            .of(RequestNetworkLabelsPayload::write, RequestNetworkLabelsPayload::read);

    public static RequestNetworkLabelsPayload read(FriendlyByteBuf buf) {
        return new RequestNetworkLabelsPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, RequestNetworkLabelsPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
