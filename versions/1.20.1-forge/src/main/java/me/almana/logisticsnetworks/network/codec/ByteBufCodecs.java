package me.almana.logisticsnetworks.network.codec;

public final class ByteBufCodecs {

    public static final StreamCodec<RegistryFriendlyByteBuf, Integer> VAR_INT = StreamCodec.of(
            (buf, value) -> buf.writeVarInt(value),
            RegistryFriendlyByteBuf::readVarInt);

    public static final StreamCodec<RegistryFriendlyByteBuf, Boolean> BOOL = StreamCodec.of(
            (buf, value) -> buf.writeBoolean(value),
            RegistryFriendlyByteBuf::readBoolean);

    public static final StreamCodec<RegistryFriendlyByteBuf, String> STRING_UTF8 = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value == null ? "" : value),
            RegistryFriendlyByteBuf::readUtf);

    private ByteBufCodecs() {
    }
}
