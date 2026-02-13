package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateChannelPayload(
        int entityId,
        int channelIndex,
        boolean enabled,
        int modeOrdinal,
        int typeOrdinal,
        int batchSize,
        int tickDelay,
        int directionOrdinal,
        int redstoneModeOrdinal,
        int distributionModeOrdinal,
        int filterModeOrdinal,
        int priority) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateChannelPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "update_channel"));

    public static final StreamCodec<FriendlyByteBuf, UpdateChannelPayload> STREAM_CODEC = StreamCodec
            .of(UpdateChannelPayload::write, UpdateChannelPayload::read);

    public static UpdateChannelPayload read(FriendlyByteBuf buf) {
        return new UpdateChannelPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    public static void write(FriendlyByteBuf buf, UpdateChannelPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeBoolean(payload.enabled);
        buf.writeVarInt(payload.modeOrdinal);
        buf.writeVarInt(payload.typeOrdinal);
        buf.writeVarInt(payload.batchSize);
        buf.writeVarInt(payload.tickDelay);
        buf.writeVarInt(payload.directionOrdinal);
        buf.writeVarInt(payload.redstoneModeOrdinal);
        buf.writeVarInt(payload.distributionModeOrdinal);
        buf.writeVarInt(payload.filterModeOrdinal);
        buf.writeVarInt(payload.priority);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
