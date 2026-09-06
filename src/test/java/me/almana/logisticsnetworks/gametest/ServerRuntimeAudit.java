package me.almana.logisticsnetworks.gametest;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID)
public final class ServerRuntimeAudit {
    private static final List<Circuit> CIRCUITS = new ArrayList<>();
    private static long warmupStart;
    private static long measuredStart;
    private static int transitions;
    private static int lateTransitions;
    private static boolean running;
    private static boolean stopRequested;
    private static long stopRequestedAt;
    private static long durationNanos;
    private static boolean prepared;

    private ServerRuntimeAudit() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!"soak".equals(System.getProperty("logisticsnetworks.parityAudit"))) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        CIRCUITS.clear();
        CIRCUITS.add(createCircuit(level, new BlockPos(0, 200, 0), "audit-a", 31, 47, Items.COBBLESTONE));
        CIRCUITS.add(createCircuit(level, new BlockPos(48, 200, 0), "audit-b", 37, 53, Items.COBBLESTONE));
        warmupStart = System.nanoTime();
        measuredStart = 0L;
        transitions = 0;
        lateTransitions = 0;
        running = true;
        stopRequested = false;
        prepared = false;
        durationNanos = Long.getLong("logisticsnetworks.parityAuditSeconds", 310L) * 1_000_000_000L;
        System.out.println("PARITY_AUDIT start asyncPlanning=" + Config.asyncPlanning
                + " networkTickingEnabled=" + Config.networkTickingEnabled
                + " dedicated=" + server.isDedicatedServer());
    }

    @SubscribeEvent
    public static void onPost(ServerTickEvent.Post event) {
        if (!running) {
            return;
        }
        long now = System.nanoTime();
        if (!prepared) {
            ServerLevel level = event.getServer().overworld();
            for (Circuit circuit : CIRCUITS) {
                if (level.getEntity(circuit.firstNodeId) == null || level.getEntity(circuit.secondNodeId) == null
                        || !level.isPositionEntityTicking(circuit.firstPos)
                        || !level.isPositionEntityTicking(circuit.secondPos)) {
                    return;
                }
            }
            NetworkRegistry registry = NetworkRegistry.get(level);
            for (Circuit circuit : CIRCUITS) {
                registry.invalidateNetwork(circuit.networkId);
            }
            prepared = true;
            warmupStart = now;
            System.out.println("PARITY_AUDIT entities_ready networks=" + CIRCUITS.size());
            return;
        }
        if (measuredStart == 0L) {
            sample(false);
            boolean moving = CIRCUITS.stream().allMatch(circuit -> circuit.transitions > 0);
            if (moving && now - warmupStart >= 5_000_000_000L) {
                measuredStart = now;
                transitions = 0;
                lateTransitions = 0;
                for (Circuit circuit : CIRCUITS) {
                    circuit.transitions = 0;
                    circuit.lateTransitions = 0;
                    circuit.movedLowerBound = 0;
                }
                event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(),
                        "spark profiler start --thread * --not-combined --interval 4 --force-java-sampler");
                event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(),
                        "spark profiler info");
                System.out.println("PARITY_AUDIT warmup_complete");
            }
            return;
        }
        sample(now - measuredStart >= durationNanos * 4L / 5L);
        if (now - measuredStart < durationNanos) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!stopRequested) {
            String comment = Config.asyncPlanning ? "parity-async-on" : "parity-async-off";
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "spark profiler stop --save-to-file --comment " + comment);
            stopRequested = true;
            stopRequestedAt = now;
            return;
        }
        Path profile = findProfile();
        if (profile == null && now - stopRequestedAt < 30_000_000_000L) {
            return;
        }
        String failure = validate();
        long elapsed = now - measuredStart;
        Path result = Path.of("parity-runtime.txt");
        String text = "asyncPlanning=" + Config.asyncPlanning + "\n"
                + "dedicated=" + server.isDedicatedServer() + "\n"
                + "elapsedNanos=" + elapsed + "\n"
                + "transitions=" + transitions + "\n"
                + "lateTransitions=" + lateTransitions + "\n"
                + "networkA=" + CIRCUITS.get(0).describe() + "\n"
                + "networkB=" + CIRCUITS.get(1).describe() + "\n"
                + "profile=" + (profile == null ? "MISSING" : profile.toAbsolutePath()) + "\n"
                + "result=" + (failure == null ? "PASS" : "FAIL " + failure) + "\n";
        try {
            Files.writeString(result, text);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        for (Circuit circuit : CIRCUITS) {
            circuit.unforce(server.overworld());
        }
        running = false;
        System.out.println("PARITY_AUDIT complete " + text.replace('\n', ' '));
        server.saveEverything(false, true, true);
        server.halt(false);
    }

    private static Path findProfile() {
        Path directory = Path.of("config", "spark");
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sparkprofile"))
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0;
                        } catch (IOException exception) {
                            return false;
                        }
                    }).findFirst().orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static Circuit createCircuit(ServerLevel level, BlockPos origin, String name,
            int forwardDelay, int reverseDelay, net.minecraft.world.item.Item item) {
        BlockPos firstPos = origin;
        BlockPos secondPos = origin.offset(4, 0, 0);
        force(level, firstPos);
        force(level, secondPos);
        level.setBlockAndUpdate(firstPos, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(secondPos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity first = (ChestBlockEntity) level.getBlockEntity(firstPos);
        ChestBlockEntity second = (ChestBlockEntity) level.getBlockEntity(secondPos);
        first.setItem(0, new ItemStack(item, 32));
        first.setChanged();
        LogisticsNodeEntity firstNode = requireNode(level, firstPos);
        LogisticsNodeEntity secondNode = requireNode(level, secondPos);
        LogisticsNetwork network = NetworkRegistry.get(level).createNetwork(name, null);
        join(level, network, firstNode);
        join(level, network, secondNode);
        configure(firstNode.getChannel(0), ChannelMode.EXPORT, forwardDelay);
        configure(secondNode.getChannel(0), ChannelMode.IMPORT, forwardDelay);
        configure(secondNode.getChannel(1), ChannelMode.EXPORT, reverseDelay);
        configure(firstNode.getChannel(1), ChannelMode.IMPORT, reverseDelay);
        NetworkRegistry.get(level).invalidateNetwork(network.getId());
        return new Circuit(network.getId(), firstNode.getUUID(), secondNode.getUUID(), firstPos, secondPos,
                first, second, item, 32, first.countItem(item), second.countItem(item));
    }

    private static void configure(ChannelData channel, ChannelMode mode, int delay) {
        channel.setEnabled(true);
        channel.setMode(mode);
        channel.setType(ChannelType.ITEM);
        channel.setBatchSize(4);
        channel.setTickDelay(delay);
        channel.setIoDirection(Direction.UP);
        channel.setRedstoneMode(RedstoneMode.ALWAYS_ON);
        channel.setDistributionMode(DistributionMode.ROUND_ROBIN);
    }

    private static void join(ServerLevel level, LogisticsNetwork network, LogisticsNodeEntity node) {
        node.setNetworkId(network.getId());
        node.setNetworkName(network.getName());
        NetworkRegistry.get(level).addNodeToNetwork(network.getId(), node.getUUID());
    }

    private static LogisticsNodeEntity requireNode(ServerLevel level, BlockPos pos) {
        LogisticsNodeEntity node = NodePlacementHelper.placeNode(level, pos);
        if (node == null) {
            throw new IllegalStateException("node placement failed");
        }
        return node;
    }

    private static void force(ServerLevel level, BlockPos pos) {
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
    }

    private static void sample(boolean late) {
        for (Circuit circuit : CIRCUITS) {
            int first = circuit.first.countItem(circuit.item);
            int second = circuit.second.countItem(circuit.item);
            if (first != circuit.lastFirst || second != circuit.lastSecond) {
                transitions++;
                circuit.transitions++;
                circuit.movedLowerBound += Math.abs(first - circuit.lastFirst);
                if (late) {
                    lateTransitions++;
                    circuit.lateTransitions++;
                }
                circuit.lastFirst = first;
                circuit.lastSecond = second;
            }
            if (first + second != circuit.total) {
                circuit.failed = "conservation " + first + "+" + second;
            }
        }
    }

    private static String validate() {
        for (Circuit circuit : CIRCUITS) {
            if (circuit.failed != null) {
                return circuit.failed;
            }
            if (circuit.transitions == 0 || circuit.lateTransitions == 0) {
                return "network movement transitions=" + circuit.transitions + " late=" + circuit.lateTransitions;
            }
        }
        if (transitions == 0 || lateTransitions == 0) {
            return "movement transitions=" + transitions + " late=" + lateTransitions;
        }
        return null;
    }

    private static final class Circuit {
        final UUID networkId;
        final UUID firstNodeId;
        final UUID secondNodeId;
        final BlockPos firstPos;
        final BlockPos secondPos;
        final ChestBlockEntity first;
        final ChestBlockEntity second;
        final net.minecraft.world.item.Item item;
        final int total;
        int lastFirst;
        int lastSecond;
        int transitions;
        int lateTransitions;
        long movedLowerBound;
        String failed;

        Circuit(UUID networkId, UUID firstNodeId, UUID secondNodeId, BlockPos firstPos, BlockPos secondPos,
                ChestBlockEntity first, ChestBlockEntity second, net.minecraft.world.item.Item item,
                int total, int lastFirst, int lastSecond) {
            this.networkId = networkId;
            this.firstNodeId = firstNodeId;
            this.secondNodeId = secondNodeId;
            this.firstPos = firstPos;
            this.secondPos = secondPos;
            this.first = first;
            this.second = second;
            this.item = item;
            this.total = total;
            this.lastFirst = lastFirst;
            this.lastSecond = lastSecond;
        }

        String describe() {
            return "id=" + networkId + ",nodes=" + firstNodeId + "/" + secondNodeId
                    + ",counts=" + lastFirst + "/" + lastSecond + ",total=" + total
                    + ",transitions=" + transitions + ",late=" + lateTransitions
                    + ",movedLowerBound=" + movedLowerBound;
        }

        void unforce(ServerLevel level) {
            level.setChunkForced(firstPos.getX() >> 4, firstPos.getZ() >> 4, false);
            level.setChunkForced(secondPos.getX() >> 4, secondPos.getZ() >> 4, false);
        }
    }
}
