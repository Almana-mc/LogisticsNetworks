package me.almana.logisticsnetworks.integration.jei;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.menu.RecipeCardMenu;
import me.almana.logisticsnetworks.registration.Registration;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
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
            slot.getDisplayedItemStack().ifPresent(stack -> ingredients.add(ingredient(slot, stack)));
        }
        Optional<StackSnapshot> output = slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getDisplayedItemStack().stream())
                .findFirst().map(StackSnapshot::of);
        menu.edit(data -> new RecipeCardData(ingredients, output, data.blocking()));
        return null;
    }

    private static RecipeCardData.Ingredient ingredient(IRecipeSlotView slot, ItemStack shown) {
        Optional<TagKey<?>> tag = slot.getTagKey();
        if (tag.isPresent() && tag.get().isFor(Registries.ITEM)) {
            return RecipeCardData.Ingredient.ofTag(tag.get().location(), shown.getCount());
        }
        return RecipeCardData.Ingredient.ofItem(shown, shown.getCount());
    }
}
