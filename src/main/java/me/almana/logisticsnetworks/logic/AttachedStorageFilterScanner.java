package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import net.minecraft.server.level.ServerLevel;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class AttachedStorageFilterScanner {

    public record Result(int added, boolean storageFound, boolean filterFull) {
    }

    private AttachedStorageFilterScanner() {
    }

    public static Result scan(ServerLevel level, LogisticsNodeEntity node, ChannelData channel, ItemStack filter) {
        // Enable when 26.1.2 is supported.
        /*
        if (node.isMountedOnCreate()) {
            return CreateCompat.isResolved(node)
                    ? scanMountedStorage(level, node, channel, filter)
                    : new Result(0, false, false);
        }
        */
        if (!level.isLoaded(node.getAttachedPos())) {
            return new Result(0, false, false);
        }

        Result total = new Result(0, false, false);
        int types = NodeUpgradeData.effectiveTypes(node, channel);
        if (ChannelType.ITEM.in(types)) {
            total = merge(total, scanItems(node.capabilities().findItemHandler(channel.getIoSide()), filter));
        }
        if (ChannelType.FLUID.in(types) && !total.filterFull()) {
            total = merge(total, scanFluids(node.capabilities().findFluidHandler(channel.getIoSide()), filter));
        }
        // Enable when 26.1.2 is supported.
        /*
        if (ChannelType.CHEMICAL.in(types) && !total.filterFull() && !node.isMountedOnCreate()) {
            total = merge(total, scanChemicals(level, node, channel, filter));
        }
        */
        return total;
    }

    private static Result merge(Result first, Result second) {
        return new Result(first.added() + second.added(), first.storageFound() || second.storageFound(),
                second.filterFull());
    }

    static Result scanItems(ResourceHandler<ItemResource> handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            ItemStack stack = resource.toStack();
            if (RecipeCardItem.is(stack) || FilterItemData.containsItem(filter, stack)) {
                continue;
            }
            if (FilterItemData.addItem(filter, stack)) {
                added++;
            } else if (!FilterItemData.hasAvailableEntrySlot(filter)) {
                return new Result(added, true, true);
            }
        }
        return new Result(added, true, false);
    }

    static Result scanFluids(ResourceHandler<FluidResource> handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int tank = 0; tank < handler.size(); tank++) {
            FluidResource resource = handler.getResource(tank);
            if (resource.isEmpty()) {
                continue;
            }
            FluidStack fluid = resource.toStack(1_000);
            if (FilterItemData.containsFluid(filter, fluid)) {
                continue;
            }
            if (FilterItemData.addFluid(filter, fluid)) {
                added++;
            } else if (!FilterItemData.hasAvailableEntrySlot(filter)) {
                return new Result(added, true, true);
            }
        }
        return new Result(added, true, false);
    }
}
