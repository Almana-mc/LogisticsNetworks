package me.almana.logisticsnetworks.data;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public sealed interface FlowResource permits FlowResource.Item, FlowResource.Fluid, FlowResource.Chemical {

    record Item(ItemStack stack) implements FlowResource {
        public Item {
            stack = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Item item && ItemStack.isSameItemSameComponents(stack, item.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }

    record Fluid(FluidStack stack) implements FlowResource {
        public Fluid {
            stack = stack.copyWithAmount(1);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Fluid fluid && FluidStack.isSameFluidSameComponents(stack, fluid.stack);
        }

        @Override
        public int hashCode() {
            return FluidStack.hashFluidAndComponents(stack);
        }
    }

    record Chemical(String id) implements FlowResource {
    }
}
