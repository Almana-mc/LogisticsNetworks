package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RecipeSets {

    public interface SetMove {
        int apply(int target, int sets);
    }

    private RecipeSets() {
    }

    public static int transfer(IItemHandler source, List<TransferEngine.ItemTransferTarget> targets,
            ItemStack[] filters, int batch, DistributionMode mode, @Nullable TransferEngine.MoveRecorder recorder,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        List<RecipeCardData> cards = new ArrayList<>();
        List<RecipeCardData.Ingredient> blockers = new ArrayList<>();
        for (ItemStack filter : filters) {
            if (!RecipeCardItem.is(filter)) continue;
            RecipeCardData card = RecipeCardData.of(filter);
            cards.add(card);
            for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
                if (!blockers.contains(ingredient)) blockers.add(ingredient);
            }
        }
        int moved = 0;
        for (RecipeCardData card : cards) {
            if (moved >= batch) break;
            RecipeSetMover mover = new RecipeSetMover(source, card, blockers, targets, recorder, provider, cache);
            int cap = Math.max(1, (batch - moved) / Math.max(1, card.setSize()));
            int sets = sets(mover.available(cap), batch - moved, card.setSize(), moved == 0);
            if (sets > 0) moved += deal(mode, targets.size(), sets, mover) * card.setSize();
        }
        return moved;
    }

    public static ItemStack[] withoutCards(ItemStack[] filters) {
        ItemStack[] copy = filters.clone();
        for (int i = 0; i < copy.length; i++) {
            if (RecipeCardItem.is(copy[i])) copy[i] = ItemStack.EMPTY;
        }
        return copy;
    }

    static int sets(int available, int budget, int setSize, boolean first) {
        if (setSize <= 0) return 0;
        int byBudget = budget / setSize;
        return Math.min(available, byBudget == 0 && first ? 1 : byBudget);
    }

    static int deal(DistributionMode mode, int targets, int sets, SetMove move) {
        return switch (mode) {
            case ROUND_ROBIN -> (int) EvenSplit.deal(targets, sets, (target, limit) -> move.apply(target, (int) limit));
            case PRIORITY_ROBIN -> firstTaker(targets, sets, move);
            default -> inOrder(targets, sets, move);
        };
    }

    private static int firstTaker(int targets, int sets, SetMove move) {
        for (int target = 0; target < targets; target++) {
            int moved = move.apply(target, sets);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private static int inOrder(int targets, int sets, SetMove move) {
        int left = sets;
        for (int target = 0; target < targets && left > 0; target++) {
            left -= move.apply(target, left);
        }
        return sets - left;
    }
}
