package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntrySlotMappingPayload(int entryIndex, String slotExpression) implements CustomPacketPayload {

    public static final Type<SetFilterEntrySlotMappingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_slot_mapping"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntrySlotMappingPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntrySlotMappingPayload::write, SetFilterEntrySlotMappingPayload::read);

    public static SetFilterEntrySlotMappingPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        String slotExpression = buf.readUtf();
        return new SetFilterEntrySlotMappingPayload(entryIndex, slotExpression);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntrySlotMappingPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeUtf(payload.slotExpression);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
