package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.client.ClientRegistries;
import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.NbtPath;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final List<String> ENTRY_FIELDS = List.of("item", "fluid", "chemical", "tag", "amount", "batch",
            "stock", "slot_map", "slot_map_expr", "enchanted", "nbt_rules", "nbt_match_any", "nbt_strict", "nbt_raw",
            "dur_op", "dur_val", "nbt_path", "nbt_val", "nbt_op");

    private LegacyComponentMigration() {
    }

    public static boolean migrateWrench(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        GlobalPos componentLink = stack.get(LogisticsDataComponents.WRENCH_AE2_LINK);
        if (componentLink != null) {
            if (!stack.has(LogisticsDataComponents.WRENCH_STORAGE_LINK)) {
                stack.set(LogisticsDataComponents.WRENCH_STORAGE_LINK,
                        new StorageLink(StorageBackend.AE2, componentLink));
            }
            stack.remove(LogisticsDataComponents.WRENCH_AE2_LINK);
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("ln_wrench")) {
            return true;
        }
        CompoundTag custom = data.copyTag();
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
            if (!stack.has(LogisticsDataComponents.WRENCH_STORAGE_LINK)) {
                GlobalPos.CODEC.parse(NbtOps.INSTANCE, link).result().ifPresent(value ->
                        stack.set(LogisticsDataComponents.WRENCH_STORAGE_LINK,
                                new StorageLink(StorageBackend.AE2,
                                        GlobalPos.of(value.dimension(), value.pos().immutable()))));
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
                if (provider == null && !NodeClipboardConfig.canDecodeItems(clipboard, null)) return false;
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
        CompoundTag root = legacyRoot(stack, GENERAL_ROOT);
        if (root == null) {
            return true;
        }
        if (!stack.has(LogisticsDataComponents.FILTER_ENTRIES)) {
            GeneralFilterConfig config = readGeneralFilter(root, provider != null ? provider : currentRegistries());
            if (config == null) {
                return false;
            }
            if (!config.entries().isEmpty()) {
                stack.set(LogisticsDataComponents.FILTER_ENTRIES, config);
            }
        }
        migrateSettings(stack, root, null);
        removeRoot(stack, GENERAL_ROOT);
        return true;
    }

    @Nullable
    private static HolderLookup.Provider currentRegistries() {
        // Client stacks need client holders
        HolderLookup.Provider client = FMLEnvironment.getDist().isClient() ? ClientRegistries.onClientThread() : null;
        if (client != null) {
            return client;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    @Nullable
    static GeneralFilterConfig readGeneralFilter(CompoundTag root, @Nullable HolderLookup.Provider provider) {
        List<GeneralFilterEntry> entries = new ArrayList<>();
        for (Tag tag : root.getListOrEmpty("items")) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            StackSnapshot item = null;
            if (entry.get("item") instanceof CompoundTag itemTag) {
                item = readItem(itemTag, provider);
                if (item == null && provider == null) {
                    // Retry once registries exist
                    return null;
                }
                if (item == null) {
                    continue;
                }
            }
            GeneralFilterEntry read = readEntry(entry, item);
            if (!read.isEmpty()) {
                entries.add(read);
            }
        }
        return new GeneralFilterConfig(entries);
    }

    @Nullable
    private static StackSnapshot readItem(CompoundTag tag, @Nullable HolderLookup.Provider provider) {
        ItemStack stack = provider == null
                ? ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY)
                : ComponentCodecs.QUIET_STACK.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
                        .result().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return null;
        }
        FilterComponentData.migrate(stack, provider);
        return StackSnapshot.of(stack.copyWithCount(1));
    }

    private static GeneralFilterEntry readEntry(CompoundTag entry, @Nullable StackSnapshot item) {
        GeneralFilterEntry.EntryCounts counts = new GeneralFilterEntry.EntryCounts(
                entry.getIntOr("amount", 0), entry.getIntOr("batch", 0), entry.getIntOr("stock", 0));
        GeneralFilterEntry.SlotMapping mapping = new GeneralFilterEntry.SlotMapping(
                Arrays.stream(entry.getIntArray("slot_map").orElse(new int[0])).boxed().toList(),
                entry.getStringOr("slot_map_expr", ""));
        Boolean enchanted = entry.contains("enchanted") ? entry.getBooleanOr("enchanted", false) : null;
        GeneralFilterEntry.NbtConstraints nbt = new GeneralFilterEntry.NbtConstraints(readRules(entry),
                entry.getBooleanOr("nbt_match_any", false),
                entry.contains("nbt_strict")
                        ? Optional.of(entry.getBooleanOr("nbt_strict", false))
                        : Optional.empty(),
                entry.getStringOr("nbt_raw", ""));
        GeneralFilterEntry.DurabilityConstraint durability = entry.contains("dur_op")
                ? new GeneralFilterEntry.DurabilityConstraint(
                        DurabilityFilterData.Operator.fromId(entry.getStringOr("dur_op", "")),
                        entry.getIntOr("dur_val", 0))
                : null;
        return new GeneralFilterEntry(entry.getIntOr("slot", 0), item, readString(entry, "fluid"),
                readString(entry, "chemical"), readString(entry, "tag"), counts, mapping, enchanted, nbt, durability);
    }

    private static List<NbtCriterion> readRules(CompoundTag entry) {
        List<NbtCriterion> rules = new ArrayList<>();
        for (Tag tag : entry.getListOrEmpty("nbt_rules")) {
            if (tag instanceof CompoundTag rule) {
                NbtCriterion criterion = readRule(rule.getStringOr("p", ""), rule.getStringOr("o", ""), rule.get("v"));
                if (criterion != null) {
                    rules.add(criterion);
                }
            }
        }
        if (!rules.isEmpty()) {
            return rules;
        }
        NbtCriterion single = readRule(entry.getStringOr("nbt_path", ""), entry.getStringOr("nbt_op", ""),
                entry.get("nbt_val"));
        return single == null ? List.of() : List.of(single);
    }

    @Nullable
    private static NbtCriterion readRule(String path, String operator, @Nullable Tag value) {
        NbtPath parsed = NbtPath.parseLenient(path);
        return parsed.isEmpty() || value == null ? null : new NbtCriterion(parsed, operator, value);
    }

    @Nullable
    private static String readString(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getStringOr(key, "") : null;
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
                    DurabilityFilterData.Operator.fromId(root.getStringOr("operator", "")));
            if (config.value() != 0 || config.operator() != DurabilityFilterData.Operator.GREATER_OR_EQUAL) {
                stack.set(LogisticsDataComponents.DURABILITY_FILTER, config);
            }
        });
    }

    public static void migrateNbtFilter(ItemStack stack) {
        migrate(stack, NBT_ROOT, root -> {
            List<NbtFilterConfig.Rule> rules = readNbtRules(root);
            NbtPath inferredPath = rules.isEmpty()
                    ? NbtPath.parseLenient(root.getStringOr("path", ""))
                    : rules.getFirst().path();
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
            NbtPath path = NbtPath.parseLenient(rule.getStringOr("path", "").trim());
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
        NbtPath path = NbtPath.parseLenient(root.getStringOr("path", "").trim());
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

    @Nullable
    private static CompoundTag legacyRoot(ItemStack stack, String rootKey) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(rootKey)) {
            return null;
        }
        return custom.copyTag().get(rootKey) instanceof CompoundTag root ? root : null;
    }

    private static CompoundTag getLegacyRoot(ItemStack stack, String rootKey) {
        CompoundTag root = legacyRoot(stack, rootKey);
        return root == null ? new CompoundTag() : root;
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
        CompoundTag root = legacyRoot(stack, rootKey);
        if (root == null) {
            return;
        }
        migration.accept(root);
        removeRoot(stack, rootKey);
    }

    private static void removeRoot(ItemStack stack, String rootKey) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
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
            ENTRY_FIELDS.forEach(extra::remove);
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
