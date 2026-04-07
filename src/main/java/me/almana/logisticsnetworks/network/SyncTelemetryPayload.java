package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SyncTelemetryPayload(UUID networkId, int channelIndex, int cursor, long[] history, int historySize) implements CustomPacketPayload {

    public static final int HISTORY_SIZE = TelemetryManager.HISTORY_SIZE;

    public static final CustomPacketPayload.Type<SyncTelemetryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = StreamCodec
            .of(SyncTelemetryPayload::write, SyncTelemetryPayload::read);

    public static SyncTelemetryPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int channelIndex = buf.readVarInt();
        int cursor = buf.readVarInt();
        long[] history = new long[HISTORY_SIZE];
        for (int i = 0; i < HISTORY_SIZE; i++) {
            history[i] = buf.readLong();
        }
        int historySize = buf.readVarInt();
        return new SyncTelemetryPayload(networkId, channelIndex, cursor, history, historySize);
    }

    public static void write(FriendlyByteBuf buf, SyncTelemetryPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeVarInt(payload.cursor);
        for (int i = 0; i < HISTORY_SIZE; i++) {
            buf.writeLong(payload.history[i]);
        }
        buf.writeVarInt(payload.historySize);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
