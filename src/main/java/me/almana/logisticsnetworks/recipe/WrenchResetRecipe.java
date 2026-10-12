package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class WrenchResetRecipe extends CustomRecipe {

    public WrenchResetRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.ingredientCount() == 1 && hasWrench(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(Registration.WRENCH.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.WRENCH_RESET_RECIPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    private static boolean hasWrench(CraftingInput input) {
        for (ItemStack stack : input.items()) {
            if (stack.is(Registration.WRENCH.get())) {
                return true;
            }
        }
        return false;
    }
}
