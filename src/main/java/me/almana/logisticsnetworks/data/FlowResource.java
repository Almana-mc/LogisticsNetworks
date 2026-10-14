package me.almana.logisticsnetworks.data;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

public sealed interface FlowResource permits FlowResource.Item, FlowResource.Fluid, FlowResource.Chemical {

    record Item(ItemStack stack) implements FlowResource {
        public Item {
            stack = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Item item && ItemStack.isSameItemSameTags(stack, item.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stack.getItem(), stack.getTag());
        }
    }

    record Fluid(FluidStack stack) implements FlowResource {
        public Fluid {
            stack = new FluidStack(stack, 1);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Fluid fluid && stack.isFluidEqual(fluid.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stack.getFluid(), stack.getTag());
        }
    }

    record Chemical(String id) implements FlowResource {
    }
}
