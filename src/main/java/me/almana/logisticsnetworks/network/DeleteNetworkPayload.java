package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DeleteNetworkPayload(UUID networkId) implements CustomPacketPayload {

    public static final Type<DeleteNetworkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "delete_network"));
    public static final StreamCodec<FriendlyByteBuf, DeleteNetworkPayload> STREAM_CODEC = StreamCodec.of(
            DeleteNetworkPayload::write, DeleteNetworkPayload::read);

    public static DeleteNetworkPayload read(FriendlyByteBuf buffer) {
        return new DeleteNetworkPayload(buffer.readUUID());
    }

    public static void write(FriendlyByteBuf buffer, DeleteNetworkPayload payload) {
        buffer.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
