package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncChannelListPayload(
        UUID networkId,
        List<ChannelEntry> channels) implements CustomPacketPayload {

    public record ChannelEntry(int channelIndex, int typeOrdinal, int nodeCount) {
    }

    public static final CustomPacketPayload.Type<SyncChannelListPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_channel_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncChannelListPayload> STREAM_CODEC = StreamCodec
            .of(SyncChannelListPayload::write, SyncChannelListPayload::read);

    public static SyncChannelListPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        List<ChannelEntry> channels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int channelIndex = buf.readVarInt();
            int typeOrdinal = buf.readVarInt();
            int nodeCount = buf.readVarInt();
            channels.add(new ChannelEntry(channelIndex, typeOrdinal, nodeCount));
        }
        return new SyncChannelListPayload(networkId, channels);
    }

    public static void write(FriendlyByteBuf buf, SyncChannelListPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeVarInt(payload.channels.size());
        for (ChannelEntry entry : payload.channels) {
            buf.writeVarInt(entry.channelIndex());
            buf.writeVarInt(entry.typeOrdinal());
            buf.writeVarInt(entry.nodeCount());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
