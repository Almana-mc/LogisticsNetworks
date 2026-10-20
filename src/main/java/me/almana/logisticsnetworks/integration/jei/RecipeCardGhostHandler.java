package me.almana.logisticsnetworks.integration.jei;

import me.almana.logisticsnetworks.client.screen.RecipeCardScreen;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeCardGhostHandler implements IGhostIngredientHandler<RecipeCardScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(RecipeCardScreen screen, ITypedIngredient<I> ingredient,
            boolean doStart) {
        Optional<RecipeCardData.Ingredient> value = value(ingredient);
        if (value.isEmpty()) return List.of();
        List<Target<I>> targets = new ArrayList<>();
        for (int i = 0; i < screen.droppableCells(); i++) {
            int index = i;
            Rect2i area = screen.cellArea(i);
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ignored) {
                    screen.dropResource(index, value.get());
                }
            });
        }
        return targets;
    }

    private static Optional<RecipeCardData.Ingredient> value(ITypedIngredient<?> ingredient) {
        Optional<ItemStack> item = ingredient.getItemStack().filter(stack -> !stack.isEmpty());
        if (item.isPresent()) return item.map(stack -> RecipeCardData.Ingredient.ofItem(stack, stack.getCount()));
        Optional<FluidStack> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK)
                .filter(stack -> !stack.isEmpty());
        if (fluid.isPresent()) return fluid.map(RecipeCardData.Ingredient::ofFluid);
        return MekanismCompat.recipeIngredient(ingredient.getIngredient());
    }

    @Override
    public void onComplete() {
    }
}
