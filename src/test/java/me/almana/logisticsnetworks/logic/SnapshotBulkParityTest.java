package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.logic.async.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.p3pp3rf1y.sophisticatedcore.upgrades.stack.StackUpgradeItem;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotBulkParityTest {
    @BeforeAll
    static void bootstrap() { SophisticatedTransferTest.bootstrap(); }
    @BeforeEach
    void mark() { ThreadGuard.markServerThread(); }
    @AfterEach
    void clear() { ThreadGuard.clearServerThread(); }

    @Test
    void registeredUpgradeCaptureMatchesActualBulkTransfer() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        when(target.wrapper.getBaseStackSizeMultiplier()).thenReturn(1);
        var upgrade = StackUpgradeItem.TYPE.create(target.wrapper,
                new ItemStack(ModItems.STACK_UPGRADE_TIER_1.get()), stack -> {});
        when(target.upgrades.getTypeWrappers(StackUpgradeItem.TYPE)).thenReturn(List.of(upgrade));
        target.inventory.setBaseSlotLimit(StackUpgradeItem.getInventorySlotLimit(target.wrapper));
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 125));
        var source = TransferParityTest.inventory(128);
        var snapshot = snapshot(Snapshots.captureItems(source), Snapshots.captureItems(target.inventory, true));
        assertEquals(3, TransferParityTest.move(source, List.of(target.inventory), false));
        int calls = target.inventory.bulkCalls;
        var planned = plan(snapshot);
        assertEquals(3, planned.channels().getFirst().moves().stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
        assertEquals(125, source.getAmountAsInt(0));
        assertEquals(128, target.inventory.getAmountAsInt(0));
        assertEquals(calls, target.inventory.bulkCalls);
        assertEquals(ItemResource.of(Items.IRON_INGOT), planned.channels().getFirst().moves().getFirst().resource());
    }

    @Test
    void occupiedUpgradedMaskedTargetMatchesActualIndexedInsertion() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 128);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 125));
        var filter = me.almana.logisticsnetworks.filter.VirtualFilterType.SMALL.createStack();
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        me.almana.logisticsnetworks.filter.FilterItemData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT), provider);
        me.almana.logisticsnetworks.filter.FilterItemData.setEntrySlotMapping(filter, 0, new int[]{0});
        var filters = new ItemStack[]{filter};
        var source = TransferParityTest.inventory(128);
        var noFilters = new ItemStack[0];
        var snapshot = captureMasked(source, target.inventory, filter);
        assertFalse(snapshot.units().getFirst().targets().getFirst().bulk());
        assertEquals(3, TransferParityTest.move(source, List.of(target.inventory), false, noFilters, filters));
        var planned = plan(snapshot).channels().getFirst().moves();
        assertEquals(3, planned.stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
        assertArrayEquals(new boolean[]{true}, planned.getFirst().targetSlotMask());
    }

    @Test
    void laterUpgradeCapacityChangesDoNotAlterOwnedSnapshot() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 128);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 125));
        var source = TransferParityTest.inventory(128);
        var old = snapshot(Snapshots.captureItems(source), Snapshots.captureItems(target.inventory, true));
        target.inventory.setBaseSlotLimit(256);
        var current = snapshot(Snapshots.captureItems(source), Snapshots.captureItems(target.inventory, true));
        assertEquals(3, plan(old).channels().getFirst().moves().stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
        assertEquals(128, plan(current).channels().getFirst().moves().stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
        assertEquals(128, source.getAmountAsInt(0));
        assertEquals(125, target.inventory.getAmountAsInt(0));
        assertEquals(0, target.inventory.bulkCalls);
    }

    @SuppressWarnings("unchecked")
    private static NetworkSnapshot captureMasked(net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> source,
            net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> target, ItemStack filter) {
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        when(level.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(level.getGameTime()).thenReturn(100L);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var exporter = capturedNode(level, 1, source);
        var importer = capturedNode(level, 2, target);
        var export = new me.almana.logisticsnetworks.data.ChannelData(true);
        export.setMode(me.almana.logisticsnetworks.data.ChannelMode.EXPORT);
        var imports = new me.almana.logisticsnetworks.data.ChannelData(true);
        imports.getFilterItems()[0] = filter;
        when(exporter.getChannel(0)).thenReturn(export);
        List<TransferEngine.ImportTarget>[] refs = new List[9];
        java.util.Arrays.fill(refs, List.of());
        refs[0] = List.of(new TransferEngine.ImportTarget(importer, imports, 0));
        var context = new TransferEngine.NetworkContext(List.of(exporter), java.util.Map.of(), refs, java.util.Map.of(), java.util.Map.of());
        var server = mock(net.minecraft.server.MinecraftServer.class);
        when(server.overworld()).thenReturn(level);
        var network = new me.almana.logisticsnetworks.data.LogisticsNetwork(UUID.randomUUID());
        try (var engine = mockStatic(TransferEngine.class, CALLS_REAL_METHODS)) {
            engine.when(() -> TransferEngine.prepareNetwork(network, server)).thenReturn(context);
            return Snapshots.captureNetwork(network, server, 1, 2).snapshot();
        }
    }

    private static me.almana.logisticsnetworks.entity.LogisticsNodeEntity capturedNode(
            net.minecraft.server.level.ServerLevel level, int x,
            net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> handler) {
        var node = mock(me.almana.logisticsnetworks.entity.LogisticsNodeEntity.class);
        when(node.level()).thenReturn(level);
        when(node.getAttachedPos()).thenReturn(new net.minecraft.core.BlockPos(x, 0, 0));
        when(node.getUUID()).thenReturn(UUID.randomUUID());
        when(node.isValidNode()).thenReturn(true);
        var caps = mock(TransferCapabilityCache.class);
        when(caps.findItemHandler(any())).thenReturn(handler);
        when(node.capabilities()).thenReturn(caps);
        return node;
    }

    private static NetworkSnapshot snapshot(NetworkSnapshot.ItemEndpoint source, NetworkSnapshot.ItemEndpoint target) {
        var noFilters = new ItemStack[0];
        var ref = new NetworkSnapshot.TargetUnit(UUID.randomUUID(), 0, noFilters, FilterMode.MATCH_ALL, false, true, 1, null);
        var unit = new NetworkSnapshot.ChannelUnit(UUID.randomUUID(), 0, 128, noFilters, FilterMode.MATCH_ALL, 0, false, List.of(ref), null, me.almana.logisticsnetworks.data.DistributionMode.PRIORITY);
        return new NetworkSnapshot(UUID.randomUUID(), 0, 0, 0, Long.MAX_VALUE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), List.of(source, target), List.of(unit));
    }

    private static TransferPlan plan(NetworkSnapshot snapshot) throws Exception {
        var task = new FutureTask<>(() -> NetworkPlanner.plan(snapshot));
        new Thread(task, "bulk-planner-test").start();
        return task.get();
    }
}
