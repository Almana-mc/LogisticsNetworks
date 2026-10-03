package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncChannelDataPayload(int entityId, int channelIndex, ChannelData channelData) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncChannelDataPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_channel_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChannelDataPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SyncChannelDataPayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    SyncChannelDataPayload::channelIndex,
                    ChannelData.STREAM_CODEC,
                    SyncChannelDataPayload::channelData,
                    SyncChannelDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
