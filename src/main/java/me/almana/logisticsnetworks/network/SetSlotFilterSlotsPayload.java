package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetSlotFilterSlotsPayload(String expression) implements CustomPacketPayload {

    public static final Type<SetSlotFilterSlotsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_slot_filter_slots"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSlotFilterSlotsPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.STRING_UTF8,
                    SetSlotFilterSlotsPayload::expression,
                    SetSlotFilterSlotsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

