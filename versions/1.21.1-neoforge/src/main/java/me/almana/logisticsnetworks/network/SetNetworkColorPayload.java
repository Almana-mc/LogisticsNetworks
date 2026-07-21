package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SetNetworkColorPayload(
        UUID networkId, int color) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetNetworkColorPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_network_color"));

    public static final StreamCodec<FriendlyByteBuf, SetNetworkColorPayload> STREAM_CODEC = StreamCodec
            .of(SetNetworkColorPayload::write, SetNetworkColorPayload::read);

    public static SetNetworkColorPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int color = buf.readInt();
        return new SetNetworkColorPayload(networkId, color);
    }

    public static void write(FriendlyByteBuf buf, SetNetworkColorPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeInt(payload.color);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
