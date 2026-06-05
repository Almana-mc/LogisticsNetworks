package me.almana.logisticsnetworks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import me.almana.logisticsnetworks.data.LogisticsNetwork;

public class LogisticsCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_NETWORKS = (context, builder) -> {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        NetworkRegistry registry = NetworkRegistry.get(level);

        Collection<LogisticsNetwork> networks;
        if (source.hasPermission(2)) {
            networks = registry.getAllNetworks().values();
        } else if (source.getEntity() instanceof ServerPlayer player) {
            networks = registry.getNetworksForPlayer(player.getUUID());
        } else {
            networks = List.of();
        }

        List<String> names = new ArrayList<>();
        for (LogisticsNetwork net : networks) {
            names.add(net.getName());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> lnCommand = Commands.literal("logisticsnetworks")
                .then(Commands.literal("removeNodes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> removeNodes(context.getSource())))
                .then(Commands.literal("removeInvalidNodes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> removeInvalidNodes(context.getSource())))
                .then(Commands.literal("cullNetwork")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> cullNetwork(context))));

        LiteralArgumentBuilder<CommandSourceStack> lnAlias = Commands.literal("ln")
                .then(Commands.literal("removeNodes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> removeNodes(context.getSource())))
                .then(Commands.literal("removeInvalidNodes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> removeInvalidNodes(context.getSource())))
                .then(Commands.literal("cullNetwork")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> cullNetwork(context))));

        dispatcher.register(lnCommand);
        dispatcher.register(lnAlias);
    }

    private static int removeNodes(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                AABB.ofSize(source.getPosition(), 60000000, 60000000, 60000000));

        int removedCount = 0;
        for (LogisticsNodeEntity node : nodes) {
            removeNode(level, node);
            removedCount++;
        }

        final int count = removedCount;
        source.sendSuccess(
                () -> Component.literal("Successfully removed " + count + " logistics nodes in this dimension."),
                true);
        return count;
    }

    private static int removeInvalidNodes(CommandSourceStack source) {
        int removedCount = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            NetworkRegistry registry = NetworkRegistry.get(level);
            List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                    AABB.ofSize(Vec3.ZERO, 60000000, 60000000, 60000000));

            for (LogisticsNodeEntity node : nodes) {
                if (!isInvalidNode(node, registry))
                    continue;

                removeNode(level, node);
                removedCount++;
            }
        }

        final int count = removedCount;
        source.sendSuccess(
                () -> Component.literal("Removed " + count + " invalid logistics nodes across all dimensions."),
                true);
        return count;
    }

    private static int cullNetwork(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        ServerLevel level = source.getLevel();
        NetworkRegistry registry = NetworkRegistry.get(level);

        LogisticsNetwork target = null;
        for (LogisticsNetwork network : registry.getAllNetworks().values()) {
            if (network.getName().equals(name)) {
                target = network;
                break;
            }
        }

        if (target == null) {
            source.sendFailure(Component.literal("No network found with name: " + name));
            return 0;
        }

        if (!source.hasPermission(2) && source.getEntity() instanceof ServerPlayer player) {
            if (target.getOwnerUuid() != null
                    && !target.getOwnerUuid().equals(player.getUUID())
                    && !(FTBTeamsCompat.isLoaded() && FTBTeamsCompat.arePlayersInSameTeam(target.getOwnerUuid(), player.getUUID()))) {
                source.sendFailure(Component.literal("You do not own this network."));
                return 0;
            }
        }

        int removedNodes = 0;
        List<UUID> nodeIds = new ArrayList<>(target.getNodeUuids());
        for (UUID nodeId : nodeIds) {
            for (ServerLevel serverLevel : source.getServer().getAllLevels()) {
                Entity entity = serverLevel.getEntity(nodeId);
                if (entity instanceof LogisticsNodeEntity node) {
                    node.setNetworkId(null);
                    removeNode(serverLevel, node);
                    removedNodes++;
                    break;
                }
            }
        }

        registry.deleteNetwork(target.getId());

        final int finalRemoved = removedNodes;
        final String networkName = name;
        source.sendSuccess(
                () -> Component.literal("Removed network \"" + networkName + "\" and " + finalRemoved + " nodes."),
                true);

        return 1;
    }

    private static boolean isInvalidNode(LogisticsNodeEntity node, NetworkRegistry registry) {
        UUID networkId = node.getNetworkId();
        if (!node.isValidNode() || networkId == null)
            return true;

        LogisticsNetwork network = registry.getNetwork(networkId);
        return network == null || !network.getNodeUuids().contains(node.getUUID());
    }

    private static void removeNode(ServerLevel level, LogisticsNodeEntity node) {
        if (node.getNetworkId() != null) {
            NetworkRegistry.get(level).removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
        }

        node.dropFilters();
        node.dropUpgrades();
        node.discard();
    }
}
