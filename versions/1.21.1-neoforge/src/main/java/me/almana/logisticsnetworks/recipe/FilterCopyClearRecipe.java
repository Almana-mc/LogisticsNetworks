package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.Set;

public class FilterCopyClearRecipe extends CustomRecipe {

    private static final Set<String> FILTER_ROOT_KEYS = Set.of(
            "ln_filter",
            "ln_tag_filter",
            "ln_mod_filter",
            "ln_amount_filter",
            "ln_durability_filter",
            "ln_nbt_filter");

    public FilterCopyClearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !buildResult(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return buildResult(input);
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

    private static ItemStack buildResult(CraftingInput input) {
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
            if (isConfiguredFilter(stack)) {
                configuredCount++;
                if (configuredSource.isEmpty()) {
                    configuredSource = stack;
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

    private static boolean isConfiguredFilter(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            return false;
        }

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (String rootKey : FILTER_ROOT_KEYS) {
            if (custom.contains(rootKey, Tag.TAG_COMPOUND) && !custom.getCompound(rootKey).isEmpty()) {
                return true;
            }
        }

        return false;
    }
}
