package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetChannelNamePayload(int entityId, int channelIndex, String name) implements CustomPacketPayload {

    public static final Type<SetChannelNamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_channel_name"));

    public static final StreamCodec<FriendlyByteBuf, SetChannelNamePayload> STREAM_CODEC = StreamCodec
            .of(SetChannelNamePayload::write, SetChannelNamePayload::read);

    private static SetChannelNamePayload read(FriendlyByteBuf buf) {
        return new SetChannelNamePayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(24));
    }

    private static void write(FriendlyByteBuf buf, SetChannelNamePayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeUtf(payload.name, 24);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
