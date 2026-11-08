package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class ItemLane implements RecipeLane {

    private final ResourceHandler<ItemResource> source;
    private final List<RecipeCardData.Ingredient> ingredients;
    private final List<RecipeEndpoint> endpoints;
    private final HolderLookup.Provider provider;
    private final FilterItemData.ReadCache cache;
    private final List<ItemStack> pending = new ArrayList<>();
    private int[][] matching = new int[0][];
    private int[] order = new int[0];

    ItemLane(ResourceHandler<ItemResource> source, List<RecipeCardData.Ingredient> ingredients,
            List<RecipeEndpoint> endpoints, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        this.source = source;
        this.ingredients = ingredients;
        this.endpoints = endpoints;
        this.provider = provider;
        this.cache = cache;
    }

    // Indexes matching slots once
    @Override
    public int available(int cap) {
        IntArrayList[] found = new IntArrayList[ingredients.size()];
        long[] stock = new long[ingredients.size()];
        for (int i = 0; i < found.length; i++) {
            found[i] = new IntArrayList();
        }
        try (var probe = Transaction.openRoot()) {
            for (int slot = 0; slot < source.size(); slot++) {
                ItemStack stack = ItemUtil.getStack(source, slot);
                if (stack.isEmpty() || stack.is(ModTags.RESOURCE_BLACKLIST_ITEMS)) continue;
                int extractable = -1;
                for (int i = 0; i < found.length; i++) {
                    if (!ingredients.get(i).matches(stack)) continue;
                    if (extractable < 0) extractable = extractable(slot, stack, probe);
                    found[i].add(slot);
                    stock[i] += extractable;
                }
            }
        }
        matching = new int[found.length][];
        long sets = Integer.MAX_VALUE;
        for (int i = 0; i < found.length; i++) {
            matching[i] = found[i].toIntArray();
            sets = Math.min(sets, stock[i] / ingredients.get(i).count());
        }
        order = IntStream.range(0, ingredients.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> ingredients.get(i).tag().isPresent())
                        .thenComparingInt(i -> matching[i].length))
                .mapToInt(Integer::intValue).toArray();
        return affordable((int) Math.min(sets, cap));
    }

    private int affordable(int sets) {
        int low = 0;
        int high = sets;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (covers(mid)) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    // Shared slots counted once
    private boolean covers(int sets) {
        Int2IntOpenHashMap left = new Int2IntOpenHashMap();
        try (var probe = Transaction.openRoot()) {
            for (int i : order) {
                int need = sets * ingredients.get(i).count();
                for (int slot : matching[i]) {
                    if (need <= 0) break;
                    ItemStack stack = ItemUtil.getStack(source, slot);
                    if (!usable(stack, i)) continue;
                    int have = left.containsKey(slot) ? left.get(slot) : extractable(slot, stack, probe);
                    int take = Math.min(have, need);
                    left.put(slot, have - take);
                    need -= take;
                }
                if (need > 0) return false;
            }
        }
        return true;
    }

    @Override
    public int fit(int target, int sets) {
        TransferEngine.ItemTransferTarget to = endpoints.get(target).items();
        int fit = affordable(sets);
        try (var probe = Transaction.openRoot()) {
            for (int i = 0; i < matching.length && fit > 0; i++) {
                int count = ingredients.get(i).count();
                fit = Math.min(fit, accepted(to, i, fit * count, probe) / count);
            }
        }
        return fit;
    }

    @Override
    public boolean move(int target, int sets, @Nullable TransactionContext tx) {
        TransferEngine.ItemTransferTarget to = endpoints.get(target).items();
        for (int i : order) {
            int amount = sets * ingredients.get(i).count();
            if (move(to, i, amount, tx) < amount) return false;
        }
        return true;
    }

    // Aborted transaction restores stock
    @Override
    public void rollback(int target) {
        pending.clear();
    }

    @Override
    public void commit(int target, @Nullable RecipeSets.Listener listener) {
        if (listener != null) {
            for (ItemStack moved : pending) {
                listener.moved(target, new FlowResource.Item(moved), moved.getCount());
            }
        }
        pending.clear();
    }

    static boolean holds(ResourceHandler<ItemResource> handler, List<RecipeCardData.Ingredient> blockers) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemStack stack = ItemUtil.getStack(handler, slot);
            if (stack.isEmpty()) continue;
            for (RecipeCardData.Ingredient blocker : blockers) {
                if (blocker.matches(stack)) return true;
            }
        }
        return false;
    }

    // One probe per exact stack
    private int accepted(TransferEngine.ItemTransferTarget target, int ingredient, int amount,
            TransactionContext probe) {
        Map<ItemResource, Integer> stock = new LinkedHashMap<>();
        for (int slot : matching[ingredient]) {
            ItemStack stack = ItemUtil.getStack(source, slot);
            if (usable(stack, ingredient)) {
                stock.merge(ItemResource.of(stack), stack.getCount(),
                        (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
            }
        }
        int accepted = 0;
        try (var check = Transaction.open(probe)) {
            for (Map.Entry<ItemResource, Integer> entry : stock.entrySet()) {
                if (accepted >= amount) break;
                ItemStack stack = entry.getKey().toStack(Math.min(amount - accepted, entry.getValue()));
                accepted += stack.getCount() - insert(target, stack, check).getCount();
            }
        }
        return accepted;
    }

    private int move(TransferEngine.ItemTransferTarget target, int ingredient, int amount,
            @Nullable TransactionContext tx) {
        int left = amount;
        for (int slot : matching[ingredient]) {
            if (left <= 0) break;
            int moved;
            do {
                moved = moveFromSlot(target, ingredient, slot, left, tx);
                left -= moved;
            } while (moved > 0 && left > 0);
        }
        return amount - left;
    }

    private int moveFromSlot(TransferEngine.ItemTransferTarget target, int ingredient, int slot, int limit,
            @Nullable TransactionContext tx) {
        ItemStack stack = ItemUtil.getStack(source, slot);
        if (!usable(stack, ingredient)) return 0;
        ItemResource resource = ItemResource.of(stack);
        ItemStack toMove = stack.copyWithCount(Math.min(limit, stack.getCount()));
        try (var move = Transaction.open(tx)) {
            int accepted = toMove.getCount() - insert(target, toMove, move).getCount();
            if (accepted <= 0 || source.extract(slot, resource, accepted, move) != accepted) return 0;
            move.commit();
            pending.add(resource.toStack(accepted));
            return accepted;
        }
    }

    private int extractable(int slot, ItemStack stack, TransactionContext probe) {
        try (var check = Transaction.open(probe)) {
            return source.extract(slot, ItemResource.of(stack), stack.getCount(), check);
        }
    }

    private ItemStack insert(TransferEngine.ItemTransferTarget target, ItemStack stack, TransactionContext tx) {
        boolean[] allowed = null;
        if (target.hasImportSlotMapping()) {
            allowed = TransferEngine.computeImportAllowedSlots(target.handler(), target.importFilters(),
                    target.importFilterMode(), stack, provider, null, cache);
            if (allowed == null) return stack;
        } else if (!FilterLogic.matchesItemInSlot(target.importFilters(), target.importFilterMode(), stack,
                provider, null, cache, -1)) {
            return stack;
        }
        return TransferEngine.insertItemWithAllowedSlots(target.handler(), stack, tx, allowed);
    }

    private boolean usable(ItemStack stack, int ingredient) {
        return !stack.is(ModTags.RESOURCE_BLACKLIST_ITEMS) && ingredients.get(ingredient).matches(stack);
    }
}
