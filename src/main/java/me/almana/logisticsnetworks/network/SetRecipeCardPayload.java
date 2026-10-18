package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.component.RecipeCardData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetRecipeCardPayload(RecipeCardData data) implements CustomPacketPayload {

    public static final Type<SetRecipeCardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_recipe_card"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRecipeCardPayload> STREAM_CODEC =
            RecipeCardData.STREAM_CODEC.map(SetRecipeCardPayload::new, SetRecipeCardPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
