package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryEnchantedPayload(int entryIndex, boolean enabled, boolean value) implements CustomPacketPayload {

    public static final Type<SetFilterEntryEnchantedPayload> TYPE = new Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "set_filter_entry_enchanted"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryEnchantedPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryEnchantedPayload::write, SetFilterEntryEnchantedPayload::read);

    public static SetFilterEntryEnchantedPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        boolean value = buf.readBoolean();
        return new SetFilterEntryEnchantedPayload(entryIndex, enabled, value);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryEnchantedPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeBoolean(payload.enabled);
        buf.writeBoolean(payload.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
