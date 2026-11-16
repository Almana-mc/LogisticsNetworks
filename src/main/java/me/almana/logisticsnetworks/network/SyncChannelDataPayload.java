package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record SyncChannelDataPayload(int entityId, int channelIndex, byte[] channelBytes, boolean first,
        boolean more) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncChannelDataPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_channel_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncChannelDataPayload> STREAM_CODEC = StreamCodec
            .of(SyncChannelDataPayload::write, SyncChannelDataPayload::read);

    public static void send(ServerPlayer player, int entityId, int channelIndex, CompoundTag channelTag) {
        List<byte[]> parts = NetworkHandler.split(channelTag);
        for (int i = 0; i < parts.size(); i++) {
            NetworkHandler.sendToPlayer(player, new SyncChannelDataPayload(entityId, channelIndex, parts.get(i),
                    i == 0, i < parts.size() - 1));
        }
    }

    public static SyncChannelDataPayload read(FriendlyByteBuf buf) {
        return new SyncChannelDataPayload(buf.readVarInt(), buf.readVarInt(), buf.readByteArray(),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void write(FriendlyByteBuf buf, SyncChannelDataPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeByteArray(payload.channelBytes);
        buf.writeBoolean(payload.first);
        buf.writeBoolean(payload.more);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
