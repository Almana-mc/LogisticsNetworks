package me.almana.logisticsnetworks.logic;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class FluidResourceOrder {
    private FluidResourceOrder() {
    }

    public static Result move(ResourceHandler<FluidResource> source, @Nullable Cursor cursor, ToIntFunction<FluidStack> transfer) {
        Map<Key, FluidStack> groups = new LinkedHashMap<>();
        Map<Key, Integer> tanks = new LinkedHashMap<>();
        for (int tank = 0; tank < source.size(); tank++) {
            FluidStack stack = source.getResource(tank).toStack(source.getAmountAsInt(tank));
            if (!stack.isEmpty()) {
                groups.putIfAbsent(key(stack), stack.copyWithAmount(1));
                tanks.putIfAbsent(key(stack), tank);
            }
        }
        var resources = new ArrayList<>(groups.keySet());
        int start = 0;
        if (cursor != null) {
            int previous = resources.indexOf(cursor.previous());
            int next = resources.indexOf(cursor.next());
            if (previous >= 0) start = previous + 1;
            else if (next >= 0) start = next;
            else while (start < resources.size() && tanks.get(resources.get(start)) < cursor.tank()) start++;
        }
        for (int offset = 0; offset < resources.size(); offset++) {
            int index = (start + offset) % resources.size();
            Key key = resources.get(index);
            FluidStack resource = groups.get(key);
            int moved = transfer.applyAsInt(resource);
            if (moved > 0) return new Result(resource, moved,
                    new Cursor(key, resources.get((index + 1) % resources.size()), tanks.get(key)));
        }
        return new Result(FluidStack.EMPTY, 0);
    }

    private static Key key(FluidStack stack) {
        return new Key(stack.getFluid(), stack.getComponentsPatch());
    }

    public record Key(Fluid fluid, DataComponentPatch components) {
    }

    public record Cursor(Key previous, Key next, int tank) {
    }

    public record Result(FluidStack resource, int moved, @Nullable Cursor cursor) {
        public Result(FluidStack resource, int moved) {
            this(resource, moved, null);
        }
    }
}
