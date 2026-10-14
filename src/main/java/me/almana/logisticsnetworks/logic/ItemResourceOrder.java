package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class ItemResourceOrder {
    public static final Result EMPTY = new Result(0, null);

    private ItemResourceOrder() {
    }

    public static Result move(IItemHandler source, @Nullable Cursor cursor, ToIntFunction<int[]> transfer) {
        Map<Key, IntArrayList> groups = groups(source);
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
            Key resource = resources.get(index);
            int moved = transfer.applyAsInt(groups.get(resource).toIntArray());
            if (moved > 0) return new Result(moved, new Cursor(resource.copy(),
                    resources.get((index + 1) % resources.size()).copy(), groups.get(resource).getInt(0)));
        }
        return EMPTY;
    }

    private static Map<Key, IntArrayList> groups(IItemHandler source) {
        Map<Key, IntArrayList> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            groups.computeIfAbsent(new Key(stack.getItem(), stack.getTag()), ignored -> new IntArrayList()).add(slot);
        }
        return groups;
    }

    public record Key(Item item, @Nullable CompoundTag tag) {
        Key copy() {
            return new Key(item, tag == null ? null : tag.copy());
        }
    }

    public record Cursor(Key previous, Key next, int slot) {
    }

    public record Result(int moved, @Nullable Cursor cursor) {
    }
}
