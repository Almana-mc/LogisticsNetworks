package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.NameFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NameFilterData {

    private static final String KEY_ROOT = "ln_name_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_NAME = "name";
    private static final String KEY_TARGET_TYPE = "target";

    private NameFilterData() {
    }

    public static boolean isNameFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NameFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isNameFilter(stack))
            return false;
        return getRoot(stack).getBoolean(KEY_IS_BLACKLIST);
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isNameFilter(stack))
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
        if (!isNameFilter(stack))
            return FilterTargetType.ITEMS;
        CompoundTag root = getRoot(stack);
        return FilterTargetType.fromOrdinal(root.getInt(KEY_TARGET_TYPE));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isNameFilter(stack))
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

    public static String getNameFilter(ItemStack stack) {
        if (!isNameFilter(stack))
            return "";
        CompoundTag root = getRoot(stack);
        return root.contains(KEY_NAME, Tag.TAG_STRING) ? root.getString(KEY_NAME) : "";
    }

    public static void setNameFilter(ItemStack stack, String name) {
        if (!isNameFilter(stack))
            return;

        updateRoot(stack, root -> {
            String normalized = normalizeName(name);
            if (normalized == null || normalized.isEmpty()) {
                root.remove(KEY_NAME);
            } else {
                root.putString(KEY_NAME, normalized);
            }
        });
    }

    public static boolean hasNameFilter(ItemStack stack) {
        return !getNameFilter(stack).isEmpty();
    }

    public static boolean containsName(ItemStack filter, ItemStack candidate) {
        if (candidate.isEmpty())
            return false;
        if (getTargetType(filter) != FilterTargetType.ITEMS)
            return false;

        String name = getNameFilter(filter);
        if (name.isEmpty())
            return false;

        String candidateName = candidate.getHoverName().getString().toLowerCase();
        return candidateName.contains(name);
    }

    public static boolean containsName(ItemStack filter, FluidStack candidate) {
        if (candidate == null || candidate.isEmpty())
            return false;
        if (getTargetType(filter) != FilterTargetType.FLUIDS)
            return false;

        String name = getNameFilter(filter);
        if (name.isEmpty())
            return false;

        String candidateName = candidate.getHoverName().getString().toLowerCase();
        return candidateName.contains(name);
    }

    public static boolean containsName(ItemStack filter, String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty())
            return false;
        if (getTargetType(filter) != FilterTargetType.CHEMICALS)
            return false;

        String name = getNameFilter(filter);
        if (name.isEmpty())
            return false;

        Component chemName = MekanismCompat.getChemicalTextComponent(chemicalId);
        String displayName = chemName != null ? chemName.getString().toLowerCase() : chemicalId.toLowerCase();
        return displayName.contains(name);
    }

    private static String normalizeName(String name) {
        if (name == null)
            return null;
        String s = name.trim().toLowerCase();
        return s.isEmpty() ? null : s;
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, java.util.function.Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = customTag.contains(KEY_ROOT, Tag.TAG_COMPOUND)
                    ? customTag.getCompound(KEY_ROOT)
                    : new CompoundTag();

            modifier.accept(root);

            if (root.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, root);
            }
        });
    }
}
