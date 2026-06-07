package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenNodeFilterPayload(int entityId, int channel, int filterSlot,
        VirtualFilterType requestedType) implements CustomPacketPayload {

    public static final Type<OpenNodeFilterPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "open_node_filter"));

    public static final StreamCodec<FriendlyByteBuf, OpenNodeFilterPayload> STREAM_CODEC = StreamCodec
            .of(OpenNodeFilterPayload::write, OpenNodeFilterPayload::read);

    public OpenNodeFilterPayload(int entityId, int channel, int filterSlot) {
        this(entityId, channel, filterSlot, VirtualFilterType.EXISTING);
    }

    private static OpenNodeFilterPayload read(FriendlyByteBuf buf) {
        return new OpenNodeFilterPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                VirtualFilterType.fromOrdinal(buf.readVarInt()));
    }

    private static void write(FriendlyByteBuf buf, OpenNodeFilterPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channel);
        buf.writeVarInt(payload.filterSlot);
        buf.writeVarInt(payload.requestedType.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
