package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SubscribeTelemetryPayload(UUID networkId, boolean subscribe, int channelIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SubscribeTelemetryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "subscribe_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SubscribeTelemetryPayload> STREAM_CODEC = StreamCodec
            .of(SubscribeTelemetryPayload::write, SubscribeTelemetryPayload::read);

    public static SubscribeTelemetryPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        boolean subscribe = buf.readBoolean();
        int channelIndex = buf.readVarInt();
        return new SubscribeTelemetryPayload(networkId, subscribe, channelIndex);
    }

    public static void write(FriendlyByteBuf buf, SubscribeTelemetryPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeBoolean(payload.subscribe);
        buf.writeVarInt(payload.channelIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
