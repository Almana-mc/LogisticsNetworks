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

    private final IChemicalHandler source;
    private final List<ChemicalStack> wanted;
    private final List<RecipeEndpoint> endpoints;
    private final FilterItemData.ReadCache cache;
    private final List<ChemicalStack> pending = new ArrayList<>();

    public ChemicalLane(IChemicalHandler source, List<RecipeCardData.Ingredient> ingredients,
            List<RecipeEndpoint> endpoints, FilterItemData.ReadCache cache) {
        this.source = source;
        this.wanted = ingredients.stream()
                .map(ingredient -> stack(ingredient.chemical().get(), ingredient.count())).toList();
        this.endpoints = endpoints;
        this.cache = cache;
    }

    @Override
    public int available(int cap) {
        long sets = cap;
        for (ChemicalStack want : wanted) {
            if (want.isEmpty() || want.is(ChemicalTransferHelper.RESOURCE_BLACKLIST_CHEMICALS))
                return 0;
            sets = Math.min(sets, source.extractChemical(scaled(want, sets), Action.SIMULATE).getAmount()
                    / want.getAmount());
        }
        return (int) sets;
    }

    @Override
    public int fit(int target, int sets) {
        RecipeEndpoint.Chemicals to = endpoints.get(target).chemicals();
        long fit = available(sets);
        for (ChemicalStack want : wanted) {
            if (fit == 0 || !FilterLogic.matchesChemical(to.filters(), to.mode(),
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
        IChemicalHandler to = endpoints.get(target).chemicals().handler();
        for (ChemicalStack want : wanted) {
            ChemicalStack request = scaled(want, sets);
            if (!to.insertChemical(request, Action.SIMULATE).isEmpty())
                return false;
            ChemicalStack pulled = source.extractChemical(request, Action.EXECUTE);
            ChemicalStack rest = pulled.isEmpty() ? ChemicalStack.EMPTY : to.insertChemical(pulled, Action.EXECUTE);
            if (!rest.isEmpty())
                source.insertChemical(rest, Action.EXECUTE);
            long moved = pulled.getAmount() - rest.getAmount();
            if (moved > 0)
                pending.add(pulled.copyWithAmount(moved));
            if (moved < request.getAmount())
                return false;
        }
        return true;
    }

    @Override
    public void rollback(int target) {
        IChemicalHandler to = endpoints.get(target).chemicals().handler();
        for (ChemicalStack moved : pending) {
            ChemicalStack back = to.extractChemical(moved, Action.EXECUTE);
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
            for (ChemicalStack moved : pending) {
                listener.moved(target, new FlowResource.Chemical(ChemicalTransferHelper.getChemicalId(moved)),
                        moved.getAmount());
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
