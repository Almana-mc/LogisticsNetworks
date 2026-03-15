package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestChannelListPayload(UUID networkId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestChannelListPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "request_channel_list"));

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
