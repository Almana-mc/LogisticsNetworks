package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetFilterEntryAmountPayload(int slot, int batch, int stock) implements CustomPacketPayload {

    public static final Type<SetFilterEntryAmountPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_amount"));

    public static final StreamCodec<FriendlyByteBuf, SetFilterEntryAmountPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryAmountPayload::write, SetFilterEntryAmountPayload::read);

    private static SetFilterEntryAmountPayload read(FriendlyByteBuf buf) {
        return new SetFilterEntryAmountPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, SetFilterEntryAmountPayload payload) {
        buf.writeVarInt(payload.slot);
        buf.writeVarInt(payload.batch);
        buf.writeVarInt(payload.stock);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
