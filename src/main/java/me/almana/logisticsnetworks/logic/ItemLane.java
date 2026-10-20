package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class ItemLane implements RecipeLane {

    private record Moved(int sourceSlot, ItemStack stack) {
    }

    private final IItemHandler source;
    private final List<RecipeCardData.Ingredient> ingredients;
    private final List<RecipeEndpoint> endpoints;
    private final HolderLookup.Provider provider;
    private final FilterItemData.ReadCache cache;
    private final List<Moved> pending = new ArrayList<>();
    private int[][] matching = new int[0][];
    private int[] order = new int[0];

    ItemLane(IItemHandler source, List<RecipeCardData.Ingredient> ingredients, List<RecipeEndpoint> endpoints,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
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
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || stack.is(ModTags.RESOURCE_BLACKLIST_ITEMS)) continue;
            int extractable = -1;
            for (int i = 0; i < found.length; i++) {
                if (!ingredients.get(i).matches(stack)) continue;
                if (extractable < 0) extractable = source.extractItem(slot, stack.getCount(), true).getCount();
                found[i].add(slot);
                stock[i] += extractable;
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
        for (int i : order) {
            int need = sets * ingredients.get(i).count();
            for (int slot : matching[i]) {
                if (need <= 0) break;
                ItemStack stack = source.getStackInSlot(slot);
                if (!usable(stack, i)) continue;
                int have = left.containsKey(slot) ? left.get(slot)
                        : source.extractItem(slot, stack.getCount(), true).getCount();
                int take = Math.min(have, need);
                left.put(slot, have - take);
                need -= take;
            }
            if (need > 0) return false;
        }
        return true;
    }

    @Override
    public int fit(int target, int sets) {
        TransferEngine.ItemTransferTarget to = endpoints.get(target).items();
        int fit = affordable(sets);
        for (int i = 0; i < matching.length && fit > 0; i++) {
            int count = ingredients.get(i).count();
            fit = Math.min(fit, accepted(to, i, fit * count) / count);
        }
        return fit;
    }

    @Override
    public boolean move(int target, int sets) {
        TransferEngine.ItemTransferTarget to = endpoints.get(target).items();
        for (int i : order) {
            int amount = sets * ingredients.get(i).count();
            if (move(to, i, amount) < amount) return false;
        }
        return true;
    }

    @Override
    public void rollback(int target) {
        TransferEngine.ItemTransferTarget to = endpoints.get(target).items();
        IItemHandler handler = to.handler();
        for (Moved moved : pending) {
            int left = moved.stack().getCount();
            for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), moved.stack())) continue;
                ItemStack back = handler.extractItem(slot, left, false);
                left -= back.getCount();
                ItemStack stuck = back.isEmpty() ? back : giveBack(moved.sourceSlot(), back);
                // Source full, keep in receiver
                if (!stuck.isEmpty()) stuck = handler.insertItem(slot, stuck, false);
                if (!stuck.isEmpty()) insert(to, stuck, false);
            }
        }
        pending.clear();
    }

    @Override
    public void commit(int target, @Nullable RecipeSets.Listener listener) {
        if (listener != null) {
            for (Moved moved : pending) {
                listener.moved(target, new FlowResource.Item(moved.stack()), moved.stack().getCount());
            }
        }
        pending.clear();
    }

    static boolean holds(IItemHandler handler, List<RecipeCardData.Ingredient> blockers) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            for (RecipeCardData.Ingredient blocker : blockers) {
                if (blocker.matches(stack)) return true;
            }
        }
        return false;
    }

    // One simulation per exact stack
    private int accepted(TransferEngine.ItemTransferTarget target, int ingredient, int amount) {
        Map<ItemResource, Integer> stock = new LinkedHashMap<>();
        for (int slot : matching[ingredient]) {
            ItemStack stack = source.getStackInSlot(slot);
            if (usable(stack, ingredient)) {
                stock.merge(new ItemResource(stack), stack.getCount(),
                        (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
            }
        }
        int accepted = 0;
        for (Map.Entry<ItemResource, Integer> entry : stock.entrySet()) {
            if (accepted >= amount) break;
            ItemStack probe = entry.getKey().toStack(Math.min(amount - accepted, entry.getValue()));
            accepted += probe.getCount() - insert(target, probe, true).getCount();
        }
        return accepted;
    }

    private int move(TransferEngine.ItemTransferTarget target, int ingredient, int amount) {
        int left = amount;
        for (int slot : matching[ingredient]) {
            if (left <= 0) break;
            int moved;
            do {
                moved = moveFromSlot(target, ingredient, slot, left);
                left -= moved;
            } while (moved > 0 && left > 0);
        }
        return amount - left;
    }

    private int moveFromSlot(TransferEngine.ItemTransferTarget target, int ingredient, int slot, int limit) {
        ItemStack stack = source.getStackInSlot(slot);
        if (!usable(stack, ingredient)) return 0;
        ItemStack probe = stack.copyWithCount(Math.min(limit, stack.getCount()));
        int fits = probe.getCount() - insert(target, probe, true).getCount();
        if (fits <= 0) return 0;
        ItemStack pulled = source.extractItem(slot, fits, false);
        if (pulled.isEmpty()) return 0;
        ItemStack rest = insert(target, pulled, false);
        int moved = pulled.getCount() - rest.getCount();
        ItemStack stuck = rest.isEmpty() ? rest : giveBack(slot, rest);
        // Last resort, like executeMove
        if (!stuck.isEmpty()) moved += stuck.getCount() - insert(target, stuck, false).getCount();
        if (moved > 0) pending.add(new Moved(slot, pulled.copyWithCount(moved)));
        return moved;
    }

    private ItemStack giveBack(int slot, ItemStack stack) {
        ItemStack left = source.insertItem(slot, stack, false);
        for (int other = 0; other < source.getSlots() && !left.isEmpty(); other++) {
            left = source.insertItem(other, left, false);
        }
        return left;
    }

    private ItemStack insert(TransferEngine.ItemTransferTarget target, ItemStack stack, boolean simulate) {
        boolean[] allowed = null;
        if (target.hasImportSlotMapping()) {
            allowed = TransferEngine.computeImportAllowedSlots(target.handler(), target.importFilters(),
                    target.importFilterMode(), stack, provider, null, cache);
            if (allowed == null) return stack;
        } else if (!FilterLogic.matchesItemInSlot(target.importFilters(), target.importFilterMode(), stack,
                provider, null, cache, -1)) {
            return stack;
        }
        return TransferEngine.insertItemWithAllowedSlots(target.handler(), target.bulkHandler(), stack, simulate,
                allowed);
    }

    private boolean usable(ItemStack stack, int ingredient) {
        return !stack.is(ModTags.RESOURCE_BLACKLIST_ITEMS) && ingredients.get(ingredient).matches(stack);
    }
}
