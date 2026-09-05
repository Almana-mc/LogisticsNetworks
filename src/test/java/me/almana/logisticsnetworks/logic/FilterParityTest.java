package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilterParityTest {
    static RegistryAccess.Frozen provider;

    @BeforeAll
    static void bootstrap() throws Exception {
        TransferParityTest.bootstrap();
        provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        bind.invoke(Items.IRON_PICKAXE.builtInRegistryHolder(),
                List.of(TagKey.create(Registries.ITEM, Identifier.parse("parity:tools"))));
        bind.invoke(Fluids.WATER.builtInRegistryHolder(),
                List.of(TagKey.create(Registries.FLUID, Identifier.parse("parity:water"))));
    }

    @Test
    void fluidTagRequiresEntryNbt() {
        var filter = filter();
        FilterItemData.setTargetType(filter, FilterTargetType.FLUIDS);
        FilterItemData.setEntryTag(filter, 0, "parity:water");
        FilterItemData.setEntryNbt(filter, 0, "missing", IntTag.valueOf(1));
        assertFalse(FilterItemData.containsFluidFull(filter, new FluidStack(Fluids.WATER, 1000), provider));
        FilterItemData.setEntryNbt(filter, 0, null, null);
        assertTrue(FilterItemData.containsFluidFull(filter, new FluidStack(Fluids.WATER, 1000), provider));
    }

    @Test
    void tagStockAndBatchRequireNbt() {
        var filter = amountFilter();
        FilterItemData.setEntryNbt(filter, 0, "quality", IntTag.valueOf(1));
        var components = new CompoundTag();
        assertAmounts(filter, components, 0, 0);
        components.putInt("quality", 1);
        assertAmounts(filter, components, 7, 3);
    }

    @Test
    void tagStockAndBatchRequireDurability() {
        var filter = amountFilter();
        FilterItemData.setEntryDurability(filter, 0, "le", 50);
        assertAmounts(filter, null, 0, 0);
        FilterItemData.setEntryDurability(filter, 0, "ge", 50);
        assertAmounts(filter, null, 7, 3);
    }

    @Test
    void tagStockAndBatchRequireEnchanted() {
        var filter = amountFilter();
        FilterItemData.setEntryEnchanted(filter, 0, true);
        assertAmounts(filter, null, 0, 0);
        FilterItemData.setEntryEnchanted(filter, 0, false);
        assertAmounts(filter, null, 7, 3);
    }

    @Test
    void itemMatchAllStopsAfterFailedWhitelist() {
        var filter = filter();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.DIAMOND), provider);
        var later = untouchedFilter();
        assertFalse(FilterLogic.matchesItem(new ItemStack[] {filter, later}, FilterMode.MATCH_ALL,
                new ItemStack(Items.IRON_INGOT), provider, null));
        verifyNoInteractions(later);
    }

    @Test
    void slottedItemMatchAllStopsAfterFailedWhitelist() {
        var filter = filter();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.DIAMOND), provider);
        var later = untouchedFilter();
        assertFalse(FilterLogic.matchesItemInSlot(new ItemStack[] {filter, later}, FilterMode.MATCH_ALL,
                new ItemStack(Items.IRON_INGOT), provider, null, null, 0));
        verifyNoInteractions(later);
    }

    @Test
    void fluidMatchAllStopsAfterFailedWhitelist() {
        var filter = filter();
        FilterItemData.setTargetType(filter, FilterTargetType.FLUIDS);
        FilterItemData.setEntryTag(filter, 0, "parity:other");
        var later = untouchedFilter();
        assertFalse(FilterLogic.matchesFluid(new ItemStack[] {filter, later}, FilterMode.MATCH_ALL,
                new FluidStack(Fluids.WATER, 1000), provider));
        verifyNoInteractions(later);
    }

    @Test
    void chemicalMatchAllStopsAfterFailedWhitelist() {
        var filter = filter();
        FilterItemData.setTargetType(filter, FilterTargetType.CHEMICALS);
        FilterItemData.setChemicalEntry(filter, 0, "mekanism:oxygen");
        var later = untouchedFilter();
        assertFalse(FilterLogic.matchesChemical(new ItemStack[] {filter, later}, FilterMode.MATCH_ALL,
                "mekanism:hydrogen"));
        verifyNoInteractions(later);
    }

    @Test
    void batchAndStockCapRealTransfers() throws Exception {
        var filter = filter();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT), provider);
        FilterItemData.setEntryBatch(filter, 0, 3);
        FilterItemData.setEntryStock(filter, 0, 4);
        var source = TransferParityTest.inventory(2, 6);
        var target = TransferParityTest.inventory(0);
        assertEquals(3, TransferParityTest.move(source, List.of(target), false,
                new ItemStack[] {filter}, new ItemStack[0]));
        assertEquals(3, target.getAmountAsInt(0));
        assertEquals(5, source.getAmountAsInt(0) + source.getAmountAsInt(1));
    }

    @Test
    void exportStockKeepsFourItemsAcrossSourceSlots() throws Exception {
        var filter = filter();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT), provider);
        FilterItemData.setEntryStock(filter, 0, 4);
        var source = TransferParityTest.inventory(2, 6);
        var target = TransferParityTest.inventory(0);
        assertEquals(4, TransferParityTest.move(source, List.of(target), false,
                new ItemStack[] {filter}, new ItemStack[0]));
        assertEquals(4, source.getAmountAsInt(0) + source.getAmountAsInt(1));
        assertEquals(4, target.getAmountAsInt(0));
    }

    @Test
    void importStockCapsExistingTargetTotal() throws Exception {
        var filter = filter();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT), provider);
        FilterItemData.setEntryStock(filter, 0, 4);
        var source = TransferParityTest.inventory(8);
        var target = TransferParityTest.inventory(2);
        assertEquals(2, TransferParityTest.move(source, List.of(target), true,
                new ItemStack[0], new ItemStack[] {filter}));
        assertEquals(6, source.getAmountAsInt(0));
        assertEquals(4, target.getAmountAsInt(0));
    }

    @Test
    void roundRobinBatchCapsEachTargetSeparately() throws Exception {
        var filter = filter();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT), provider);
        FilterItemData.setEntryBatch(filter, 0, 3);
        var source = TransferParityTest.inventory(2, 6);
        var first = TransferParityTest.inventory(0);
        var second = TransferParityTest.inventory(0);
        assertEquals(6, TransferParityTest.move(source, List.of(first, second), true,
                new ItemStack[] {filter}, new ItemStack[0]));
        assertEquals(3, first.getAmountAsInt(0));
        assertEquals(3, second.getAmountAsInt(0));
        assertEquals(2, source.getAmountAsInt(0) + source.getAmountAsInt(1));
    }

    @Test
    void strictMatchingPreservesExactComponents() {
        var filter = filter();
        var expected = new ItemStack(Items.IRON_INGOT);
        expected.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("kept"));
        FilterItemData.setEntry(filter, 0, expected, provider);
        FilterItemData.setEntryNbtStrict(filter, 0, true);
        assertTrue(FilterItemData.containsItemFull(filter, expected, provider));
        assertFalse(FilterItemData.containsItemFull(filter, new ItemStack(Items.IRON_INGOT), provider));
    }

    @Test
    void matchAnyStillChecksLaterWhitelistsAndBlacklists() {
        var miss = filter();
        FilterItemData.setEntry(miss, 0, new ItemStack(Items.DIAMOND), provider);
        var match = filter();
        FilterItemData.setEntry(match, 0, new ItemStack(Items.IRON_INGOT), provider);
        var candidate = new ItemStack(Items.IRON_INGOT);
        assertTrue(FilterLogic.matchesItem(new ItemStack[] {miss, match}, FilterMode.MATCH_ANY, candidate, provider, null));
        FilterItemData.setBlacklist(match, true);
        assertFalse(FilterLogic.matchesItem(new ItemStack[] {miss, match}, FilterMode.MATCH_ANY, candidate, provider, null));
    }

    private static ItemStack amountFilter() {
        var filter = filter();
        FilterItemData.setEntryTag(filter, 0, "parity:tools");
        FilterItemData.setEntryStock(filter, 0, 7);
        FilterItemData.setEntryBatch(filter, 0, 3);
        return filter;
    }

    private static void assertAmounts(ItemStack filter, CompoundTag components, int stock, int batch) {
        var candidate = new ItemStack(Items.IRON_PICKAXE);
        assertEquals(stock, FilterItemData.getItemAmountThresholdFull(filter, candidate, provider, components));
        assertEquals(batch, FilterItemData.getItemBatchLimitFull(filter, candidate, provider, components, null));
    }

    static ItemStack filter() {
        var item = mock(BaseFilterItem.class);
        when(item.getSlotCount()).thenReturn(9);
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }

    private static ItemStack untouchedFilter() {
        return mock(ItemStack.class);
    }
}
