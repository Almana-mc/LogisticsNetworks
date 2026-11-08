package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class FluidLane implements RecipeLane {

    private final ResourceHandler<FluidResource> source;
    private final List<FluidStack> wanted;
    private final List<RecipeEndpoint> endpoints;
    private final HolderLookup.Provider provider;
    private final FilterItemData.ReadCache cache;
    private final List<FluidStack> pending = new ArrayList<>();

    FluidLane(ResourceHandler<FluidResource> source, List<RecipeCardData.Ingredient> ingredients,
            List<RecipeEndpoint> endpoints, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        this.source = source;
        this.wanted = ingredients.stream().map(ingredient -> ingredient.fluidStack(ingredient.count())).toList();
        this.endpoints = endpoints;
        this.provider = provider;
        this.cache = cache;
    }

    @Override
    public int available(int cap) {
        int sets = cap;
        for (FluidStack want : wanted) {
            if (want.isEmpty() || want.is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
                return 0;
            FluidStack request = scaled(want, sets);
            try (var probe = Transaction.openRoot()) {
                sets = Math.min(sets, source.extract(FluidResource.of(request), request.getAmount(), probe)
                        / want.getAmount());
            }
        }
        return sets;
    }

    @Override
    public int fit(int target, int sets) {
        TransferEngine.FluidTransferTarget to = endpoints.get(target).fluids();
        int fit = available(sets);
        for (FluidStack want : wanted) {
            if (fit == 0 || !FilterLogic.matchesFluid(to.filters(), to.mode(), want, provider, cache))
                return 0;
            FluidStack request = scaled(want, fit);
            try (var probe = Transaction.openRoot()) {
                fit = Math.min(fit, to.handler().insert(FluidResource.of(request), request.getAmount(), probe)
                        / want.getAmount());
            }
        }
        return fit;
    }

    // Receiver checked before draining
    @Override
    public boolean move(int target, int sets, @Nullable TransactionContext tx) {
        ResourceHandler<FluidResource> to = endpoints.get(target).fluids().handler();
        for (FluidStack want : wanted) {
            FluidStack request = scaled(want, sets);
            FluidResource resource = FluidResource.of(request);
            try (var move = Transaction.open(tx)) {
                int filled = to.insert(resource, request.getAmount(), move);
                if (filled < request.getAmount() || source.extract(resource, filled, move) != filled)
                    return false;
                move.commit();
            }
            pending.add(request);
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
            for (FluidStack moved : pending) {
                listener.moved(target, new FlowResource.Fluid(moved), moved.getAmount());
            }
        }
        pending.clear();
    }

    static boolean holds(ResourceHandler<FluidResource> handler, List<RecipeCardData.Ingredient> blockers) {
        for (int tank = 0; tank < handler.size(); tank++) {
            FluidStack stack = FluidUtil.getStack(handler, tank);
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
