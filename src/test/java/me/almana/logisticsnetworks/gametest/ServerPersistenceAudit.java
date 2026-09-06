package me.almana.logisticsnetworks.gametest;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID)
public final class ServerPersistenceAudit {
    private static final Pattern CIRCUIT = Pattern.compile(
            "network[AB]=id=([^,]+),nodes=([^/]+)/([^,]+),counts=.*");
    private static List<SavedCircuit> circuits;
    private static boolean running;

    private ServerPersistenceAudit() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!"verify".equals(System.getProperty("logisticsnetworks.parityAudit"))) {
            return;
        }
        try {
            List<Matcher> matches = Files.readAllLines(Path.of("parity-runtime.txt")).stream()
                    .map(CIRCUIT::matcher).filter(Matcher::matches).toList();
            if (matches.size() != 2) {
                throw new IllegalStateException("saved circuit IDs missing");
            }
            circuits = List.of(
                    saved(matches.get(0), "audit-a", new BlockPos(0, 200, 0), new BlockPos(4, 200, 0)),
                    saved(matches.get(1), "audit-b", new BlockPos(48, 200, 0), new BlockPos(52, 200, 0)));
            ServerLevel level = event.getServer().overworld();
            for (SavedCircuit circuit : circuits) {
                force(level, circuit.firstPos, true);
                force(level, circuit.secondPos, true);
            }
            running = true;
            System.out.println("PARITY_RELOAD start dedicated=" + event.getServer().isDedicatedServer());
        } catch (Throwable throwable) {
            finish(event.getServer(), "FAIL " + throwable);
        }
    }

    @SubscribeEvent
    public static void onPost(ServerTickEvent.Post event) {
        if (!running) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        for (SavedCircuit circuit : circuits) {
            if (!(level.getEntity(circuit.firstNode) instanceof LogisticsNodeEntity)
                    || !(level.getEntity(circuit.secondNode) instanceof LogisticsNodeEntity)
                    || !level.isPositionEntityTicking(circuit.firstPos)
                    || !level.isPositionEntityTicking(circuit.secondPos)) {
                return;
            }
        }
        try {
            NetworkRegistry registry = NetworkRegistry.get(level);
            for (SavedCircuit circuit : circuits) {
                LogisticsNetwork network = registry.getNetwork(circuit.network);
                require(network != null, circuit.name + " network missing");
                require(circuit.name.equals(network.getName()), circuit.name + " name mismatch");
                require(network.getNodeUuids().equals(java.util.Set.of(circuit.firstNode, circuit.secondNode)),
                        circuit.name + " membership mismatch " + network.getNodeUuids());
                checkNode(level, circuit.firstNode, circuit.network, circuit.firstPos);
                checkNode(level, circuit.secondNode, circuit.network, circuit.secondPos);
            }
            finish(event.getServer(), "PASS networks=2 nodes=4");
        } catch (Throwable throwable) {
            finish(event.getServer(), "FAIL " + throwable);
        }
    }

    private static void checkNode(ServerLevel level, UUID id, UUID network, BlockPos attached) {
        LogisticsNodeEntity node = (LogisticsNodeEntity) level.getEntity(id);
        require(network.equals(node.getNetworkId()), "node network mismatch " + id);
        require(attached.equals(node.getAttachedPos()), "node attachment mismatch " + id);
        require(node.isValidNode(), "node invalid " + id);
    }

    private static SavedCircuit saved(Matcher matcher, String name, BlockPos firstPos, BlockPos secondPos) {
        return new SavedCircuit(UUID.fromString(matcher.group(1)), UUID.fromString(matcher.group(2)),
                UUID.fromString(matcher.group(3)), name, firstPos, secondPos);
    }

    private static void finish(MinecraftServer server, String result) {
        try {
            Files.writeString(Path.of("parity-reload.txt"), "result=" + result + "\n");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        if (circuits != null) {
            for (SavedCircuit circuit : circuits) {
                force(server.overworld(), circuit.firstPos, false);
                force(server.overworld(), circuit.secondPos, false);
            }
        }
        running = false;
        System.out.println("PARITY_RELOAD complete result=" + result);
        server.saveEverything(false, true, true);
        server.halt(false);
    }

    private static void force(ServerLevel level, BlockPos pos, boolean forced) {
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, forced);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record SavedCircuit(UUID network, UUID firstNode, UUID secondNode, String name,
            BlockPos firstPos, BlockPos secondPos) {
    }
}
