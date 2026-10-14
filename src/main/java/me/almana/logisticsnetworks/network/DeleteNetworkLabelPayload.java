package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DeleteNetworkLabelPayload(UUID networkId, String label) implements CustomPacketPayload {

    public static final Type<DeleteNetworkLabelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "delete_network_label"));
    public static final StreamCodec<FriendlyByteBuf, DeleteNetworkLabelPayload> STREAM_CODEC = StreamCodec.of(
            DeleteNetworkLabelPayload::write, DeleteNetworkLabelPayload::read);

    public static DeleteNetworkLabelPayload read(FriendlyByteBuf buffer) {
        return new DeleteNetworkLabelPayload(buffer.readUUID(), buffer.readUtf(64));
    }

    public static void write(FriendlyByteBuf buffer, DeleteNetworkLabelPayload payload) {
        buffer.writeUUID(payload.networkId);
        buffer.writeUtf(payload.label, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
