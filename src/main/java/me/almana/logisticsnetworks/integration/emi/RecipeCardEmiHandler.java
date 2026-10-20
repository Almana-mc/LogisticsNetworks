package me.almana.logisticsnetworks.integration.emi;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.menu.RecipeCardMenu;
import mekanism.api.IMekanismAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RecipeCardEmiHandler implements EmiRecipeHandler<RecipeCardMenu> {

    private static final int CONTAINER_AMOUNT = 1000;

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<RecipeCardMenu> screen) {
        return new EmiPlayerInventory(List.of());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return true;
    }

    @Override
    public boolean alwaysDisplaySupport(EmiRecipe recipe) {
        return false;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<RecipeCardMenu> context) {
        return true;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<RecipeCardMenu> context) {
        List<RecipeCardData.Ingredient> ingredients = new ArrayList<>();
        for (EmiIngredient input : recipe.getInputs()) {
            RecipeCardData.Ingredient ingredient = ingredient(input);
            if (ingredient != null) ingredients.add(ingredient);
        }
        if (ingredients.isEmpty()) return false;
        Optional<StackSnapshot> output = recipe.getOutputs().stream().map(EmiStack::getItemStack)
                .filter(stack -> !stack.isEmpty()).findFirst().map(StackSnapshot::of);
        context.getScreenHandler().edit(data -> new RecipeCardData(ingredients, output, data.blocking()));
        return true;
    }

    @Nullable
    private static RecipeCardData.Ingredient ingredient(EmiIngredient input) {
        if (input instanceof TagEmiIngredient tag && tag.key.isFor(Registries.ITEM)) {
            return RecipeCardData.Ingredient.ofTag(tag.key.location(),
                    (int) Math.max(1, Math.min(RecipeCardData.MAX_COUNT, input.getAmount())));
        }
        for (EmiStack stack : input.getEmiStacks()) {
            RecipeCardData.Ingredient found = ingredientOf(stack, input.getAmount());
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    static RecipeCardData.Ingredient ingredientOf(EmiStack stack, long amount) {
        if (stack.getKey() instanceof Fluid fluid) {
            return RecipeCardData.Ingredient.ofFluid(new FluidStack(fluid,
                    (int) Math.min(RecipeCardData.MAX_AMOUNT, amount > 0 ? amount : CONTAINER_AMOUNT)));
        }
        ItemStack item = stack.getItemStack();
        if (!item.isEmpty()) {
            return RecipeCardData.Ingredient.ofItem(item, (int) Math.max(1, Math.min(RecipeCardData.MAX_COUNT, amount)));
        }
        return MekanismCompat.isLoaded() ? chemical(stack, amount) : null;
    }

    @Nullable
    private static RecipeCardData.Ingredient chemical(EmiStack stack, long amount) {
        return IMekanismAccess.INSTANCE.emiHelper().asChemicalStack(stack)
                .map(chemical -> chemical.copyWithAmount(amount > 0 ? amount : CONTAINER_AMOUNT))
                .flatMap(ChemicalTransferHelper::recipeIngredient)
                .orElse(null);
    }
}
