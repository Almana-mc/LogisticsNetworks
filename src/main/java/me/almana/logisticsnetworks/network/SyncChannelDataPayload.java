package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncChannelDataPayload(int entityId, int channelIndex, CompoundTag channelData) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncChannelDataPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_channel_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncChannelDataPayload> STREAM_CODEC = StreamCodec
            .of(SyncChannelDataPayload::write, SyncChannelDataPayload::read);

    public static SyncChannelDataPayload read(FriendlyByteBuf buf) {
        return new SyncChannelDataPayload(buf.readVarInt(), buf.readVarInt(), buf.readNbt());
    }

    public static void write(FriendlyByteBuf buf, SyncChannelDataPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeNbt(payload.channelData);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
