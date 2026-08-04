package me.almana.logisticsnetworks.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.almana.logisticsnetworks.item.LegacyFilterItem;
import me.almana.logisticsnetworks.util.NbtAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class LegacyFilterDataTest {
    @Test
    void convertsHistoricalFilterData() {
        CompoundTag amountData = custom("amount_filter");
        NbtAccess.getCompound(amountData, "ln_amount_filter").putInt("amount", 128);
        assertEquals(128, NbtAccess.getInt(
                LegacyFilterData.convert(LegacyFilterItem.Kind.AMOUNT, amountData), "legacy_amount", 0));

        CompoundTag durabilityData = custom("durability_filter");
        CompoundTag durability = NbtAccess.getCompound(durabilityData, "ln_durability_filter");
        durability.putString("operator", "le");
        durability.putInt("value", 42);
        CompoundTag durabilityEntry = entry(
                LegacyFilterData.convert(LegacyFilterItem.Kind.DURABILITY, durabilityData));
        assertEquals("le", NbtAccess.getString(durabilityEntry, "dur_op", ""));
        assertEquals(42, NbtAccess.getInt(durabilityEntry, "dur_val", 0));

        CompoundTag nbtData = custom("nbt_filter");
        CompoundTag nbt = NbtAccess.getCompound(nbtData, "ln_nbt_filter");
        nbt.putBoolean("blacklist", true);
        nbt.putString("path", "minecraft:damage");
        nbt.putInt("value", 4);
        CompoundTag nbtRoot = LegacyFilterData.convert(LegacyFilterItem.Kind.NBT, nbtData);
        assertTrue(NbtAccess.getBoolean(nbtRoot, "blacklist", false));
        assertEquals("minecraft:damage", NbtAccess.getString(
                (CompoundTag) NbtAccess.getList(entry(nbtRoot), "nbt_rules", 10).get(0), "p", ""));

        CompoundTag slotData = custom("slot_filter");
        NbtAccess.getCompound(slotData, "ln_slot_filter").putIntArray("slots", new int[] { 5, 2, 5, 70 });
        assertArrayEquals(new int[] { 2, 5 }, NbtAccess.getIntArray(
                entry(LegacyFilterData.convert(LegacyFilterItem.Kind.SLOT, slotData)), "slot_map"));

        CompoundTag tagData = custom("tag_filter");
        CompoundTag tag = NbtAccess.getCompound(tagData, "ln_tag_filter");
        tag.putInt("target", FilterTargetType.FLUIDS.ordinal());
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf("forge:water"));
        tag.put("tags", tags);
        CompoundTag tagRoot = LegacyFilterData.convert(LegacyFilterItem.Kind.TAG, tagData);
        assertEquals(FilterTargetType.FLUIDS.ordinal(), NbtAccess.getInt(tagRoot, "target", 0));
        assertEquals("forge:water", NbtAccess.getString(entry(tagRoot), "tag", ""));
    }

    private static CompoundTag custom(String id) {
        CompoundTag custom = new CompoundTag();
        custom.put("ln_" + id, new CompoundTag());
        return custom;
    }

    private static CompoundTag entry(CompoundTag root) {
        return (CompoundTag) NbtAccess.getList(root, "items", 10).get(0);
    }
}
