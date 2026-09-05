package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageParityTest {
    @BeforeAll
    static void bootstrap() {
        TransferParityTest.bootstrap();
    }

    @Test
    void rejectsTwoNodesAtSameBackingPosition() throws Exception {
        var level = level();
        assertEquals(0, transfer(level, BlockPos.ZERO, level, BlockPos.ZERO, false));
    }

    @Test
    void rejectsSameHandlerAtDifferentPositions() throws Exception {
        var level = level();
        assertEquals(0, transfer(level, BlockPos.ZERO, level, BlockPos.ZERO.east(), true));
    }

    @Test
    void rejectsBothHalvesOfDoubleChest() throws Exception {
        var level = level();
        var left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        var rightPos = BlockPos.ZERO.relative(ChestBlock.getConnectedDirection(left));
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(left);
        when(level.getBlockState(rightPos)).thenReturn(left.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        assertEquals(0, transfer(level, BlockPos.ZERO, level, rightPos, false));
    }

    @Test
    void allowsSameCoordinatesInDifferentDimensions() throws Exception {
        var source = level();
        var target = level();
        when(target.dimension()).thenReturn(Level.NETHER);
        assertEquals(8, transfer(source, BlockPos.ZERO, target, BlockPos.ZERO, false));
    }

    @Test
    void allowsUnconnectedSingleChests() throws Exception {
        var level = level();
        when(level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState());
        assertEquals(8, transfer(level, BlockPos.ZERO, level, BlockPos.ZERO.east(), false));
    }

    @Test
    void chestAllSidesUsesOneCanonicalCapability() throws Exception {
        var level = level();
        when(level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState());
        var node = node(level, BlockPos.ZERO, TransferParityTest.inventory(0));
        var capabilities = new TransferCapabilityCache(node);
        var field = TransferCapabilityCache.class.getDeclaredField("items");
        field.setAccessible(true);
        var caches = (BlockCapabilityCache<?, ?>[]) field.get(capabilities);
        var handlers = new ResourceHandler<?>[6];
        for (var side : Direction.values()) {
            handlers[side.ordinal()] = TransferParityTest.inventory(0);
            var cache = mock(BlockCapabilityCache.class);
            when(cache.getCapability()).thenReturn(handlers[side.ordinal()]);
            caches[side.ordinal()] = cache;
        }
        assertSame(handlers[Direction.UP.ordinal()], capabilities.findItemHandler(null));
        assertSame(handlers[Direction.DOWN.ordinal()], capabilities.findItemHandler(Direction.DOWN));
        when(level.getBlockState(any())).thenReturn(Blocks.FURNACE.defaultBlockState());
        assertEquals(6, capabilities.findItemHandler(null).size());
    }

    @Test
    void equalPriorityImportsUseSortedUuidOrder() {
        var level = level();
        var server = mock(MinecraftServer.class);
        when(server.getAllLevels()).thenReturn(List.of(level));
        var network = new LogisticsNetwork(UUID.randomUUID());
        var low = new UUID(0, 15);
        var high = new UUID(0, 16);
        for (var id : List.of(high, low)) {
            var node = node(level, BlockPos.ZERO, TransferParityTest.inventory(0));
            when(node.getUUID()).thenReturn(id);
            var channel = new ChannelData(true);
            channel.setMode(ChannelMode.IMPORT);
            when(node.getChannel(0)).thenReturn(channel);
            when(level.getEntity(id)).thenReturn(node);
            network.addNode(id);
        }
        try (var lifecycle = mockStatic(ServerLifecycleHooks.class); var upgrades = mockStatic(NodeUpgradeData.class)) {
            lifecycle.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            network.rebuildCache(null);
        }
        assertEquals(List.of(low, high), network.getItemImports()[0].stream().map(NodeRef::nodeId).toList());
    }

    private static ServerLevel level() {
        var level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        return level;
    }

    private static LogisticsNodeEntity node(ServerLevel level, BlockPos pos, ResourceHandler<ItemResource> handler) {
        var node = mock(LogisticsNodeEntity.class);
        when(node.level()).thenReturn(level);
        when(node.getAttachedPos()).thenReturn(pos);
        when(node.getUUID()).thenReturn(UUID.randomUUID());
        when(node.isValidNode()).thenReturn(true);
        var capabilities = mock(TransferCapabilityCache.class);
        when(capabilities.findItemHandler(any())).thenReturn(handler);
        when(node.capabilities()).thenReturn(capabilities);
        return node;
    }

    private static int transfer(ServerLevel sourceLevel, BlockPos sourcePos, ServerLevel targetLevel,
            BlockPos targetPos, boolean sharedHandler) throws Exception {
        var source = TransferParityTest.inventory(8);
        var target = sharedHandler ? source : TransferParityTest.inventory(0);
        var sourceNode = node(sourceLevel, sourcePos, source);
        var targetNode = node(targetLevel, targetPos, target);
        var channel = new ChannelData(true);
        var type = Class.forName(TransferEngine.class.getName() + "$ImportTarget");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var method = TransferEngine.class.getDeclaredMethod("transferItems", LogisticsNodeEntity.class,
                ServerLevel.class, ChannelData.class, int.class, List.class, int.class, Map.class);
        method.setAccessible(true);
        var moved = (int) method.invoke(null, sourceNode, sourceLevel, channel, 0,
                List.of(constructor.newInstance(targetNode, channel, 0)), 8,
                Map.of(sourceNode.getUUID(), true, targetNode.getUUID(), true));
        assertEquals(sharedHandler ? 8 : 8 - moved, source.getAmountAsInt(0));
        assertEquals(sharedHandler ? 8 : moved, target.getAmountAsInt(0));
        return moved;
    }
}
