package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public final class AttachedStorageFilterScanner {
    public record Result(int added, boolean storageFound, boolean filterFull) {
    }

    private AttachedStorageFilterScanner() {
    }

    public static Result scan(ServerLevel level, LogisticsNodeEntity node, ChannelData channel, ItemStack filter) {
        BlockPos pos = node.getAttachedPos();
        FilterTargetType target = FilterTargetType.forChannel(channel.getType());
        if (!level.isLoaded(pos) || target == null) {
            return new Result(0, false, false);
        }

        TransferEngine.CapCache capabilities = new TransferEngine.CapCache();
        return switch (target) {
            case ITEMS -> scanItems(capabilities.findItemHandler(level, pos, channel.getIoDirection()), filter);
            case FLUIDS -> scanFluids(capabilities.findFluidHandler(level, pos, channel.getIoDirection()), filter);
            case CHEMICALS -> MekanismCompat.isLoaded()
                    ? scanChemicals(capabilities.findChemicalHandler(level, pos, channel.getIoDirection()), filter)
                    : new Result(0, false, false);
        };
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
            if (FilterItemData.containsItem(filter, stack)) {
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Result scanChemicals(IChemicalHandler handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false);
        }
        int added = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
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
