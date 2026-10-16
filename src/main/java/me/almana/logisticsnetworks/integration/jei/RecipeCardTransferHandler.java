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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
            firstItem(slot).ifPresent(stack -> ingredients.add(ingredient(slot, stack)));
        }
        if (ingredients.isEmpty()) return null;
        Optional<StackSnapshot> output = slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> firstItem(slot).stream())
                .findFirst().map(StackSnapshot::of);
        menu.edit(data -> new RecipeCardData(ingredients, output, data.blocking()));
        return null;
    }

    // Stable pick, ignores cycling
    private static Optional<ItemStack> firstItem(IRecipeSlotView slot) {
        return slot.getItemStacks().findFirst();
    }

    private static RecipeCardData.Ingredient ingredient(IRecipeSlotView slot, ItemStack shown) {
        Set<Item> items = slot.getItemStacks().map(ItemStack::getItem).collect(Collectors.toSet());
        if (items.size() > 1) {
            Optional<TagKey<Item>> tag = shown.getTags().filter(key -> holdsExactly(key, items))
                    .min(Comparator.comparing(key -> key.location().toString()));
            if (tag.isPresent()) return RecipeCardData.Ingredient.ofTag(tag.get().location(), shown.getCount());
        }
        return RecipeCardData.Ingredient.ofItem(shown, shown.getCount());
    }

    // JEI 15 hides slot tags
    private static boolean holdsExactly(TagKey<Item> key, Set<Item> items) {
        return BuiltInRegistries.ITEM.getTag(key).map(set -> set.size() == items.size()
                && set.stream().allMatch(holder -> items.contains(holder.value()))).orElse(false);
    }
}
