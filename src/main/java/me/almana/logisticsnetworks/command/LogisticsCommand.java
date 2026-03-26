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
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

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
        if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> removeNodes(context.getSource())))
                .then(Commands.literal("cullNetwork")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> cullNetwork(context))));

        LiteralArgumentBuilder<CommandSourceStack> lnAlias = Commands.literal("ln")
                .then(Commands.literal("removeNodes")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> removeNodes(context.getSource())))
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
            if (node.getNetworkId() != null) {
                NetworkRegistry.get(level).removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
            }

            node.dropFilters();
            node.dropUpgrades();
            node.discard(); // Safely removes it without triggering drops again via standard tick()
            removedCount++;
        }

        final int count = removedCount;
        source.sendSuccess(
                () -> Component.literal("Successfully removed " + count + " logistics nodes in this dimension."),
                true);
        return count;
    }

    private static int cullNetwork(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        ServerLevel level = source.getLevel();
        NetworkRegistry registry = NetworkRegistry.get(level);

        // Find network by name
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

        // Check ownership: must own the network, be a teammate, or be op
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && source.getEntity() instanceof ServerPlayer player) {
            if (target.getOwnerUuid() != null
                    && !target.getOwnerUuid().equals(player.getUUID())
                    && !(FTBTeamsCompat.isLoaded() && FTBTeamsCompat.arePlayersInSameTeam(target.getOwnerUuid(), player.getUUID()))) {
                source.sendFailure(Component.literal("You do not own this network."));
                return 0;
            }
        }

        // Remove all node entities belonging to this network
        int removedNodes = 0;
        List<UUID> nodeIds = new ArrayList<>(target.getNodeUuids());
        for (UUID nodeId : nodeIds) {
            for (ServerLevel serverLevel : source.getServer().getAllLevels()) {
                Entity entity = serverLevel.getEntity(nodeId);
                if (entity instanceof LogisticsNodeEntity node) {
                    node.setNetworkId(null);
                    node.dropFilters();
                    node.dropUpgrades();
                    node.discard();
                    removedNodes++;
                    break;
                }
            }
        }

        // Delete the network
        registry.deleteNetwork(target.getId());

        final int finalRemoved = removedNodes;
        final String networkName = name;
        source.sendSuccess(
                () -> Component.literal("Removed network \"" + networkName + "\" and " + finalRemoved + " nodes."),
                true);

        return 1;
    }
}
