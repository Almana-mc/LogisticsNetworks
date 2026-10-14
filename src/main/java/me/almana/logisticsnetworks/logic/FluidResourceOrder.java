package me.almana.logisticsnetworks.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class FluidResourceOrder {
    private FluidResourceOrder() {
    }

    public static Result move(IFluidHandler source, @Nullable Cursor cursor, ToIntFunction<FluidStack> transfer) {
        Map<Key, FluidStack> groups = new LinkedHashMap<>();
        Map<Key, Integer> tanks = new LinkedHashMap<>();
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack stack = source.getFluidInTank(tank);
            if (!stack.isEmpty()) {
                Key key = new Key(stack.getFluid(), stack.getTag());
                groups.putIfAbsent(key, new FluidStack(stack, 1));
                tanks.putIfAbsent(key, tank);
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
            int moved = transfer.applyAsInt(groups.get(key));
            if (moved > 0) return new Result(moved,
                    new Cursor(key.copy(), resources.get((index + 1) % resources.size()).copy(), tanks.get(key)));
        }
        return new Result(0, null);
    }

    public record Key(Fluid fluid, @Nullable CompoundTag tag) {
        Key copy() {
            return new Key(fluid, tag == null ? null : tag.copy());
        }
    }

    public record Cursor(Key previous, Key next, int tank) {
    }

    public record Result(int moved, @Nullable Cursor cursor) {
    }
}
