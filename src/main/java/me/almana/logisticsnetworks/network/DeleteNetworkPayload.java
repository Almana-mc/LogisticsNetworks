package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record DeleteNetworkPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteNetworkPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "delete_network"));

    public static final StreamCodec<FriendlyByteBuf, DeleteNetworkPayload> STREAM_CODEC = StreamCodec
            .of(DeleteNetworkPayload::write, DeleteNetworkPayload::read);

    public static DeleteNetworkPayload read(FriendlyByteBuf buf) {
        return new DeleteNetworkPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, DeleteNetworkPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
