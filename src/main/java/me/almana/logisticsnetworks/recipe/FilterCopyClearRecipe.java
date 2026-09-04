package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.component.FilterComponentData;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class FilterCopyClearRecipe extends CustomRecipe {

    public FilterCopyClearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !buildResult(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return buildResult(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.FILTER_COPY_CLEAR_RECIPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    private static ItemStack buildResult(CraftingInput input, HolderLookup.Provider provider) {
        Item targetItem = null;
        ItemStack configuredSource = ItemStack.EMPTY;
        int configuredCount = 0;
        int filterCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (!stack.is(ModTags.FILTERS)) {
                return ItemStack.EMPTY;
            }

            Item item = stack.getItem();
            if (targetItem == null) {
                targetItem = item;
            } else if (targetItem != item) {
                return ItemStack.EMPTY;
            }

            filterCount++;
            ItemStack migrated = stack.copy();
            if (FilterComponentData.isConfigured(migrated, provider)) {
                configuredCount++;
                if (configuredSource.isEmpty()) {
                    configuredSource = migrated;
                }
            }
        }

        if (targetItem == null) {
            return ItemStack.EMPTY;
        }

        if (filterCount == 1) {
            return new ItemStack(targetItem);
        }

        if (configuredCount == 1) {
            return configuredSource.copyWithCount(filterCount);
        }

        return ItemStack.EMPTY;
    }
}
