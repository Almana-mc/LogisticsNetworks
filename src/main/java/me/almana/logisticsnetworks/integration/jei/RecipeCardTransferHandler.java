package me.almana.logisticsnetworks.integration.jei;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.menu.RecipeCardMenu;
import me.almana.logisticsnetworks.registration.Registration;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeCardTransferHandler implements IUniversalRecipeTransferHandler<RecipeCardMenu> {

    @Override
    public Class<? extends RecipeCardMenu> getContainerClass() {
        return RecipeCardMenu.class;
    }

    @Override
    public Optional<MenuType<RecipeCardMenu>> getMenuType() {
        return Optional.of(Registration.RECIPE_CARD_MENU.get());
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(RecipeCardMenu menu, Object recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) return null;
        List<RecipeCardData.Ingredient> ingredients = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            ingredient(slot).ifPresent(ingredients::add);
        }
        if (ingredients.isEmpty()) return null;
        Optional<StackSnapshot> output = slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> firstItem(slot).stream())
                .findFirst().map(StackSnapshot::of);
        menu.edit(data -> new RecipeCardData(ingredients, output, data.blocking()));
        return null;
    }

    private static Optional<RecipeCardData.Ingredient> ingredient(IRecipeSlotView slot) {
        Optional<ItemStack> item = firstItem(slot);
        if (item.isPresent()) return item.map(stack -> itemIngredient(slot, stack));
        Optional<ITypedIngredient<?>> shown = slot.getDisplayedIngredients().findFirst();
        if (shown.isEmpty()) return Optional.empty();
        Optional<FluidStack> fluid = shown.get().getIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluid.isPresent()) return fluid.filter(stack -> !stack.isEmpty()).map(RecipeCardData.Ingredient::ofFluid);
        return MekanismCompat.recipeIngredient(shown.get().getIngredient());
    }

    // Stable pick, ignores cycling
    private static Optional<ItemStack> firstItem(IRecipeSlotView slot) {
        return slot.getDisplayedIngredients().map(ITypedIngredient::getItemStack)
                .flatMap(Optional::stream).findFirst();
    }

    private static RecipeCardData.Ingredient itemIngredient(IRecipeSlotView slot, ItemStack shown) {
        Optional<TagKey<?>> tag = slot.getTagKey();
        if (tag.isPresent() && tag.get().isFor(Registries.ITEM) && slot.getItemStacks().limit(2).count() > 1) {
            return RecipeCardData.Ingredient.ofTag(tag.get().location(), shown.getCount());
        }
        return RecipeCardData.Ingredient.ofItem(shown, shown.getCount());
    }
}
