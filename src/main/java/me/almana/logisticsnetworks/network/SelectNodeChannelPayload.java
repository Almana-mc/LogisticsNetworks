package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectNodeChannelPayload(
        int entityId,
        int channelIndex) implements CustomPacketPayload {

    public static final Type<SelectNodeChannelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "select_node_channel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectNodeChannelPayload> STREAM_CODEC = StreamCodec
            .of(SelectNodeChannelPayload::write, SelectNodeChannelPayload::read);

    public static SelectNodeChannelPayload read(RegistryFriendlyByteBuf buf) {
        return new SelectNodeChannelPayload(buf.readVarInt(), buf.readVarInt());
    }

    public static void write(RegistryFriendlyByteBuf buf, SelectNodeChannelPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
