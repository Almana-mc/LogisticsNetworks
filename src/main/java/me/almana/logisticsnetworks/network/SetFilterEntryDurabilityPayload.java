package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetFilterEntryDurabilityPayload(int slot, String operator, int value) implements CustomPacketPayload {

    public static final Type<SetFilterEntryDurabilityPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_durability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryDurabilityPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryDurabilityPayload::slot,
                    ByteBufCodecs.STRING_UTF8,
                    SetFilterEntryDurabilityPayload::operator,
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryDurabilityPayload::value,
                    SetFilterEntryDurabilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
