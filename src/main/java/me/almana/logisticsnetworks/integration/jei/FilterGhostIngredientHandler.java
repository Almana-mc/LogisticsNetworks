package me.almana.logisticsnetworks.integration.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class FilterGhostIngredientHandler implements IGhostIngredientHandler<FilterScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(FilterScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<FluidStack> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluid.isPresent()) {
            if (screen.acceptsFluidSelectorGhostIngredient()) {
                return castTargets(buildSelectorFluidTarget(screen, fluid.get()));
            }
            if (!screen.supportsGhostIngredientTargets()) {
                return List.of();
            }
            return castTargets(buildFluidTargets(screen, fluid.get()));
        }

        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isPresent() && !item.get().isEmpty()) {
            if (screen.acceptsItemSelectorGhostIngredient()) {
                return castTargets(buildSelectorItemTarget(screen, item.get()));
            }
            if (!screen.supportsGhostIngredientTargets()) {
                return List.of();
            }
            return castTargets(buildItemTargets(screen, item.get()));
        }

        return List.of();
    }

    @Override
    public void onComplete() {
    }

    private List<Target<FluidStack>> buildFluidTargets(FilterScreen screen, FluidStack fluidStack) {
        int slotCount = screen.getGhostFilterSlotCount();
        if (slotCount <= 0 || fluidStack == null || fluidStack.isEmpty()) {
            return List.of();
        }

        List<Target<FluidStack>> targets = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            int slotIndex = slot;
            targets.add(new FilterTarget<>(screen.getGhostFilterSlotArea(slotIndex), ignored -> screen.setGhostFluidFilterEntry(slotIndex, fluidStack)));
        }
        return targets;
    }

    private List<Target<FluidStack>> buildSelectorFluidTarget(FilterScreen screen, FluidStack fluidStack) {
        if (!screen.acceptsFluidSelectorGhostIngredient() || fluidStack == null || fluidStack.isEmpty()) {
            return List.of();
        }
        return List.of(new FilterTarget<>(screen.getSelectorGhostArea(), ignored -> screen.setSelectorGhostFluid(fluidStack)));
    }

    private List<Target<ItemStack>> buildItemTargets(FilterScreen screen, ItemStack itemStack) {
        int slotCount = screen.getGhostFilterSlotCount();
        if (slotCount <= 0 || itemStack.isEmpty()) {
            return List.of();
        }

        List<Target<ItemStack>> targets = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            int slotIndex = slot;
            targets.add(new FilterTarget<>(screen.getGhostFilterSlotArea(slotIndex), ignored -> screen.setGhostItemFilterEntry(slotIndex, itemStack)));
        }
        return targets;
    }

    private List<Target<ItemStack>> buildSelectorItemTarget(FilterScreen screen, ItemStack itemStack) {
        if (!screen.acceptsItemSelectorGhostIngredient() || itemStack.isEmpty()) {
            return List.of();
        }
        return List.of(new FilterTarget<>(screen.getSelectorGhostArea(), ignored -> screen.setSelectorGhostItem(itemStack)));
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> castTargets(List<? extends Target<?>> targets) {
        return (List<Target<I>>) (List<?>) targets;
    }

    private record FilterTarget<I>(Rect2i area, Consumer<I> setter) implements Target<I> {
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
