package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryAmountPayload(int entryIndex, int amount) implements CustomPacketPayload {

    public static final Type<SetFilterEntryAmountPayload> TYPE = new Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "set_filter_entry_amount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryAmountPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryAmountPayload::entryIndex,
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryAmountPayload::amount,
                    SetFilterEntryAmountPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
