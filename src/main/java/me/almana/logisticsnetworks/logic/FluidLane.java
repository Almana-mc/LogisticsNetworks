package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class FluidLane implements RecipeLane {

    private record Moved(FluidStack stack, IFluidHandler to) {
    }

    private final IFluidHandler source;
    private final List<RecipeCardData.Ingredient> ingredients;
    private final List<FluidStack> wanted;
    private final List<FluidStack> totals = new ArrayList<>();
    private final List<RecipeEndpoint> endpoints;
    private final HolderLookup.Provider provider;
    private final FilterItemData.ReadCache cache;
    private final List<Moved> pending = new ArrayList<>();

    FluidLane(IFluidHandler source, List<RecipeCardData.Ingredient> ingredients, List<RecipeEndpoint> endpoints,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        this.source = source;
        this.ingredients = ingredients;
        this.wanted = ingredients.stream().map(ingredient -> ingredient.fluidStack(ingredient.count())).toList();
        this.endpoints = endpoints;
        this.provider = provider;
        this.cache = cache;
        // Same fluid on several sides
        for (FluidStack want : wanted) {
            int at = indexOf(want);
            if (at < 0) totals.add(want);
            else totals.set(at, want.copyWithAmount(totals.get(at).getAmount() + want.getAmount()));
        }
    }

    private int indexOf(FluidStack want) {
        for (int i = 0; i < totals.size(); i++) {
            if (FluidStack.isSameFluidSameComponents(totals.get(i), want)) return i;
        }
        return -1;
    }

    @Override
    public int available(int cap) {
        int sets = cap;
        for (FluidStack want : totals) {
            if (want.isEmpty() || want.is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
                return 0;
            sets = Math.min(sets, source.drain(scaled(want, sets), IFluidHandler.FluidAction.SIMULATE).getAmount()
                    / want.getAmount());
        }
        return sets;
    }

    @Override
    public int fit(int target, int sets) {
        int fit = available(sets);
        for (int i = 0; i < wanted.size(); i++) {
            TransferEngine.FluidTransferTarget to = target(target, i);
            FluidStack want = wanted.get(i);
            if (fit == 0 || to == null || !FilterLogic.matchesFluid(to.filters(), to.mode(), want, provider, cache))
                return 0;
            fit = Math.min(fit, to.handler().fill(scaled(want, fit), IFluidHandler.FluidAction.SIMULATE)
                    / want.getAmount());
        }
        return fit;
    }

    @Override
    public boolean move(int target, int sets) {
        for (int i = 0; i < wanted.size(); i++) {
            IFluidHandler to = target(target, i).handler();
            FluidStack request = scaled(wanted.get(i), sets);
            if (to.fill(request, IFluidHandler.FluidAction.SIMULATE) < request.getAmount())
                return false;
            FluidStack drained = source.drain(request, IFluidHandler.FluidAction.EXECUTE);
            int filled = drained.isEmpty() ? 0 : to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (filled < drained.getAmount())
                source.fill(drained.copyWithAmount(drained.getAmount() - filled), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0)
                pending.add(new Moved(drained.copyWithAmount(filled), to));
            if (filled < request.getAmount())
                return false;
        }
        return true;
    }

    @Nullable
    private TransferEngine.FluidTransferTarget target(int endpoint, int ingredient) {
        return endpoints.get(endpoint).fluids(ingredients.get(ingredient).side());
    }

    @Override
    public void rollback(int target) {
        for (Moved moved : pending) {
            IFluidHandler to = moved.to();
            FluidStack back = to.drain(moved.stack(), IFluidHandler.FluidAction.EXECUTE);
            int kept = back.isEmpty() ? 0 : source.fill(back, IFluidHandler.FluidAction.EXECUTE);
            // Source full, keep in receiver
            if (kept < back.getAmount())
                to.fill(back.copyWithAmount(back.getAmount() - kept), IFluidHandler.FluidAction.EXECUTE);
        }
        pending.clear();
    }

    @Override
    public void commit(int target, @Nullable RecipeSets.Listener listener) {
        if (listener != null) {
            for (Moved moved : pending) {
                listener.moved(target, new FlowResource.Fluid(moved.stack()), moved.stack().getAmount());
            }
        }
        pending.clear();
    }

    static boolean holds(IFluidHandler handler, List<RecipeCardData.Ingredient> blockers) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty())
                continue;
            for (RecipeCardData.Ingredient blocker : blockers) {
                if (blocker.matches(stack))
                    return true;
            }
        }
        return false;
    }

    private static FluidStack scaled(FluidStack want, int sets) {
        return want.copyWithAmount((int) Math.min(Integer.MAX_VALUE, (long) want.getAmount() * sets));
    }
}
