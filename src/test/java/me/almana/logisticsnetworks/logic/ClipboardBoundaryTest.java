package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.client.lnet.LnetNetworkFile;
import me.almana.logisticsnetworks.component.*;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardBoundaryTest {
    @BeforeAll
    static void bootstrap() throws Exception { WrenchComponentTest.bootstrap(); }

    @Test
    void legacyAndTypedLnetFixturesKeepEquivalentConfiguration() throws Exception {
        String prefix = "version=1.0\nnetwork=Transport\nlabel=Dock\nvisible=false\nclipboard=";
        String legacy = "{channels:[{index:0,enabled:1b,mode:EXPORT,batch:32,io:all,priority:-5}],renderVisible:0b,"
                + "filters:[{channel:0,slot:0,item:{id:\"logisticsnetworks:name_filter\",count:1,components:{"
                + "\"minecraft:custom_data\":{ln_name_filter:{name:Iron}}}}}]}";
        String typed = "{channels:[{index:0,enabled:1b,mode:EXPORT,batch:32,io:all,priority:-5}],renderVisible:0b,"
                + "filters:[{channel:0,slot:0,item:{id:\"logisticsnetworks:name_filter\",count:1,components:{"
                + "\"logisticsnetworks:name_filter\":{expression:Iron,scope:name}}}}]}";
        var provider = ComponentMigrationTest.registries();
        var source = LnetNetworkFile.readString(prefix + legacy);
        var target = LnetNetworkFile.readString(prefix + typed);
        var first = NodeClipboardConfig.load(source.nodes().getFirst().clipboardTag(), provider);
        var second = NodeClipboardConfig.load(target.nodes().getFirst().clipboardTag(), provider);
        assertNotNull(first); assertNotNull(second);
        assertEquals("Iron", NameFilterData.getNameFilter(first.getFilterItem(0, 0)));
        assertEquals(first.toComponentSnapshot(provider), second.toComponentSnapshot(provider));
        var saved = new LnetNetworkFile(source.networkName(), List.of(new LnetNetworkFile.NodeEntry("Dock", false,
                NodeClipboardConfig.fromComponentSnapshot(first.toComponentSnapshot(provider)).save(provider))));
        var reloaded = LnetNetworkFile.readString(saved.writeString());
        assertEquals("Transport", reloaded.networkName()); assertFalse(reloaded.nodes().getFirst().visible());
        assertEquals(first.toComponentSnapshot(provider), NodeClipboardConfig.load(
                reloaded.nodes().getFirst().clipboardTag(), provider).toComponentSnapshot(provider));
    }

    @Test
    void nestedDeferredFilterResolvesWithoutRevivingAuthoritativeEmptyEntries() throws Exception {
        var filter = new ItemStack(Registration.SMALL_FILTER.get());
        var root = TagParser.parseCompoundFully("{items:[{slot:0,item:{id:\"minecraft:iron_ingot\",count:1,"
                + "components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":1}}},future_entry:keep}]}");
        CompoundTag custom = new CompoundTag(); custom.put("ln_filter", root);
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var config = NodeClipboardConfig.createEmpty(); config.setFilterItem(0, 0, filter);
        var wrench = new ItemStack(Registration.WRENCH.get());
        WrenchItem.setClipboard(wrench, config, null);
        var provider = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var restored = WrenchItem.getClipboard(wrench, provider).getFilterItem(0, 0);
        assertTrue(FilterItemData.getEntry(restored, 0, provider).isEnchanted());
        assertEquals("keep", restored.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_filter")
                .getListOrEmpty("items").getCompoundOrEmpty(0).getStringOr("future_entry", ""));
        filter.set(LogisticsDataComponents.FILTER_ENTRIES, new GeneralFilterConfig(List.of()));
        filter.set(LogisticsDataComponents.FILTER_SETTINGS, FilterSettings.DEFAULT);
        config.setFilterItem(0, 0, filter);
        WrenchItem.setClipboard(wrench, config, null);
        restored = WrenchItem.getClipboard(wrench, provider).getFilterItem(0, 0);
        assertTrue(FilterItemData.getEntry(restored, 0, provider).isEmpty());
        assertFalse(FilterComponentData.isConfigured(restored, provider));
        assertTrue(filter.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_filter").contains("items"));
    }

    @Test
    void migrationAndClearsPreserveUnknownPositionsAndDoNotReviveOldState() throws Exception {
        var wrench = new ItemStack(Registration.WRENCH.get());
        var root = TagParser.parseCompoundFully("{mode:mass_placement,mass_dimension:\"minecraft:overworld\","
                + "mass_corner_a:0L,mass_corner_b:274877911041L,mass_selected_block:\"minecraft:stone\","
                + "mass_selections:[{dimension:\"minecraft:overworld\",pos:0L,future:keep}],"
                + "ae2_link:{dimension:\"minecraft:overworld\",pos:[I;2,3,4],future:keep},future:keep}");
        var custom = new CompoundTag(); custom.put("ln_wrench", root); custom.putInt("other", 5);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        WrenchItem.setColors(wrench, 0x010203, 0x030201);
        assertTrue(LegacyComponentMigration.migrateWrench(wrench, null));
        assertEquals(GlobalPos.of(Level.OVERWORLD, new BlockPos(2, 3, 4)), WrenchItem.getAE2LinkPos(wrench));
        assertEquals(1, WrenchItem.getMassSelections(wrench).size());
        assertEquals(Identifier.parse("minecraft:stone"), WrenchItem.getMassSelectedBlock(wrench));
        WrenchItem.clearAE2Link(wrench); WrenchItem.clearMassSelections(wrench); WrenchItem.clearClipboard(wrench);
        WrenchItem.setMode(wrench, WrenchItem.Mode.WRENCH);
        assertNull(WrenchItem.getAE2LinkPos(wrench)); assertTrue(WrenchItem.getMassSelections(wrench).isEmpty());
        assertNull(WrenchItem.getMassSelectedBlock(wrench));
        assertEquals(0x010203, WrenchItem.getCaseColor(wrench));
        var residual = wrench.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("ln_wrench");
        assertEquals("keep", residual.getCompoundOrEmpty("ae2_link").getStringOr("future", ""));
        assertEquals("keep", residual.getListOrEmpty("mass_selections").getCompoundOrEmpty(0).getStringOr("future", ""));
        var before = wrench.get(DataComponents.CUSTOM_DATA);
        assertTrue(LegacyComponentMigration.migrateWrench(wrench, null));
        assertSame(before, wrench.get(DataComponents.CUSTOM_DATA));
    }

    @Test
    void massSelectionsRetainLimitRemovalAndOwnedCoordinates() {
        var wrench = new ItemStack(Registration.WRENCH.get());
        var positions = java.util.stream.IntStream.range(0, 10_000).mapToObj(x -> GlobalPos.of(Level.OVERWORLD, new BlockPos(x, 0, 0))).toList();
        wrench.set(LogisticsDataComponents.WRENCH_MASS_PLACEMENT, new WrenchMassPlacement(Optional.empty(), Optional.empty(), positions));
        assertFalse(WrenchItem.addMassSelection(wrench, new WrenchItem.MassSelectionTarget(Level.OVERWORLD, new BlockPos(10_000, 0, 0))));
        assertTrue(WrenchItem.toggleMassSelection(wrench, new WrenchItem.MassSelectionTarget(Level.OVERWORLD, BlockPos.ZERO)));
        var mutable = new BlockPos.MutableBlockPos(10_000, 0, 0);
        assertTrue(WrenchItem.addMassSelection(wrench, new WrenchItem.MassSelectionTarget(Level.OVERWORLD, mutable)));
        mutable.set(20_000, 0, 0);
        assertEquals(new BlockPos(10_000, 0, 0), WrenchItem.getMassSelections(wrench).getLast().pos());
        WrenchItem.removeMassSelections(wrench, List.of(new WrenchItem.MassSelectionTarget(Level.OVERWORLD, new BlockPos(10_000, 0, 0))));
        assertEquals(9999, WrenchItem.getMassSelections(wrench).size());
    }
}
