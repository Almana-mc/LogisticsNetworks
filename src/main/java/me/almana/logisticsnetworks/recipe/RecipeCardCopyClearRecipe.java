package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.registration.Registration;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class RecipeCardCopyClearRecipe extends CustomRecipe {

    public static final RecipeCardCopyClearRecipe INSTANCE = new RecipeCardCopyClearRecipe();
    public static final MapCodec<RecipeCardCopyClearRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCardCopyClearRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<RecipeCardCopyClearRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack encoded = ItemStack.EMPTY;
        int blanks = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (!RecipeCardItem.is(stack)) return ItemStack.EMPTY;
            if (!stack.has(LogisticsDataComponents.RECIPE_CARD.get())) {
                blanks++;
            } else if (encoded.isEmpty()) {
                encoded = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (encoded.isEmpty()) return ItemStack.EMPTY;
        return blanks == 0 ? new ItemStack(Registration.RECIPE_CARD.get()) : encoded.copyWithCount(blanks + 1);
    }

    @Override
    public RecipeSerializer<RecipeCardCopyClearRecipe> getSerializer() {
        return SERIALIZER;
    }
}
