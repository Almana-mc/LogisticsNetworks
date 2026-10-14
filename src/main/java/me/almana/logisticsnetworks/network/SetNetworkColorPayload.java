package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SetNetworkColorPayload(UUID networkId, int color) implements CustomPacketPayload {

    public static final Type<SetNetworkColorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_network_color"));
    public static final StreamCodec<FriendlyByteBuf, SetNetworkColorPayload> STREAM_CODEC = StreamCodec.of(
            SetNetworkColorPayload::write, SetNetworkColorPayload::read);

    public static SetNetworkColorPayload read(FriendlyByteBuf buffer) {
        return new SetNetworkColorPayload(buffer.readUUID(), buffer.readInt());
    }

    public static void write(FriendlyByteBuf buffer, SetNetworkColorPayload payload) {
        buffer.writeUUID(payload.networkId);
        buffer.writeInt(payload.color);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
