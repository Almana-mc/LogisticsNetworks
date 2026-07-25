package me.almana.logisticsnetworks.integration.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class NodeGhostIngredientHandler implements IGhostIngredientHandler<NodeScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(NodeScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isEmpty() || item.get().isEmpty()) {
            return List.of();
        }
        ItemStack stack = item.get();
        List<Target<ItemStack>> targets = new ArrayList<>();
        for (int i = 0; i < screen.getFilterSlotCount(); i++) {
            if (screen.isFilterSlotItemAddable(i)) {
                int slot = i;
                targets.add(new NodeTarget<>(screen.getFilterSlotArea(slot),
                        ignored -> screen.addItemToFilterSlot(slot, stack)));
            }
        }
        return castTargets(targets);
    }

    @Override
    public void onComplete() {
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> castTargets(List<? extends Target<?>> targets) {
        return (List<Target<I>>) (List<?>) targets;
    }

    private record NodeTarget<I>(Rect2i area, Consumer<I> setter) implements Target<I> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            setter.accept(ingredient);
        }
    }
}
