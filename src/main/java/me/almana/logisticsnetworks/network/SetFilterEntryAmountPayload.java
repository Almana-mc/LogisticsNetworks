package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetFilterEntryAmountPayload(int slot, int amount) implements CustomPacketPayload {

    public static final Type<SetFilterEntryAmountPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_amount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryAmountPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryAmountPayload::slot,
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryAmountPayload::amount,
                    SetFilterEntryAmountPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
