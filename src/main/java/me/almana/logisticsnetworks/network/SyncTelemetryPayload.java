package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SyncTelemetryPayload(
        UUID networkId,
        int channelIndex,
        int typeOrdinal,
        long[] history,
        int historyIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTelemetryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = StreamCodec
            .of(SyncTelemetryPayload::write, SyncTelemetryPayload::read);

    public static SyncTelemetryPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int channelIndex = buf.readVarInt();
        int typeOrdinal = buf.readVarInt();
        long[] history = new long[TelemetryManager.HISTORY_SIZE];
        for (int i = 0; i < TelemetryManager.HISTORY_SIZE; i++) history[i] = buf.readLong();
        int index = buf.readVarInt();
        return new SyncTelemetryPayload(networkId, channelIndex, typeOrdinal, history, index);
    }

    public static void write(FriendlyByteBuf buf, SyncTelemetryPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeVarInt(payload.typeOrdinal);
        for (long v : payload.history) buf.writeLong(v);
        buf.writeVarInt(payload.historyIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
