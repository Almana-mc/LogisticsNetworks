package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.mockito.MockedStatic;
import java.util.*;
import static org.mockito.Mockito.*;

class CommitFixture implements AutoCloseable {
    final ServerLevel level = mock(ServerLevel.class);
    final MinecraftServer server = mock(MinecraftServer.class);
    final LogisticsNetwork network = spy(new LogisticsNetwork(UUID.randomUUID()));
    final ChannelData export = channel(ChannelMode.EXPORT);
    final List<ChannelData> imports = new ArrayList<>();
    final List<LogisticsNodeEntity> targets = new ArrayList<>();
    final LogisticsNodeEntity source;
    final TransferEngine.NetworkContext context;
    final MockedStatic<TransferEngine> engine = mockStatic(TransferEngine.class, CALLS_REAL_METHODS);
    final MockedStatic<NetworkRegistry> registries = mockStatic(NetworkRegistry.class);
    final long[] lastExecution = new long[9];

    @SafeVarargs
    @SuppressWarnings("unchecked")
    CommitFixture(ResourceHandler<ItemResource> sourceHandler, ResourceHandler<ItemResource>... handlers) {
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getGameTime()).thenReturn(100L);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.registryAccess()).thenReturn(PlannerDifferentialTest.provider());
        when(server.overworld()).thenReturn(level);
        source = node(1, sourceHandler);
        when(source.getChannel(0)).thenReturn(export);
        when(source.getLastExecution(anyInt())).thenAnswer(c -> lastExecution[c.getArgument(0)]);
        doAnswer(c -> { lastExecution[c.getArgument(0)] = c.getArgument(1); return null; })
                .when(source).setLastExecution(anyInt(), anyLong());
        List<TransferEngine.ImportTarget>[] refs = new List[9];
        Arrays.fill(refs, List.of());
        refs[0] = new ArrayList<>();
        for (int i = 0; i < handlers.length; i++) {
            var target = node(i + 2, handlers[i]);
            var channel = channel(ChannelMode.IMPORT);
            when(target.getChannel(0)).thenReturn(channel);
            imports.add(channel);
            targets.add(target);
            refs[0].add(new TransferEngine.ImportTarget(target, channel, 0));
        }
        var nodes = new ArrayList<LogisticsNodeEntity>();
        nodes.add(source);
        nodes.addAll(targets);
        context = new TransferEngine.NetworkContext(nodes, Map.of(), refs, Map.of(), Map.of(source.getUUID(), 3));
        engine.when(() -> TransferEngine.prepareNetwork(network, server)).thenReturn(context);
        var registry = mock(NetworkRegistry.class);
        var telemetry = mock(TelemetryManager.class);
        when(telemetry.isActive(network.getId())).thenReturn(true);
        when(registry.getTelemetryManager()).thenReturn(telemetry);
        registries.when(() -> NetworkRegistry.get(level)).thenReturn(registry);
    }

    TransferPlan plan() throws Exception {
        var snapshot = Snapshots.captureNetwork(network, server, 42, 1000).snapshot();
        return PlannerDifferentialTest.worker(() -> NetworkPlanner.plan(snapshot));
    }

    TransferCommitter.ItemCommitResult commit(TransferPlan plan) {
        return TransferCommitter.commitItems(plan, network, server, 42);
    }

    private LogisticsNodeEntity node(int x, ResourceHandler<ItemResource> handler) {
        var node = mock(LogisticsNodeEntity.class);
        when(node.level()).thenReturn(level);
        when(node.getAttachedPos()).thenReturn(new BlockPos(x, 0, 0));
        when(node.getUUID()).thenReturn(UUID.randomUUID());
        when(node.isValidNode()).thenReturn(true);
        var caps = mock(TransferCapabilityCache.class);
        when(caps.findItemHandler(any())).thenReturn(handler);
        when(node.capabilities()).thenReturn(caps);
        network.addNode(node.getUUID());
        return node;
    }

    static ChannelData channel(ChannelMode mode) {
        var channel = new ChannelData(true);
        channel.setMode(mode);
        return channel;
    }

    @Override
    public void close() {
        engine.close();
        registries.close();
    }
}
