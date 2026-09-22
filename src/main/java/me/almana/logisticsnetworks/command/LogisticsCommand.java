package me.almana.logisticsnetworks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

        Collection<LogisticsNetwork> networks = source.getEntity() instanceof ServerPlayer player
                ? registry.getVisibleNetworks(player)
                : registry.getAllNetworks().values();

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
                .then(Commands.literal("cullNetwork")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> cullNetwork(context))))
                .then(adminCommand());

        LiteralArgumentBuilder<CommandSourceStack> lnAlias = Commands.literal("ln")
                .then(Commands.literal("removeNodes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> removeNodes(context.getSource())))
                .then(Commands.literal("cullNetwork")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> cullNetwork(context))))
                .then(adminCommand());

        dispatcher.register(lnCommand);
        dispatcher.register(lnAlias);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("admin")
                .executes(context -> toggleOwnAdminMode(context.getSource()))
                .then(Commands.literal("mode")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> toggleAdminMode(
                                        context.getSource(), EntityArgument.getPlayer(context, "player")))));
    }

    private static int toggleOwnAdminMode(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = NodeAccessPolicy.isAdminMode(player);
        if (!source.hasPermission(2) && !enabled) {
            source.sendFailure(Component.translatable("message.logisticsnetworks.admin.enable_denied"));
            return 0;
        }
        return toggleAdminMode(source, player);
    }

    private static int toggleAdminMode(CommandSourceStack source, ServerPlayer player) {
        boolean enabled = !NodeAccessPolicy.isAdminMode(player);
        NodeAccessPolicy.setAdminMode(player, enabled);
        refreshNetworkList(player);

        String stateKey = enabled
                ? "message.logisticsnetworks.admin.enabled"
                : "message.logisticsnetworks.admin.disabled";
        if (source.getEntity() == player) {
            source.sendSuccess(() -> Component.translatable(stateKey), false);
        } else {
            player.sendSystemMessage(Component.translatable(stateKey));
            String targetKey = enabled
                    ? "message.logisticsnetworks.admin.enabled_other"
                    : "message.logisticsnetworks.admin.disabled_other";
            source.sendSuccess(() -> Component.translatable(targetKey, player.getDisplayName()), true);
        }
        return 1;
    }

    private static void refreshNetworkList(ServerPlayer player) {
        if (player.containerMenu instanceof ComputerMenu menu) {
            menu.requestNetworkList(player);
        } else if (player.containerMenu instanceof NodeMenu || player.containerMenu instanceof ClipboardMenu) {
            NodeMenu.sendAvailableNetworkListToClient(player);
        }
    }

    private static int removeNodes(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                AABB.ofSize(source.getPosition(), 60000000, 60000000, 60000000));

        int removedCount = 0;
        for (LogisticsNodeEntity node : nodes) {
            if (node.getNetworkId() != null) {
                NetworkRegistry registry = NetworkRegistry.get(level);
                registry.removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
                registry.evictCapabilities(level, node.getAttachedPos());
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

        if (source.getEntity() instanceof ServerPlayer player) {
            if (!NodeAccessPolicy.canAccess(target.getOwnerUuid(), player)) {
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
