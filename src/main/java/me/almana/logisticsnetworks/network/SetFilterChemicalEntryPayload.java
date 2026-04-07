package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterChemicalEntryPayload(
        int slot,
        String chemicalId) implements CustomPacketPayload {

    public static final Type<SetFilterChemicalEntryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_chemical_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterChemicalEntryPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterChemicalEntryPayload::slot,
                    ByteBufCodecs.STRING_UTF8,
                    SetFilterChemicalEntryPayload::chemicalId,
                    SetFilterChemicalEntryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

