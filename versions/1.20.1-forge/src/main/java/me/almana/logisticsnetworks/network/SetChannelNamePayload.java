package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SetChannelNamePayload(int entityId, int channelIndex, String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetChannelNamePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_channel_name"));

    public static final StreamCodec<FriendlyByteBuf, SetChannelNamePayload> STREAM_CODEC = StreamCodec
            .of(SetChannelNamePayload::write, SetChannelNamePayload::read);

    public static SetChannelNamePayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int channelIndex = buf.readVarInt();
        String name = buf.readUtf(24);
        return new SetChannelNamePayload(entityId, channelIndex, name);
    }

    public static void write(FriendlyByteBuf buf, SetChannelNamePayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeUtf(payload.name, 24);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
