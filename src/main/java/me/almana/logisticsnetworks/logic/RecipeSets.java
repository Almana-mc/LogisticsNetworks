package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class RecipeSets {

    public interface SetMove {
        int apply(int target, int sets);
    }

    public interface Listener {
        void moved(int endpoint, FlowResource resource, long amount);
    }

    public record Source(@Nullable IItemHandler items, @Nullable IFluidHandler fluids,
            @Nullable IChemicalHandler chemicals) {
    }

    private RecipeSets() {
    }

    public static List<RecipeCardData> cards(ItemStack[] filters) {
        List<RecipeCardData> cards = new ArrayList<>();
        for (ItemStack filter : filters) {
            cards.addAll(FilterItemData.recipeCards(filter));
        }
        return cards;
    }

    public static Set<RecipeCardData.Kind> kinds(List<RecipeCardData> cards) {
        Set<RecipeCardData.Kind> kinds = EnumSet.noneOf(RecipeCardData.Kind.class);
        for (RecipeCardData card : cards) {
            for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
                kinds.add(ingredient.kind());
            }
        }
        return kinds;
    }

    public static int transfer(Source source, List<RecipeEndpoint> endpoints, List<RecipeCardData> cards, int batch,
            int tier, DistributionMode mode, @Nullable Listener listener, HolderLookup.Provider provider,
            FilterItemData.ReadCache cache) {
        List<RecipeCardData.Ingredient> blockers = new ArrayList<>();
        for (RecipeCardData card : cards) {
            for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
                if (!blockers.contains(ingredient)) blockers.add(ingredient);
            }
        }
        int moved = 0;
        for (RecipeCardData card : cards) {
            if (moved >= batch) break;
            List<RecipeLane> lanes = lanes(source, card, endpoints, provider, cache);
            List<Integer> usable = usable(card, endpoints);
            if (lanes.isEmpty() || usable.isEmpty()) continue;
            int sets = setsPerOp(batch - moved, card, tier);
            for (RecipeLane lane : lanes) {
                sets = Math.min(sets, lane.available(sets));
            }
            if (sets <= 0) continue;
            List<RecipeCardData.Ingredient> blocking = card.blocking() ? blockers : null;
            moved += deal(mode, usable.size(), sets,
                    (target, count) -> place(lanes, usable.get(target), count, blocking, endpoints, listener));
        }
        return moved;
    }

    public static int setsPerOp(int budget, RecipeCardData card, int tier) {
        long sets = budget;
        for (RecipeCardData.Kind kind : RecipeCardData.Kind.values()) {
            long amount = card.amount(kind);
            if (amount > 0) {
                sets = Math.min(sets, Math.max(1, NodeUpgradeData.getOperationCap(kind.channel, tier) / amount));
            }
        }
        return (int) Math.max(1, sets);
    }

    static int deal(DistributionMode mode, int targets, int sets, SetMove move) {
        return switch (mode) {
            case ROUND_ROBIN -> (int) EvenSplit.deal(targets, sets, (target, limit) -> move.apply(target, (int) limit));
            case PRIORITY_ROBIN -> firstTaker(targets, sets, move);
            default -> inOrder(targets, sets, move);
        };
    }

    private static List<RecipeLane> lanes(Source source, RecipeCardData card, List<RecipeEndpoint> endpoints,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        List<RecipeLane> lanes = new ArrayList<>(3);
        for (RecipeCardData.Kind kind : RecipeCardData.Kind.values()) {
            List<RecipeCardData.Ingredient> part = card.ingredients().stream()
                    .filter(ingredient -> ingredient.kind() == kind).toList();
            if (part.isEmpty()) continue;
            RecipeLane lane = switch (kind) {
                case ITEM -> source.items() == null ? null
                        : new ItemLane(source.items(), part, endpoints, provider, cache);
                case FLUID -> source.fluids() == null ? null
                        : new FluidLane(source.fluids(), part, endpoints, provider, cache);
                case CHEMICAL -> null;
            };
            if (lane == null) return List.of();
            lanes.add(lane);
        }
        return lanes;
    }

    private static List<Integer> usable(RecipeCardData card, List<RecipeEndpoint> endpoints) {
        Set<RecipeCardData.Kind> needs = kinds(List.of(card));
        List<Integer> usable = new ArrayList<>();
        for (int i = 0; i < endpoints.size(); i++) {
            if (endpoints.get(i).serves(needs)) usable.add(i);
        }
        return usable;
    }

    // All lanes or none
    private static int place(List<RecipeLane> lanes, int target, int sets,
            @Nullable List<RecipeCardData.Ingredient> blockers, List<RecipeEndpoint> endpoints,
            @Nullable Listener listener) {
        if (blockers != null && holds(endpoints.get(target), blockers)) return 0;
        int fit = sets;
        for (RecipeLane lane : lanes) {
            fit = Math.min(fit, lane.fit(target, fit));
            if (fit == 0) return 0;
        }
        for (int i = 0; i < lanes.size(); i++) {
            if (lanes.get(i).move(target, fit)) continue;
            for (int done = i; done >= 0; done--) {
                lanes.get(done).rollback(target);
            }
            return 0;
        }
        for (RecipeLane lane : lanes) {
            lane.commit(target, listener);
        }
        return fit;
    }

    private static boolean holds(RecipeEndpoint endpoint, List<RecipeCardData.Ingredient> blockers) {
        return endpoint.items() != null && ItemLane.holds(endpoint.items().handler(), blockers)
                || endpoint.fluids() != null && FluidLane.holds(endpoint.fluids().handler(), blockers);
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
