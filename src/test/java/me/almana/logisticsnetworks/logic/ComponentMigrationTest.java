package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.component.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import java.util.List;
import java.util.Optional;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentMigrationTest {
    @BeforeAll
    static void bootstrap() {
        TransferParityTest.bootstrap();
    }

    @Test
    void registersPersistentNetworkComponents() {
        for (String id : new String[]{"filter_settings", "filter_entries", "tag_filter", "mod_filter", "name_filter",
                "amount_filter", "durability_filter", "nbt_filter", "slot_filter", "wrench_mode", "wrench_clipboard",
                "wrench_ae2_link", "wrench_mass_placement"}) {
            var component = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("logisticsnetworks", id));
            assertTrue(component.isPresent(), id);
            assertNotNull(component.orElseThrow().codec());
            assertNotNull(component.orElseThrow().streamCodec());
        }
    }

    @Test
    void generalAccessorMigratesWithoutLosingOtherCustomKeys() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        CompoundTag root = new CompoundTag();
        root.putBoolean("blacklist", true);
        root.putInt("target", 1);
        CompoundTag custom = new CompoundTag();
        custom.put("ln_filter", root);
        custom.putString("other_mod", "keep");
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        assertTrue(FilterItemData.isBlacklist(filter));
        assertEquals(FilterTargetType.FLUIDS, FilterItemData.getTargetType(filter));
        CompoundTag remaining = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        assertFalse(remaining.contains("ln_filter"));
        assertEquals("keep", remaining.getStringOr("other_mod", ""));
        var first = filter.getComponentsPatch();
        assertTrue(FilterItemData.isBlacklist(filter));
        assertEquals(first, filter.getComponentsPatch());
    }

    @Test
    void modAndNameAccessorsMigrateLegacyState() {
        ItemStack mod = new ItemStack(Registration.MOD_FILTER.get());
        CompoundTag root = new CompoundTag();
        ListTag mods = new ListTag();
        mods.add(StringTag.valueOf("minecraft"));
        root.put("mods", mods);
        legacy(mod, "ln_mod_filter", root);
        assertEquals(java.util.List.of("minecraft"), ModFilterData.getModFilters(mod));
        assertFalse(mod.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("ln_mod_filter"));
        ItemStack name = new ItemStack(Registration.NAME_FILTER.get());
        root = new CompoundTag();
        root.putString("name", "Iron");
        root.putInt("scope", 1);
        legacy(name, "ln_name_filter", root);
        assertEquals("Iron", NameFilterData.getNameFilter(name));
        assertEquals(NameMatchScope.fromOrdinal(1), NameFilterData.getMatchScope(name));
        assertFalse(name.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("ln_name_filter"));
    }

    @Test
    void entrySetterProducesTypedStateAndCacheSeesEdits() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var cache = FilterItemData.createReadCache();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT), provider);
        assertTrue(FilterItemData.hasAnyItemMatchEntries(filter, cache));
        FilterItemData.setBlacklist(filter, true);
        assertTrue(FilterItemData.isBlacklist(filter, cache));
        FilterItemData.setEntry(filter, 0, ItemStack.EMPTY, provider);
        assertFalse(FilterItemData.hasAnyItemMatchEntries(filter, cache));
        assertFalse(filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("ln_filter"));
    }

    @Test
    void deferredRegistryEntriesKeepSettingsAndOriginalRoot() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        CompoundTag root = itemRoot("missing_mod:missing_item");
        root.putBoolean("blacklist", true);
        root.putInt("target", 2);
        legacy(filter, "ln_filter", root);
        var before = filter.getComponentsPatch();
        assertFalse(LegacyComponentMigration.migrateGeneralFilter(filter, registries()));
        assertEquals(before, filter.getComponentsPatch());
        assertTrue(FilterItemData.isBlacklist(filter));
        assertEquals(FilterTargetType.CHEMICALS, FilterItemData.getTargetType(filter));
        assertEquals(before, filter.getComponentsPatch());
        filter.set(LogisticsDataComponents.FILTER_ENTRIES, new GeneralFilterConfig(List.of(GeneralFilterEntry.empty(2))));
        var config = filter.get(LogisticsDataComponents.FILTER_ENTRIES);
        assertFalse(LegacyComponentMigration.migrateGeneralFilter(filter, registries()));
        assertSame(config, filter.get(LogisticsDataComponents.FILTER_ENTRIES));
        assertTrue(FilterItemData.isBlacklist(filter));
        assertEquals(FilterTargetType.CHEMICALS, FilterItemData.getTargetType(filter));
        assertEquals(root, filter.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_filter"));
    }

    @Test
    void registryDependentLegacyEntryDefersUntilProviderArrives() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        CompoundTag root = itemRoot("minecraft:iron_ingot");
        CompoundTag enchantments = new CompoundTag(); enchantments.putInt("minecraft:sharpness", 1);
        CompoundTag components = new CompoundTag(); components.put("minecraft:enchantments", enchantments);
        root.getListOrEmpty("items").getCompoundOrEmpty(0).getCompoundOrEmpty("item").put("components", components);
        legacy(filter, "ln_filter", root);
        assertFalse(LegacyComponentMigration.migrateGeneralFilter(filter, null));
        assertEquals(root, filter.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_filter"));
        var provider = net.minecraft.data.registries.VanillaRegistries.createLookup();
        assertTrue(LegacyComponentMigration.migrateGeneralFilter(filter, provider));
        assertTrue(FilterItemData.getEntry(filter, 0, provider).is(Items.IRON_INGOT));
        assertTrue(FilterItemData.getEntry(filter, 0, provider).isEnchanted());
        assertFalse(filter.has(DataComponents.CUSTOM_DATA));
        FilterItemData.setEntryStock(filter, 0, 19);
        assertTrue(FilterItemData.getEntry(filter, 0, provider).isEnchanted());
        assertEquals(19, filter.get(LogisticsDataComponents.FILTER_ENTRIES).entries().getFirst().counts().stock());
    }

    @Test
    void legacyEntryPreservesAllMetadataAndUnknownFields() {
        ItemStack filter = new ItemStack(Registration.BIG_FILTER.get());
        CompoundTag root = itemRoot("minecraft:iron_pickaxe");
        CompoundTag entry = root.getListOrEmpty("items").getCompoundOrEmpty(0);
        entry.putInt("amount", 9);
        entry.putInt("stock", 7);
        entry.putInt("batch", 3);
        entry.putIntArray("slot_map", new int[]{1, 4});
        entry.putString("slot_map_expr", "1,4");
        entry.putBoolean("enchanted", false);
        entry.putString("dur_op", "ge");
        entry.putInt("dur_val", 23);
        entry.putString("nbt_raw", "{foo:1}");
        entry.putBoolean("nbt_strict", false);
        entry.putBoolean("nbt_match_any", true);
        entry.putString("nbt_path", "minecraft:damage");
        entry.putString("nbt_op", ">=");
        entry.putInt("nbt_val", 4);
        entry.putString("future_entry", "preserve");
        root.putString("future_root", "preserve");
        legacy(filter, "ln_filter", root);
        assertTrue(LegacyComponentMigration.migrateGeneralFilter(filter, registries()));
        var config = filter.get(LogisticsDataComponents.FILTER_ENTRIES);
        var value = config.entries().getFirst();
        assertEquals(new GeneralFilterEntry.EntryCounts(9, 3, 7), value.counts());
        assertEquals(List.of(1, 4), value.slotMapping().slots());
        assertEquals("1,4", value.slotMapping().expression());
        assertEquals(Boolean.FALSE, value.enchanted());
        assertEquals(23, value.durability().value());
        assertEquals(DurabilityFilterData.Operator.GREATER_OR_EQUAL, value.durability().operator());
        assertEquals(Optional.of(false), value.nbt().strict());
        assertTrue(value.nbt().matchAny());
        assertEquals("{foo:1}", value.nbt().raw());
        assertEquals("minecraft:damage", value.nbt().rules().getFirst().path());
        assertEquals(">=", value.nbt().rules().getFirst().operator());
        assertEquals(4, value.nbt().rules().getFirst().value().asInt().orElseThrow());
        CompoundTag remaining = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty("ln_filter");
        assertEquals("preserve", remaining.getStringOr("future_root", ""));
        assertEquals("preserve", remaining.getListOrEmpty("items").getCompoundOrEmpty(0).getStringOr("future_entry", ""));
        var before = filter.getComponentsPatch();
        assertTrue(LegacyComponentMigration.migrateGeneralFilter(filter, registries()));
        assertEquals(before, filter.getComponentsPatch());
        FilterItemData.setEntry(filter, 0, ItemStack.EMPTY, registries());
        assertTrue(FilterItemData.getEntry(filter, 0, registries()).isEmpty());
    }

    @Test
    void typedModAndNameEditsInvalidatePersistentCache() {
        var cache = FilterItemData.createReadCache();
        ItemStack mod = new ItemStack(Registration.MOD_FILTER.get());
        ModFilterData.addModFilter(mod, "minecraft");
        assertTrue(ModFilterData.containsMod(mod, new ItemStack(Items.IRON_INGOT), cache));
        mod.set(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(List.of("other")));
        assertFalse(ModFilterData.containsMod(mod, new ItemStack(Items.IRON_INGOT), cache));
        mod.set(LogisticsDataComponents.FILTER_SETTINGS, new FilterSettings(FilterTargetType.CHEMICALS, true));
        assertEquals(FilterTargetType.CHEMICALS, ModFilterData.getTargetType(mod, cache));
        assertTrue(ModFilterData.isBlacklist(mod, cache));
        ItemStack name = new ItemStack(Registration.NAME_FILTER.get());
        NameFilterData.setNameFilter(name, "Iron");
        assertTrue(NameFilterData.containsName(name, new ItemStack(Items.IRON_INGOT), cache));
        name.set(LogisticsDataComponents.NAME_FILTER, new NameFilterConfig("Gold", NameMatchScope.NAME));
        assertFalse(NameFilterData.containsName(name, new ItemStack(Items.IRON_INGOT), cache));
        name.set(LogisticsDataComponents.FILTER_SETTINGS, new FilterSettings(FilterTargetType.FLUIDS, true));
        assertEquals(FilterTargetType.FLUIDS, NameFilterData.getTargetType(name, cache));
        assertTrue(NameFilterData.isBlacklist(name, cache));
    }

    @Test
    void supportsRemovedFilterLegacyValuesWithoutRestoringItems() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        CompoundTag custom = new CompoundTag();
        CompoundTag amount = new CompoundTag(); amount.putInt("amount", 17); custom.put("ln_amount_filter", amount);
        CompoundTag durability = new CompoundTag(); durability.putInt("value", 45); durability.putString("operator", "le");
        custom.put("ln_durability_filter", durability);
        CompoundTag slots = new CompoundTag(); slots.putIntArray("slots", new int[]{4, 2, 4, -1, 54}); custom.put("ln_slot_filter", slots);
        CompoundTag nbt = new CompoundTag(); nbt.putString("path", "minecraft:damage"); nbt.putInt("value", 3);
        nbt.putInt("target", 2); custom.put("ln_nbt_filter", nbt);
        CompoundTag tags = new CompoundTag(); ListTag tagList = new ListTag(); tagList.add(StringTag.valueOf("#minecraft:logs"));
        tags.put("tags", tagList); custom.put("ln_tag_filter", tags);
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        FilterComponentData.migrate(filter, registries());
        assertEquals(17, filter.get(LogisticsDataComponents.AMOUNT_FILTER).amount());
        assertEquals(45, filter.get(LogisticsDataComponents.DURABILITY_FILTER).value());
        assertEquals(DurabilityFilterData.Operator.LESS_OR_EQUAL, filter.get(LogisticsDataComponents.DURABILITY_FILTER).operator());
        assertEquals(List.of(2, 4), filter.get(LogisticsDataComponents.SLOT_FILTER).slots());
        assertEquals("minecraft:logs", filter.get(LogisticsDataComponents.TAG_FILTER).tag());
        assertEquals(FilterTargetType.CHEMICALS, filter.get(LogisticsDataComponents.FILTER_SETTINGS).target());
        assertEquals("minecraft:damage", filter.get(LogisticsDataComponents.NBT_FILTER).rules().getFirst().path());
        var first = filter.getComponentsPatch();
        FilterComponentData.migrate(filter, registries());
        assertEquals(first, filter.getComponentsPatch());
    }

    @Test
    void sameCacheTracksPendingLegacyIdentityAndKeepsUnchangedView() throws Exception {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        legacy(filter, "ln_filter", itemRoot("missing_mod:missing_item"));
        var cache = FilterItemData.createReadCache();
        assertFalse(FilterItemData.isBlacklist(filter, cache));
        var field = FilterItemData.ReadCache.class.getDeclaredField("itemViews");
        field.setAccessible(true);
        var views = (java.util.Map<?, ?>) field.get(cache);
        Object first = views.get(filter);
        assertFalse(FilterItemData.isBlacklist(filter, cache));
        assertSame(first, views.get(filter));
        CustomData.update(DataComponents.CUSTOM_DATA, filter, custom ->
                custom.getCompoundOrEmpty("ln_filter").putBoolean("blacklist", true));
        assertTrue(FilterItemData.isBlacklist(filter, cache));
        assertNotSame(first, views.get(filter));
        filter.set(LogisticsDataComponents.FILTER_SETTINGS, new FilterSettings(FilterTargetType.ITEMS, false));
        assertFalse(FilterItemData.isBlacklist(filter, cache));
    }

    @Test
    void generalMigrationKeepsFluidChemicalTagAndLegacyAmountFallback() {
        ItemStack filter = new ItemStack(Registration.BIG_FILTER.get());
        ListTag entries = new ListTag();
        for (int slot = 0; slot < 3; slot++) {
            CompoundTag entry = new CompoundTag(); entry.putInt("slot", slot); entry.putInt("amount", 13);
            entry.putString(new String[]{"fluid", "chemical", "tag"}[slot],
                    new String[]{"minecraft:water", "mekanism:oxygen", "minecraft:logs"}[slot]);
            entries.add(entry);
        }
        CompoundTag root = new CompoundTag(); root.put("items", entries);
        legacy(filter, "ln_filter", root);
        assertTrue(LegacyComponentMigration.migrateGeneralFilter(filter, registries()));
        var values = filter.get(LogisticsDataComponents.FILTER_ENTRIES).entries();
        assertEquals("minecraft:water", values.get(0).fluidId());
        assertEquals("mekanism:oxygen", values.get(1).chemicalId());
        assertEquals("minecraft:logs", values.get(2).tag());
        assertEquals(13, FilterItemData.getEntryStock(filter, 0));
        assertEquals(13, FilterItemData.getEntryStock(filter, 1));
        assertEquals(13, FilterItemData.getEntryStock(filter, 2));
    }

    @Test
    void singleNbtAccessorsAndRuleEditorShareTypedState() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.setEntryTag(filter, 0, "minecraft:logs");
        FilterItemData.setEntryNbt(filter, 0, "quality", net.minecraft.nbt.IntTag.valueOf(1), ">=");
        assertEquals("quality", FilterItemData.getEntryNbtPath(filter, 0));
        assertEquals(">=", FilterItemData.getEntryNbtOperator(filter, 0));
        assertEquals(net.minecraft.nbt.IntTag.valueOf(1), FilterItemData.getEntryNbtValue(filter, 0));
        assertTrue(FilterItemData.setSlotNbtRuleValue(filter, 0, 0, net.minecraft.nbt.IntTag.valueOf(3)));
        assertEquals(net.minecraft.nbt.IntTag.valueOf(3), FilterItemData.getSlotNbtRules(filter, 0).getFirst().value());
        FilterItemData.setEntryNbt(filter, 0, null, null);
        assertTrue(FilterItemData.getSlotNbtRules(filter, 0).isEmpty());
        FilterItemData.setEntryNbt(filter, 0, "quality", net.minecraft.nbt.IntTag.valueOf(4));
        FilterItemData.setEntryNbtRaw(filter, 0, "{quality:5}");
        assertTrue(FilterItemData.getSlotNbtRules(filter, 0).isEmpty());
        assertEquals("{quality:5}", FilterItemData.getEntryNbtRaw(filter, 0));
    }

    @Test
    void clearingTypedEntriesCannotReviveDeferredLegacyEntries() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.GOLD_INGOT), registries());
        CompoundTag root = itemRoot("missing_mod:missing_item");
        root.putBoolean("blacklist", true);
        legacy(filter, "ln_filter", root);
        FilterItemData.setEntry(filter, 0, ItemStack.EMPTY, registries());
        assertTrue(FilterItemData.isBlacklist(filter));
        FilterItemData.setBlacklist(filter, false);
        assertFalse(FilterItemData.isBlacklist(filter));
        CustomData.update(DataComponents.CUSTOM_DATA, filter, custom -> custom.getCompoundOrEmpty("ln_filter")
                .getListOrEmpty("items").getCompoundOrEmpty(0).getCompoundOrEmpty("item").putString("id", "minecraft:iron_ingot"));
        assertTrue(LegacyComponentMigration.migrateGeneralFilter(filter, registries()));
        assertTrue(FilterItemData.getEntry(filter, 0, registries()).isEmpty());
        assertFalse(FilterItemData.isBlacklist(filter));
    }

    @Test
    void shrinkingRuleListRemainsEditableFromTwoToOneToZero() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        assertTrue(FilterItemData.addSlotNbtRule(filter, 0, "first", "=", net.minecraft.nbt.IntTag.valueOf(1)));
        assertTrue(FilterItemData.addSlotNbtRule(filter, 0, "second", ">=", net.minecraft.nbt.IntTag.valueOf(2)));
        assertEquals(List.of("first", "second"), FilterItemData.getSlotNbtRules(filter, 0).stream()
                .map(FilterItemData.SlotNbtRule::path).toList());
        assertTrue(FilterItemData.removeSlotNbtRule(filter, 0, 0));
        assertEquals("second", FilterItemData.getSlotNbtRules(filter, 0).getFirst().path());
        assertTrue(FilterItemData.setSlotNbtRuleValue(filter, 0, 0, net.minecraft.nbt.IntTag.valueOf(3)));
        assertEquals(net.minecraft.nbt.IntTag.valueOf(3), FilterItemData.getSlotNbtRules(filter, 0).getFirst().value());
        assertTrue(FilterItemData.removeSlotNbtRule(filter, 0, 0));
        assertTrue(FilterItemData.getSlotNbtRules(filter, 0).isEmpty());
        assertFalse(FilterItemData.hasEntryNbt(filter, 0));
        assertTrue(FilterItemData.addSlotNbtRule(filter, 0, "third", "!=", net.minecraft.nbt.IntTag.valueOf(4)));
        assertEquals("third", FilterItemData.getSlotNbtRules(filter, 0).getFirst().path());
    }

    static RegistryAccess.Frozen registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    static CompoundTag itemRoot(String id) {
        CompoundTag item = new CompoundTag(); item.putString("id", id); item.putInt("count", 1);
        CompoundTag entry = new CompoundTag(); entry.putInt("slot", 0); entry.put("item", item);
        ListTag entries = new ListTag(); entries.add(entry);
        CompoundTag root = new CompoundTag(); root.put("items", entries);
        return root;
    }

    static void legacy(ItemStack stack, String key, CompoundTag root) {
        CompoundTag custom = new CompoundTag();
        custom.put(key, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
}
