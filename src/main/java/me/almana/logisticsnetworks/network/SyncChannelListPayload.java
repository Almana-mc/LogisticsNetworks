package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncChannelListPayload(UUID networkId, List<ChannelEntry> channels) implements CustomPacketPayload {

    public record ChannelEntry(int channelIndex, int mode, int type) {
    }

    public static final CustomPacketPayload.Type<SyncChannelListPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "sync_channel_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncChannelListPayload> STREAM_CODEC = StreamCodec
            .of(SyncChannelListPayload::write, SyncChannelListPayload::read);

    public static SyncChannelListPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        List<ChannelEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int channelIndex = buf.readVarInt();
            int mode = buf.readVarInt();
            int type = buf.readVarInt();
            entries.add(new ChannelEntry(channelIndex, mode, type));
        }
        return new SyncChannelListPayload(networkId, entries);
    }

    public static void write(FriendlyByteBuf buf, SyncChannelListPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeVarInt(payload.channels.size());
        for (ChannelEntry entry : payload.channels) {
            buf.writeVarInt(entry.channelIndex);
            buf.writeVarInt(entry.mode);
            buf.writeVarInt(entry.type);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
