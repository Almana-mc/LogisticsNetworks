package me.almana.logisticsnetworks.integration.jei;

import me.almana.logisticsnetworks.client.screen.RecipeCardScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeCardGhostHandler implements IGhostIngredientHandler<RecipeCardScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(RecipeCardScreen screen, ITypedIngredient<I> ingredient,
            boolean doStart) {
        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isEmpty() || item.get().isEmpty()) return List.of();
        ItemStack stack = item.get();
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
                    screen.dropIngredient(index, stack);
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
