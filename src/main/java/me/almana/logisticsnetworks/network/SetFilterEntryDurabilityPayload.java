package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryDurabilityPayload(int entryIndex, String comparison, int value) implements CustomPacketPayload {

    public static final Type<SetFilterEntryDurabilityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_durability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryDurabilityPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryDurabilityPayload::write, SetFilterEntryDurabilityPayload::read);

    public static SetFilterEntryDurabilityPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        String comparison = buf.readUtf();
        int value = buf.readVarInt();
        return new SetFilterEntryDurabilityPayload(entryIndex, comparison, value);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryDurabilityPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeUtf(payload.comparison);
        buf.writeVarInt(payload.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
