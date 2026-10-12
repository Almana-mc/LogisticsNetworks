package me.almana.logisticsnetworks.network;

import io.netty.buffer.ByteBuf;
import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StampRecipeCardPayload(int entityId, int channel, int slot) implements CustomPacketPayload {

    public static final Type<StampRecipeCardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "stamp_recipe_card"));

    public static final StreamCodec<ByteBuf, StampRecipeCardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StampRecipeCardPayload::entityId,
            ByteBufCodecs.VAR_INT, StampRecipeCardPayload::channel,
            ByteBufCodecs.VAR_INT, StampRecipeCardPayload::slot,
            StampRecipeCardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
