package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class WrenchResetRecipe extends CustomRecipe {

    public WrenchResetRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        int count = 0;
        boolean wrench = false;
        for (ItemStack stack : input.getItems()) {
            if (stack.isEmpty()) continue;
            count++;
            wrench |= stack.is(Registration.WRENCH.get());
        }
        return count == 1 && wrench;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
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
}
