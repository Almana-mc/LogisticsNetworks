package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetFilterEntryTagPayload(int slot, String tag) implements CustomPacketPayload {

    public static final Type<SetFilterEntryTagPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_tag"));

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
