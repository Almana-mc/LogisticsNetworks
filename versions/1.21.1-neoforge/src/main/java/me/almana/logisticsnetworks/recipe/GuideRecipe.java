package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.integration.guideme.GuideMeCompat;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class GuideRecipe extends CustomRecipe {

    public GuideRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean paper = false;
        boolean wrench = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.PAPER) && !paper) paper = true;
            else if (stack.getItem() instanceof WrenchItem && !wrench) wrench = true;
            else return false;
        }
        return paper && wrench;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return GuideMeCompat.createGuideItem();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return GuideMeCompat.createGuideItem();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof WrenchItem) remaining.set(i, stack.copy());
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.GUIDE_RECIPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
