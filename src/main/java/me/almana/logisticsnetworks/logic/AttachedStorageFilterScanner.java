package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AttachedStorageFilterScanner {
    public record Result(int added, boolean storageFound, boolean filterFull, boolean tooLarge) {
    }

    private AttachedStorageFilterScanner() {
    }

    public static Result scan(ServerLevel level, LogisticsNodeEntity node, ChannelData channel, ItemStack filter) {
        BlockPos pos = node.getAttachedPos();
        FilterTargetType target = FilterTargetType.forChannel(channel.getType());
        boolean mounted = node.isMountedOnCreate();
        if ((!mounted && !level.isLoaded(pos)) || !CreateCompat.isResolved(node) || target == null) {
            return new Result(0, false, false, false);
        }

        TransferEngine.CapCache capabilities = new TransferEngine.CapCache();
        return switch (target) {
            case ITEMS -> scanItems(mounted ? CreateCompat.findMountedItemHandler(node)
                    : capabilities.findItemHandler(level, pos, channel.getIoDirection()), filter);
            case FLUIDS -> scanFluids(mounted ? CreateCompat.findMountedFluidHandler(node)
                    : capabilities.findFluidHandler(level, pos, channel.getIoDirection()), filter);
            case CHEMICALS -> MekanismCompat.isLoaded() && !mounted
                    ? scanChemicals(capabilities.findChemicalHandler(level, pos, channel.getIoDirection()), filter)
                    : new Result(0, false, false, false);
        };
    }

    static Result scanItems(IItemHandler handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false, false);
        }
        Set<Item> seen = new HashSet<>();
        List<GeneralFilterEntry> candidates = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && !RecipeCardItem.is(stack) && FilterItemData.fitsEntry(stack)
                    && seen.add(stack.getItem())) {
                candidates.add(GeneralFilterEntry.empty(0).withItem(StackSnapshot.of(stack.copyWithCount(1))));
            }
        }
        return addAll(filter, candidates);
    }

    private static Result scanFluids(IFluidHandler handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false, false);
        }
        List<GeneralFilterEntry> candidates = new ArrayList<>();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (!fluid.isEmpty()) {
                candidates.add(resource(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(), null));
            }
        }
        return addAll(filter, candidates);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Result scanChemicals(IChemicalHandler handler, ItemStack filter) {
        if (handler == null) {
            return new Result(0, false, false, false);
        }
        List<GeneralFilterEntry> candidates = new ArrayList<>();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            String chemicalId = ChemicalTransferHelper.getChemicalId(handler.getChemicalInTank(tank));
            if (chemicalId != null) {
                candidates.add(resource(null, chemicalId));
            }
        }
        return addAll(filter, candidates);
    }

    private static GeneralFilterEntry resource(@Nullable String fluidId, @Nullable String chemicalId) {
        return new GeneralFilterEntry(0, null, fluidId, chemicalId, null, null, null, null);
    }

    private static Result addAll(ItemStack filter, List<GeneralFilterEntry> candidates) {
        FilterItemData.BulkAdd result = FilterItemData.addAll(filter, candidates);
        return new Result(result.added(), true, result.full(), result.tooLarge());
    }
}
