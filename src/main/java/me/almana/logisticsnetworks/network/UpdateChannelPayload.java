package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record UpdateChannelPayload(
        int entityId,
        int channelIndex,
        boolean enabled,
        int modeOrdinal,
        int types,
        List<Integer> batches,
        int recipeSets,
        int tickDelay,
        int directionOrdinal,
        int redstoneModeOrdinal,
        int distributionModeOrdinal,
        int filterModeOrdinal,
        int priority,
        boolean resourceRoundRobin) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateChannelPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "update_channel"));

    public static final StreamCodec<FriendlyByteBuf, UpdateChannelPayload> STREAM_CODEC = StreamCodec
            .of(UpdateChannelPayload::write, UpdateChannelPayload::read);

    public static UpdateChannelPayload of(int entityId, int channelIndex, ChannelData channel) {
        List<Integer> batches = new ArrayList<>(ChannelType.values().length);
        for (ChannelType type : ChannelType.values()) {
            batches.add(channel.getBatch(type));
        }
        return new UpdateChannelPayload(entityId, channelIndex, channel.isEnabled(), channel.getMode().ordinal(),
                channel.getTypes(), batches, channel.getRecipeSets(), channel.getTickDelay(),
                channel.getIoSide().ordinal(),
                channel.getRedstoneMode().ordinal(), channel.getDistributionMode().ordinal(),
                channel.getFilterMode().ordinal(), channel.getPriority(), channel.isResourceRoundRobin());
    }

    public static UpdateChannelPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int channelIndex = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        int mode = buf.readVarInt();
        int types = buf.readVarInt();
        List<Integer> batches = new ArrayList<>(ChannelType.values().length);
        for (int i = 0; i < ChannelType.values().length; i++) {
            batches.add(buf.readVarInt());
        }
        return new UpdateChannelPayload(entityId, channelIndex, enabled, mode, types, batches,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void write(FriendlyByteBuf buf, UpdateChannelPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeBoolean(payload.enabled);
        buf.writeVarInt(payload.modeOrdinal);
        buf.writeVarInt(payload.types);
        for (int i = 0; i < ChannelType.values().length; i++) {
            buf.writeVarInt(i < payload.batches.size() ? payload.batches.get(i) : 0);
        }
        buf.writeVarInt(payload.recipeSets);
        buf.writeVarInt(payload.tickDelay);
        buf.writeVarInt(payload.directionOrdinal);
        buf.writeVarInt(payload.redstoneModeOrdinal);
        buf.writeVarInt(payload.distributionModeOrdinal);
        buf.writeVarInt(payload.filterModeOrdinal);
        buf.writeVarInt(payload.priority);
        buf.writeBoolean(payload.resourceRoundRobin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
