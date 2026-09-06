package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
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

        FilterTargetType target = FilterTargetType.forChannel(channel.getType());
        if (target == null) {
            return new Result(0, false, false);
        }

        return switch (target) {
            case ITEMS -> scanItems(node.capabilities().findItemHandler(channel.getIoDirection()), filter,
                    level.registryAccess());
            case FLUIDS -> scanFluids(node.capabilities().findFluidHandler(channel.getIoDirection()), filter);
            case CHEMICALS -> {
                // Enable when 26.1.2 is supported.
                /*
                yield scanChemicals(level, node, channel, filter);
                */
                yield new Result(0, false, false);
            }
        };
    }

    static Result scanItems(ResourceHandler<ItemResource> handler, ItemStack filter,
            HolderLookup.Provider provider) {
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
            if (FilterItemData.containsItem(filter, stack, provider)) {
                continue;
            }
            if (FilterItemData.addItem(filter, stack, provider)) {
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
