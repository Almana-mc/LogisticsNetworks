package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class RecipeCardCopyClearRecipe extends CustomRecipe {

    public RecipeCardCopyClearRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return !assemble(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ItemStack encoded = ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (!RecipeCardItem.is(stack)) return ItemStack.EMPTY;
            if (!RecipeCardData.has(stack)) {
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
