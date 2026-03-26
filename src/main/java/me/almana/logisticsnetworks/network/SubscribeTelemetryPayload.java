package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record SubscribeTelemetryPayload(UUID networkId, boolean subscribe,
        int channelIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SubscribeTelemetryPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "subscribe_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SubscribeTelemetryPayload> STREAM_CODEC = StreamCodec
            .of(SubscribeTelemetryPayload::write, SubscribeTelemetryPayload::read);

    public static SubscribeTelemetryPayload read(FriendlyByteBuf buf) {
        return new SubscribeTelemetryPayload(buf.readUUID(), buf.readBoolean(), buf.readVarInt());
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
