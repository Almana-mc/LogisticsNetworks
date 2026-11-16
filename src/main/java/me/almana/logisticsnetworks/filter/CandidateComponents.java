package me.almana.logisticsnetworks.filter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CandidateComponents {
    static final NbtPath COMPONENTS_PATH = NbtPath.of(NbtPath.Component.of("components"));
    static final NbtPath FLUID_COMPONENTS_PATH = NbtPath.of(NbtPath.Component.of("fluid"),
            NbtPath.Component.of("components"));
    private static final String TAG = "tag";
    private static final String DURABILITY = "minecraft:durability";
    private static final String ENCHANTED = "minecraft:enchanted";
    private static final List<String> DERIVED = List.of("minecraft:max_stack_size", "minecraft:rarity",
            "minecraft:damage", "minecraft:max_damage", DURABILITY, ENCHANTED);

    private final ItemStack item;
    private final @Nullable CompoundTag fluidTag;
    private final Map<String, Tag> values = new HashMap<>();
    private @Nullable CompoundTag full;

    public CandidateComponents(ItemStack item, HolderLookup.Provider provider) {
        this(item, (CompoundTag) null);
    }

    private CandidateComponents(ItemStack item, @Nullable CompoundTag fluidTag) {
        this.item = item;
        this.fluidTag = fluidTag;
    }

    public static @Nullable CandidateComponents of(FluidStack fluid, HolderLookup.Provider provider) {
        return fluid.hasTag() ? new CandidateComponents(ItemStack.EMPTY, fluid.getTag()) : null;
    }

    public @Nullable Tag resolve(NbtPath path) {
        if (path.isEmpty())
            return null;
        if (path.equals(COMPONENTS_PATH) || path.equals(FLUID_COMPONENTS_PATH))
            return full().copy();
        if (path.startsWith(FLUID_COMPONENTS_PATH))
            path = path.drop(2);
        else if (path.startsWith(COMPONENTS_PATH))
            path = path.drop(1);
        if (!(path.components()[0] instanceof NbtPath.StringComponent key))
            return null;
        Tag found = path.drop(1).getFrom(get(key.value()));
        return found == null ? null : found.copy();
    }

    CompoundTag select(Set<String> keys) {
        CompoundTag selected = new CompoundTag();
        for (String key : keys) {
            Tag value = get(key);
            if (value != null)
                selected.put(key, value);
        }
        return selected;
    }

    CompoundTag full() {
        if (full == null) {
            CompoundTag map = new CompoundTag();
            Tag tag = get(TAG);
            if (tag != null)
                map.put(TAG, tag);
            for (String key : DERIVED) {
                Tag value = get(key);
                if (value != null)
                    map.put(key, value);
            }
            full = map;
        }
        return full;
    }

    private @Nullable Tag get(String key) {
        Tag value = values.get(key);
        if (value == null && !values.containsKey(key)) {
            value = compute(key);
            values.put(key, value);
        }
        return value;
    }

    private @Nullable Tag compute(String key) {
        // 1.20.1 keeps item data in one tag
        if (key.equals(TAG)) {
            CompoundTag tag = item.isEmpty() ? fluidTag : item.getTag();
            return tag == null ? null : tag.copy();
        }
        return item.isEmpty() ? null : derived(key);
    }

    private @Nullable Tag derived(String key) {
        boolean damageable = item.isDamageableItem();
        return switch (key) {
            case "minecraft:max_stack_size" -> IntTag.valueOf(item.getMaxStackSize());
            case "minecraft:rarity" -> StringTag.valueOf(item.getRarity().name().toLowerCase(Locale.ROOT));
            case "minecraft:damage" -> damageable ? IntTag.valueOf(item.getDamageValue()) : null;
            case "minecraft:max_damage" -> damageable ? IntTag.valueOf(item.getMaxDamage()) : null;
            case DURABILITY -> IntTag.valueOf(damageable ? Math.max(0, item.getMaxDamage() - item.getDamageValue()) : 0);
            case ENCHANTED -> ByteTag.valueOf(item.isEnchanted());
            // 1.3.4 rules could name these
            case "id" -> StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            case "count" -> ByteTag.valueOf((byte) item.getCount());
            default -> null;
        };
    }
}
