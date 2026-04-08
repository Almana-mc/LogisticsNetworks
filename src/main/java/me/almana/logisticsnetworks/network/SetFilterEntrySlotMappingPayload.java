package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntrySlotMappingPayload(int entryIndex, String slotExpression) implements CustomPacketPayload {

    public static final Type<SetFilterEntrySlotMappingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_slot_mapping"));

    public static final StreamCodec<FriendlyByteBuf, SetFilterEntrySlotMappingPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntrySlotMappingPayload::write, SetFilterEntrySlotMappingPayload::read);

    private static SetFilterEntrySlotMappingPayload read(FriendlyByteBuf buf) {
        return new SetFilterEntrySlotMappingPayload(buf.readVarInt(), buf.readUtf());
    }

    private static void write(FriendlyByteBuf buf, SetFilterEntrySlotMappingPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeUtf(payload.slotExpression);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
