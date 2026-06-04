package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryTagPayload(int slot, String tag) implements CustomPacketPayload {

    public static final Type<SetFilterEntryTagPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_filter_entry_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryTagPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryTagPayload::slot,
                    ByteBufCodecs.STRING_UTF8,
                    SetFilterEntryTagPayload::tag,
                    SetFilterEntryTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
