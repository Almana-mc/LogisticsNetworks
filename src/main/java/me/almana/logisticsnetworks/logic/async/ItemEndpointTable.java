package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ItemEndpointTable {

    private static final int ALL_SIDES = Direction.values().length;

    private final Map<EndpointKey, Integer> indexes = new HashMap<>();
    private final IdentityHashMap<ResourceHandler<ItemResource>, Integer> handlerIndexes = new IdentityHashMap<>();
    private final List<NetworkSnapshot.ItemEndpoint> endpoints = new ArrayList<>();
    private final Map<Object, Integer> networks = new IdentityHashMap<>();
    private final List<StorageEndpoint> networkEndpoints = new ArrayList<>();
    private final List<Set<Item>> requestedCounts = new ArrayList<>();
    private final List<DirectStorageBinding> bindings = new ArrayList<>();

    int capture(LogisticsNodeEntity node, @Nullable Direction direction, ResourceHandler<ItemResource> handler,
            Snapshots.OccupiedSlotBudget budget) {
        return capture(node, direction, handler, budget, false);
    }

    int capture(LogisticsNodeEntity node, @Nullable Direction direction, ResourceHandler<ItemResource> handler,
            Snapshots.OccupiedSlotBudget budget, boolean bulk) {
        ThreadGuard.requireServerThread();
        bulk |= BuiltInRegistries.BLOCK.getKey(node.level().getBlockState(node.getAttachedPos()).getBlock())
                .getNamespace().equals("functionalstorage");
        EndpointKey key = EndpointKey.of(node, direction, handler);
        Integer existing = indexes.get(key);
        if (existing == null) existing = handlerIndexes.get(handler);
        if (existing != null) {
            indexes.put(key, existing);
            handlerIndexes.put(handler, existing);
            NetworkSnapshot.ItemEndpoint endpoint = endpoints.get(existing);
            if (bulk && endpoint.direct() == null && endpoint.bulkSlotLimits() == null) {
                endpoints.set(existing, withBulkSlotLimits(endpoint, handler));
            }
            return existing;
        }

        int index = endpoints.size();
        NetworkSnapshot.ItemEndpoint captured = capture(handler, budget, bulk);
        if (captured.direct() != null) {
            StorageEndpoint storage = DirectStorageHandlers.endpoint(handler);
            int network = networks.computeIfAbsent(storage.networkIdentity(), ignored -> {
                networkEndpoints.add(storage);
                requestedCounts.add(new HashSet<>());
                return networkEndpoints.size() - 1;
            });
            int binding = bindings.size();
            bindings.add(new DirectStorageBinding(storage));
            NetworkSnapshot.DirectEndpoint direct = captured.direct();
            captured = directEndpoint(new NetworkSnapshot.DirectEndpoint(
                    network, binding, direct.exporting(), direct.exports(), Map.of()));
        }
        endpoints.add(captured);
        indexes.put(key, index);
        handlerIndexes.put(handler, index);
        return index;
    }

    List<NetworkSnapshot.ItemEndpoint> endpoints() {
        List<Map<Item, Long>> counts = new ArrayList<>(networkEndpoints.size());
        for (int index = 0; index < networkEndpoints.size(); index++) {
            Set<Item> requested = requestedCounts.get(index);
            counts.add(requested.isEmpty() ? Map.of()
                    : Map.copyOf(networkEndpoints.get(index).itemCounts(requested, false)));
        }
        for (int index = 0; index < endpoints.size(); index++) {
            NetworkSnapshot.DirectEndpoint direct = endpoints.get(index).direct();
            if (direct != null) {
                endpoints.set(index, directEndpoint(new NetworkSnapshot.DirectEndpoint(
                        direct.network(), direct.binding(), direct.exporting(), direct.exports(),
                        counts.get(direct.network()))));
            }
        }
        return List.copyOf(endpoints);
    }

    List<DirectStorageBinding> bindings() {
        return List.copyOf(bindings);
    }

    void requireCounts(int target, int source) {
        NetworkSnapshot.DirectEndpoint destination = endpoints.get(target).direct();
        if (destination == null) return;
        NetworkSnapshot.ItemEndpoint origin = endpoints.get(source);
        Set<Item> requested = requestedCounts.get(destination.network());
        if (origin.direct() != null) {
            for (StorageEndpoint.StoredItem stored : origin.direct().exports()) {
                requested.add(stored.stack().getItem());
            }
        } else {
            for (ItemStack stack : origin.occupiedStacks()) requested.add(stack.getItem());
        }
    }

    static NetworkSnapshot.ItemEndpoint capture(ResourceHandler<ItemResource> handler,
            @Nullable Snapshots.OccupiedSlotBudget budget) {
        return capture(handler, budget, false);
    }

    static NetworkSnapshot.ItemEndpoint capture(ResourceHandler<ItemResource> handler,
            @Nullable Snapshots.OccupiedSlotBudget budget, boolean bulk) {
        ThreadGuard.requireServerThread();
        int view = DirectStorageHandlers.snapshotView(handler);
        if (view != 0) {
            List<StorageEndpoint.StoredItem> exports = new ArrayList<>();
            for (StorageEndpoint.StoredItem stored : DirectStorageHandlers.exports(handler)) {
                if (budget != null) budget.retain();
                exports.add(new StorageEndpoint.StoredItem(stored.stack().copyWithCount(1), stored.amount()));
            }
            return directEndpoint(new NetworkSnapshot.DirectEndpoint(
                    0, 0, view == 1, List.copyOf(exports), Map.of()));
        }

        int slots = handler.size();
        List<Integer> occupied = new ArrayList<>();
        List<ItemStack> copies = new ArrayList<>();
        List<Integer> limits = new ArrayList<>();
        int[] bulkSlotLimits = bulk ? new int[slots] : null;

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = ItemUtil.getStack(handler, slot);
            int slotLimit = bulk ? handler.getCapacityAsInt(slot, handler.getResource(slot)) : 0;
            if (bulk) bulkSlotLimits[slot] = slotLimit;
            if (stack.isEmpty()) continue;
            if (budget != null) budget.retain();
            occupied.add(slot);
            copies.add(stack.copy());
            limits.add(bulk ? slotLimit : handler.getCapacityAsInt(slot, handler.getResource(slot)));
        }

        int[] occupiedSlots = new int[occupied.size()];
        int[] occupiedLimits = new int[occupied.size()];
        for (int index = 0; index < occupied.size(); index++) {
            occupiedSlots[index] = occupied.get(index);
            occupiedLimits[index] = limits.get(index);
        }

        int defaultLimit = slots > 0
                ? handler.getCapacityAsInt(firstEmptySlot(handler, slots), ItemResource.EMPTY) : 64;
        return new NetworkSnapshot.ItemEndpoint(slots, occupiedSlots, copies.toArray(ItemStack[]::new),
                defaultLimit, occupiedLimits, bulkSlotLimits);
    }

    private static NetworkSnapshot.ItemEndpoint directEndpoint(NetworkSnapshot.DirectEndpoint direct) {
        return new NetworkSnapshot.ItemEndpoint(0, new int[0], new ItemStack[0], 0, new int[0], null, direct);
    }

    private static NetworkSnapshot.ItemEndpoint withBulkSlotLimits(
            NetworkSnapshot.ItemEndpoint endpoint, ResourceHandler<ItemResource> handler) {
        ThreadGuard.requireServerThread();
        int[] bulkSlotLimits = new int[handler.size()];
        for (int slot = 0; slot < bulkSlotLimits.length; slot++) {
            bulkSlotLimits[slot] = handler.getCapacityAsInt(slot, handler.getResource(slot));
        }
        return new NetworkSnapshot.ItemEndpoint(endpoint.totalSlots(), endpoint.occupiedSlots(),
                endpoint.occupiedStacks(), endpoint.defaultSlotLimit(), endpoint.occupiedSlotLimits(), bulkSlotLimits);
    }

    private static int firstEmptySlot(ResourceHandler<ItemResource> handler, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            if (ItemUtil.getStack(handler, slot).isEmpty()) return slot;
        }
        return 0;
    }

    private record EndpointKey(@Nullable ResourceKey<Level> dimension, long position, int direction, int view) {
        private static EndpointKey of(LogisticsNodeEntity node, @Nullable Direction direction,
                ResourceHandler<ItemResource> handler) {
            int side = direction == null ? ALL_SIDES : direction.ordinal();
            ServerLevel level = (ServerLevel) node.level();
            return new EndpointKey(level.dimension(), node.getAttachedPos().asLong(), side,
                    DirectStorageHandlers.snapshotView(handler));
        }
    }
}
