package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetNameFilterPayload(String name) implements CustomPacketPayload {

    public static final Type<SetNameFilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_name_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetNameFilterPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.STRING_UTF8,
                    SetNameFilterPayload::name,
                    SetNameFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
