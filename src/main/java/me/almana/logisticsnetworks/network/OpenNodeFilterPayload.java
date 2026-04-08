package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenNodeFilterPayload(int entityId, int channel, int filterSlot) implements CustomPacketPayload {

    public static final Type<OpenNodeFilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "open_node_filter"));

    public static final StreamCodec<FriendlyByteBuf, OpenNodeFilterPayload> STREAM_CODEC = StreamCodec
            .of(OpenNodeFilterPayload::write, OpenNodeFilterPayload::read);

    private static OpenNodeFilterPayload read(FriendlyByteBuf buf) {
        return new OpenNodeFilterPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, OpenNodeFilterPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channel);
        buf.writeVarInt(payload.filterSlot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
