package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryTagPayload(int slot, String tag, int kindOrdinal) implements CustomPacketPayload {

    public static final Type<SetFilterEntryTagPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_filter_entry_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryTagPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryTagPayload::slot,
                    ByteBufCodecs.STRING_UTF8,
                    SetFilterEntryTagPayload::tag,
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryTagPayload::kindOrdinal,
                    SetFilterEntryTagPayload::new);

    public SetFilterEntryTagPayload(int slot, String tag, FilterTargetType kind) {
        this(slot, tag, kind.ordinal());
    }

    public static SetFilterEntryTagPayload clear(int slot) {
        return new SetFilterEntryTagPayload(slot, "", 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
