package me.almana.logisticsnetworks.recipe;

import me.almana.logisticsnetworks.integration.guideme.GuideMeCompat;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class GuideRecipe extends CustomRecipe {

    public GuideRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        boolean paper = false;
        boolean wrench = false;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.PAPER) && !paper) paper = true;
            else if (stack.getItem() instanceof WrenchItem && !wrench) wrench = true;
            else return false;
        }
        return paper && wrench;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return GuideMeCompat.createGuideItem();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return GuideMeCompat.createGuideItem();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < input.getContainerSize(); i++) {
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
