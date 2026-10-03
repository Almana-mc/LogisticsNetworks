package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record SlotStack(int slot, ItemStack stack) {

    public static final MapCodec<SlotStack> MAP_CODEC = mapCodec(ComponentCodecs.STACK);
    static final MapCodec<SlotStack> QUIET_MAP_CODEC = mapCodec(ComponentCodecs.QUIET_STACK);
    public static final Codec<List<SlotStack>> LIST_CODEC = ComponentCodecs.lenientList(MAP_CODEC.codec());

    private static final Codec<SlotStack> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("Slot", 0).forGetter(SlotStack::slot),
            ComponentCodecs.STACK.lenientOptionalFieldOf("Item", ItemStack.EMPTY).forGetter(SlotStack::stack)
    ).apply(instance, SlotStack::new));
    public static final Codec<List<SlotStack>> LEGACY_LIST_CODEC = ComponentCodecs.lenientList(LEGACY_CODEC);

    private static MapCodec<SlotStack> mapCodec(Codec<ItemStack> stack) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SlotStack::slot),
                stack.lenientOptionalFieldOf("item", ItemStack.EMPTY).forGetter(SlotStack::stack)
        ).apply(instance, SlotStack::new));
    }

    public static List<SlotStack> nonEmpty(List<ItemStack> stacks) {
        List<SlotStack> entries = new ArrayList<>();
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty()) {
                entries.add(new SlotStack(slot, stacks.get(slot)));
            }
        }
        return entries;
    }

    public static ItemStack[] toSlots(List<SlotStack> entries, int size) {
        ItemStack[] slots = new ItemStack[size];
        Arrays.fill(slots, ItemStack.EMPTY);
        for (SlotStack entry : entries) {
            if (entry.slot() >= 0 && entry.slot() < size) {
                slots[entry.slot()] = entry.stack();
            }
        }
        return slots;
    }
}
