package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
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

    private LegacyComponentMigration() {
    }

    public static boolean migrateWrench(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!(custom.get("ln_wrench") instanceof CompoundTag root)) {
            return true;
        }
        CompoundTag before = root.copy();
        if (!stack.has(LogisticsDataComponents.WRENCH_MODE)) {
            WrenchItem.Mode mode = WrenchItem.Mode.fromId(root.getStringOr("mode", ""));
            if (mode != WrenchItem.Mode.WRENCH) {
                stack.set(LogisticsDataComponents.WRENCH_MODE, mode);
            }
        }
        root.remove("mode");
        migrateWrenchPositions(stack, root);
        boolean complete = migrateWrenchClipboard(stack, root, provider);
        if (!before.equals(root)) {
            if (root.isEmpty()) {
                custom.remove("ln_wrench");
            }
            writeCustomData(stack, custom);
        }
        return complete;
    }

    private static void migrateWrenchPositions(ItemStack stack, CompoundTag root) {
        if (root.get("ae2_link") instanceof CompoundTag link) {
            if (!stack.has(LogisticsDataComponents.WRENCH_AE2_LINK)) {
                GlobalPos.CODEC.parse(NbtOps.INSTANCE, link).result().ifPresent(value ->
                        stack.set(LogisticsDataComponents.WRENCH_AE2_LINK,
                                GlobalPos.of(value.dimension(), value.pos().immutable())));
            }
            link.remove("dimension");
            link.remove("pos");
            if (link.isEmpty()) root.remove("ae2_link");
        }
        if (!stack.has(LogisticsDataComponents.WRENCH_MASS_PLACEMENT)) {
            WrenchMassPlacement value = readMassPlacement(root);
            if (!value.isEmpty()) stack.set(LogisticsDataComponents.WRENCH_MASS_PLACEMENT, value);
        }
        for (String key : List.of("mass_dimension", "mass_corner_a", "mass_corner_b", "mass_selected_block")) {
            root.remove(key);
        }
        stripWrenchEntries(root, "mass_selections", List.of("dimension", "pos"));
    }

    private static boolean migrateWrenchClipboard(ItemStack stack, CompoundTag root,
            @Nullable HolderLookup.Provider provider) {
        if (!hasLegacyClipboard(root)) return true;
        if (!stack.has(LogisticsDataComponents.WRENCH_CLIPBOARD)) {
            Tag tag = root.get("clipboard");
            if (tag instanceof CompoundTag clipboard) {
                if (!NodeClipboardConfig.canDecodeItems(clipboard, provider)) return false;
                NodeClipboardConfig config = NodeClipboardConfig.load(clipboard, provider);
                stack.set(LogisticsDataComponents.WRENCH_CLIPBOARD, config == null
                        ? WrenchClipboard.invalid() : WrenchClipboard.valid(config.toComponentSnapshot(provider)));
            } else {
                stack.set(LogisticsDataComponents.WRENCH_CLIPBOARD, WrenchClipboard.invalid());
            }
        }
        stripWrenchClipboard(root);
        return true;
    }

    public static boolean hasWrenchClipboard(ItemStack stack) {
        return stack.has(LogisticsDataComponents.WRENCH_CLIPBOARD)
                || hasLegacyClipboard(getLegacyRoot(stack, "ln_wrench"));
    }

    private static boolean hasLegacyClipboard(CompoundTag root) {
        Tag tag = root.get("clipboard");
        if (tag == null) return false;
        if (!(tag instanceof CompoundTag clipboard)) return true;
        if (clipboard.isEmpty() || clipboard.contains("version")) return true;
        if (!(clipboard.get("channels") instanceof ListTag channels)) return false;
        return channels.isEmpty() || channels.stream().anyMatch(value ->
                value instanceof CompoundTag channel && channel.contains("index"));
    }

    public static void clearWrenchClipboard(ItemStack stack) {
        migrateWrench(stack, null);
        stack.remove(LogisticsDataComponents.WRENCH_CLIPBOARD);
        if (hasLegacyClipboard(getLegacyRoot(stack, "ln_wrench"))) {
            updateLegacyRoot(stack, "ln_wrench", LegacyComponentMigration::stripWrenchClipboard);
        }
    }

    private static void stripWrenchClipboard(CompoundTag root) {
        if (!(root.get("clipboard") instanceof CompoundTag clipboard)) {
            root.remove("clipboard");
            return;
        }
        for (String key : List.of("version", "network_id", "network_name", "renderVisible", "node_label")) {
            clipboard.remove(key);
        }
        stripWrenchEntries(clipboard, "channels", List.of("index", "enabled", "mode", "type", "batch", "delay",
                "io", "redstone", "distribution", "filter_mode", "priority", "name"));
        stripWrenchEntries(clipboard, "filters", List.of("channel", "slot", "item"));
        stripWrenchEntries(clipboard, "upgrades", List.of("slot", "item"));
        stripWrenchEntries(clipboard, "required_items", List.of("item", "count"));
        if (clipboard.isEmpty()) root.remove("clipboard");
    }

    private static void stripWrenchEntries(CompoundTag root, String key, List<String> fields) {
        if (!(root.get(key) instanceof ListTag entries)) return;
        ListTag residual = new ListTag();
        for (Tag entry : entries) {
            if (entry instanceof CompoundTag compound) {
                CompoundTag remaining = compound.copy();
                fields.forEach(remaining::remove);
                if (!remaining.isEmpty()) residual.add(remaining);
            } else {
                residual.add(entry.copy());
            }
        }
        if (residual.isEmpty()) root.remove(key);
        else root.put(key, residual);
    }

    private static WrenchMassPlacement readMassPlacement(CompoundTag root) {
        Optional<WrenchMassPlacement.Area> area = Optional.empty();
        Identifier dimensionId = root.getString("mass_dimension").filter(value -> !value.isBlank())
                .map(Identifier::tryParse).orElse(null);
        if (dimensionId != null && root.getLong("mass_corner_a").isPresent()) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            GlobalPos first = GlobalPos.of(dimension, BlockPos.of(root.getLongOr("mass_corner_a", 0)));
            Optional<BlockPos> second = root.getLong("mass_corner_b").map(BlockPos::of);
            area = Optional.of(new WrenchMassPlacement.Area(first, second));
        }
        Optional<Identifier> selectedBlock = root.getString("mass_selected_block").filter(value -> !value.isBlank())
                .map(Identifier::tryParse);
        List<GlobalPos> selections = new ArrayList<>();
        for (Tag value : root.getListOrEmpty("mass_selections")) {
            if (!(value instanceof CompoundTag entry)) continue;
            Identifier dimension = entry.getString("dimension").filter(id -> !id.isBlank())
                    .map(Identifier::tryParse).orElse(null);
            if (dimension != null && entry.getLong("pos").isPresent()) {
                selections.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLongOr("pos", 0))));
            }
        }
        return new WrenchMassPlacement(area, selectedBlock, selections);
    }

    public static void migrateTagFilter(ItemStack stack) {
        migrate(stack, TAG_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.TAG_FILTER)) {
                return;
            }
            ListTag tags = root.getListOrEmpty("tags");
            for (int i = 0; i < tags.size(); i++) {
                String tag = FilterTagUtil.normalizeTag(tags.getStringOr(i, ""));
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

    public static CompoundTag getGeneralFilterRoot(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        boolean migrated = migrateGeneralFilter(stack, provider);
        GeneralFilterConfig config = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        CompoundTag root = migrated
                ? GeneralFilterBridge.write(config == null ? new GeneralFilterConfig(List.of()) : config, provider)
                : getLegacyRoot(stack, GENERAL_ROOT);
        if (!migrated && config != null) {
            CompoundTag typed = GeneralFilterBridge.write(config, provider);
            root.remove("items");
            if (typed.contains("items")) root.put("items", typed.get("items"));
        }
        if (migrated || stack.has(LogisticsDataComponents.FILTER_SETTINGS)) {
            writeSettings(root, FilterSettingsData.get(stack));
        }
        return root;
    }

    public static void updateGeneralFilterRoot(ItemStack stack, @Nullable HolderLookup.Provider provider,
            Consumer<CompoundTag> modifier) {
        boolean hasLegacy = hasLegacyRoot(stack, GENERAL_ROOT);
        boolean migrated = migrateGeneralFilter(stack, provider);
        if (!hasLegacy || migrated || stack.has(LogisticsDataComponents.FILTER_ENTRIES)) {
            GeneralFilterConfig current = stack.getOrDefault(LogisticsDataComponents.FILTER_ENTRIES,
                    new GeneralFilterConfig(List.of()));
            CompoundTag root = getGeneralFilterRoot(stack, provider);
            modifier.accept(root);
            GeneralFilterBridge.ReadResult result = GeneralFilterBridge.read(root, provider, current);
            if (!result.complete()) {
                updateLegacyRoot(stack, GENERAL_ROOT, pending -> pending.merge(root));
                return;
            }
            migrateSettingsFromWorkingRoot(stack, root, !migrated);
            if (migrated && result.config().entries().isEmpty()) {
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
            ListTag mods = root.getListOrEmpty("mods");
            for (int i = 0; i < mods.size(); i++) {
                String value = mods.getStringOr(i, "").trim().toLowerCase(Locale.ROOT);
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
            String expression = root.getStringOr("name", "").trim();
            if (!expression.isEmpty()) {
                stack.set(LogisticsDataComponents.NAME_FILTER,
                        new NameFilterConfig(expression, me.almana.logisticsnetworks.filter.NameMatchScope.fromOrdinal(root.getIntOr("scope", 0))));
            }
        });
    }

    public static void migrateAmountFilter(ItemStack stack) {
        migrate(stack, AMOUNT_ROOT, root -> {
            migrateSettings(stack, root, null);
            if (stack.has(LogisticsDataComponents.AMOUNT_FILTER)) {
                return;
            }
            int amount = root.contains("amount") ? root.getIntOr("amount", 0) : AmountFilterConfig.DEFAULT_AMOUNT;
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
                    root.getIntOr("value", 0),
                    me.almana.logisticsnetworks.filter.DurabilityFilterData.Operator.fromId(root.getStringOr("operator", "")));
            if (config.value() != 0 || config.operator() != me.almana.logisticsnetworks.filter.DurabilityFilterData.Operator.GREATER_OR_EQUAL) {
                stack.set(LogisticsDataComponents.DURABILITY_FILTER, config);
            }
        });
    }

    public static void migrateNbtFilter(ItemStack stack) {
        migrate(stack, NBT_ROOT, root -> {
            List<NbtFilterConfig.Rule> rules = readNbtRules(root);
            String inferredPath = rules.isEmpty() ? root.getStringOr("path", "") : rules.getFirst().path();
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
            int[] stored = root.getIntArray("slots").orElse(new int[0]);
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
        ListTag stored = root.getListOrEmpty("rules");
        for (Tag tag : stored) {
            if (!(tag instanceof CompoundTag rule)) {
                continue;
            }
            String path = rule.getStringOr("path", "").trim();
            Tag value = rule.get("value");
            if (path.isEmpty() || value == null) {
                continue;
            }
            NbtFilterData.Operator operator = rule.contains("operator")
                    ? NbtFilterData.Operator.fromOrdinal(rule.getIntOr("operator", 0))
                    : NbtFilterData.Operator.EQUALS;
            boolean enabled = !rule.contains("enabled") || rule.getBooleanOr("enabled", false);
            rules.add(new NbtFilterConfig.Rule(path, operator, value, enabled));
        }
        if (!rules.isEmpty()) {
            return rules;
        }
        String path = root.getStringOr("path", "").trim();
        Tag value = root.get("value");
        return path.isEmpty() || value == null
                ? List.of()
                : List.of(new NbtFilterConfig.Rule(path, NbtFilterData.Operator.EQUALS, value, true));
    }

    private static void migrateSettings(ItemStack stack, CompoundTag root, FilterTargetType inferredTarget) {
        if (stack.has(LogisticsDataComponents.FILTER_SETTINGS)) {
            return;
        }
        FilterTargetType target = root.contains("target")
                ? FilterTargetType.fromOrdinal(root.getIntOr("target", 0))
                : inferredTarget == null ? FilterTargetType.ITEMS : inferredTarget;
        FilterSettings settings = new FilterSettings(target, root.getBooleanOr("blacklist", false));
        if (!settings.isDefault()) {
            stack.set(LogisticsDataComponents.FILTER_SETTINGS, settings);
        }
    }

    private static void migrateSettingsFromWorkingRoot(ItemStack stack, CompoundTag root, boolean pending) {
        FilterSettings settings = new FilterSettings(
                FilterTargetType.fromOrdinal(root.getIntOr("target", 0)), root.getBooleanOr("blacklist", false));
        if (pending) {
            stack.set(LogisticsDataComponents.FILTER_SETTINGS, settings);
        } else {
            FilterSettingsData.set(stack, settings);
        }
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
        CompoundTag remaining = custom.getCompoundOrEmpty(rootKey).copy();
        remaining.remove("target");
        remaining.remove("blacklist");
        List<String> fields = switch (rootKey) {
            case TAG_ROOT -> List.of("tags");
            case MOD_ROOT -> List.of("mods");
            case NAME_ROOT -> List.of("name", "scope");
            case AMOUNT_ROOT -> List.of("amount");
            case DURABILITY_ROOT -> List.of("operator", "value");
            case NBT_ROOT -> List.of("path", "value");
            case SLOT_ROOT -> List.of("slots");
            default -> List.of();
        };
        fields.forEach(remaining::remove);
        if (rootKey.equals(GENERAL_ROOT)) {
            retainUnknownEntries(remaining);
        } else if (rootKey.equals(NBT_ROOT)) {
            retainUnknownNbtRules(remaining);
        }
        if (remaining.isEmpty()) {
            custom.remove(rootKey);
        } else {
            custom.put(rootKey, remaining);
        }
        writeCustomData(stack, custom);
    }

    private static void retainUnknownEntries(CompoundTag remaining) {
        ListTag entries = new ListTag();
        for (Tag tag : remaining.getListOrEmpty("items")) {
            if (!(tag instanceof CompoundTag entry)) {
                entries.add(tag.copy());
                continue;
            }
            CompoundTag extra = entry.copy();
            ListTag rules = new ListTag();
            for (Tag rule : extra.getListOrEmpty("nbt_rules")) {
                if (rule instanceof CompoundTag criterion) {
                    CompoundTag unknown = criterion.copy();
                    List.of("p", "o", "v").forEach(unknown::remove);
                    if (!unknown.isEmpty()) rules.add(unknown);
                }
            }
            GeneralFilterBridge.ENTRY_FIELDS.forEach(extra::remove);
            if (!rules.isEmpty()) extra.put("nbt_rules", rules);
            if (extra.size() > (extra.contains("slot") ? 1 : 0)) entries.add(extra);
        }
        remaining.remove("items");
        if (!entries.isEmpty()) remaining.put("items", entries);
    }

    private static void retainUnknownNbtRules(CompoundTag remaining) {
        ListTag rules = new ListTag();
        for (Tag tag : remaining.getListOrEmpty("rules")) {
            if (tag instanceof CompoundTag rule) {
                CompoundTag extra = rule.copy();
                if (!rule.getStringOr("path", "").trim().isEmpty() && rule.contains("value")) {
                    List.of("path", "operator", "value", "enabled").forEach(extra::remove);
                }
                if (!extra.isEmpty()) rules.add(extra);
            } else {
                rules.add(tag.copy());
            }
        }
        remaining.remove("rules");
        if (!rules.isEmpty()) remaining.put("rules", rules);
    }

    private static void writeCustomData(ItemStack stack, CompoundTag custom) {
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().equals(custom)) {
            return;
        }
        if (custom.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
    }
}
