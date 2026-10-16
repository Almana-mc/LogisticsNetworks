package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

public final class ItemResourceOrder {
    public static final Result EMPTY = new Result(null, 0);

    private ItemResourceOrder() {
    }

    public static Result move(IItemHandler source, @Nullable Cursor cursor,
            @Nullable ItemResource required, ToIntFunction<int[]> transfer) {
        Map<ItemResource, IntArrayList> groups = groups(source);
        var resources = new ArrayList<>(groups.keySet());
        int start = 0;
        if (cursor != null) {
            int previous = resources.indexOf(cursor.previous());
            int next = resources.indexOf(cursor.next());
            if (previous >= 0) start = previous + 1;
            else if (next >= 0) start = next;
            else {
                while (start < resources.size() && groups.get(resources.get(start)).getInt(0) < cursor.slot()) start++;
            }
        }
        for (int offset = 0; offset < resources.size(); offset++) {
            int index = (start + offset) % resources.size();
            ItemResource resource = resources.get(index);
            if (required != null && !required.equals(resource)) continue;
            int moved = transfer.applyAsInt(groups.get(resource).toIntArray());
            if (moved > 0) return new Result(resource, moved, new Cursor(resource,
                    resources.get((index + 1) % resources.size()), groups.get(resource).getInt(0)));
        }
        return EMPTY;
    }

    public static int moveEach(IItemHandler source, int limit, ToIntBiFunction<int[], Integer> transfer) {
        int moved = 0;
        for (IntArrayList slots : groups(source).values()) {
            if (moved >= limit) break;
            moved += transfer.applyAsInt(slots.toIntArray(), limit - moved);
        }
        return moved;
    }

    @Nullable
    public static Cursor after(IItemHandler source, ItemResource resource) {
        var groups = groups(source);
        var resources = new ArrayList<>(groups.keySet());
        int index = resources.indexOf(resource);
        return index < 0 ? null : new Cursor(resource, resources.get((index + 1) % resources.size()),
                groups.get(resource).getInt(0));
    }

    private static Map<ItemResource, IntArrayList> groups(IItemHandler source) {
        Map<ItemResource, IntArrayList> groups = new LinkedHashMap<>();
        Map<Integer, List<ItemResource>> variants = new HashMap<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            groups.computeIfAbsent(resourceOf(variants, stack), ignored -> new IntArrayList()).add(slot);
        }
        return groups;
    }

    // One copy per group
    private static ItemResource resourceOf(Map<Integer, List<ItemResource>> variants, ItemStack stack) {
        List<ItemResource> known = variants.computeIfAbsent(Objects.hash(stack.getItem(), stack.getTag()),
                ignored -> new ArrayList<>(1));
        for (ItemResource resource : known) {
            if (resource.matches(stack)) return resource;
        }
        ItemResource resource = new ItemResource(stack);
        known.add(resource);
        return resource;
    }

    public record Cursor(ItemResource previous, ItemResource next, int slot) {
    }

    public record Result(@Nullable ItemResource resource, int moved, @Nullable Cursor cursor) {
        public Result(@Nullable ItemResource resource, int moved) {
            this(resource, moved, null);
        }
    }
}
