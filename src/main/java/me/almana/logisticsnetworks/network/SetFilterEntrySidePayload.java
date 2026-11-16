package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntrySidePayload(int entryIndex, int side) implements CustomPacketPayload {

    public static final Type<SetFilterEntrySidePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_side"));

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
