package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetSlotFilterSlotsPayload(String expression) implements CustomPacketPayload {

    public static final Type<SetSlotFilterSlotsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_slot_filter_slots"));

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
