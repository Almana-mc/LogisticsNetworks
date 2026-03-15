package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SyncChannelDataPayload(int entityId, int channelIndex, CompoundTag channelTag) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncChannelDataPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "sync_channel_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncChannelDataPayload> STREAM_CODEC = StreamCodec
            .of(SyncChannelDataPayload::write, SyncChannelDataPayload::read);

    public static SyncChannelDataPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int channelIndex = buf.readVarInt();
        CompoundTag channelTag = buf.readNbt();
        return new SyncChannelDataPayload(entityId, channelIndex, channelTag);
    }

    public static void write(FriendlyByteBuf buf, SyncChannelDataPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeNbt(payload.channelTag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
