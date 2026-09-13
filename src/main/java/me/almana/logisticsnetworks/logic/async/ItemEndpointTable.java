package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ItemEndpointTable {

    private static final int ALL_SIDES = Direction.values().length;

    private final Map<EndpointKey, Integer> indexes = new HashMap<>();
    private final IdentityHashMap<IItemHandler, Map<Integer, Integer>> handlerIndexes =
            new IdentityHashMap<>();
    private final List<NetworkSnapshot.ItemEndpoint> endpoints = new ArrayList<>();
    private final Map<Object, Integer> networks = new IdentityHashMap<>();
    private final List<StorageEndpoint> networkEndpoints = new ArrayList<>();
    private final List<Set<Item>> requestedCounts = new ArrayList<>();
    private final List<DirectStorageBinding> bindings = new ArrayList<>();

    int capture(LogisticsNodeEntity node, @Nullable Direction direction, IItemHandler handler,
            Snapshots.OccupiedSlotBudget budget) {
        return capture(node, direction, handler, budget, false);
    }

    int capture(LogisticsNodeEntity node, @Nullable Direction direction, IItemHandler handler,
            Snapshots.OccupiedSlotBudget budget, boolean bulk) {
        EndpointKey key = EndpointKey.of(node, direction, handler);
        Integer existing = indexes.get(key);
        if (existing == null && !node.isMountedOnCreate()) {
            Map<Integer, Integer> sides = handlerIndexes.get(handler);
            existing = sides != null ? sides.get(key.direction()) : null;
        }
        if (existing != null) {
            indexes.put(key, existing);
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
            var direct = captured.direct();
            captured = directEndpoint(new NetworkSnapshot.DirectEndpoint(network, binding,
                    direct.exporting(), direct.exports(), Map.of()));
        }
        endpoints.add(captured);
        indexes.put(key, index);
        if (!node.isMountedOnCreate()) {
            handlerIndexes.computeIfAbsent(handler, ignored -> new HashMap<>())
                    .put(key.direction(), index);
        }
        return index;
    }

    List<NetworkSnapshot.ItemEndpoint> endpoints() {
        List<Map<Item, Long>> counts = new ArrayList<>();
        for (int i = 0; i < networkEndpoints.size(); i++) {
            counts.add(requestedCounts.get(i).isEmpty() ? Map.of()
                    : Map.copyOf(networkEndpoints.get(i).itemCounts(requestedCounts.get(i), false)));
        }
        for (int i = 0; i < endpoints.size(); i++) {
            var direct = endpoints.get(i).direct();
            if (direct != null) {
                endpoints.set(i, directEndpoint(new NetworkSnapshot.DirectEndpoint(direct.network(), direct.binding(),
                        direct.exporting(), direct.exports(), counts.get(direct.network()))));
            }
        }
        return List.copyOf(endpoints);
    }

    List<DirectStorageBinding> bindings() {
        return List.copyOf(bindings);
    }

    void requireCounts(int target, int source) {
        var destination = endpoints.get(target).direct();
        if (destination == null) return;
        var origin = endpoints.get(source);
        Set<Item> requested = requestedCounts.get(destination.network());
        if (origin.direct() != null) {
            for (var entry : origin.direct().exports()) requested.add(entry.stack().getItem());
        } else {
            for (ItemStack stack : origin.occupiedStacks()) requested.add(stack.getItem());
        }
    }

    private static NetworkSnapshot.ItemEndpoint directEndpoint(NetworkSnapshot.DirectEndpoint direct) {
        return new NetworkSnapshot.ItemEndpoint(0, new int[0], new ItemStack[0], 0, new int[0], null, direct);
    }

    static NetworkSnapshot.ItemEndpoint capture(IItemHandler handler,
            @Nullable Snapshots.OccupiedSlotBudget budget) {
        return capture(handler, budget, false);
    }

    static NetworkSnapshot.ItemEndpoint capture(IItemHandler handler,
            @Nullable Snapshots.OccupiedSlotBudget budget, boolean bulk) {
        ThreadGuard.requireServerThread();

        if (DirectStorageHandlers.snapshotView(handler) != 0) {
            List<StorageEndpoint.StoredItem> exports = new ArrayList<>();
            for (var stored : DirectStorageHandlers.exports(handler)) {
                if (budget != null) budget.retain();
                exports.add(new StorageEndpoint.StoredItem(stored.stack().copyWithCount(1), stored.amount()));
            }
            return directEndpoint(new NetworkSnapshot.DirectEndpoint(0, 0,
                    DirectStorageHandlers.snapshotView(handler) == 1, List.copyOf(exports), Map.of()));
        }

        int slots = handler.getSlots();
        List<Integer> occupied = new ArrayList<>();
        List<ItemStack> copies = new ArrayList<>();
        List<Integer> limits = new ArrayList<>();
        int[] bulkSlotLimits = bulk ? new int[slots] : null;

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            int slotLimit = bulk ? handler.getSlotLimit(slot) : 0;
            if (bulk) {
                bulkSlotLimits[slot] = slotLimit;
            }
            if (stack.isEmpty()) {
                continue;
            }
            if (budget != null) {
                budget.retain();
            }
            occupied.add(slot);
            copies.add(stack.copy());
            limits.add(bulk ? slotLimit : handler.getSlotLimit(slot));
        }

        int[] occupiedSlots = new int[occupied.size()];
        int[] occupiedLimits = new int[occupied.size()];
        for (int i = 0; i < occupied.size(); i++) {
            occupiedSlots[i] = occupied.get(i);
            occupiedLimits[i] = limits.get(i);
        }

        int defaultLimit = slots > 0 ? handler.getSlotLimit(firstEmptySlot(handler, slots)) : 64;
        return new NetworkSnapshot.ItemEndpoint(
                slots, occupiedSlots, copies.toArray(ItemStack[]::new), defaultLimit, occupiedLimits,
                bulkSlotLimits);
    }

    private static NetworkSnapshot.ItemEndpoint withBulkSlotLimits(
            NetworkSnapshot.ItemEndpoint endpoint, IItemHandler handler) {
        ThreadGuard.requireServerThread();

        int[] bulkSlotLimits = new int[handler.getSlots()];
        for (int slot = 0; slot < bulkSlotLimits.length; slot++) {
            bulkSlotLimits[slot] = handler.getSlotLimit(slot);
        }
        return new NetworkSnapshot.ItemEndpoint(
                endpoint.totalSlots(),
                endpoint.occupiedSlots(),
                endpoint.occupiedStacks(),
                endpoint.defaultSlotLimit(),
                endpoint.occupiedSlotLimits(),
                bulkSlotLimits);
    }

    private static int firstEmptySlot(IItemHandler handler, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return 0;
    }

    private record EndpointKey(
            @Nullable ResourceKey<Level> dimension,
            long position,
            @Nullable UUID mountedNode,
            int direction,
            int view) {

        private static EndpointKey of(LogisticsNodeEntity node, @Nullable Direction direction,
                IItemHandler handler) {
            int side = direction == null ? ALL_SIDES : direction.ordinal();
            int view = DirectStorageHandlers.snapshotView(handler);
            if (node.isMountedOnCreate()) {
                return new EndpointKey(null, 0L, node.getUUID(), side, view);
            }
            ServerLevel level = (ServerLevel) node.level();
            return new EndpointKey(level.dimension(), node.getAttachedPos().asLong(), null, side, view);
        }
    }
}
