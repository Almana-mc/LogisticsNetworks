package me.almana.logisticsnetworks.gametest;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.AttachedStorageFilterScanner;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.BuiltinTestFunctions;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID)
public final class RuntimeGameTests {
    private static final Identifier ENVIRONMENT = id("runtime_environment");
    private RuntimeGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("logisticsnetworks.parityAudit")) {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                    ENVIRONMENT, new TestEnvironmentDefinition.AllOf(List.of()));
            register(event, environment, "round_robin", 240, RuntimeGameTests::roundRobin);
            register(event, environment, "priority", 240, RuntimeGameTests::priority);
            register(event, environment, "round_robin_uneven", 240, RuntimeGameTests::roundRobinUneven);
            register(event, environment, "priority_uneven", 240, RuntimeGameTests::priorityUneven);
            register(event, environment, "scanner", 100, RuntimeGameTests::scanner);
            register(event, environment, "double_chest_opposite_masks", 240,
                    RuntimeGameTests::doubleChestOppositeMasks);
            register(event, environment, "shared_destination_capacity", 240,
                    RuntimeGameTests::sharedDestinationCapacity);
        }
    }

    public static void roundRobin(GameTestHelper helper) {
        TransferFixture fixture = fixture(helper, "round-robin", DistributionMode.ROUND_ROBIN, 0, 0, 8);
        helper.succeedWhen(() -> fixture.assertCounts(helper, 0, 4, 4));
    }

    public static void priority(GameTestHelper helper) {
        TransferFixture fixture = fixture(helper, "priority", DistributionMode.PRIORITY, 10, 0, 8);
        helper.succeedWhen(() -> fixture.assertCounts(helper, 0, 8, 0));
    }

    public static void roundRobinUneven(GameTestHelper helper) {
        TransferFixture fixture = fixture(helper, "round-robin-uneven", DistributionMode.ROUND_ROBIN, 0, 0, 8);
        fixture.targetA.setItem(0, new ItemStack(Items.COBBLESTONE, 62));
        fixture.targetA.setItem(1, new ItemStack(Items.DIRT, 64));
        for (int slot = 2; slot < fixture.targetA.getContainerSize(); slot++) {
            fixture.targetA.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        fixture.targetA.setChanged();
        helper.succeedWhen(() -> fixture.assertCounts(helper, 0, 64, 6));
    }

    public static void priorityUneven(GameTestHelper helper) {
        TransferFixture fixture = fixture(helper, "priority-uneven", DistributionMode.PRIORITY, 10, 0, 8);
        fixture.targetA.setItem(0, new ItemStack(Items.COBBLESTONE, 62));
        fixture.targetA.setItem(1, new ItemStack(Items.DIRT, 64));
        for (int slot = 2; slot < fixture.targetA.getContainerSize(); slot++) {
            fixture.targetA.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        fixture.targetA.setChanged();
        helper.succeedWhen(() -> fixture.assertCounts(helper, 0, 64, 6));
    }

    public static void scanner(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 1, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        chest.setItem(1, new ItemStack(Items.DIRT, 3));
        chest.setChanged();
        ServerLevel level = helper.getLevel();
        LogisticsNodeEntity node = requireNode(level, helper.absolutePos(chestPos));
        configure(node.getChannel(0), ChannelMode.IMPORT, DistributionMode.PRIORITY, 0);
        ItemStack filter = Registration.SMALL_FILTER.get().getDefaultInstance();
        AttachedStorageFilterScanner.Result first = AttachedStorageFilterScanner.scan(level, node, node.getChannel(0), filter);
        AttachedStorageFilterScanner.Result second = AttachedStorageFilterScanner.scan(level, node, node.getChannel(0), filter);
        if (first.added() != 2 || !first.storageFound() || first.filterFull() || second.added() != 0
                || !FilterItemData.containsItem(filter, new ItemStack(Items.COBBLESTONE), level.registryAccess())
                || !FilterItemData.containsItem(filter, new ItemStack(Items.DIRT), level.registryAccess())) {
            helper.fail("scanner result mismatch");
        }
        helper.succeed();
    }

    public static void doubleChestOppositeMasks(GameTestHelper helper) {
        BlockPos leftPos = new BlockPos(2, 1, 2);
        BlockState left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos rightPos = leftPos.relative(ChestBlock.getConnectedDirection(left));
        BlockState right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);
        helper.setBlock(leftPos, left);
        helper.setBlock(rightPos, right);

        BlockState actualLeft = helper.getBlockState(leftPos);
        BlockState actualRight = helper.getBlockState(rightPos);
        if (actualLeft.getValue(ChestBlock.TYPE) != ChestType.LEFT
                || actualRight.getValue(ChestBlock.TYPE) != ChestType.RIGHT
                || !leftPos.relative(ChestBlock.getConnectedDirection(actualLeft)).equals(rightPos)
                || !rightPos.relative(ChestBlock.getConnectedDirection(actualRight)).equals(leftPos)) {
            helper.fail("double chest did not remain reciprocal");
        }

        ChestBlockEntity leftChest = helper.getBlockEntity(leftPos, ChestBlockEntity.class);
        ChestBlockEntity rightChest = helper.getBlockEntity(rightPos, ChestBlockEntity.class);
        leftChest.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        leftChest.setChanged();

        ServerLevel level = helper.getLevel();
        BlockPos absoluteLeft = helper.absolutePos(leftPos);
        BlockPos absoluteRight = helper.absolutePos(rightPos);
        LogisticsNodeEntity exporter = requireNode(level, absoluteLeft);
        LogisticsNodeEntity importer = requireNode(level, absoluteRight);
        LogisticsNetwork network = NetworkRegistry.get(level).createNetwork("double-chest-masks", null);
        join(level, network, exporter);
        join(level, network, importer);
        configure(exporter.getChannel(0), ChannelMode.EXPORT, DistributionMode.PRIORITY, 0);
        configure(importer.getChannel(0), ChannelMode.IMPORT, DistributionMode.PRIORITY, 0);

        ResourceHandler<ItemResource> sourceHandler = exporter.capabilities().findItemHandler(Direction.UP);
        ResourceHandler<ItemResource> targetHandler = importer.capabilities().findItemHandler(Direction.UP);
        if (sourceHandler == null || targetHandler == null || sourceHandler.size() != 54 || targetHandler.size() != 54
                || sourceHandler == targetHandler) {
            helper.fail("double chest capability mismatch");
        }
        int sourceSlot = -1;
        for (int slot = 0; slot < sourceHandler.size(); slot++) {
            if (!sourceHandler.getResource(slot).isEmpty()) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0) {
            helper.fail("double chest source slot missing");
        }
        int destinationSlot = sourceSlot < 27 ? sourceSlot + 27 : sourceSlot - 27;
        if (!targetHandler.getResource(destinationSlot).isEmpty()) {
            helper.fail("double chest destination slot occupied");
        }
        exporter.getChannel(0).setFilterItem(0, mappedFilter(level, sourceSlot));
        importer.getChannel(0).setFilterItem(0, mappedFilter(level, destinationSlot));
        long setupTime = level.getGameTime();
        NetworkRegistry.get(level).invalidateNetwork(network.getId());
        int sourceIndex = sourceSlot;
        int destinationIndex = destinationSlot;

        helper.runAfterDelay(80, () -> {
            if (exporter.getLastExecution(0) <= setupTime) {
                helper.fail("double chest exporter was not attempted");
            }
            if (!TransferEngine.isSameItemStorage(level, absoluteLeft, level, absoluteRight)) {
                helper.fail("double chest storage identity missing");
            }
            if (sourceHandler.getResource(sourceIndex).isEmpty()
                    || sourceHandler.getAmountAsLong(sourceIndex) != 8
                    || !targetHandler.getResource(destinationIndex).isEmpty()
                    || leftChest.countItem(Items.COBBLESTONE) + rightChest.countItem(Items.COBBLESTONE) != 8
                    || containsOtherItem(leftChest, Items.COBBLESTONE)
                    || containsOtherItem(rightChest, Items.COBBLESTONE)) {
                helper.fail("double chest disjoint mask moved items");
            }
            helper.succeed();
        });
    }

    public static void sharedDestinationCapacity(GameTestHelper helper) {
        BlockPos sourceAPos = new BlockPos(2, 1, 2);
        BlockPos sourceBPos = new BlockPos(5, 1, 2);
        BlockPos targetPos = new BlockPos(8, 1, 2);
        helper.setBlock(sourceAPos, Blocks.CHEST);
        helper.setBlock(sourceBPos, Blocks.CHEST);
        helper.setBlock(targetPos, Blocks.CHEST);
        ChestBlockEntity sourceA = helper.getBlockEntity(sourceAPos, ChestBlockEntity.class);
        ChestBlockEntity sourceB = helper.getBlockEntity(sourceBPos, ChestBlockEntity.class);
        ChestBlockEntity target = helper.getBlockEntity(targetPos, ChestBlockEntity.class);
        sourceA.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        sourceB.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        target.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
        for (int slot = 1; slot < target.getContainerSize(); slot++) {
            target.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        sourceA.setChanged();
        sourceB.setChanged();
        target.setChanged();

        ServerLevel level = helper.getLevel();
        LogisticsNodeEntity exporterA = requireNode(level, helper.absolutePos(sourceAPos));
        LogisticsNodeEntity exporterB = requireNode(level, helper.absolutePos(sourceBPos));
        LogisticsNodeEntity importer = requireNode(level, helper.absolutePos(targetPos));
        LogisticsNetwork network = NetworkRegistry.get(level).createNetwork("shared-destination", null);
        join(level, network, exporterA);
        join(level, network, exporterB);
        join(level, network, importer);
        configure(exporterA.getChannel(0), ChannelMode.EXPORT, DistributionMode.PRIORITY, 0);
        configure(exporterB.getChannel(0), ChannelMode.EXPORT, DistributionMode.PRIORITY, 0);
        configure(importer.getChannel(0), ChannelMode.IMPORT, DistributionMode.PRIORITY, 0);
        long setupTime = level.getGameTime();
        NetworkRegistry.get(level).invalidateNetwork(network.getId());

        helper.succeedWhen(() -> {
            int countA = sourceA.countItem(Items.COBBLESTONE);
            int countB = sourceB.countItem(Items.COBBLESTONE);
            int targetCobble = target.countItem(Items.COBBLESTONE);
            int dirt = sourceA.countItem(Items.DIRT) + sourceB.countItem(Items.DIRT) + target.countItem(Items.DIRT);
            if (exporterA.getLastExecution(0) <= setupTime || exporterB.getLastExecution(0) <= setupTime
                    || targetCobble != 64 || countA + countB != 12
                    || countA < 0 || countA > 8 || countB < 0 || countB > 8
                    || countA + countB + targetCobble != 76 || dirt != 26 * 64) {
                helper.fail("shared capacity counts=" + countA + "," + countB + "," + targetCobble
                        + ",dirt=" + dirt);
            }
        });
    }

    private static ItemStack mappedFilter(ServerLevel level, int mappedSlot) {
        ItemStack filter = Registration.SMALL_FILTER.get().getDefaultInstance();
        FilterItemData.setEntry(filter, 0, new ItemStack(Items.COBBLESTONE), level.registryAccess());
        FilterItemData.setEntrySlotMapping(filter, 0, new int[] { mappedSlot });
        return filter;
    }

    private static boolean containsOtherItem(ChestBlockEntity chest, net.minecraft.world.item.Item allowed) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && !stack.is(allowed)) {
                return true;
            }
        }
        return false;
    }

    private static TransferFixture fixture(GameTestHelper helper, String name, DistributionMode distribution,
            int priorityA, int priorityB, int sourceCount) {
        BlockPos sourcePos = new BlockPos(2, 1, 2);
        BlockPos targetAPos = new BlockPos(5, 1, 2);
        BlockPos targetBPos = new BlockPos(8, 1, 2);
        helper.setBlock(sourcePos, Blocks.CHEST);
        helper.setBlock(targetAPos, Blocks.CHEST);
        helper.setBlock(targetBPos, Blocks.CHEST);
        ChestBlockEntity source = helper.getBlockEntity(sourcePos, ChestBlockEntity.class);
        ChestBlockEntity targetA = helper.getBlockEntity(targetAPos, ChestBlockEntity.class);
        ChestBlockEntity targetB = helper.getBlockEntity(targetBPos, ChestBlockEntity.class);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, sourceCount));
        source.setChanged();

        ServerLevel level = helper.getLevel();
        LogisticsNodeEntity exporter = requireNode(level, helper.absolutePos(sourcePos));
        LogisticsNodeEntity importerA = requireNode(level, helper.absolutePos(targetAPos));
        LogisticsNodeEntity importerB = requireNode(level, helper.absolutePos(targetBPos));
        LogisticsNetwork network = NetworkRegistry.get(level).createNetwork(name, null);
        join(level, network, exporter);
        join(level, network, importerA);
        join(level, network, importerB);
        configure(exporter.getChannel(0), ChannelMode.EXPORT, distribution, 0);
        configure(importerA.getChannel(0), ChannelMode.IMPORT, DistributionMode.PRIORITY, priorityA);
        configure(importerB.getChannel(0), ChannelMode.IMPORT, DistributionMode.PRIORITY, priorityB);
        NetworkRegistry.get(level).invalidateNetwork(network.getId());
        return new TransferFixture(source, targetA, targetB, sourceCount);
    }

    private static void join(ServerLevel level, LogisticsNetwork network, LogisticsNodeEntity node) {
        node.setNetworkId(network.getId());
        node.setNetworkName(network.getName());
        NetworkRegistry.get(level).addNodeToNetwork(network.getId(), node.getUUID());
    }

    private static void configure(ChannelData channel, ChannelMode mode, DistributionMode distribution, int priority) {
        channel.setEnabled(true);
        channel.setMode(mode);
        channel.setType(ChannelType.ITEM);
        channel.setBatchSize(8);
        channel.setTickDelay(1);
        channel.setIoDirection(Direction.UP);
        channel.setRedstoneMode(RedstoneMode.ALWAYS_ON);
        channel.setDistributionMode(distribution);
        channel.setPriority(priority);
    }

    private static LogisticsNodeEntity requireNode(ServerLevel level, BlockPos pos) {
        LogisticsNodeEntity node = NodePlacementHelper.placeNode(level, pos);
        if (node == null) {
            throw new IllegalStateException("node placement failed");
        }
        return node;
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String path, int maxTicks,
            Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), maxTicks, 0, true,
                Rotation.NONE, false, 1, 1, false, 16);
        event.registerTest(id(path), new RuntimeTest(data, body));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, path);
    }

    private static final class RuntimeTest extends FunctionGameTestInstance {
        private final Consumer<GameTestHelper> body;

        private RuntimeTest(TestData<Holder<TestEnvironmentDefinition<?>>> data,
                Consumer<GameTestHelper> body) {
            super(BuiltinTestFunctions.ALWAYS_PASS, data);
            this.body = body;
        }

        @Override
        public void run(GameTestHelper helper) {
            body.accept(helper);
        }
    }

    private record TransferFixture(ChestBlockEntity source, ChestBlockEntity targetA, ChestBlockEntity targetB,
            int initial) {
        void assertCounts(GameTestHelper helper, int expectedSource, int expectedA, int expectedB) {
            int sourceCount = source.countItem(Items.COBBLESTONE);
            int a = targetA.countItem(Items.COBBLESTONE);
            int b = targetB.countItem(Items.COBBLESTONE);
            if (sourceCount != expectedSource || a != expectedA || b != expectedB
                    || sourceCount + a + b != initial + (expectedA == 64 ? 62 : 0)) {
                helper.fail("counts=" + sourceCount + "," + a + "," + b);
            }
        }
    }
}
