package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class ItemResourceOrder {
    public static final Result EMPTY = new Result(null, 0);

    private ItemResourceOrder() {
    }

    public static Result move(ResourceHandler<ItemResource> source, @Nullable Cursor cursor,
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

    @Nullable
    public static Cursor after(ResourceHandler<ItemResource> source, ItemResource resource) {
        var groups = groups(source);
        var resources = new ArrayList<>(groups.keySet());
        int index = resources.indexOf(resource);
        return index < 0 ? null : new Cursor(resource, resources.get((index + 1) % resources.size()),
                groups.get(resource).getInt(0));
    }

    private static Map<ItemResource, IntArrayList> groups(ResourceHandler<ItemResource> source) {
        Map<ItemResource, IntArrayList> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < source.size(); slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty() || source.getAmountAsLong(slot) == 0) continue;
            groups.computeIfAbsent(resource, ignored -> new IntArrayList()).add(slot);
        }
        return groups;
    }

    public record Cursor(ItemResource previous, ItemResource next, int slot) {
    }

    public record Result(@Nullable ItemResource resource, int moved, @Nullable Cursor cursor) {
        public Result(@Nullable ItemResource resource, int moved) {
            this(resource, moved, null);
        }
    }
}
