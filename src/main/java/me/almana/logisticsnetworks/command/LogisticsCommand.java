package me.almana.logisticsnetworks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYERS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildCommand(Commands.literal("logisticsnetworks")));
        dispatcher.register(buildCommand(Commands.literal("ln")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("removeNodes")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> removeNodes(context.getSource())))
                .then(Commands.literal("cullNetwork")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> cullNetwork(context))))
                .then(Commands.literal("locate")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("network", StringArgumentType.string())
                                .suggests(SUGGEST_NETWORKS)
                                .executes(context -> locate(context, null))
                                .then(Commands.argument("owner", StringArgumentType.string())
                                        .suggests(SUGGEST_PLAYERS)
                                        .executes(context -> locate(context, StringArgumentType.getString(context, "owner"))))))
                .then(Commands.literal("list")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "page")))));
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

    private record LoadedNode(ServerLevel level, BlockPos pos) {}

    private static final int CHUNK_LINE_CAP = 15;
    private static final int PAGE_SIZE = 10;

    private static int locate(CommandContext<CommandSourceStack> context, String ownerArg) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(context, "network");
        NetworkRegistry registry = NetworkRegistry.get(source.getLevel());

        UUID ownerFilter = null;
        if (ownerArg != null) {
            ownerFilter = resolvePlayerUuid(server, ownerArg);
            if (ownerFilter == null) {
                source.sendFailure(Component.literal("Unknown player: " + ownerArg));
                return 0;
            }
        }

        List<LogisticsNetwork> matches = new ArrayList<>();
        for (LogisticsNetwork net : registry.getAllNetworks().values()) {
            if (!net.getName().equals(name)) continue;
            if (ownerFilter != null && !ownerFilter.equals(net.getOwnerUuid())) continue;
            matches.add(net);
        }

        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("No network found with name: " + name));
            return 0;
        }
        if (matches.size() > 1) {
            List<String> owners = new ArrayList<>();
            for (LogisticsNetwork net : matches) owners.add(ownerName(server, net.getOwnerUuid()));
            source.sendFailure(Component.literal("Multiple networks named \"" + name
                    + "\" (owners: " + String.join(", ", owners) + "). Specify owner."));
            return 0;
        }

        LogisticsNetwork target = matches.get(0);
        List<LoadedNode> loaded = collectLoadedNodes(target, server);

        source.sendSuccess(() -> Component.literal("Network \"" + target.getName() + "\" (owner "
                + ownerName(server, target.getOwnerUuid()) + "), " + target.getNodeUuids().size()
                + " nodes, " + loaded.size() + " loaded").withStyle(ChatFormatting.GOLD), false);

        if (loaded.isEmpty()) {
            source.sendSuccess(() -> Component.literal(target.getNodeUuids().size()
                    + " nodes, none in loaded chunks").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        LoadedNode nearest = nearestNode(loaded, source);
        source.sendSuccess(() -> Component.literal("Nearest: ").withStyle(ChatFormatting.GRAY)
                .append(posLabel(nearest)).append(" ").append(tpComponent(nearest.level(), nearest.pos())), false);

        Map<String, List<LoadedNode>> byChunk = new LinkedHashMap<>();
        for (LoadedNode n : loaded) {
            String key = n.level().dimension().identifier() + " " + (n.pos().getX() >> 4) + " " + (n.pos().getZ() >> 4);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
        }

        int shown = 0;
        for (Map.Entry<String, List<LoadedNode>> entry : byChunk.entrySet()) {
            if (shown >= CHUNK_LINE_CAP) break;
            LoadedNode rep = entry.getValue().get(0);
            int cx = rep.pos().getX() >> 4;
            int cz = rep.pos().getZ() >> 4;
            source.sendSuccess(() -> Component.literal("- " + rep.level().dimension().identifier()
                    + " chunk " + cx + "," + cz + " (" + entry.getValue().size() + " nodes) ")
                    .withStyle(ChatFormatting.GRAY).append(tpComponent(rep.level(), rep.pos())), false);
            shown++;
        }
        if (byChunk.size() > shown) {
            int more = byChunk.size() - shown;
            source.sendSuccess(() -> Component.literal("...and " + more + " more chunks")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    private static int list(CommandSourceStack source, int page) {
        MinecraftServer server = source.getServer();
        NetworkRegistry registry = NetworkRegistry.get(source.getLevel());

        List<LogisticsNetwork> all = new ArrayList<>(registry.getAllNetworks().values());
        all.sort(Comparator.comparingInt((LogisticsNetwork n) -> n.getNodeUuids().size()).reversed());

        if (all.isEmpty()) {
            source.sendFailure(Component.literal("No networks exist."));
            return 0;
        }

        int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.max(1, Math.min(page, totalPages));
        int start = (current - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());

        source.sendSuccess(() -> Component.literal(all.size() + " networks  (page " + current + "/" + totalPages + ")")
                .withStyle(ChatFormatting.GOLD), false);

        for (int i = start; i < end; i++) {
            LogisticsNetwork net = all.get(i);
            List<LoadedNode> loaded = collectLoadedNodes(net, server);
            MutableComponent line = Component.literal("- " + net.getName() + " (" + ownerName(server, net.getOwnerUuid())
                    + ") " + net.getNodeUuids().size() + " nodes ").withStyle(ChatFormatting.GRAY);
            if (loaded.isEmpty()) {
                line.append(Component.literal("[unloaded]").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                LoadedNode n = loaded.get(0);
                line.append(tpComponent(n.level(), n.pos()));
            }
            source.sendSuccess(() -> line, false);
        }

        if (totalPages > 1) {
            MutableComponent nav = Component.empty();
            if (current > 1) nav.append(pageArrow("<< Prev", current - 1)).append(Component.literal("  "));
            if (current < totalPages) nav.append(pageArrow("Next >>", current + 1));
            source.sendSuccess(() -> nav, false);
        }
        return 1;
    }

    private static MutableComponent pageArrow(String label, int page) {
        return Component.literal("[" + label + "]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand("/ln list " + page))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Page " + page))));
    }

    private static List<LoadedNode> collectLoadedNodes(LogisticsNetwork network, MinecraftServer server) {
        List<LoadedNode> result = new ArrayList<>();
        for (UUID nodeId : network.getNodeUuids()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(nodeId);
                if (entity instanceof LogisticsNodeEntity node) {
                    result.add(new LoadedNode(level, node.getAttachedPos()));
                    break;
                }
            }
        }
        return result;
    }

    private static LoadedNode nearestNode(List<LoadedNode> loaded, CommandSourceStack source) {
        Vec3 origin = source.getPosition();
        ServerLevel sourceLevel = source.getLevel();
        LoadedNode best = null;
        double bestDist = Double.MAX_VALUE;
        for (LoadedNode n : loaded) {
            if (n.level() != sourceLevel) continue;
            double d = n.pos().getCenter().distanceToSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = n;
            }
        }
        return best != null ? best : loaded.get(0);
    }

    private static Component posLabel(LoadedNode n) {
        return Component.literal(n.level().dimension().identifier() + " "
                + n.pos().getX() + " " + n.pos().getY() + " " + n.pos().getZ());
    }

    private static MutableComponent tpComponent(ServerLevel level, BlockPos pos) {
        String cmd = "/execute in " + level.dimension().identifier() + " run tp @s "
                + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ();
        return Component.literal("[TP]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Teleport here"))));
    }

    private static UUID resolvePlayerUuid(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        return online != null ? online.getUUID() : null;
    }

    private static String ownerName(MinecraftServer server, UUID ownerUuid) {
        if (ownerUuid == null) return "unowned";
        ServerPlayer online = server.getPlayerList().getPlayer(ownerUuid);
        return online != null ? online.getScoreboardName() : ownerUuid.toString().substring(0, 8);
    }
}
