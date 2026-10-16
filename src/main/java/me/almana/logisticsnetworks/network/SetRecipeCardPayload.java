package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SetRecipeCardPayload(RecipeCardData data) implements CustomPacketPayload {

    public static final Type<SetRecipeCardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_recipe_card"));

    public static final StreamCodec<FriendlyByteBuf, SetRecipeCardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> RecipeCardData.STREAM_CODEC.encode(buf, payload.data()),
            buf -> new SetRecipeCardPayload(RecipeCardData.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
