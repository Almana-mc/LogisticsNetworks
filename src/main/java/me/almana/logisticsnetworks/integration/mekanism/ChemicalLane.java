// Enable when 26.1.2 is supported.
/*
package me.almana.logisticsnetworks.integration.mekanism;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.RecipeEndpoint;
import me.almana.logisticsnetworks.logic.RecipeLane;
import me.almana.logisticsnetworks.logic.RecipeSets;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ChemicalLane implements RecipeLane {

    private record Moved(ChemicalStack stack, IChemicalHandler to) {
    }

    private final IChemicalHandler source;
    private final List<RecipeCardData.Ingredient> ingredients;
    private final List<ChemicalStack> wanted;
    private final List<ChemicalStack> totals = new ArrayList<>();
    private final List<RecipeEndpoint> endpoints;
    private final FilterItemData.ReadCache cache;
    private final List<Moved> pending = new ArrayList<>();

    public ChemicalLane(IChemicalHandler source, List<RecipeCardData.Ingredient> ingredients,
            List<RecipeEndpoint> endpoints, FilterItemData.ReadCache cache) {
        this.source = source;
        this.ingredients = ingredients;
        this.wanted = ingredients.stream()
                .map(ingredient -> stack(ingredient.chemical().get(), ingredient.count())).toList();
        this.endpoints = endpoints;
        this.cache = cache;
        // Same chemical on several sides
        for (ChemicalStack want : wanted) {
            int at = indexOf(want);
            if (at < 0) totals.add(want);
            else totals.set(at, want.copyWithAmount(totals.get(at).getAmount() + want.getAmount()));
        }
    }

    private int indexOf(ChemicalStack want) {
        for (int i = 0; i < totals.size(); i++) {
            if (ChemicalStack.isSameChemical(totals.get(i), want)) return i;
        }
        return -1;
    }

    @Override
    public int available(int cap) {
        long sets = cap;
        for (ChemicalStack want : totals) {
            if (want.isEmpty() || want.is(ChemicalTransferHelper.RESOURCE_BLACKLIST_CHEMICALS))
                return 0;
            sets = Math.min(sets, source.extractChemical(scaled(want, sets), Action.SIMULATE).getAmount()
                    / want.getAmount());
        }
        return (int) sets;
    }

    @Override
    public int fit(int target, int sets) {
        long fit = available(sets);
        for (int i = 0; i < wanted.size(); i++) {
            RecipeEndpoint.Chemicals to = target(target, i);
            ChemicalStack want = wanted.get(i);
            if (fit == 0 || to == null || !FilterLogic.matchesChemical(to.filters(), to.mode(),
                    ChemicalTransferHelper.getChemicalId(want), cache))
                return 0;
            ChemicalStack request = scaled(want, fit);
            long accepted = request.getAmount() - to.handler().insertChemical(request, Action.SIMULATE).getAmount();
            fit = Math.min(fit, accepted / want.getAmount());
        }
        return (int) fit;
    }

    @Override
    public boolean move(int target, int sets) {
        for (int i = 0; i < wanted.size(); i++) {
            IChemicalHandler to = target(target, i).handler();
            ChemicalStack request = scaled(wanted.get(i), sets);
            if (!to.insertChemical(request, Action.SIMULATE).isEmpty())
                return false;
            ChemicalStack pulled = source.extractChemical(request, Action.EXECUTE);
            ChemicalStack rest = pulled.isEmpty() ? ChemicalStack.EMPTY : to.insertChemical(pulled, Action.EXECUTE);
            if (!rest.isEmpty())
                source.insertChemical(rest, Action.EXECUTE);
            long moved = pulled.getAmount() - rest.getAmount();
            if (moved > 0)
                pending.add(new Moved(pulled.copyWithAmount(moved), to));
            if (moved < request.getAmount())
                return false;
        }
        return true;
    }

    @Nullable
    private RecipeEndpoint.Chemicals target(int endpoint, int ingredient) {
        return endpoints.get(endpoint).chemicals(ingredients.get(ingredient).side());
    }

    @Override
    public void rollback(int target) {
        for (Moved moved : pending) {
            IChemicalHandler to = moved.to();
            ChemicalStack back = to.extractChemical(moved.stack(), Action.EXECUTE);
            ChemicalStack stuck = back.isEmpty() ? back : source.insertChemical(back, Action.EXECUTE);
            // Source full, keep in receiver
            if (!stuck.isEmpty())
                to.insertChemical(stuck, Action.EXECUTE);
        }
        pending.clear();
    }

    @Override
    public void commit(int target, @Nullable RecipeSets.Listener listener) {
        if (listener != null) {
            for (Moved moved : pending) {
                listener.moved(target, new FlowResource.Chemical(ChemicalTransferHelper.getChemicalId(moved.stack())),
                        moved.stack().getAmount());
            }
        }
        pending.clear();
    }

    public static boolean holds(IChemicalHandler handler, List<RecipeCardData.Ingredient> blockers) {
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            String id = ChemicalTransferHelper.getChemicalId(handler.getChemicalInTank(tank));
            if (id == null)
                continue;
            for (RecipeCardData.Ingredient blocker : blockers) {
                if (blocker.chemical().filter(id::equals).isPresent())
                    return true;
            }
        }
        return false;
    }

    private static ChemicalStack stack(String id, long amount) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? ChemicalStack.EMPTY : MekanismAPI.CHEMICAL_REGISTRY.getHolder(key)
                .map(holder -> new ChemicalStack(holder, amount)).orElse(ChemicalStack.EMPTY);
    }

    private static ChemicalStack scaled(ChemicalStack want, long sets) {
        return want.copyWithAmount(want.getAmount() * sets);
    }
}
*/
