package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class AttachedStorageFilterScanner {
    public record Result(int added, boolean storageFound, boolean filterFull) {
    }

    private AttachedStorageFilterScanner() {
    }

    public static Result scan(ServerLevel level, LogisticsNodeEntity node, ChannelData channel, ItemStack filter) {
        if (!node.isMountedOnCreate() && !level.isLoaded(node.getAttachedPos())) {
            return new Result(0, false, false);
        }
        if (!CreateCompat.isResolved(node)) {
            return new Result(0, false, false);
        }

        TransferCapabilityCache capabilities = NetworkRegistry.get(level).getCapabilityCache();
        Result total = new Result(0, false, false);
        if (channel.hasType(ChannelType.ITEM)) {
            total = merge(total, scanItems(capabilities.findItemHandler(node, channel.getIoSide()), filter));
        }
        if (channel.hasType(ChannelType.FLUID) && !total.filterFull()) {
            total = merge(total, scanFluids(capabilities.findFluidHandler(node, channel.getIoSide()), filter));
        }
        if (channel.hasType(ChannelType.CHEMICAL) && !total.filterFull() && !node.isMountedOnCreate()) {
            total = merge(total, scanChemicals(level, node, channel, capabilities, filter));
        }
        return total;
    }

    private static Result merge(Result first, Result second) {
        return new Result(first.added() + second.added(), first.storageFound() || second.storageFound(),
                second.filterFull());
    }

    static Result scanItems(IItemHandler handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
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

    private static Result scanFluids(IFluidHandler handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.isEmpty()) {
                continue;
            }
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

    private static Result scanChemicals(ServerLevel level, LogisticsNodeEntity node, ChannelData channel,
            TransferCapabilityCache capabilities, ItemStack filter) {
        if (!MekanismCompat.isLoaded()) {
            return new Result(0, false, false);
        }
        IChemicalHandler handler = capabilities.findChemicalHandler(level, node.getAttachedPos(), channel.getIoSide());
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            String chemicalId = ChemicalTransferHelper.getChemicalId(handler.getChemicalInTank(tank));
            if (chemicalId == null) {
                continue;
            }
            if (FilterItemData.containsChemical(filter, chemicalId)) {
                continue;
            }
            if (FilterItemData.addChemical(filter, chemicalId)) {
                added++;
            } else if (!FilterItemData.hasAvailableEntrySlot(filter)) {
                return new Result(added, true, true);
            }
        }
        return new Result(added, true, false);
    }
}
