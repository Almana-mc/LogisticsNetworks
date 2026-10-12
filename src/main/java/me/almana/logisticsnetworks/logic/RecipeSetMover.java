package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RecipeSetMover implements RecipeSets.SetMove {

    private record Moved(int sourceSlot, ItemStack stack) {
    }

    private final IItemHandler source;
    private final RecipeCardData card;
    private final List<RecipeCardData.Ingredient> blockers;
    private final List<TransferEngine.ItemTransferTarget> targets;
    @Nullable
    private final TransferEngine.MoveRecorder recorder;
    private final HolderLookup.Provider provider;
    private final FilterItemData.ReadCache cache;

    RecipeSetMover(IItemHandler source, RecipeCardData card, List<RecipeCardData.Ingredient> blockers,
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

    int available() {
        if (card.ingredients().isEmpty()) return 0;
        int sets = Integer.MAX_VALUE;
        for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
            sets = Math.min(sets, stock(ingredient) / ingredient.count());
        }
        return sets;
    }

    @Override
    public int apply(int targetIndex, int sets) {
        TransferEngine.ItemTransferTarget target = targets.get(targetIndex);
        int count = fit(target, sets);
        if (count == 0) return 0;
        List<Moved> done = new ArrayList<>();
        for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
            int amount = count * ingredient.count();
            if (move(target, ingredient, amount, done) < amount) {
                rollback(target, done);
                return 0;
            }
        }
        if (recorder != null) {
            for (Moved moved : done) {
                recorder.record(moved.sourceSlot(), targetIndex, moved.stack(), null);
            }
        }
        return count;
    }

    private int stock(RecipeCardData.Ingredient ingredient) {
        long total = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (usable(stack, ingredient)) {
                total += source.extractItem(slot, stack.getCount(), true).getCount();
            }
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private int fit(TransferEngine.ItemTransferTarget target, int sets) {
        if (card.blocking() && holdsBlocker(target.handler())) return 0;
        int fit = sets;
        for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
            fit = Math.min(fit, accepted(target, ingredient, sets * ingredient.count()) / ingredient.count());
            if (fit == 0) return 0;
        }
        return fit;
    }

    // One simulation per exact stack
    private int accepted(TransferEngine.ItemTransferTarget target, RecipeCardData.Ingredient ingredient, int amount) {
        Map<ItemResource, Integer> stock = new LinkedHashMap<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (usable(stack, ingredient)) stock.merge(new ItemResource(stack), stack.getCount(), Integer::sum);
        }
        int accepted = 0;
        for (Map.Entry<ItemResource, Integer> entry : stock.entrySet()) {
            if (accepted >= amount) break;
            ItemStack probe = entry.getKey().toStack(Math.min(amount - accepted, entry.getValue()));
            accepted += probe.getCount() - insert(target, probe, true).getCount();
        }
        return accepted;
    }

    private int move(TransferEngine.ItemTransferTarget target, RecipeCardData.Ingredient ingredient, int amount,
            List<Moved> done) {
        int left = amount;
        for (int slot = 0; slot < source.getSlots() && left > 0; slot++) {
            int moved;
            do {
                moved = moveFromSlot(target, ingredient, slot, left, done);
                left -= moved;
            } while (moved > 0 && left > 0);
        }
        return amount - left;
    }

    private int moveFromSlot(TransferEngine.ItemTransferTarget target, RecipeCardData.Ingredient ingredient,
            int slot, int limit, List<Moved> done) {
        ItemStack stack = source.getStackInSlot(slot);
        if (!usable(stack, ingredient)) return 0;
        ItemStack probe = stack.copyWithCount(Math.min(limit, stack.getCount()));
        int fits = probe.getCount() - insert(target, probe, true).getCount();
        if (fits <= 0) return 0;
        ItemStack pulled = source.extractItem(slot, fits, false);
        if (pulled.isEmpty()) return 0;
        ItemStack rest = insert(target, pulled, false);
        int moved = pulled.getCount() - rest.getCount();
        if (!rest.isEmpty()) giveBack(slot, rest);
        if (moved > 0) done.add(new Moved(slot, pulled.copyWithCount(moved)));
        return moved;
    }

    private void rollback(TransferEngine.ItemTransferTarget target, List<Moved> done) {
        IItemHandler handler = target.handler();
        for (Moved moved : done) {
            int left = moved.stack().getCount();
            for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), moved.stack())) continue;
                ItemStack back = handler.extractItem(slot, left, false);
                left -= back.getCount();
                if (!back.isEmpty()) giveBack(moved.sourceSlot(), back);
            }
        }
    }

    private void giveBack(int slot, ItemStack stack) {
        ItemStack left = source.insertItem(slot, stack, false);
        for (int other = 0; other < source.getSlots() && !left.isEmpty(); other++) {
            left = source.insertItem(other, left, false);
        }
    }

    private boolean holdsBlocker(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            for (RecipeCardData.Ingredient blocker : blockers) {
                if (blocker.matches(stack)) return true;
            }
        }
        return false;
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

    private static boolean usable(ItemStack stack, RecipeCardData.Ingredient ingredient) {
        return !stack.is(ModTags.RESOURCE_BLACKLIST_ITEMS) && ingredient.matches(stack);
    }
}
