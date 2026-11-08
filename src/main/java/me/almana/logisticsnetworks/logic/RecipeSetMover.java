package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
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

final class RecipeSetMover implements RecipeSets.SetMove {

    private record Moved(int sourceSlot, ItemStack stack) {
    }

    private final ResourceHandler<ItemResource> source;
    private final RecipeCardData card;
    private final List<RecipeCardData.Ingredient> blockers;
    private final List<TransferEngine.ItemTransferTarget> targets;
    @Nullable
    private final TransferEngine.MoveRecorder recorder;
    private final HolderLookup.Provider provider;
    private final FilterItemData.ReadCache cache;
    private int[][] matching = new int[0][];
    private int[] order = new int[0];

    RecipeSetMover(ResourceHandler<ItemResource> source, RecipeCardData card, List<RecipeCardData.Ingredient> blockers,
            List<TransferEngine.ItemTransferTarget> targets, @Nullable TransferEngine.MoveRecorder recorder,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        this.source = source;
        this.card = card;
        this.blockers = blockers;
        this.targets = targets;
        this.recorder = recorder;
        this.provider = provider;
        this.cache = cache;
    }

    // Indexes matching slots once
    int available(int cap) {
        List<RecipeCardData.Ingredient> ingredients = card.ingredients();
        if (ingredients.isEmpty()) return 0;
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
                int need = sets * card.ingredients().get(i).count();
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
    public int apply(int targetIndex, int sets) {
        TransferEngine.ItemTransferTarget target = targets.get(targetIndex);
        int count = fit(target, affordable(sets));
        if (count == 0) return 0;
        List<Moved> done = new ArrayList<>();
        boolean direct = DirectStorageHandlers.isDirect(target.handler());
        boolean placed = true;
        // Shared network capacity: commit each
        try (var tx = direct ? null : Transaction.openRoot()) {
            for (int i : order) {
                int amount = count * card.ingredients().get(i).count();
                placed = move(target, i, amount, done, tx) >= amount;
                if (!placed) break;
            }
            if (placed && tx != null) tx.commit();
        }
        if (recorder != null && (placed || direct)) {
            for (Moved moved : done) {
                recorder.record(moved.sourceSlot(), targetIndex, moved.stack(), null);
            }
        }
        return placed ? count : 0;
    }

    private int fit(TransferEngine.ItemTransferTarget target, int sets) {
        if (card.blocking() && holdsBlocker(target.handler())) return 0;
        int fit = sets;
        try (var probe = Transaction.openRoot()) {
            for (int i = 0; i < matching.length; i++) {
                int count = card.ingredients().get(i).count();
                fit = Math.min(fit, accepted(target, i, sets * count, probe) / count);
                if (fit == 0) return 0;
            }
        }
        return fit;
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

    private int move(TransferEngine.ItemTransferTarget target, int ingredient, int amount, List<Moved> done,
            @Nullable TransactionContext tx) {
        int left = amount;
        for (int slot : matching[ingredient]) {
            if (left <= 0) break;
            int moved;
            do {
                moved = moveFromSlot(target, ingredient, slot, left, done, tx);
                left -= moved;
            } while (moved > 0 && left > 0);
        }
        return amount - left;
    }

    private int moveFromSlot(TransferEngine.ItemTransferTarget target, int ingredient, int slot, int limit,
            List<Moved> done, @Nullable TransactionContext tx) {
        ItemStack stack = ItemUtil.getStack(source, slot);
        if (!usable(stack, ingredient)) return 0;
        ItemResource resource = ItemResource.of(stack);
        ItemStack toMove = stack.copyWithCount(Math.min(limit, stack.getCount()));
        try (var move = Transaction.open(tx)) {
            int accepted = toMove.getCount() - insert(target, toMove, move).getCount();
            if (accepted <= 0 || source.extract(slot, resource, accepted, move) != accepted) return 0;
            move.commit();
            done.add(new Moved(slot, resource.toStack(accepted)));
            return accepted;
        }
    }

    private int extractable(int slot, ItemStack stack, TransactionContext probe) {
        try (var check = Transaction.open(probe)) {
            return source.extract(slot, ItemResource.of(stack), stack.getCount(), check);
        }
    }

    private boolean holdsBlocker(ResourceHandler<ItemResource> handler) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemStack stack = ItemUtil.getStack(handler, slot);
            if (stack.isEmpty()) continue;
            for (RecipeCardData.Ingredient blocker : blockers) {
                if (blocker.matches(stack)) return true;
            }
        }
        return false;
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
        return !stack.is(ModTags.RESOURCE_BLACKLIST_ITEMS) && card.ingredients().get(ingredient).matches(stack);
    }
}
