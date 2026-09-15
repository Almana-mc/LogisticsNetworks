package me.almana.logisticsnetworks.integration.emi;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

final class FilterDragDrop implements EmiDragDropHandler<FilterScreen> {
    @Override
    public boolean dropStack(FilterScreen screen, EmiIngredient ingredient, int x, int y) {
        for (Target target : targets(screen, ingredient)) {
            if (target.area().contains(x, y)) {
                target.accept().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(FilterScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                       int mouseX, int mouseY, float delta) {
        for (Target target : targets(screen, ingredient)) {
            Rect2i area = target.area();
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(),
                    area.getY() + area.getHeight(), 0x8822BB33);
        }
    }

    private List<Target> targets(FilterScreen screen, EmiIngredient ingredient) {
        if (ingredient.getEmiStacks().size() != 1) return List.of();
        EmiStack stack = ingredient.getEmiStacks().getFirst();
        ItemStack item = stack.getItemStack();
        if (stack.getKey() instanceof Fluid fluid) {
            FluidStack value = new FluidStack(fluid, 1000);
            value.applyComponents(stack.getComponentChanges());
            if (screen.acceptsFluidSelectorGhostIngredient()) {
                return List.of(new Target(screen.getSelectorGhostArea(), () -> screen.setSelectorGhostFluid(value)));
            }
            return slotTargets(screen, slot -> screen.setGhostFluidFilterEntry(slot, value));
        }
        if (item.isEmpty()) return List.of();
        if (screen.isDetailPageOpen()) {
            Rect2i area = screen.getDetailSlotArea();
            return area == null ? List.of() : List.of(new Target(area, () -> screen.setDetailGhostItem(item)));
        }
        if (screen.acceptsItemSelectorGhostIngredient()) {
            return List.of(new Target(screen.getSelectorGhostArea(), () -> screen.setSelectorGhostItem(item)));
        }
        return slotTargets(screen, slot -> screen.setGhostItemFilterEntry(slot, item));
    }

    private List<Target> slotTargets(FilterScreen screen, java.util.function.IntConsumer accept) {
        if (!screen.supportsGhostIngredientTargets()) return List.of();
        List<Target> targets = new ArrayList<>();
        for (int slot = 0; slot < screen.getGhostFilterSlotCount(); slot++) {
            int index = slot;
            targets.add(new Target(screen.getGhostFilterSlotArea(slot), () -> accept.accept(index)));
        }
        return targets;
    }

    private record Target(Rect2i area, Runnable accept) {
    }
}
