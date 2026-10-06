package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DeleteNetworkLabelPayload(UUID networkId, String label) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteNetworkLabelPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "delete_network_label"));

    public static final StreamCodec<FriendlyByteBuf, DeleteNetworkLabelPayload> STREAM_CODEC = StreamCodec
            .of(DeleteNetworkLabelPayload::write, DeleteNetworkLabelPayload::read);

    public static DeleteNetworkLabelPayload read(FriendlyByteBuf buf) {
        return new DeleteNetworkLabelPayload(buf.readUUID(), buf.readUtf(64));
    }

    public static void write(FriendlyByteBuf buf, DeleteNetworkLabelPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUtf(payload.label, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
