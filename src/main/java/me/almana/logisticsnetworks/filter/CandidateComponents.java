package me.almana.logisticsnetworks.filter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CandidateComponents {
    private static final String DURABILITY = "minecraft:durability";
    private static final String ENCHANTED = "minecraft:enchanted";
    private static final List<String> DERIVED = List.of("minecraft:max_stack_size", "minecraft:rarity",
            "minecraft:damage", "minecraft:max_damage", DURABILITY, ENCHANTED);

    private final ItemStack item;
    private final HolderLookup.Provider provider;
    private final Map<String, Tag> values = new HashMap<>();
    private @Nullable DataComponentPatch patch;
    private @Nullable CompoundTag full;

    public CandidateComponents(ItemStack item, HolderLookup.Provider provider) {
        this.item = item;
        this.provider = provider;
    }

    private CandidateComponents(DataComponentPatch patch, HolderLookup.Provider provider) {
        this(ItemStack.EMPTY, provider);
        this.patch = patch;
    }

    public static @Nullable CandidateComponents of(FluidStack fluid, HolderLookup.Provider provider) {
        DataComponentPatch patch = fluid.getComponentsPatch();
        return patch.isEmpty() ? null : new CandidateComponents(patch, provider);
    }

    public @Nullable Tag resolve(NbtPath path) {
        if (path.isEmpty())
            return null;
        if (path.equals(NbtFilterData.COMPONENTS_PATH) || path.equals(NbtFilterData.FLUID_COMPONENTS_PATH))
            return full().copy();
        if (path.startsWith(NbtFilterData.FLUID_COMPONENTS_PATH))
            path = path.drop(2);
        else if (path.startsWith(NbtFilterData.COMPONENTS_PATH))
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
            CompoundTag map = (CompoundTag) DataComponentPatch.CODEC
                    .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), patch()).getOrThrow();
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
        // Removed components use full map
        if (key.startsWith("!"))
            return full().get(key);
        if (item.isEmpty())
            return encode(key);
        // Always derived, never encoded
        if (key.equals(DURABILITY) || key.equals(ENCHANTED))
            return derived(key);
        Tag value = encode(key);
        return value != null ? value : derived(key);
    }

    private @Nullable Tag encode(String key) {
        Identifier id = Identifier.tryParse(key);
        DataComponentType<?> type = id == null || !id.toString().equals(key)
                ? null
                : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        return type == null || type.isTransient() ? null : encodeValue(type);
    }

    private @Nullable <T> Tag encodeValue(DataComponentType<T> type) {
        Optional<? extends T> value = patch().getPatch(type);
        if (value == null || value.isEmpty())
            return null;
        return type.codecOrThrow().encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), value.get())
                .getOrThrow();
    }

    private DataComponentPatch patch() {
        if (patch == null)
            patch = item.getComponentsPatch();
        return patch;
    }

    private @Nullable Tag derived(String key) {
        boolean damageable = item.isDamageableItem();
        return switch (key) {
            case "minecraft:max_stack_size" -> IntTag.valueOf(item.getMaxStackSize());
            case "minecraft:rarity" -> StringTag.valueOf(item.getRarity().getSerializedName());
            case "minecraft:damage" -> damageable ? IntTag.valueOf(item.getDamageValue()) : null;
            case "minecraft:max_damage" -> damageable ? IntTag.valueOf(item.getMaxDamage()) : null;
            case DURABILITY -> IntTag.valueOf(damageable ? Math.max(0, item.getMaxDamage() - item.getDamageValue()) : 0);
            case ENCHANTED -> ByteTag.valueOf(item.isEnchanted());
            default -> null;
        };
    }
}
