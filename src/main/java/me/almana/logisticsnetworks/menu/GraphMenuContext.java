package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.block.ComputerBlockEntity;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record GraphMenuContext(BlockPos computerPos, Identifier computerDimension, UUID networkId) {
    public static GraphMenuContext read(FriendlyByteBuf buf) {
        return new GraphMenuContext(buf.readBlockPos(), buf.readIdentifier(), buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(computerPos);
        buf.writeIdentifier(computerDimension);
        buf.writeUUID(networkId);
    }

    public boolean stillValid(Player player) {
        if (!player.level().dimension().identifier().equals(computerDimension)
                || player.distanceToSqr(computerPos.getCenter()) >= 64.0) return false;
        if (player.level().isClientSide()) return true;
        if (!(player.level().getBlockEntity(computerPos) instanceof ComputerBlockEntity)) return false;
        LogisticsNetwork network = NetworkRegistry.get((ServerLevel) player.level()).getNetwork(networkId);
        return network != null && (NodeAccessPolicy.canAccess(network.getOwnerUuid(), player.getUUID())
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    public boolean canEdit(Player player, LogisticsNodeEntity node) {
        if (node == null || !stillValid(player)) return false;
        if (player.level().isClientSide()) return true;
        return node.isAlive() && node.isValidNode() && networkId.equals(node.getNetworkId())
                && node.level() instanceof ServerLevel level && level.getEntity(node.getUUID()) == node
                && NetworkRegistry.get(level).getNetwork(networkId).getNodeUuids().contains(node.getUUID())
                && node.isOwnedBy(player);
    }
}
