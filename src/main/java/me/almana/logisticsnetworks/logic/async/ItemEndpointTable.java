package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ItemEndpointTable {

    private static final int ALL_SIDES = Direction.values().length;

    private final Map<EndpointKey, Integer> indexes = new HashMap<>();
    private final IdentityHashMap<IItemHandler, Map<Integer, Integer>> handlerIndexes =
            new IdentityHashMap<>();
    private final List<NetworkSnapshot.ItemEndpoint> endpoints = new ArrayList<>();

    int capture(LogisticsNodeEntity node, @Nullable Direction direction, IItemHandler handler,
            Snapshots.OccupiedSlotBudget budget) {
        return capture(node, direction, handler, budget, false);
    }

    int capture(LogisticsNodeEntity node, @Nullable Direction direction, IItemHandler handler,
            Snapshots.OccupiedSlotBudget budget, boolean bulk) {
        EndpointKey key = EndpointKey.of(node, direction);
        Integer existing = indexes.get(key);
        if (existing == null && !node.isMountedOnCreate()) {
            Map<Integer, Integer> sides = handlerIndexes.get(handler);
            existing = sides != null ? sides.get(key.direction()) : null;
        }
        if (existing != null) {
            indexes.put(key, existing);
            NetworkSnapshot.ItemEndpoint endpoint = endpoints.get(existing);
            if (bulk && endpoint.bulkSlotLimits() == null) {
                endpoints.set(existing, withBulkSlotLimits(endpoint, handler));
            }
            return existing;
        }

        int index = endpoints.size();
        endpoints.add(capture(handler, budget, bulk));
        indexes.put(key, index);
        if (!node.isMountedOnCreate()) {
            handlerIndexes.computeIfAbsent(handler, ignored -> new HashMap<>())
                    .put(key.direction(), index);
        }
        return index;
    }

    List<NetworkSnapshot.ItemEndpoint> endpoints() {
        return List.copyOf(endpoints);
    }

    static NetworkSnapshot.ItemEndpoint capture(IItemHandler handler,
            @Nullable Snapshots.OccupiedSlotBudget budget) {
        return capture(handler, budget, false);
    }

    static NetworkSnapshot.ItemEndpoint capture(IItemHandler handler,
            @Nullable Snapshots.OccupiedSlotBudget budget, boolean bulk) {
        ThreadGuard.requireServerThread();

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
            int direction) {

        private static EndpointKey of(LogisticsNodeEntity node, @Nullable Direction direction) {
            int side = direction == null ? ALL_SIDES : direction.ordinal();
            if (node.isMountedOnCreate()) {
                return new EndpointKey(null, 0L, node.getUUID(), side);
            }
            ServerLevel level = (ServerLevel) node.level();
            return new EndpointKey(level.dimension(), node.getAttachedPos().asLong(), null, side);
        }
    }
}
