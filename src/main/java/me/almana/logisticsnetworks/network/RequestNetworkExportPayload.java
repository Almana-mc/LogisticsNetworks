package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RequestNetworkExportPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkExportPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "request_network_export"));

    public static final StreamCodec<FriendlyByteBuf, RequestNetworkExportPayload> STREAM_CODEC = StreamCodec
            .of(RequestNetworkExportPayload::write, RequestNetworkExportPayload::read);

    public static RequestNetworkExportPayload read(FriendlyByteBuf buf) {
        return new RequestNetworkExportPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, RequestNetworkExportPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
