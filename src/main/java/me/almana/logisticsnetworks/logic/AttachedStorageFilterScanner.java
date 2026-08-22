package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.HolderLookup;
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
        if (!level.isLoaded(node.getAttachedPos())) {
            return new Result(0, false, false);
        }

        TransferCapabilityCache capabilities = NetworkRegistry.get(level).getCapabilityCache();
        FilterTargetType target = FilterTargetType.forChannel(channel.getType());
        if (target == null) {
            return new Result(0, false, false);
        }

        return switch (target) {
            case ITEMS -> scanItems(capabilities.findItemHandler(level, node.getAttachedPos(), channel.getIoDirection()),
                    filter, level.registryAccess());
            case FLUIDS -> scanFluids(
                    capabilities.findFluidHandler(level, node.getAttachedPos(), channel.getIoDirection()), filter);
            case CHEMICALS -> scanChemicals(level, node, channel, capabilities, filter);
        };
    }

    static Result scanItems(IItemHandler handler, ItemStack filter, HolderLookup.Provider provider) {
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
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
        IChemicalHandler handler = capabilities.findChemicalHandler(level, node.getAttachedPos(), channel.getIoDirection());
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
