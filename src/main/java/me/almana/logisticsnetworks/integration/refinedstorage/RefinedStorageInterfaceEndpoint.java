package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.iface.InterfaceExportState;
import com.refinedmods.refinedstorage.api.network.impl.node.iface.InterfaceNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNodeActor;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RefinedStorageInterfaceEndpoint implements StorageEndpoint {

    private final InterfaceNetworkNode node;
    private final Network network;
    private final NetworkNodeActor actor;

    RefinedStorageInterfaceEndpoint(InterfaceNetworkNode node, Network network) {
        this.node = node;
        this.network = network;
        this.actor = new NetworkNodeActor(node);
    }

    @Override
    public Object networkIdentity() {
        return network;
    }

    @Override
    public boolean isValid() {
        return node.isActive() && node.getNetwork() == network;
    }

    @Override
    public List<StoredItem> exportableItems() {
        return storedItems(exportableResources());
    }

    @Override
    public List<StoredItem> storedItems() {
        Set<ResourceKey> resources = new LinkedHashSet<>();
        storage().getAll().forEach(entry -> resources.add(entry.resource()));
        return storedItems(resources);
    }

    private List<StoredItem> storedItems(Set<ResourceKey> resources) {
        List<StoredItem> items = new ArrayList<>();
        for (ResourceKey key : resources) {
            if (key instanceof ItemResource resource) {
                items.add(new StoredItem(resource.toItemStack(), storage().get(resource)));
            }
        }
        items.sort(Comparator.comparing((StoredItem item) ->
                        BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString())
                .thenComparing(item -> item.stack().getComponentsPatch().toString()));
        return items;
    }

    @Override
    public List<StoredFluid> exportableFluids() {
        return storedFluids(exportableResources());
    }

    @Override
    public List<StoredFluid> storedFluids() {
        Set<ResourceKey> resources = new LinkedHashSet<>();
        storage().getAll().forEach(entry -> resources.add(entry.resource()));
        return storedFluids(resources);
    }

    private List<StoredFluid> storedFluids(Set<ResourceKey> resources) {
        List<StoredFluid> fluids = new ArrayList<>();
        for (ResourceKey key : resources) {
            if (key instanceof FluidResource resource) {
                FluidStack stack = new FluidStack(
                        BuiltInRegistries.FLUID.wrapAsHolder(resource.fluid()), 1, resource.components());
                fluids.add(new StoredFluid(stack, storage().get(resource)));
            }
        }
        fluids.sort(Comparator.comparing((StoredFluid fluid) ->
                        BuiltInRegistries.FLUID.getKey(fluid.stack().getFluid()).toString())
                .thenComparing(fluid -> fluid.stack().getComponentsPatch().toString()));
        return fluids;
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

    private Set<ResourceKey> exportableResources() {
        Set<ResourceKey> resources = new LinkedHashSet<>();
        InterfaceExportState state = node.getExportState();
        if (state == null || !isValid()) return resources;
        for (int slot = 0; slot < state.getSlots(); slot++) {
            ResourceKey requested = state.getRequestedResource(slot);
            if (requested == null) continue;
            for (ResourceKey candidate : state.expandExportCandidates(storage(), requested)) {
                if (storage().get(candidate) > 0) resources.add(candidate);
            }
        }
        return resources;
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

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.EXECUTE;
    }
}
