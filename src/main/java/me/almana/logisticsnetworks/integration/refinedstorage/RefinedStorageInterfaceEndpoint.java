package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.iface.InterfaceExportState;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNodeActor;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import me.almana.logisticsnetworks.utils.HashUtils;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

final class RefinedStorageInterfaceEndpoint implements StorageEndpoint {

    private final AbstractNetworkNode node;
    private final Supplier<InterfaceExportState> exportState;
    private final Network network;
    private final NetworkNodeActor actor;

    RefinedStorageInterfaceEndpoint(AbstractNetworkNode node, Supplier<InterfaceExportState> exportState,
            Network network) {
        this.node = node;
        this.exportState = exportState;
        this.network = network;
        this.actor = new NetworkNodeActor(node);
    }

    @Override
    public Object networkIdentity() {
        return network;
    }

    @Override
    public Object endpointIdentity() {
        return node;
    }

    @Override
    public boolean isValid() {
        return node.isActive() && node.getNetwork() == network;
    }

    @Override
    public List<StoredItem> exportableItems(boolean fresh) {
        InterfaceExportState state = exportState.get();
        if (state == null || !isValid()) return List.of();
        StorageNetworkComponent storage = storage();
        List<StoredItem> items = new ArrayList<>();
        Set<ResourceKey> seen = new HashSet<>();
        for (int slot = 0; slot < state.getSlots(); slot++) {
            ResourceKey requested = state.getRequestedResource(slot);
            if (requested == null) continue;
            for (ResourceKey candidate : state.expandExportCandidates(storage, requested)) {
                if (!(candidate instanceof ItemResource resource)
                        || !state.isExportedResourceValid(requested, candidate)
                        || !seen.add(candidate)) continue;
                long amount = storage.get(resource);
                if (amount > 0) items.add(new StoredItem(resource.toItemStack(), amount));
            }
        }
        return items;
    }

    @Override
    public Object2LongMap<Item> itemCounts(Set<Item> items, boolean fresh) {
        if (items.isEmpty() || !isValid()) {
            return HashUtils.itemToLongMap();
        }
        StorageNetworkComponent storage = storage();
        var counts = HashUtils.itemToLongMap();
        for (Item item : items) counts.put(item, 0L);
        for (ResourceAmount entry : storage.getAll()) {
            if (entry.resource() instanceof ItemResource resource && items.contains(resource.item())) {
                counts.merge(resource.item(), entry.amount(), RefinedStorageInterfaceEndpoint::saturatingAdd);
            }
        }
        return counts;
    }

    @Override
    public List<StoredFluid> exportableFluids(boolean fresh) {
        InterfaceExportState state = exportState.get();
        if (state == null || !isValid()) return List.of();
        StorageNetworkComponent storage = storage();
        List<StoredFluid> fluids = new ArrayList<>();
        Set<ResourceKey> seen = new HashSet<>();
        for (int slot = 0; slot < state.getSlots(); slot++) {
            ResourceKey requested = state.getRequestedResource(slot);
            if (requested == null) continue;
            for (ResourceKey candidate : state.expandExportCandidates(storage, requested)) {
                if (!(candidate instanceof FluidResource resource)
                        || !state.isExportedResourceValid(requested, candidate)
                        || !seen.add(candidate)) continue;
                long amount = storage.get(resource);
                if (amount > 0) {
                    FluidStack stack = new FluidStack(
                            BuiltInRegistries.FLUID.wrapAsHolder(resource.fluid()), 1, resource.components());
                    fluids.add(new StoredFluid(stack, amount));
                }
            }
        }
        return fluids;
    }

    @Override
    public long fluidCount(FluidStack stack, boolean fresh) {
        if (stack.isEmpty() || !isValid()) return 0;
        return Math.max(0, storage().get(new FluidResource(stack.getFluid(), stack.getComponentsPatch())));
    }

    @Override
    public boolean canExportItem(ItemStack stack) {
        return !stack.isEmpty() && canExport(ItemResource.ofItemStack(stack));
    }

    @Override
    public boolean canExportFluid(FluidStack stack) {
        return !stack.isEmpty() && canExport(new FluidResource(stack.getFluid(), stack.getComponentsPatch()));
    }

    @Override
    public long insertItem(ItemStack stack, long amount, boolean simulate) {
        return insert(ItemResource.ofItemStack(stack), amount, simulate);
    }

    @Override
    public long extractItem(ItemStack stack, long amount, boolean simulate) {
        return extract(ItemResource.ofItemStack(stack), amount, simulate);
    }

    @Override
    public long insertFluid(FluidStack stack, long amount, boolean simulate) {
        return insert(new FluidResource(stack.getFluid(), stack.getComponentsPatch()), amount, simulate);
    }

    @Override
    public long extractFluid(FluidStack stack, long amount, boolean simulate) {
        return extract(new FluidResource(stack.getFluid(), stack.getComponentsPatch()), amount, simulate);
    }

    @Override
    public boolean storesChemicals() {
        return RefinedStorageChemicals.isLoaded();
    }

    @Override
    public List<ChemicalStack> exportableChemicals() {
        InterfaceExportState state = exportState.get();
        if (state == null || !isValid()) return List.of();
        StorageNetworkComponent storage = storage();
        List<ChemicalStack> chemicals = new ArrayList<>();
        Set<ResourceKey> seen = new HashSet<>();
        for (int slot = 0; slot < state.getSlots(); slot++) {
            ResourceKey requested = state.getRequestedResource(slot);
            if (requested == null) continue;
            for (ResourceKey candidate : state.expandExportCandidates(storage, requested)) {
                if (!state.isExportedResourceValid(requested, candidate) || !seen.add(candidate)) continue;
                ChemicalStack stack = RefinedStorageChemicals.stack(candidate, storage.get(candidate));
                if (stack != null) chemicals.add(stack);
            }
        }
        return chemicals;
    }

    @Override
    public long chemicalCount(ChemicalStack stack) {
        ResourceKey resource = RefinedStorageChemicals.resource(stack);
        return resource == null || !isValid() ? 0 : Math.max(0, storage().get(resource));
    }

    @Override
    public long insertChemical(ChemicalStack stack, long amount, boolean simulate) {
        ResourceKey resource = RefinedStorageChemicals.resource(stack);
        return resource == null ? 0 : insert(resource, amount, simulate);
    }

    @Override
    public long extractChemical(ChemicalStack stack, long amount, boolean simulate) {
        ResourceKey resource = RefinedStorageChemicals.resource(stack);
        return resource != null && canExport(resource) ? extract(resource, amount, simulate) : 0;
    }

    private boolean canExport(ResourceKey candidate) {
        InterfaceExportState state = exportState.get();
        if (state == null || !isValid()) return false;
        for (int slot = 0; slot < state.getSlots(); slot++) {
            ResourceKey requested = state.getRequestedResource(slot);
            if (requested != null && state.isExportedResourceValid(requested, candidate)) return true;
        }
        return false;
    }

    private long insert(ResourceKey resource, long amount, boolean simulate) {
        if (!isValid() || amount <= 0) return 0;
        return storage().insert(resource, amount, action(simulate), actor);
    }

    private long extract(ResourceKey resource, long amount, boolean simulate) {
        if (!isValid() || amount <= 0) return 0;
        return storage().extract(resource, amount, action(simulate), actor);
    }

    private StorageNetworkComponent storage() {
        return network.getComponent(StorageNetworkComponent.class);
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.EXECUTE;
    }
}
