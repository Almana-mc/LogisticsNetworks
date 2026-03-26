package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.item.ModFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ModFilterData {

    private static final String KEY_ROOT = "ln_mod_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_MODS = "mods";
    private static final String KEY_TARGET_TYPE = "target";

    private ModFilterData() {
    }

    public static boolean isModFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ModFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isModFilter(stack))
            return false;
        return getRoot(stack).getBooleanOr(KEY_IS_BLACKLIST, false);
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isModFilter(stack))
            return;

        updateRoot(stack, root -> {
            if (isBlacklist) {
                root.putBoolean(KEY_IS_BLACKLIST, true);
            } else {
                root.remove(KEY_IS_BLACKLIST);
            }
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isModFilter(stack))
            return FilterTargetType.ITEMS;
        CompoundTag root = getRoot(stack);
        return FilterTargetType.fromOrdinal(root.getIntOr(KEY_TARGET_TYPE, FilterTargetType.ITEMS.ordinal()));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isModFilter(stack))
            return;

        FilterTargetType target = type == null ? FilterTargetType.ITEMS : type;
        updateRoot(stack, root -> {
            if (target == FilterTargetType.ITEMS) {
                root.remove(KEY_TARGET_TYPE);
            } else {
                root.putInt(KEY_TARGET_TYPE, target.ordinal());
            }
        });
    }

    public static List<String> getModFilters(ItemStack stack) {
        if (!isModFilter(stack))
            return List.of();

        CompoundTag root = getRoot(stack);
        if (!root.contains(KEY_MODS))
            return List.of();

        ListTag list = root.getListOrEmpty(KEY_MODS);
        List<String> mods = new ArrayList<>(list.size());

        for (int i = 0; i < list.size(); i++) {
            Tag tag = list.get(i);
            if (tag instanceof StringTag st) {
                String modId = normalizeModId(st.value());
                if (modId != null) {
                    mods.add(modId);
                }
            }
        }
        return mods;
    }

    public static boolean hasAnyMods(ItemStack stack) {
        return !getModFilters(stack).isEmpty();
    }

    public static boolean addModFilter(ItemStack stack, String rawModId) {
        if (!isModFilter(stack))
            return false;
        String modId = normalizeModId(rawModId);
        if (modId == null)
            return false;

        boolean[] changed = { false };
        updateRoot(stack, root -> {
            ListTag list = root.getListOrEmpty(KEY_MODS);

            for (Tag t : list) {
                if (t instanceof StringTag st && st.value().equals(modId))
                    return;
            }

            list.add(StringTag.valueOf(modId));
            root.put(KEY_MODS, list);
            changed[0] = true;
        });
        return changed[0];
    }

    public static boolean setSingleModFilter(ItemStack stack, String rawModId) {
        if (!isModFilter(stack))
            return false;
        String modId = normalizeModId(rawModId);
        if (modId == null)
            return false;

        boolean[] changed = { false };
        updateRoot(stack, root -> {
            ListTag list = root.getListOrEmpty(KEY_MODS);
            boolean alreadySingle = list.size() == 1
                    && list.get(0) instanceof StringTag st
                    && modId.equals(st.value());
            if (alreadySingle) {
                return;
            }

            ListTag single = new ListTag();
            single.add(StringTag.valueOf(modId));
            root.put(KEY_MODS, single);
            changed[0] = true;
        });
        return changed[0];
    }

    public static boolean removeModFilter(ItemStack stack, String rawModId) {
        if (!isModFilter(stack))
            return false;
        String modId = normalizeModId(rawModId);
        if (modId == null)
            return false;

        boolean[] changed = { false };
        updateRoot(stack, root -> {
            if (!root.contains(KEY_MODS))
                return;

            ListTag list = root.getListOrEmpty(KEY_MODS);
            boolean removed = list.removeIf(t -> t instanceof StringTag st && st.value().equals(modId));

            if (removed) {
                if (list.isEmpty()) {
                    root.remove(KEY_MODS);
                } else {
                    root.put(KEY_MODS, list);
                }
                changed[0] = true;
            }
        });
        return changed[0];
    }

    public static boolean containsMod(ItemStack stack, ItemStack candidate) {
        if (candidate.isEmpty())
            return false;
        if (getTargetType(stack) != FilterTargetType.ITEMS)
            return false;

        return checkModMatch(stack, BuiltInRegistries.ITEM.getKey(candidate.getItem()));
    }

    public static boolean containsMod(ItemStack stack, FluidStack candidate) {
        if (candidate == null || candidate.isEmpty())
            return false;
        if (getTargetType(stack) != FilterTargetType.FLUIDS)
            return false;

        return checkModMatch(stack, BuiltInRegistries.FLUID.getKey(candidate.getFluid()));
    }

    private static boolean checkModMatch(ItemStack stack, Identifier id) {
        if (id == null)
            return false;
        String namespace = id.getNamespace();

        for (String mod : getModFilters(stack)) {
            if (mod.equals(namespace))
                return true;
        }
        return false;
    }

    public static boolean containsMod(ItemStack stack, String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty())
            return false;
        if (getTargetType(stack) != FilterTargetType.CHEMICALS)
            return false;

        Identifier id = Identifier.tryParse(chemicalId);
        return checkModMatch(stack, id);
    }

    private static String normalizeModId(String modId) {
        if (modId == null)
            return null;
        String s = modId.trim().toLowerCase();
        if (s.isEmpty())
            return null;

        // "minecraft:stone" -> "minecraft"
        int colon = s.indexOf(':');
        if (colon != -1) {
            return s.substring(0, colon); // just the namespace
        }
        return s;
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.getCompound(KEY_ROOT).orElseGet(CompoundTag::new);
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = customTag.getCompound(KEY_ROOT).orElseGet(CompoundTag::new);

            modifier.accept(root);

            if (root.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, root);
            }
        });
    }
}
