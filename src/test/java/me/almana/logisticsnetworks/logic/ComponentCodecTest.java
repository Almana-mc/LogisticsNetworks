package me.almana.logisticsnetworks.logic;

import io.netty.buffer.Unpooled;
import me.almana.logisticsnetworks.component.*;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.recipe.FilterCopyClearRecipe;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ComponentCodecTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TransferParityTest.bootstrap();
        var bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        for (var item : List.of(Registration.SMALL_FILTER, Registration.MEDIUM_FILTER, Registration.BIG_FILTER,
                Registration.MOD_FILTER, Registration.NAME_FILTER)) {
            bind.invoke(item.get().builtInRegistryHolder(), List.of(ModTags.FILTERS));
        }
    }

    @Test
    void allThirteenComponentsSurviveRegistryAndNetworkStackRoundTrips() {
        ItemStack filter = populatedStack();
        var ops = ComponentMigrationTest.registries().createSerializationContext(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, filter).getOrThrow();
        ItemStack disk = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(filter, disk));
        assertEquals(filter.getComponentsPatch().hashCode(), disk.getComponentsPatch().hashCode());
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ComponentMigrationTest.registries());
        try {
            ItemStack.STREAM_CODEC.encode(buf, filter);
            ItemStack network = ItemStack.STREAM_CODEC.decode(buf);
            assertTrue(ItemStack.isSameItemSameComponents(filter, network));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void copiedValuesOwnNbtCollectionsAndItemSnapshots() {
        CompoundTag value = new CompoundTag(); value.putInt("n", 4);
        var rules = new ArrayList<NbtCriterion>(); rules.add(new NbtCriterion("test", "=", value));
        var nbt = new GeneralFilterEntry.NbtConstraints(rules, false, Optional.empty(), "");
        ItemStack item = new ItemStack(Items.IRON_INGOT, 3);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(value));
        StackSnapshot snapshot = StackSnapshot.of(item);
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        var entry = new GeneralFilterEntry(0, snapshot, null, null, null, GeneralFilterEntry.EntryCounts.EMPTY,
                GeneralFilterEntry.SlotMapping.EMPTY, null, nbt, null);
        filter.set(LogisticsDataComponents.FILTER_ENTRIES, new GeneralFilterConfig(List.of(entry)));
        ItemStack copy = filter.copy();
        value.putInt("n", 8); rules.clear(); item.setCount(7);
        ((CompoundTag) entry.nbt().rules().getFirst().value()).putInt("n", 9);
        ItemStack restored = copy.get(LogisticsDataComponents.FILTER_ENTRIES).entries().getFirst().item().toStack();
        restored.setCount(15);
        CustomData.update(DataComponents.CUSTOM_DATA, restored, tag -> tag.putInt("n", 9));
        assertEquals(4, ((CompoundTag) entry.nbt().rules().getFirst().value()).getIntOr("n", 0));
        assertEquals(3, snapshot.toStack().getCount());
        assertEquals(4, snapshot.toStack().get(DataComponents.CUSTOM_DATA).copyTag().getIntOr("n", 0));
        assertThrows(UnsupportedOperationException.class, () -> entry.nbt().rules().clear());
        var before = filter.getComponentsPatch();
        FilterItemData.setEntryStock(copy, 0, 20);
        assertEquals(before, filter.getComponentsPatch());
        assertNotEquals(filter.getComponentsPatch(), copy.getComponentsPatch());
    }

    @Test
    void massSelectionValueOwnsMutablePositions() {
        var mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        var second = new BlockPos.MutableBlockPos(4, 5, 6);
        var pos = GlobalPos.of(Level.OVERWORLD, mutable);
        var placement = new WrenchMassPlacement(Optional.of(new WrenchMassPlacement.Area(pos, Optional.of(second))),
                Optional.empty(), List.of(pos));
        int hash = placement.hashCode();
        mutable.set(9, 9, 9); second.set(8, 8, 8);
        assertEquals(new BlockPos(1, 2, 3), placement.selections().getFirst().pos());
        assertEquals(new BlockPos(1, 2, 3), placement.area().orElseThrow().first().pos());
        assertEquals(new BlockPos(4, 5, 6), placement.area().orElseThrow().second().orElseThrow());
        assertEquals(hash, placement.hashCode());
    }

    @Test
    void recipeRecognizesTypedAndLegacyNameWithoutMutatingInputs() {
        for (boolean typed : List.of(false, true)) {
            ItemStack configured = new ItemStack(Registration.NAME_FILTER.get());
            if (typed) {
                NameFilterData.setNameFilter(configured, "Iron");
            } else {
                CompoundTag root = new CompoundTag(); root.putString("name", "Iron");
                ComponentMigrationTest.legacy(configured, "ln_name_filter", root);
            }
            ItemStack blank = new ItemStack(Registration.NAME_FILTER.get());
            var before = configured.getComponentsPatch();
            var input = CraftingInput.of(2, 1, List.of(configured, blank));
            assertTrue(FilterCopyClearRecipe.INSTANCE.matches(input, null));
            assertEquals(before, configured.getComponentsPatch());
            ItemStack output = FilterCopyClearRecipe.INSTANCE.assemble(input);
            assertEquals(2, output.getCount());
            assertEquals(before, output.getComponentsPatch());
            assertTrue(FilterCopyClearRecipe.INSTANCE.assemble(CraftingInput.of(1, 1, List.of(configured)))
                    .getComponentsPatch().isEmpty());
        }
    }

    @Test
    void allVirtualFilterFormsKeepTheirCapacityAndTypedMetadata() {
        for (var type : List.of(VirtualFilterType.SMALL, VirtualFilterType.MEDIUM, VirtualFilterType.BIG)) {
            ItemStack stack = type.createStack();
            int last = FilterItemData.getCapacity(stack) - 1;
            FilterItemData.setEntry(stack, last, new ItemStack(Items.IRON_INGOT), ComponentMigrationTest.registries());
            FilterItemData.setEntryStock(stack, last, 7);
            assertTrue(FilterItemData.getEntry(stack, last, ComponentMigrationTest.registries()).is(Items.IRON_INGOT));
            assertEquals(7, stack.get(LogisticsDataComponents.FILTER_ENTRIES).entries().getFirst().counts().stock());
            assertEquals(type, VirtualFilterType.fromStack(stack));
        }
        ItemStack mod = VirtualFilterType.MOD.createStack();
        ModFilterData.addModFilter(mod, "minecraft");
        assertEquals(List.of("minecraft"), mod.get(LogisticsDataComponents.MOD_FILTER).namespaces());
        ItemStack name = VirtualFilterType.NAME.createStack();
        NameFilterData.setNameFilter(name, "Iron");
        assertEquals("Iron", name.get(LogisticsDataComponents.NAME_FILTER).expression());
    }

    @Test
    void recipeDistinguishesAuthoritativeEmptyStateFromPendingLegacy() {
        ItemStack cleared = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.setEntry(cleared, 0, new ItemStack(Items.GOLD_INGOT), ComponentMigrationTest.registries());
        ComponentMigrationTest.legacy(cleared, "ln_filter", ComponentMigrationTest.itemRoot("missing_mod:missing_item"));
        FilterItemData.setEntry(cleared, 0, ItemStack.EMPTY, ComponentMigrationTest.registries());
        FilterItemData.setBlacklist(cleared, false);
        ItemStack blank = new ItemStack(Registration.SMALL_FILTER.get());
        var before = cleared.getComponentsPatch();
        assertFalse(FilterCopyClearRecipe.INSTANCE.matches(CraftingInput.of(2, 1, List.of(cleared, blank)), null));
        assertEquals(before, cleared.getComponentsPatch());
        ItemStack pending = new ItemStack(Registration.SMALL_FILTER.get());
        ComponentMigrationTest.legacy(pending, "ln_filter", ComponentMigrationTest.itemRoot("missing_mod:missing_item"));
        before = pending.getComponentsPatch();
        assertTrue(FilterCopyClearRecipe.INSTANCE.matches(CraftingInput.of(2, 1, List.of(pending, blank)), null));
        assertEquals(before, pending.getComponentsPatch());
    }

    @Test
    void clearedMigratedFilterKeepsUnknownMetadataWithoutConfiguringRecipe() {
        ItemStack cleared = new ItemStack(Registration.SMALL_FILTER.get());
        CompoundTag root = ComponentMigrationTest.itemRoot("minecraft:iron_ingot");
        CompoundTag entry = root.getListOrEmpty("items").getCompoundOrEmpty(0);
        entry.putString("future_entry", "preserve-entry");
        CompoundTag rule = new CompoundTag();
        rule.putString("p", "minecraft:damage");
        rule.putString("o", ">=");
        rule.putInt("v", 1);
        rule.putString("future_rule", "preserve-rule");
        var rules = new net.minecraft.nbt.ListTag(); rules.add(rule); entry.put("nbt_rules", rules);
        ComponentMigrationTest.legacy(cleared, "ln_filter", root);
        FilterComponentData.migrate(cleared, ComponentMigrationTest.registries());
        FilterItemData.setEntry(cleared, 0, ItemStack.EMPTY, ComponentMigrationTest.registries());
        assertFalse(cleared.has(LogisticsDataComponents.FILTER_ENTRIES));
        CompoundTag residual = cleared.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_filter")
                .getListOrEmpty("items").getCompoundOrEmpty(0);
        assertEquals("preserve-entry", residual.getStringOr("future_entry", ""));
        assertEquals("preserve-rule", residual.getListOrEmpty("nbt_rules").getCompoundOrEmpty(0)
                .getStringOr("future_rule", ""));
        var before = cleared.getComponentsPatch();
        assertFalse(FilterComponentData.isConfigured(cleared, ComponentMigrationTest.registries()));
        ItemStack blank = new ItemStack(Registration.SMALL_FILTER.get());
        assertFalse(FilterCopyClearRecipe.INSTANCE.matches(CraftingInput.of(2, 1, List.of(cleared, blank)), null));
        ItemStack configured = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.setEntry(configured, 0, new ItemStack(Items.GOLD_INGOT), ComponentMigrationTest.registries());
        var input = CraftingInput.of(2, 1, List.of(configured, cleared));
        assertTrue(FilterCopyClearRecipe.INSTANCE.matches(input, null));
        ItemStack result = FilterCopyClearRecipe.INSTANCE.assemble(input);
        assertEquals(2, result.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(configured, result));
        assertEquals(before, cleared.getComponentsPatch());
    }

    private static ItemStack populatedStack() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_PICKAXE), ComponentMigrationTest.registries());
        FilterItemData.setEntryStock(filter, 0, 8);
        FilterItemData.setBlacklist(filter, true);
        filter.set(LogisticsDataComponents.TAG_FILTER, new TagFilterConfig("minecraft:logs"));
        filter.set(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(List.of("minecraft")));
        filter.set(LogisticsDataComponents.NAME_FILTER, new NameFilterConfig("Iron", NameMatchScope.NAME));
        filter.set(LogisticsDataComponents.AMOUNT_FILTER, new AmountFilterConfig(25));
        filter.set(LogisticsDataComponents.DURABILITY_FILTER, new DurabilityFilterConfig(45, DurabilityFilterData.Operator.LESS_OR_EQUAL));
        filter.set(LogisticsDataComponents.NBT_FILTER, new NbtFilterConfig(List.of(new NbtFilterConfig.Rule(
                "minecraft:damage", NbtFilterData.Operator.NOT_EQUALS, IntTag.valueOf(4), false))));
        filter.set(LogisticsDataComponents.SLOT_FILTER, new SlotFilterConfig(List.of(2, 4)));
        filter.set(LogisticsDataComponents.WRENCH_MODE, WrenchItem.Mode.COPY_PASTE);
        var channel = new ClipboardSnapshot.ChannelState(true, ChannelMode.EXPORT, ChannelType.ITEM, 8, 5,
                Optional.of(Direction.DOWN), RedstoneMode.ALWAYS_ON, DistributionMode.ROUND_ROBIN, FilterMode.MATCH_ALL, 4, "test");
        var snapshot = new ClipboardSnapshot(List.of(channel), List.of(new ClipboardSnapshot.FilterSlot(0, 1,
                StackSnapshot.of(new ItemStack(Items.PAPER)))), List.of(new ClipboardSnapshot.ItemSlot(0,
                StackSnapshot.of(new ItemStack(Items.DIAMOND)))), Optional.of(new UUID(1, 2)), Optional.of("Network"), false, "Label");
        filter.set(LogisticsDataComponents.WRENCH_CLIPBOARD, WrenchClipboard.valid(snapshot));
        filter.set(LogisticsDataComponents.WRENCH_AE2_LINK, GlobalPos.of(Level.NETHER, new BlockPos(1, 2, 3)));
        filter.set(LogisticsDataComponents.WRENCH_MASS_PLACEMENT, new WrenchMassPlacement(Optional.of(
                new WrenchMassPlacement.Area(GlobalPos.of(Level.OVERWORLD, new BlockPos(4, 5, 6)), Optional.of(new BlockPos(7, 8, 9)))),
                Optional.of(Identifier.parse("minecraft:stone")), List.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 11, 12)))));
        return filter;
    }
}
