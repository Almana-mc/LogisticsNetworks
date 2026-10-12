package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class RecipeCardCopyClearRecipe extends CustomRecipe {

    public RecipeCardCopyClearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
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
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.RECIPE_CARD_COPY_CLEAR_RECIPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
