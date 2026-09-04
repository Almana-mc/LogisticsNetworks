package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class LegacyComponentMigration {

    private static final String GENERAL_ROOT = "ln_filter";
    private static final String TAG_ROOT = "ln_tag_filter";
    private static final String MOD_ROOT = "ln_mod_filter";
    private static final String NAME_ROOT = "ln_name_filter";
    private static final String AMOUNT_ROOT = "ln_amount_filter";
    private static final String DURABILITY_ROOT = "ln_durability_filter";
    private static final String NBT_ROOT = "ln_nbt_filter";
    private static final String SLOT_ROOT = "ln_slot_filter";
    private static final String WRENCH_ROOT = "ln_wrench";

    private LegacyComponentMigration() {
    }

    public static void migrateTagFilter(ItemStack stack) {
        migrate(stack, TAG_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.TAG_FILTER)) {
                return;
            }
            ListTag tags = root.getList("tags", Tag.TAG_STRING);
            for (int i = 0; i < tags.size(); i++) {
                String tag = FilterTagUtil.normalizeTag(tags.getString(i));
                if (tag != null) {
                    stack.set(LogisticsDataComponents.TAG_FILTER, new TagFilterConfig(tag));
                    return;
                }
            }
        });
    }

    public static boolean migrateGeneralFilter(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!(custom.get(GENERAL_ROOT) instanceof CompoundTag root)) {
            return true;
        }
        GeneralFilterConfig current = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        if (current != null) {
            migrateSettings(stack, root, null);
            removeRoot(stack, custom, GENERAL_ROOT);
            return true;
        }
        GeneralFilterBridge.ReadResult result = GeneralFilterBridge.read(root, provider, current);
        if (!result.complete()) {
            return false;
        }
        migrateSettings(stack, root, null);
        if (current == null && !result.config().entries().isEmpty()) {
            stack.set(LogisticsDataComponents.FILTER_ENTRIES, result.config());
        }
        removeRoot(stack, custom, GENERAL_ROOT);
        return true;
    }

    public static boolean migrateWrench(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!(custom.get(WRENCH_ROOT) instanceof CompoundTag root)) {
            return true;
        }

        if (!stack.has(LogisticsDataComponents.WRENCH_MODE)) {
            WrenchItem.Mode mode = WrenchItem.Mode.fromId(root.getString("mode"));
            if (mode != WrenchItem.Mode.WRENCH) {
                stack.set(LogisticsDataComponents.WRENCH_MODE, mode);
            }
        }
        if (!stack.has(LogisticsDataComponents.WRENCH_AE2_LINK) && root.contains("ae2_link")) {
            GlobalPos.CODEC.parse(NbtOps.INSTANCE, root.get("ae2_link")).result()
                    .ifPresent(value -> stack.set(LogisticsDataComponents.WRENCH_AE2_LINK, value));
        }
        if (!stack.has(LogisticsDataComponents.WRENCH_MASS_PLACEMENT)) {
            WrenchMassPlacement massPlacement = readMassPlacement(root);
            if (!massPlacement.isEmpty()) {
                stack.set(LogisticsDataComponents.WRENCH_MASS_PLACEMENT, massPlacement);
            }
        }

        boolean clipboardComplete = true;
        Tag clipboardTag = root.get("clipboard");
        if (!stack.has(LogisticsDataComponents.WRENCH_CLIPBOARD) && clipboardTag != null) {
            if (!(clipboardTag instanceof CompoundTag clipboardRoot)) {
                stack.set(LogisticsDataComponents.WRENCH_CLIPBOARD, WrenchClipboard.invalid());
            } else if (provider == null && clipboardHasItems(clipboardRoot)) {
                clipboardComplete = false;
            } else {
                NodeClipboardConfig clipboard = NodeClipboardConfig.load(clipboardRoot, provider);
                WrenchClipboard value = clipboard == null
                        ? WrenchClipboard.invalid()
                        : WrenchClipboard.valid(clipboard.toComponentSnapshot(provider));
                stack.set(LogisticsDataComponents.WRENCH_CLIPBOARD, value);
            }
        }

        if (clipboardComplete || stack.has(LogisticsDataComponents.WRENCH_CLIPBOARD)) {
            removeRoot(stack, custom, WRENCH_ROOT);
            return true;
        }

        CompoundTag pending = new CompoundTag();
        pending.put("clipboard", root.getCompound("clipboard").copy());
        custom.put(WRENCH_ROOT, pending);
        writeCustomData(stack, custom);
        return false;
    }

    public static boolean hasWrenchClipboard(ItemStack stack) {
        if (stack.has(LogisticsDataComponents.WRENCH_CLIPBOARD)) {
            return true;
        }
        return getLegacyRoot(stack, WRENCH_ROOT).contains("clipboard");
    }

    public static void clearWrenchClipboard(ItemStack stack) {
        migrateWrench(stack, null);
        stack.remove(LogisticsDataComponents.WRENCH_CLIPBOARD);
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!(custom.get(WRENCH_ROOT) instanceof CompoundTag stored)) {
            return;
        }
        CompoundTag root = stored.copy();
        root.remove("clipboard");
        if (root.isEmpty()) {
            custom.remove(WRENCH_ROOT);
        } else {
            custom.put(WRENCH_ROOT, root);
        }
        writeCustomData(stack, custom);
    }

    public static CompoundTag getGeneralFilterRoot(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        boolean migrated = migrateGeneralFilter(stack, provider);
        GeneralFilterConfig config = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        CompoundTag root = migrated
                ? GeneralFilterBridge.write(config == null ? new GeneralFilterConfig(List.of()) : config, provider)
                : getLegacyRoot(stack, GENERAL_ROOT);
        writeSettings(root, FilterSettingsData.get(stack));
        return root;
    }

    public static void updateGeneralFilterRoot(ItemStack stack, @Nullable HolderLookup.Provider provider,
            Consumer<CompoundTag> modifier) {
        boolean hasLegacy = hasLegacyRoot(stack, GENERAL_ROOT);
        boolean migrated = migrateGeneralFilter(stack, provider);
        if (!hasLegacy || migrated || stack.has(LogisticsDataComponents.FILTER_ENTRIES)) {
            GeneralFilterConfig current = stack.getOrDefault(LogisticsDataComponents.FILTER_ENTRIES,
                    new GeneralFilterConfig(List.of()));
            CompoundTag root = GeneralFilterBridge.write(current, provider);
            writeSettings(root, FilterSettingsData.get(stack));
            modifier.accept(root);
            migrateSettingsFromWorkingRoot(stack, root);
            GeneralFilterBridge.ReadResult result = GeneralFilterBridge.read(root, provider, current);
            if (result.config().entries().isEmpty()) {
                stack.remove(LogisticsDataComponents.FILTER_ENTRIES);
            } else {
                stack.set(LogisticsDataComponents.FILTER_ENTRIES, result.config());
            }
            return;
        }
        updateLegacyRoot(stack, GENERAL_ROOT, modifier);
    }

    public static void migrateModFilter(ItemStack stack) {
        migrate(stack, MOD_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.MOD_FILTER)) {
                return;
            }
            List<String> namespaces = new ArrayList<>();
            ListTag mods = root.getList("mods", Tag.TAG_STRING);
            for (int i = 0; i < mods.size(); i++) {
                String value = mods.getString(i).trim().toLowerCase(Locale.ROOT);
                int separator = value.indexOf(':');
                if (separator >= 0) {
                    value = value.substring(0, separator);
                }
                if (!value.isEmpty()) {
                    namespaces.add(value);
                }
            }
            if (!namespaces.isEmpty()) {
                stack.set(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(namespaces));
            }
        });
    }

    public static void migrateNameFilter(ItemStack stack) {
        migrate(stack, NAME_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.NAME_FILTER)) {
                return;
            }
            String expression = root.getString("name").trim();
            if (!expression.isEmpty()) {
                stack.set(LogisticsDataComponents.NAME_FILTER,
                        new NameFilterConfig(expression, me.almana.logisticsnetworks.filter.NameMatchScope.fromOrdinal(root.getInt("scope"))));
            }
        });
    }

    public static void migrateAmountFilter(ItemStack stack) {
        migrate(stack, AMOUNT_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.AMOUNT_FILTER)) {
                return;
            }
            int amount = root.contains("amount", Tag.TAG_INT) ? root.getInt("amount") : AmountFilterConfig.DEFAULT_AMOUNT;
            AmountFilterConfig config = new AmountFilterConfig(amount);
            if (config.amount() != AmountFilterConfig.DEFAULT_AMOUNT) {
                stack.set(LogisticsDataComponents.AMOUNT_FILTER, config);
            }
        });
    }

    public static void migrateDurabilityFilter(ItemStack stack) {
        migrate(stack, DURABILITY_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.DURABILITY_FILTER)) {
                return;
            }
            DurabilityFilterConfig config = new DurabilityFilterConfig(
                    root.getInt("value"),
                    me.almana.logisticsnetworks.filter.DurabilityFilterData.Operator.fromId(root.getString("operator")));
            if (config.value() != 0 || config.operator() != me.almana.logisticsnetworks.filter.DurabilityFilterData.Operator.GREATER_OR_EQUAL) {
                stack.set(LogisticsDataComponents.DURABILITY_FILTER, config);
            }
        });
    }

    public static void migrateNbtFilter(ItemStack stack) {
        migrate(stack, NBT_ROOT, root -> {
            List<NbtFilterConfig.Rule> rules = readNbtRules(root);
            String inferredPath = rules.isEmpty() ? root.getString("path") : rules.getFirst().path();
            FilterTargetType inferred = NbtFilterData.isFluidPath(inferredPath)
                    ? FilterTargetType.FLUIDS
                    : FilterTargetType.ITEMS;
            migrateSettings(stack, root, inferred);
            if (!stack.has(LogisticsDataComponents.NBT_FILTER) && !rules.isEmpty()) {
                stack.set(LogisticsDataComponents.NBT_FILTER, new NbtFilterConfig(rules));
            }
        });
    }

    public static void migrateSlotFilter(ItemStack stack) {
        migrate(stack, SLOT_ROOT, root -> {
            migrateSettings(stack, root, FilterTargetType.ITEMS);
            if (stack.has(LogisticsDataComponents.SLOT_FILTER)) {
                return;
            }
            int[] stored = root.getIntArray("slots");
            if (stored.length > 0) {
                List<Integer> slots = new ArrayList<>(stored.length);
                for (int slot : stored) {
                    slots.add(slot);
                }
                SlotFilterConfig config = new SlotFilterConfig(slots);
                if (!config.slots().isEmpty()) {
                    stack.set(LogisticsDataComponents.SLOT_FILTER, config);
                }
            }
        });
    }

    private static List<NbtFilterConfig.Rule> readNbtRules(CompoundTag root) {
        List<NbtFilterConfig.Rule> rules = new ArrayList<>();
        ListTag stored = root.getList("rules", Tag.TAG_COMPOUND);
        for (Tag tag : stored) {
            if (!(tag instanceof CompoundTag rule)) {
                continue;
            }
            String path = rule.getString("path").trim();
            Tag value = rule.get("value");
            if (path.isEmpty() || value == null) {
                continue;
            }
            NbtFilterData.Operator operator = rule.contains("operator", Tag.TAG_INT)
                    ? NbtFilterData.Operator.fromOrdinal(rule.getInt("operator"))
                    : NbtFilterData.Operator.EQUALS;
            boolean enabled = !rule.contains("enabled", Tag.TAG_BYTE) || rule.getBoolean("enabled");
            rules.add(new NbtFilterConfig.Rule(path, operator, value, enabled));
        }
        if (!rules.isEmpty()) {
            return rules;
        }
        String path = root.getString("path").trim();
        Tag value = root.get("value");
        return path.isEmpty() || value == null
                ? List.of()
                : List.of(new NbtFilterConfig.Rule(path, NbtFilterData.Operator.EQUALS, value, true));
    }

    private static WrenchMassPlacement readMassPlacement(CompoundTag root) {
        Optional<WrenchMassPlacement.Area> area = Optional.empty();
        ResourceLocation dimensionId = ResourceLocation.tryParse(root.getString("mass_dimension"));
        if (dimensionId != null && root.contains("mass_corner_a", Tag.TAG_LONG)) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            GlobalPos first = GlobalPos.of(dimension, BlockPos.of(root.getLong("mass_corner_a")));
            Optional<BlockPos> second = root.contains("mass_corner_b", Tag.TAG_LONG)
                    ? Optional.of(BlockPos.of(root.getLong("mass_corner_b")))
                    : Optional.empty();
            area = Optional.of(new WrenchMassPlacement.Area(first, second));
        }

        Optional<ResourceLocation> selectedBlock = Optional.ofNullable(
                ResourceLocation.tryParse(root.getString("mass_selected_block")));
        List<GlobalPos> selections = new ArrayList<>();
        for (Tag value : root.getList("mass_selections", Tag.TAG_COMPOUND)) {
            if (!(value instanceof CompoundTag entry)) {
                continue;
            }
            ResourceLocation selectionDimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (selectionDimension != null && entry.contains("pos", Tag.TAG_LONG)) {
                selections.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, selectionDimension),
                        BlockPos.of(entry.getLong("pos"))));
            }
        }
        return new WrenchMassPlacement(area, selectedBlock, selections);
    }

    private static boolean clipboardHasItems(CompoundTag clipboard) {
        return !clipboard.getList("filters", Tag.TAG_COMPOUND).isEmpty()
                || !clipboard.getList("upgrades", Tag.TAG_COMPOUND).isEmpty();
    }

    private static void migrateSettings(ItemStack stack, CompoundTag root, FilterTargetType inferredTarget) {
        if (stack.has(LogisticsDataComponents.FILTER_SETTINGS)) {
            return;
        }
        FilterTargetType target = root.contains("target", Tag.TAG_INT)
                ? FilterTargetType.fromOrdinal(root.getInt("target"))
                : inferredTarget == null ? FilterTargetType.ITEMS : inferredTarget;
        FilterSettings settings = new FilterSettings(target, root.getBoolean("blacklist"));
        if (!settings.isDefault()) {
            stack.set(LogisticsDataComponents.FILTER_SETTINGS, settings);
        }
    }

    private static void migrateSettingsFromWorkingRoot(ItemStack stack, CompoundTag root) {
        FilterSettingsData.set(stack, new FilterSettings(
                FilterTargetType.fromOrdinal(root.getInt("target")), root.getBoolean("blacklist")));
    }

    private static void writeSettings(CompoundTag root, FilterSettings settings) {
        root.remove("target");
        root.remove("blacklist");
        if (settings.target() != FilterTargetType.ITEMS) {
            root.putInt("target", settings.target().ordinal());
        }
        if (settings.blacklist()) {
            root.putBoolean("blacklist", true);
        }
    }

    private static boolean hasLegacyRoot(ItemStack stack, String rootKey) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .get(rootKey) instanceof CompoundTag;
    }

    private static CompoundTag getLegacyRoot(ItemStack stack, String rootKey) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.get(rootKey) instanceof CompoundTag root ? root.copy() : new CompoundTag();
    }

    private static void updateLegacyRoot(ItemStack stack, String rootKey, Consumer<CompoundTag> modifier) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag root = custom.get(rootKey) instanceof CompoundTag stored ? stored.copy() : new CompoundTag();
        modifier.accept(root);
        if (root.isEmpty()) {
            custom.remove(rootKey);
        } else {
            custom.put(rootKey, root);
        }
        if (custom.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
    }

    private static void migrate(ItemStack stack, String rootKey, Consumer<CompoundTag> migration) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!(custom.get(rootKey) instanceof CompoundTag root)) {
            return;
        }
        migration.accept(root.copy());
        removeRoot(stack, custom, rootKey);
    }

    private static void removeRoot(ItemStack stack, CompoundTag custom, String rootKey) {
        custom.remove(rootKey);
        writeCustomData(stack, custom);
    }

    private static void writeCustomData(ItemStack stack, CompoundTag custom) {
        if (custom.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
    }
}
