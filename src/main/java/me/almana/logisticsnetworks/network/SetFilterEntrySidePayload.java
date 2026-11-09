package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetFilterEntrySidePayload(int entryIndex, int side) implements CustomPacketPayload {

    public static final Type<SetFilterEntrySidePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_filter_entry_side"));

    public static final StreamCodec<FriendlyByteBuf, SetFilterEntrySidePayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntrySidePayload::write, SetFilterEntrySidePayload::read);

    private static SetFilterEntrySidePayload read(FriendlyByteBuf buf) {
        return new SetFilterEntrySidePayload(buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, SetFilterEntrySidePayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeVarInt(payload.side);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
