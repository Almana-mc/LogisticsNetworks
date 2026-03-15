package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestChannelListPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestChannelListPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "request_channel_list"));

    public static final StreamCodec<FriendlyByteBuf, RequestChannelListPayload> STREAM_CODEC = StreamCodec
            .of(RequestChannelListPayload::write, RequestChannelListPayload::read);

    public static RequestChannelListPayload read(FriendlyByteBuf buf) {
        return new RequestChannelListPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, RequestChannelListPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
