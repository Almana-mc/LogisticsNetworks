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

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;

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

        if (MekanismCompat.isLoaded()) {
            List<Target<I>> chemTargets = getChemicalTargets(screen, ingredient);
            if (chemTargets != null && !chemTargets.isEmpty()) {
                return chemTargets;
            }
        }

        return List.of();
    }

    private <I> List<Target<I>> getChemicalTargets(FilterScreen screen, ITypedIngredient<I> ingredient) {
        Object underlying = ingredient.getIngredient();
        if (underlying instanceof ChemicalStack chemStack) {
            if (!chemStack.isEmpty()) {
                if (screen.acceptsItemSelectorGhostIngredient()) {
                    return castTargets(buildSelectorChemicalTarget(screen, chemStack));
                }
                if (!screen.supportsGhostIngredientTargets()) {
                    return List.of();
                }
                List<Target<ChemicalStack>> targets = buildChemicalTargets(screen, chemStack);
                return castTargets(targets);
            }
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
            targets.add(new FilterTarget<>(screen.getGhostFilterSlotArea(slotIndex),
                    ignored -> screen.setGhostFluidFilterEntry(slotIndex, fluidStack)));
        }
        return targets;
    }

    private List<Target<ChemicalStack>> buildChemicalTargets(FilterScreen screen, ChemicalStack chemStack) {
        int slotCount = screen.getGhostFilterSlotCount();
        if (slotCount <= 0 || chemStack == null || chemStack.isEmpty()) {
            return List.of();
        }

        List<Target<ChemicalStack>> targets = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            int slotIndex = slot;
            targets.add(new FilterTarget<>(screen.getGhostFilterSlotArea(slotIndex), ignored -> screen
                    .setGhostChemicalFilterEntry(slotIndex, chemStack.getChemical().getRegistryName().toString())));
        }
        return targets;
    }

    private List<Target<FluidStack>> buildSelectorFluidTarget(FilterScreen screen, FluidStack fluidStack) {
        if (!screen.acceptsFluidSelectorGhostIngredient() || fluidStack == null || fluidStack.isEmpty()) {
            return List.of();
        }
        return List.of(
                new FilterTarget<>(screen.getSelectorGhostArea(), ignored -> screen.setSelectorGhostFluid(fluidStack)));
    }

    private List<Target<ItemStack>> buildItemTargets(FilterScreen screen, ItemStack itemStack) {
        int slotCount = screen.getGhostFilterSlotCount();
        if (slotCount <= 0 || itemStack.isEmpty()) {
            return List.of();
        }

        List<Target<ItemStack>> targets = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            int slotIndex = slot;
            targets.add(new FilterTarget<>(screen.getGhostFilterSlotArea(slotIndex),
                    ignored -> screen.setGhostItemFilterEntry(slotIndex, itemStack)));
        }
        return targets;
    }

    private List<Target<ItemStack>> buildSelectorItemTarget(FilterScreen screen, ItemStack itemStack) {
        if (!screen.acceptsItemSelectorGhostIngredient() || itemStack.isEmpty()) {
            return List.of();
        }
        return List.of(
                new FilterTarget<>(screen.getSelectorGhostArea(), ignored -> screen.setSelectorGhostItem(itemStack)));
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> castTargets(List<? extends Target<?>> targets) {
        return (List<Target<I>>) (List<?>) targets;
    }

    private List<Target<ChemicalStack>> buildSelectorChemicalTarget(FilterScreen screen, ChemicalStack chemStack) {
        if (!screen.acceptsItemSelectorGhostIngredient() || chemStack == null || chemStack.isEmpty()) {
            return List.of();
        }
        String id = me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper.getChemicalId(chemStack);
        List<String> tags = chemStack.getTags().map(t -> t.location().toString()).toList();
        net.minecraft.network.chat.Component name = me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper
                .getChemicalTextComponent(id);
        return List.of(new FilterTarget<>(screen.getSelectorGhostArea(),
                ignored -> screen.setSelectorGhostChemical(id, tags, name)));
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
