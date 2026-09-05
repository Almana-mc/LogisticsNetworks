package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.*;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WrenchComponentTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        ComponentCodecTest.bootstrap();
        var bind = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
        bind.setAccessible(true);
        bind.invoke(Registration.IRON_UPGRADE.get().builtInRegistryHolder(),
                java.util.List.of(me.almana.logisticsnetworks.registration.ModTags.UPGRADES));
    }

    @Test
    void modeMigratesAndKeepsColorsAndUnknownState() {
        ItemStack wrench = wrench();
        CompoundTag root = new CompoundTag();
        root.putString("mode", "copy_paste"); root.putString("future", "keep");
        CompoundTag custom = new CompoundTag(); custom.put("ln_wrench", root); custom.putInt("other", 9);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        WrenchItem.setColors(wrench, 0x123456, 0x654321);
        assertEquals(WrenchItem.Mode.COPY_PASTE, WrenchItem.getMode(wrench));
        assertEquals(WrenchItem.Mode.COPY_PASTE, wrench.get(LogisticsDataComponents.WRENCH_MODE));
        WrenchItem.setMode(wrench, WrenchItem.Mode.WRENCH);
        assertEquals(0x123456, WrenchItem.getCaseColor(wrench));
        assertEquals(0x654321, WrenchItem.getScreenColor(wrench));
        assertEquals("keep", wrench.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_wrench").getStringOr("future", ""));
        var before = wrench.get(DataComponents.CUSTOM_DATA);
        WrenchItem.getMode(wrench);
        assertSame(before, wrench.get(DataComponents.CUSTOM_DATA));
    }

    @Test
    void settersUseTypedOwnedPositions() {
        ItemStack wrench = wrench();
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        WrenchItem.setAE2Link(wrench, Level.OVERWORLD, pos);
        WrenchItem.setMassSelectionFirstCorner(wrench, Level.OVERWORLD, pos);
        WrenchItem.setMassSelectionSecondCorner(wrench, new BlockPos(4, 5, 6));
        assertNotNull(wrench.get(LogisticsDataComponents.WRENCH_AE2_LINK));
        assertNotNull(wrench.get(LogisticsDataComponents.WRENCH_MASS_PLACEMENT));
        pos.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), WrenchItem.getAE2LinkPos(wrench).pos());
        assertEquals(64, WrenchItem.getMassSelectionArea(wrench, Level.OVERWORLD).volume());
        assertEquals(2048, WrenchItem.getMaxMassNodes());
        var ops = ComponentMigrationTest.registries().createSerializationContext(NbtOps.INSTANCE);
        var restored = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, wrench).getOrThrow()).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(wrench, restored));
    }

    @Test
    void clipboardStoresIndependentTypedSnapshot() {
        ItemStack wrench = wrench();
        var config = NodeClipboardConfig.createEmpty(); config.setChannelEnabled(0, true);
        WrenchItem.setClipboard(wrench, config, ComponentMigrationTest.registries());
        assertNotNull(wrench.get(LogisticsDataComponents.WRENCH_CLIPBOARD));
        config.setChannelEnabled(0, false);
        assertTrue(WrenchItem.getClipboard(wrench, ComponentMigrationTest.registries()).isChannelEnabled(0));
        WrenchItem.clearClipboard(wrench);
        assertNull(WrenchItem.getClipboard(wrench, ComponentMigrationTest.registries()));
    }


    @Test
    void legacyClipboardDefersEnchantedUpgradeAndPreservesUnknownNestedFields() {
        ItemStack wrench = wrench();
        CompoundTag clipboard = NodeClipboardConfig.createEmpty().save(ComponentMigrationTest.registries());
        CompoundTag item = new CompoundTag(); item.putString("id", "logisticsnetworks:iron_upgrade"); item.putInt("count", 1);
        CompoundTag enchantments = new CompoundTag(); enchantments.putInt("minecraft:sharpness", 1);
        CompoundTag components = new CompoundTag(); components.put("minecraft:enchantments", enchantments);
        item.put("components", components);
        CompoundTag entry = new CompoundTag(); entry.putInt("slot", 0); entry.put("item", item); entry.putString("future", "keep");
        var upgrades = new net.minecraft.nbt.ListTag(); upgrades.add(entry); clipboard.put("upgrades", upgrades);
        CompoundTag root = new CompoundTag(); root.put("clipboard", clipboard);
        CompoundTag custom = new CompoundTag(); custom.put("ln_wrench", root);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        WrenchItem.getMode(wrench);
        assertFalse(wrench.has(LogisticsDataComponents.WRENCH_CLIPBOARD));
        var provider = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var loaded = WrenchItem.getClipboard(wrench, provider);
        assertNotNull(loaded);
        assertTrue(loaded.getUpgradeItem(0).isEnchanted());
        var saved = NodeClipboardConfig.load(loaded.save(provider), provider);
        assertTrue(saved.getUpgradeItem(0).isEnchanted());
        assertEquals("keep", wrench.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_wrench")
                .getCompoundOrEmpty("clipboard").getListOrEmpty("upgrades").getCompoundOrEmpty(0).getStringOr("future", ""));
        var before = wrench.get(DataComponents.CUSTOM_DATA);
        WrenchItem.getClipboard(wrench, provider);
        assertSame(before, wrench.get(DataComponents.CUSTOM_DATA));
    }


    @Test
    void savedAndNetworkClipboardOwnsNestedFiltersAndUpgrades() {
        var provider = ComponentMigrationTest.registries();
        ItemStack wrench = wrench();
        ItemStack filter = new ItemStack(Registration.NAME_FILTER.get());
        me.almana.logisticsnetworks.filter.NameFilterData.setNameFilter(filter, "Iron");
        var config = NodeClipboardConfig.createEmpty();
        config.setFilterItem(0, 0, filter);
        config.setUpgradeItem(0, new ItemStack(Registration.IRON_UPGRADE.get()));
        WrenchItem.setClipboard(wrench, config, provider);
        WrenchItem.setColors(wrench, 0xABCDEF, 0xFEDCBA);
        var ops = provider.createSerializationContext(NbtOps.INSTANCE);
        var disk = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, wrench).getOrThrow()).getOrThrow();
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), provider);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, disk);
            var network = ItemStack.STREAM_CODEC.decode(buffer);
            assertTrue(ItemStack.isSameItemSameComponents(wrench, network));
            var loaded = WrenchItem.getClipboard(network, provider);
            assertEquals("Iron", me.almana.logisticsnetworks.filter.NameFilterData.getNameFilter(loaded.getFilterItem(0, 0)));
            assertTrue(loaded.getUpgradeItem(0).is(Registration.IRON_UPGRADE.get()));
            loaded.clear();
            config.clear();
            assertTrue(WrenchItem.getClipboard(network, provider).getUpgradeItem(0).is(Registration.IRON_UPGRADE.get()));
            assertEquals(0xABCDEF, WrenchItem.getCaseColor(network));
        } finally {
            buffer.release();
        }
    }

    @Test
    void clearingDeferredClipboardKeepsOtherStateAndCannotReviveIt() throws Exception {
        ItemStack wrench = wrench();
        var root = net.minecraft.nbt.TagParser.parseCompoundFully("{mode:copy_paste,clipboard:{channels:[],"
                + "upgrades:[{slot:0,item:{id:\"missing:upgrade\",count:1},future:keep}],future:keep},future:keep}");
        var custom = new CompoundTag(); custom.put("ln_wrench", root);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        WrenchItem.setColors(wrench, 0xABABAB, 0xCDCDCD);
        assertFalse(LegacyComponentMigration.migrateWrench(wrench, null));
        WrenchItem.clearClipboard(wrench);
        assertNull(WrenchItem.getClipboard(wrench, ComponentMigrationTest.registries()));
        assertFalse(LegacyComponentMigration.hasWrenchClipboard(wrench));
        assertEquals(WrenchItem.Mode.COPY_PASTE, WrenchItem.getMode(wrench));
        assertEquals(0xABABAB, WrenchItem.getCaseColor(wrench));
        var remaining = wrench.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_wrench");
        assertEquals("keep", remaining.getCompoundOrEmpty("clipboard").getStringOr("future", ""));
        assertEquals("keep", remaining.getCompoundOrEmpty("clipboard").getListOrEmpty("upgrades")
                .getCompoundOrEmpty(0).getStringOr("future", ""));
    }


    @Test
    void successfulClipboardClearLeavesNestedUnknownMetadataInert() throws Exception {
        ItemStack wrench = wrench();
        var root = net.minecraft.nbt.TagParser.parseCompoundFully("{clipboard:{version:1,channels:["
                + "{index:0,enabled:1b,future_channel:keep}],future_clipboard:keep},future_wrench:keep}");
        var custom = new CompoundTag(); custom.put("ln_wrench", root);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var provider = ComponentMigrationTest.registries();
        assertTrue(WrenchItem.getClipboard(wrench, provider).isChannelEnabled(0));
        WrenchItem.clearClipboard(wrench);
        assertNull(WrenchItem.getClipboard(wrench, provider));
        assertFalse(LegacyComponentMigration.hasWrenchClipboard(wrench));
        assertFalse(wrench.has(LogisticsDataComponents.WRENCH_CLIPBOARD));
        assertFalse(wrench.has(LogisticsDataComponents.WRENCH_MASS_PLACEMENT));
        var residual = wrench.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_wrench");
        assertEquals("keep", residual.getStringOr("future_wrench", ""));
        assertEquals("keep", residual.getCompoundOrEmpty("clipboard").getStringOr("future_clipboard", ""));
        assertEquals("keep", residual.getCompoundOrEmpty("clipboard").getListOrEmpty("channels")
                .getCompoundOrEmpty(0).getStringOr("future_channel", ""));
        var before = wrench.get(DataComponents.CUSTOM_DATA);
        assertTrue(LegacyComponentMigration.migrateWrench(wrench, provider));
        assertSame(before, wrench.get(DataComponents.CUSTOM_DATA));
        assertNull(WrenchItem.getClipboard(wrench, provider));
    }

    private static ItemStack wrench() { return new ItemStack(Registration.WRENCH.get()); }
}
